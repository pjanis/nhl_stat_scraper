(ns nhl-stat-scraper.database.populate
  (:require
    [clojure.data]
    [clojure.java.jdbc :as jdbc]
    [clojure.java.io :as io]
    [clj-time.core]
    [clj-time.local]
    [nhl-stat-scraper.parse.json :as parse-json]
    [nhl-stat-scraper.parse.statsapi-json :as parse-statsapi]
    [nhl-stat-scraper.parse.html :as parse-html]
    [nhl-stat-scraper.common.games]
    [nhl-stat-scraper.common.parse]
    [nhl-stat-scraper.database.teams]
    [nhl-stat-scraper.database.games]
    [nhl-stat-scraper.database.divisions]
    [nhl-stat-scraper.database.conferences]
    [nhl-stat-scraper.database.plays]
    [nhl-stat-scraper.database.players]
    [nhl-stat-scraper.database.populate-helpers :as helpers]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn season-start-date [season]
  (case season
    2019 "2019-10-01" ;TODO placeholder until announced
    2018 "2018-10-03"
    2017 "2017-10-04"
    2016 "2016-10-12"
    2015 "2015-10-07"
    2014 "2014-10-08"
    2013 "2013-10-01"
    2012 "2013-01-19"
    2011 "2011-10-06"
    2010 "2010-10-07"
    2009 "2009-10-01"
    2008 "2008-10-04"
    2007 "2007-09-29"
    2006 "2006-10-04"
    2005 "2005-10-05"))

(defn season-from-date [date]
  (let [year (nhl-stat-scraper.common.parse/parse-int (subs date 0 4))]
    (if (> 0 (compare (season-start-date year) date))
      year
      (- year 1))))

(defn season-api [season & _]
  (cond
    (< season 2017) :nhl
    :else :statsapi))

(defn date-api [date & _]
  (cond
    (< 0 (compare (season-start-date 2017) date)) :nhl
    :else :statsapi))

(def league-structure (into {} (for [[year league] (nhl-stat-scraper.common.parse/slurp-json (io/resource "data/league_structure.json"))]
                                 [(nhl-stat-scraper.common.parse/parse-int year) league])))

(defn conference-divisions [structure conference] (keys (get structure conference)))
(defn conference-division-teams [structure conference division] (get (get structure conference) division))

(defn populate-season-teams [season team-names]
  (nhl-stat-scraper.database.teams/add-new-season season team-names db-pg/pg-datasource))

(defn populate-season-divisions [season division-names]
  (nhl-stat-scraper.database.divisions/add-new-season season division-names db-pg/pg-datasource))

(defn populate-season-conferences [season conference-names]
  (nhl-stat-scraper.database.conferences/add-new-season season conference-names db-pg/pg-datasource))

(defn populate-season-league [season league]
  (let [conferences (nhl-stat-scraper.database.conferences/add-new-season season (keys league) db-pg/pg-datasource)]
    (doseq [conference conferences]
      (let [conference-name (get conference :name)
            divisions (nhl-stat-scraper.database.divisions/add-new-season season (conference-divisions league conference-name) db-pg/pg-datasource)]
        (nhl-stat-scraper.database.divisions/add-new-season-to-conference-divisions season (get conference :db_id) (map #(get % :db_id) divisions))
        (doseq [division divisions]
          (let [division-name (get division :name)
                teams (nhl-stat-scraper.database.teams/add-new-season season (conference-division-teams league conference-name division-name) db-pg/pg-datasource)]
            (nhl-stat-scraper.database.teams/add-new-season-to-division-teams season (get division :db_id) (map #(get % :db_id) teams))))))))

(defn populate-league-structure
  ([] (populate-league-structure league-structure))
  ([structure] (populate-league-structure structure db-pg/pg-datasource))
  ([structure datasource]
    (let [seasons (apply sorted-set (keys structure))
          this-year (clj-time.core/year (clj-time.core/now))]
      (doseq [season (range (first seasons) (inc this-year))]
        (populate-season-league season (get structure (first (rsubseq seasons <= season))))))))

(defmulti update-team-details-for-season season-api)
(defmethod update-team-details-for-season :nhl
  ([season] (update-team-details-for-season season db-pg/pg-datasource))
  ([season datasource]
    (let [db-teams (nhl-stat-scraper.database.teams/get-teams-by-season season)
          incomplete-teams (filter #(or (nil? (get % :common_name)) (nil? (get % :abreviation ))) db-teams)]
      (if-not (empty? incomplete-teams)
        (let [json-teams (nhl-stat-scraper.parse.json/all-teams (count db-teams) (season-start-date season) #{})]
          (doseq [team incomplete-teams]
            (nhl-stat-scraper.database.teams/update-team (get team :db_id) (first (filter #(= (get team :name) (get % :name)) json-teams)))))))))
(defmethod update-team-details-for-season :statsapi
  ([season] (update-team-details-for-season season db-pg/pg-datasource))
  ([season datasource]
    (let [db-teams (nhl-stat-scraper.database.teams/get-teams-by-season season)
          incomplete-teams (filter #(or (nil? (get % :common_name)) (nil? (get % :abreviation )) (nil? (get % :statsapi_id))) db-teams)]
      (if-not (empty? incomplete-teams)
        (let [json-teams (parse-statsapi/teams)]
          (doseq [team incomplete-teams]
            (nhl-stat-scraper.database.teams/update-team (get team :db_id) (first (filter #(= (get team :name) (get % :name)) json-teams)))))))))

(defn rebuild-league-structure
  "Used to migrate from old league structure to new. Shouldn't be needed most years"
  ([]
    (nhl-stat-scraper.database.general/reset-table "conference_divisions")
    (nhl-stat-scraper.database.general/reset-table "division_teams")
    (nhl-stat-scraper.database.general/reset-table "divisions")
    (nhl-stat-scraper.database.general/reset-table "conferences")
    (let [last-active-seasons (nhl-stat-scraper.database.teams/last-active-seasons)]
      (nhl-stat-scraper.database.teams/set-all-seasons [2125 2125]) ;Update in database.teams/last-season if changed
      (populate-league-structure)
      (let [old-teams (nhl-stat-scraper.database.teams/get-teams-by-season 2125)]
        (doseq [old-team old-teams]
          (if-let [new-team (nhl-stat-scraper.database.teams/get-team-by-name-and-season
                              (get old-team :name)
                              (get last-active-seasons (get old-team :db_id)))]
            (do
              (nhl-stat-scraper.database.games/replace-team-id (get old-team :db_id) (get new-team :db_id))
              (nhl-stat-scraper.database.general/delete-row "db_id" (get old-team :db_id) "teams"))))))
    (doseq [season (range 2005 2018)]
      (update-team-details-for-season season))))

(defn update-league-structure
  ([] (update-league-structure (inc (nhl-stat-scraper.database.teams/last-season)) league-structure))
  ([season structure]
    (if (nhl-stat-scraper.common.games/in? (keys structure) season)
      (rebuild-league-structure)
      (let [current-structure-year (->> (keys structure)
                                        (sort)
                                        (filter #(> season %))
                                        (last))]
        (populate-season-league season (get structure current-structure-year))))))

(defn populate-team-colors []
  (nhl-stat-scraper.database.teams/populate-team-colors db-pg/pg-datasource (parse-html/wikipedia-team-info)))

;Only for pre-2017 seasons
(defn populate-game-summaries-from-parsed-json [parsed-json]
  (if-let [game-summaries (seq (filter #(empty? (nhl-stat-scraper.database.games/db-game-summary-ids (:game_id %)))
                                       (parse-json/game-summaries-from-parsed-json parsed-json)))]
    (nhl-stat-scraper.database.games/insert-game-summaries db-pg/pg-datasource game-summaries)
    nil))

;Only for pre-2017 seasons
(defn populate-game-summaries-on [date]
  (if-let [game-summaries (seq (filter #(empty? (nhl-stat-scraper.database.games/db-game-summary-ids (:game_id %)))
                                       (parse-json/game-summaries-on date)))]
    (nhl-stat-scraper.database.games/insert-game-summaries db-pg/pg-datasource game-summaries)
    nil))

;Only for pre-2017 seasons
(defn populate-game-summaries
  ([] (populate-game-summaries "2015-10-07"))
  ([date] (populate-game-summaries date nil))
  ([date season]
    (if (> 0 (compare (season-start-date 2017) date))
      (print "populate-game-summaries only works for season before 2017, use populate-season-game-summaries instead")
      (let [parsed-json (parse-json/parsed-date-json date)]
        (if (or (nil? season)
                (and (not (empty? (get parsed-json "games")))
                     (= season
                        (nhl-stat-scraper.common.games/season-from-id (get (first (get parsed-json "games")) "id")))))
          (do
            (print (format "Populating %s\n" date)) ;TODO move to timbre logging
            (populate-game-summaries-from-parsed-json parsed-json)
            (let [next-date (parse-json/next-date-from-parsed-json parsed-json)]
              (if (not-empty next-date) (populate-game-summaries next-date season)))))))))

(defmulti populate-season-game-summaries season-api)
(defmethod populate-season-game-summaries :nhl [season]
  (populate-game-summaries (season-start-date season) season))
(defmethod populate-season-game-summaries :statsapi [season]
  (nhl-stat-scraper.database.games/insert-game-summaries
    db-pg/pg-datasource
    (->> (parse-statsapi/game-summaries-between-dates (season-start-date season) (str (+ 1 season) "-06-30"))
         ;remove games against non-league teams (primarily for preseason
         (filter #(not (or (nil? (get % :home_team_db_id )) (nil? (get % :visiting_team_db_id ))))))))

(defn populate-players-and-roster [game-summary]
  (let [game-id (get game-summary :game_id)
        home-team-id (or
                       (get game-summary :home_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-home-team-id game-id))
        away-team-id (or
                       (get game-summary :visiting_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-away-team-id game-id))]
  (if-let [raw-roster  (parse-html/game-roster-raw game-id)]
    (do
      (nhl-stat-scraper.database.players/update-players-and-roster game-id home-team-id (parse-html/home-player-list raw-roster))
      (nhl-stat-scraper.database.players/update-players-and-roster game-id away-team-id (parse-html/away-player-list raw-roster))))))

(defn populate-player-shifts [game-summary]
  (let [game-id (get game-summary :game_id)
        home-team-id (or
                       (get game-summary :home_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-home-team-id game-id))
        away-team-id (or
                       (get game-summary :visiting_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-away-team-id game-id))]
    (if-let [home-shifts (parse-html/home-player-shifts game-id)]
      (nhl-stat-scraper.database.players/add-game-player-shifts game-id home-team-id home-shifts))
    (if-let [away-shifts (parse-html/away-player-shifts game-id)]
      (nhl-stat-scraper.database.players/add-game-player-shifts game-id away-team-id away-shifts))))

(defn populate-game-plays [game-summary]
  (let [game-id (get game-summary :game_id)
        home-team-id (or
                       (get game-summary :home_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-home-team-id game-id))
        away-team-id (or
                       (get game-summary :visiting_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-away-team-id game-id))]
    (if-let [game-plays (parse-html/game-plays game-id)]
      (doseq [play game-plays]
        (let [db-play (first (apply nhl-stat-scraper.database.plays/add-play game-id play))]
          (helpers/insert-parsed-db-play game-id db-play))))))

(defn populate-game-details [game-summary]
  (populate-players-and-roster game-summary)
  (populate-player-shifts game-summary)
  (populate-game-plays game-summary))

(defn populate-all-game-details []
  (doseq [game-summary (nhl-stat-scraper.database.games/db-game-summaries)]
    (populate-game-details game-summary)))

(defn update-game-summary
  ([game-summary] (update-game-summary game-summary []))
  ([game-summary selected-keys]
    (let [db-ids (nhl-stat-scraper.database.games/db-game-summary-ids (get game-summary :game_id))
          selected-game-summary (if (empty? selected-keys) game-summary (select-keys game-summary selected-keys))]
      (if (= (count db-ids) 1)
        (nhl-stat-scraper.database.games/update-game-summary (first db-ids) selected-game-summary)
        (if (= (count db-ids) 0)
          (println (format "Missing db entry for game_id %s" (get game-summary :game_id)))
          (println (format "Multiple db entries for game_id %s" (get game-summary :game_id))))))))

(defn update-or-insert-game-summary
  ([game-summary] (update-game-summary game-summary []))
  ([game-summary selected-keys]
    (let [db-ids (nhl-stat-scraper.database.games/db-game-summary-ids (get game-summary :game_id))
          selected-game-summary (if (empty? selected-keys) game-summary (select-keys game-summary selected-keys))]
      (if (= (count db-ids) 1)
        (nhl-stat-scraper.database.games/update-game-summary (first db-ids) selected-game-summary)
        (if (= (count db-ids) 0)
          (nhl-stat-scraper.database.games/insert-game-summaries [game-summary])
          (println (format "Multiple db entries for game_id %s" (get game-summary :game_id))))))))

(defmulti update-calendar date-api)
(defmethod update-calendar :nhl [start-date stop-date]
  (println "Not Implemented for older data sources. Past calendars should be complete when populated."))
(defmethod update-calendar :statsapi
  ([start-date stop-date]
    (let [new-game-summaries (reduce
                               #(assoc %1 (get %2 :game_id) %2)
                               {}
                               (parse-statsapi/game-summaries-between-dates start-date stop-date))
          existing-game-summaries (reduce
                                    #(assoc %1 (get %2 :game_id) %2)
                                    {}
                                    (nhl-stat-scraper.database.games/db-game-summaries-between start-date stop-date))
          [new-game-ids removed-game-ids overlap-game-ids] (clojure.data/diff (keys new-game-summaries) (keys existing-game-summaries))]
      (doseq [game-summary (map #(get new-game-summaries %) new-game-ids)]
        (update-or-insert-game-summary game-summary [:game_id :game_date :game_start]))
      (doseq [game-summary (map parse-statsapi/game-summary-from-game-id removed-game-ids)]
        (update-game-summary game-summary [:game_id :game_date :game_start]))
      (doseq [game-summary (map #(get new-game-summaries %) (filter #(not= (get-in new-game-summaries [% :game_start]) (get-in existing-game-summaries [% :game_start])) overlap-game-ids))]
        (update-game-summary game-summary [:game_id :game_date :game_start])))))

(defn update-full-calendar [season] (update-calendar (season-start-date season) (str (inc season) "-07-31")))
(defn update-calendar-from [start-date] (update-calendar start-date (str (inc (season-from-date start-date)) "-07-31")))
(defn update-calendar-on [on-date] (update-calendar on-date on-date))
(defn update-incomplete-calendar []
  (let [start-date (first nhl-stat-scraper.database.games/incomplete-dates)]
    (update-calendar start-date (str (inc (season-from-date start-date)) "-07-31"))))

(defmulti update-game-summaries-between date-api)
(defmethod update-game-summaries-between :nhl
  ([start-date stop-date]
    (if (>= 0 (compare start-date stop-date))
      (let [game-summaries (parse-json/game-summaries-on start-date)]
        (doseq [game-summary game-summaries] (update-game-summary game-summary))
        (let [next-date (parse-json/next-date start-date)]
          (if (not-empty next-date) (update-game-summaries-between next-date stop-date)))))))
(defmethod update-game-summaries-between :statsapi
  ([start-date stop-date]
    (let [game-summaries (parse-statsapi/game-summaries-between-dates start-date stop-date)]
      (doseq [game-summary game-summaries] (update-game-summary game-summary)))))

(defn update-full-game-summaries [season] (update-game-summaries-between (season-start-date season) (str (inc season) "-07-31")))
(defn update-game-summaries-from [start-date] (update-game-summaries-between start-date (str (inc (season-from-date start-date)) "-07-31")))
(defn update-game-summaries-on [on-date] (update-game-summaries-between on-date on-date))
(defn update-incomplete-game-summaries []
  (update-game-summaries-between
    (first (nhl-stat-scraper.database.games/incomplete-dates))
    (clj-time.local/format-local-time (clj-time.local/local-now) :date)))

;Updating game_summary for good measure (calendar page has had incorrect values for ongoing games before)
(defmulti update-games-between date-api)
(defmethod update-games-between :nhl
  ([start-date stop-date] (println "Full game data is only implemented for the 2017 season and after.")))
(defmethod update-games-between :statsapi
  ([start-date stop-date]
   (let [game-ids (map #(get % :game_id) (nhl-stat-scraper.database.games/db-game-summaries-between start-date stop-date))]
     (doseq [game-id game-ids]
       (let [game-summary (parse-statsapi/game-summary-from-game-id game-id)]
         (update-game-summary game-summary))))))

(defn update-full-games [season]
  (println "Updating full games for the entire season requires ~1200 requests. Please use sparingly. Starting now...")
  (update-games-between (season-start-date season) (str (inc season) "-07-31")))
(defn update-games-from [start-date] (update-games-between start-date (str (inc (season-from-date start-date)) "-07-31")))
(defn update-games-on [on-date] (update-games-between on-date on-date))
(defn update-incomplete-games []
  (let [incomplete-game-ids (map #(get % :game_id) (nhl-stat-scraper.database.games/incomplete-game-summaries))]
     (doseq [game-id incomplete-game-ids]
       (let [game-summary (parse-statsapi/game-summary-from-game-id game-id)]
         (update-game-summary game-summary)))))
