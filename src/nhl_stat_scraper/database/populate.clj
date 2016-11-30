(ns nhl-stat-scraper.database.populate
  (:require
    [clojure.java.jdbc :as jdbc]
    [nhl-stat-scraper.parse.json :as parse-json]
    [nhl-stat-scraper.parse.html :as parse-html]
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
    2016 "2016-10-12"
    2015 "2015-10-07"
    2014 "2014-10-08"
    2013 "2013-10-01"
    2012 "2013-01-19"
    2011 "2011-10-06"))

(defn populate-teams []
  (nhl-stat-scraper.database.teams/populate-teams db-pg/pg-datasource (parse-json/all-teams)))

(defn populate-team-colors []
  (nhl-stat-scraper.database.teams/populate-team-colors db-pg/pg-datasource (parse-html/wikipedia-team-info)))

(defn populate-game-summaries-from-parsed-json [parsed-json]
  (if-let [game-summaries (seq (filter #(empty? (nhl-stat-scraper.database.games/db-game-summary-ids (:game_id %)))
                                       (parse-json/game-summaries-from-parsed-json parsed-json)))]
    (nhl-stat-scraper.database.games/insert-game-summaries db-pg/pg-datasource game-summaries)
    nil))

(defn populate-game-summaries-on [date]
  (if-let [game-summaries (seq (filter #(empty? (nhl-stat-scraper.database.games/db-game-summary-ids (:game_id %)))
                                       (parse-json/game-summaries-on date)))]
    (nhl-stat-scraper.database.games/insert-game-summaries db-pg/pg-datasource game-summaries)
    nil))

(defn populate-game-summaries
  ([] (populate-game-summaries "2015-10-07"))
  ([date] (populate-game-summaries date nil))
  ([date season]
    (let [parsed-json (parse-json/parsed-date-json date)]
      (if (or (nil? season)
              (and (not (empty? (get parsed-json "games")))
                   (= season
                      (nhl-stat-scraper.common.parse/parse-int (subs (str (get (first (get parsed-json "games")) "id")) 0 4)))))
        (do
          (print (format "Populating %s\n" date)) ;TODO move to timbre logging
          (populate-game-summaries-from-parsed-json parsed-json)
          (let [next-date (parse-json/next-date-from-parsed-json parsed-json)]
            (if (not-empty next-date) (populate-game-summaries next-date season))))))))

(defn populate-season-game-summaries [season]
  (populate-game-summaries (season-start-date season) season))

(defn populate-divisions []
  (nhl-stat-scraper.database.divisions/populate-divisions db-pg/pg-datasource))

(defn populate-conferences []
  (nhl-stat-scraper.database.conferences/populate-conferences db-pg/pg-datasource))

(defn populate-division-teams []
  (nhl-stat-scraper.database.teams/populate-division-teams
    db-pg/pg-datasource
    (nhl-stat-scraper.database.divisions/db-divisions)
    (parse-html/wikipedia-team-info)))

(defn populate-conference-teams []
  (nhl-stat-scraper.database.teams/populate-conference-teams
    db-pg/pg-datasource
    (nhl-stat-scraper.database.conferences/db-conferences)
    (parse-html/wikipedia-team-info)))

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

(defn update-game-summaries-on
  ([update-date] (update-game-summaries-on update-date true))
  ([update-date update-details]
    (doseq [game-summary (parse-json/game-summaries-on update-date)] (update-game-summary game-summary update-details))))

(defn update-game-summaries
  ([] (update-game-summaries true))
  ([update-details]
    (doseq [incomplete-date (nhl-stat-scraper.database.games/incomplete-dates)]
      (if (< (compare incomplete-date (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (new java.util.Date))) 1)
        (update-game-summaries-on incomplete-date update-details)))))
