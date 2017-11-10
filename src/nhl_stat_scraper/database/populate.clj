(ns nhl-stat-scraper.database.populate
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.java.io :as io]
    [clj-time.core]
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
    2018 "2018-10-01"  ;TODO placeholder until 2018 schedule announced
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

;TODO Create simpler update for yearly changes
(defn update-league-structure
  "Used to migrate from old league structure to new. Shouldn't be needed most years"
  []
  (nhl-stat-scraper.database.general/reset-table "conference_divisions")
  (nhl-stat-scraper.database.general/reset-table "division_teams")
  (nhl-stat-scraper.database.general/reset-table "divisions")
  (nhl-stat-scraper.database.general/reset-table "conferences")
  (nhl-stat-scraper.database.teams/set-all-seasons [2025 2025])
  (populate-league-structure)
  (let [old-teams (nhl-stat-scraper.database.teams/get-teams-by-season 2025)]
    (doseq [old-team old-teams]
      (if-let [new-team (nhl-stat-scraper.database.teams/get-team-by-name-and-season (get old-team :name) 2016)]
        (do
          (nhl-stat-scraper.database.games/replace-team-id (get old-team :db_id) (get new-team :db_id))
          (nhl-stat-scraper.database.general/delete-row "db_id" (get old-team :db_id) "teams")))))
  (doseq [season (range 2005 2018)]
    (update-team-details-for-season season))
  )

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
    (parse-statsapi/game-summaries-between-dates (season-start-date season) (str (+ 1 season) "-06-30"))))

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

(defn update-game-details [game-summary]
  (nhl-stat-scraper.database.players/delete-game-player-shifts (get game-summary :game_id))
  (nhl-stat-scraper.database.players/delete-game-players (get game-summary :game_id))
  (nhl-stat-scraper.database.plays/delete-all-play-details-for-game (get game-summary :game_id))
  ;TODO ? check player rosters for new players and remove
  (populate-game-details game-summary))

(defn update-game-summary
  ([game-summary] (update-game-summary game-summary true))
  ([game-summary update-details]
    (let [db-ids (nhl-stat-scraper.database.games/db-game-summary-ids (get game-summary :game_id))]
      (if (= (count db-ids) 1)
        (do
          (nhl-stat-scraper.database.games/update-game-summary (first db-ids) game-summary)
          (if update-details (update-game-details game-summary)))
        (if (= (count db-ids) 0)
          (print (format "Missing db entry for game_id %s" (get game-summary :game_id)))
          (print (format "Multiple db entries for game_id %s" (get game-summary :game_id))))))))

(defmulti update-game-summaries-on date-api)
(defmethod update-game-summaries-on :nhl
  ([update-date] (update-game-summaries-on update-date true))
  ([update-date update-details]
    (doseq [game-summary (parse-json/game-summaries-on update-date)] (update-game-summary game-summary update-details))))
(defmethod update-game-summaries-on :statsapi
  ([update-date] (update-game-summaries-on update-date true))
  ([update-date update-details]
    (doseq [game-summary (parse-statsapi/game-summaries-on update-date)] (update-game-summary game-summary update-details))
    (doseq [game-summary (nhl-stat-scraper.database.games/complete-game-summaries-without-statsapi-game)]
      (update-game-summary (parse-statsapi/game-summary-from-game-id (get game-summary :game_id)) update-details))))

(defmulti update-game-summaries-from date-api)
(defmethod update-game-summaries-from :nhl
  ([update-date] (update-game-summaries-from update-date true))
  ([update-date update-details] (update-game-summaries-from update-date
                                                            update-details
                                                            (season-from-date update-date)))
  ([update-date update-details season-to-update]
    (let [game-summaries (parse-json/game-summaries-on update-date)]
      (if (= season-to-update (nhl-stat-scraper.common.games/season-from-id (get (first game-summaries) :game_id)))
          (do
            (doseq [game-summary game-summaries] (update-game-summary game-summary update-details))
            (let [next-date (parse-json/next-date update-date)]
              (if (not-empty next-date) (update-game-summaries-from next-date update-details season-to-update))))))))
(defmethod update-game-summaries-from :statsapi
  ([update-date] (update-game-summaries-from update-date true))
  ([update-date update-details] (update-game-summaries-from update-date
                                                            update-details
                                                            (season-from-date update-date)))
  ([update-date update-details season-to-update]
    (let [game-summaries (parse-statsapi/game-summaries-between-dates update-date (str (+ 1 season-to-update) "-07-31"))]
      (doseq [game-summary game-summaries] (update-game-summary game-summary update-details))
      (doseq [game-summary (nhl-stat-scraper.database.games/complete-game-summaries-without-statsapi-game)]
        (update-game-summary (parse-statsapi/game-summary-from-game-id (get game-summary :game_id)) update-details)))))

(defn update-game-summaries
  ([] (update-game-summaries true))
  ([update-details]
    (let [incomplete-dates (nhl-stat-scraper.database.games/incomplete-dates)]
      (if (and (not (empty? incomplete-dates))
               (clj-time.core/before? (nhl-stat-scraper.common.parse/string-to-date (first incomplete-dates))
                                      (clj-time.core/minus (clj-time.core/now) (clj-time.core/days 1))))
        (update-game-summaries-from (first incomplete-dates) update-details)
        (doseq [incomplete-date incomplete-dates]
          (if (not (clj-time.core/after? (nhl-stat-scraper.common.parse/string-to-date incomplete-date)
                                         (clj-time.core/now)))
            (update-game-summaries-on incomplete-date update-details)))))))

