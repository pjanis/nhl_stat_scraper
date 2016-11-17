(ns nhl-stat-scraper.database.games
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [clj-time.jdbc]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn insert-game-summaries
  ([datasource game-summaries]
    (if (not (empty? game-summaries))
      (apply jdbc/insert! datasource :game_summaries (sort-by :game_id game-summaries) ))))

(defn season-part-subquery [season-part]
  (case season-part
    "regular" " regular_season=TRUE AND "
    "preseason" " preseason=TRUE AND "
    "portseason" " postseason=TRUE AND "
    ""))


(defn db-game-summaries
  ([] (db-game-summaries 2015 "regular" db-pg/pg-datasource))
  ([season season-part] (db-game-summaries season season-part db-pg/pg-datasource))
  ([season season-part datasource]
    (jdbc/query
      datasource
      [(format (str "SELECT * FROM game_summaries WHERE"
                    (season-part-subquery season-part)
                    "season=%d")
               season)])))

(defn db-game-summaries-on
  ([date-str] (db-game-summaries-on date-str db-pg/pg-datasource))
  ([date-str datasource]  (jdbc/query datasource [(format "SELECT * FROM game_summaries WHERE game_date='%s'" date-str)])))

(defn db-game-summary-ids
  ([game-id] (db-game-summary-ids game-id db-pg/pg-datasource))
  ([game-id datasource]  (map :db_id (jdbc/query datasource [(format "SELECT db_id FROM game_summaries WHERE game_id='%s'" game-id)]))))

(defn db-game-summary-home-team-id
  ([game-id] (db-game-summary-home-team-id game-id db-pg/pg-datasource))
  ([game-id datasource]  (first
                           (map :home_team_db_id
                                (jdbc/query
                                  datasource
                                  [(format "SELECT home_team_db_id FROM game_summaries WHERE game_id='%s'" game-id)])))))

(defn db-game-summary-away-team-id
  ([game-id] (db-game-summary-away-team-id game-id db-pg/pg-datasource))
  ([game-id datasource]  (first
                           (map :visiting_team_db_id
                                (jdbc/query
                                  datasource
                                  [(format "SELECT visiting_team_db_id FROM game_summaries WHERE game_id='%s'" game-id)])))))

(defn team-home-games
  ([team-id] (team-home-games team-id 2015 "regular" db-pg/pg-datasource))
  ([team-id season season-part] (team-home-games team-id season season-part db-pg/pg-datasource))
  ([team-id season season-part datasource] (jdbc/query
                          datasource
                          [(format (str "SELECT * FROM game_summaries WHERE home_team_db_id=%d AND "
                                        (season-part-subquery season-part)
                                        "season=%d")
                                   team-id
                                   season)])))

(defn team-away-games
  ([team-id] (team-away-games team-id 2015 "regular" db-pg/pg-datasource))
  ([team-id season season-part] (team-away-games team-id season season-part db-pg/pg-datasource))
  ([team-id season season-part datasource] (jdbc/query
                          datasource
                          [(format (str "SELECT * FROM game_summaries WHERE visiting_team_db_id=%d AND "
                                        (season-part-subquery season-part)
                                        "season=%d")
                                   team-id
                                   season)])))


(defn incomplete-dates
  ([] (incomplete-dates db-pg/pg-datasource))
  ([datasource]
    (map
      #(.toString %)
      (map
        :game_date
        (jdbc/query datasource [(format "SELECT DISTINCT game_date FROM game_summaries WHERE complete=FALSE ORDER BY game_date")])))))

(defn game-summaries-with-incomplete-details
  ([] (game-summaries-with-incomplete-details db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * FROM game_summaries
                            WHERE NOT EXISTS
                              (SELECT * FROM plays WHERE game_summaries.game_id = plays.game_id
                                                    AND plays.play_event= 'GEND')"])))

(defn update-game-summary
  ([db_id game-summary] (update-game-summary db_id game-summary db-pg/pg-datasource))
  ([db_id game-summary datasource]
    (try
      (jdbc/update! datasource :game_summaries game-summary [(format "db_id=%d and game_id='%s'" db_id (get game-summary :game_id))])
      (catch Exception e (.getNextException e)))))

(defn game-summaries-exist
  ([season] (game-summaries-exist season "regular"))
  ([season season-part] (game-summaries-exist season season-part db-pg/pg-datasource))
  ([season season-part datasource]
    (< 0 (:count (first (jdbc/query
                          datasource
                          [(format (str "SELECT count(*) FROM game_summaries WHERE "
                                        (season-part-subquery season-part)
                                        "season=%d")
                                   season)]))))))
