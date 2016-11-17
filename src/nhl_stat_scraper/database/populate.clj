(ns nhl-stat-scraper.database.populate
  (:require
    [clojure.java.jdbc :as jdbc]
    [nhl-stat-scraper.parse.json :as json-parse]
    [nhl-stat-scraper.parse.html :as html-parse]
    [nhl-stat-scraper.database.teams]
    [nhl-stat-scraper.database.games]
    [nhl-stat-scraper.database.divisions]
    [nhl-stat-scraper.database.conferences]
    [nhl-stat-scraper.database.plays]
    [nhl-stat-scraper.database.players]
    [nhl-stat-scraper.database.populate-helpers :as helpers]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn populate-teams []
  (nhl-stat-scraper.database.teams/populate-teams db-pg/pg-datasource (json-parse/all-teams)))

(defn populate-team-colors []
  (nhl-stat-scraper.database.teams/populate-team-colors db-pg/pg-datasource (html-parse/wikipedia-team-info)))

(defn populate-game-summaries
  ([] (populate-game-summaries "2015-10-07"))
  ([start-date]
    (print (format "Populating %s\n" start-date))
    (nhl-stat-scraper.database.games/insert-game-summaries db-pg/pg-datasource (json-parse/game-summaries-on start-date))
    (let [next-date (json-parse/next-date start-date)]
      (if (not-empty next-date) (populate-game-summaries next-date)))))

(defn populate-divisions []
  (nhl-stat-scraper.database.divisions/populate-divisions db-pg/pg-datasource))

(defn populate-conferences []
  (nhl-stat-scraper.database.conferences/populate-conferences db-pg/pg-datasource))

(defn populate-division-teams []
  (nhl-stat-scraper.database.teams/populate-division-teams
    db-pg/pg-datasource
    (nhl-stat-scraper.database.divisions/db-divisions)
    (html-parse/wikipedia-team-info)))

(defn populate-conference-teams []
  (nhl-stat-scraper.database.teams/populate-conference-teams
    db-pg/pg-datasource
    (nhl-stat-scraper.database.conferences/db-conferences)
    (html-parse/wikipedia-team-info)))

(defn populate-players-and-roster [game-summary]
  (let [game-id (get game-summary :game_id)
        home-team-id (or
                       (get game-summary :home_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-home-team-id game-id))
        away-team-id (or
                       (get game-summary :visiting_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-away-team-id game-id))]
  (if-let [raw-roster  (html-parse/game-roster-raw game-id)]
    (do
      (nhl-stat-scraper.database.players/update-players-and-roster game-id home-team-id (html-parse/home-player-list raw-roster))
      (nhl-stat-scraper.database.players/update-players-and-roster game-id away-team-id (html-parse/away-player-list raw-roster))))))

(defn populate-player-shifts [game-summary]
  (let [game-id (get game-summary :game_id)
        home-team-id (or
                       (get game-summary :home_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-home-team-id game-id))
        away-team-id (or
                       (get game-summary :visiting_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-away-team-id game-id))]
    (if-let [home-shifts (html-parse/home-player-shifts game-id)]
      (nhl-stat-scraper.database.players/add-game-player-shifts game-id home-team-id home-shifts))
    (if-let [away-shifts (html-parse/away-player-shifts game-id)]
      (nhl-stat-scraper.database.players/add-game-player-shifts game-id away-team-id away-shifts))))

(defn populate-game-plays [game-summary]
  (let [game-id (get game-summary :game_id)
        home-team-id (or
                       (get game-summary :home_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-home-team-id game-id))
        away-team-id (or
                       (get game-summary :visiting_team_db_id)
                       (nhl-stat-scraper.database.games/db-game-summary-away-team-id game-id))]
    (if-let [game-plays (html-parse/game-plays game-id)]
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
    (doseq [game-summary (json-parse/game-summaries-on update-date)] (update-game-summary game-summary update-details))))

(defn update-game-summaries
  ([] (update-game-summaries true))
  ([update-details]
    (doseq [incomplete-date (nhl-stat-scraper.database.games/incomplete-dates)]
      (if (< (compare incomplete-date (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (new java.util.Date))) 1)
        (update-game-summaries-on incomplete-date update-details)))))
