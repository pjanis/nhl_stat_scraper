(ns nhl-stat-scraper.database.games
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [clj-time.jdbc]
    [nhl-stat-scraper.common.parse :as common-parse]
    [nhl-stat-scraper.database.general :as db-general]
    [nhl-stat-scraper.database.postgres :as db-pg]))

;TODO Replace SQL with db-general


(defn insert-game-summaries
  ([game-summaries] (insert-game-summaries db-pg/pg-datasource game-summaries))
  ([datasource game-summaries]
    (if (not (empty? game-summaries))
      (jdbc/insert-multi! datasource :game_summaries (sort-by :game_id game-summaries) ))))

(defn season-part-subquery [season-part]
  (case season-part
    "regular" " regular_season=TRUE AND "
    "preseason" " preseason=TRUE AND "
    "postseason" " postseason=TRUE AND "
    ""))

(defn add-season-part [params part-name]
  (merge
    params
    (case part-name
      "regular" {:regular_season true}
      "preseason" {:preseason true}
      "postseason" {:postseason true})))

(defn db-game-summaries
  ([] (db-game-summaries 2015 "regular" db-pg/pg-datasource))
  ([season season-part] (db-game-summaries season season-part db-pg/pg-datasource))
  ([season season-part datasource]
    (db-general/get-all-by-values (add-season-part {:season season} season-part) "game_summaries" datasource)))

(defn db-game-summaries-on
  ([date-str] (db-game-summaries-on date-str db-pg/pg-datasource))
  ([date-str datasource]
    (db-general/get-all-by-values {:game_date (common-parse/string-to-date date-str)} "game_summaries" datasource)))

(defn db-game-summaries-between
  ([start-date-str stop-date-str] (db-game-summaries-between start-date-str stop-date-str db-pg/pg-datasource))
  ([start-date-str stop-date-str datasource]
    (jdbc/query datasource [(format "SELECT * FROM game_summaries
                            WHERE game_date >= '%s'
                            AND game_date <= '%s'"
                            start-date-str stop-date-str)])))

(defn db-game-summary-ids
  ([game-id] (db-game-summary-ids game-id db-pg/pg-datasource))
  ([game-id datasource] (map :db_id (db-general/get-all-by-values {:game_id (str game-id)} "game_summaries" datasource))))

(defn db-game-summary-home-team-id
  ([game-id] (db-game-summary-home-team-id game-id db-pg/pg-datasource))
  ([game-id datasource]
    (get (db-general/get-first-by-values {:game_id game-id} "game_id DESC" "game_summaries" datasource) :home_team_db_id)))

(defn db-game-summary-away-team-id
  ([game-id] (db-game-summary-away-team-id game-id db-pg/pg-datasource))
  ([game-id datasource]
    (get (db-general/get-first-by-values {:game_id game-id} "game_id DESC" "game_summaries" datasource) :visiting_team_db_id)))

(defn team-home-games
  ([team-id] (team-home-games team-id 2015 "regular" db-pg/pg-datasource))
  ([team-id season season-part] (team-home-games team-id season season-part db-pg/pg-datasource))
  ([team-id season season-part datasource]
    (db-general/get-all-by-values
      (add-season-part {:season season :home_team_db_id team-id} season-part)
      "game_summaries"
      datasource)))

(defn team-away-games
  ([team-id] (team-away-games team-id 2015 "regular" db-pg/pg-datasource))
  ([team-id season season-part] (team-away-games team-id season season-part db-pg/pg-datasource))
  ([team-id season season-part datasource]
    (db-general/get-all-by-values
      (add-season-part {:season season :visiting_team_db_id team-id} season-part)
      "game_summaries"
      datasource)))

;TODO create db-general function for distinct
(defn incomplete-dates
  ([] (incomplete-dates db-pg/pg-datasource))
  ([datasource]
    (->> (jdbc/query datasource [(format "SELECT DISTINCT game_date FROM game_summaries WHERE complete=FALSE ORDER BY game_date")])
        (map :game_date)
        (map #(.toString %))
        (map #(subs % 0 10)))))

(defn game-summaries-with-incomplete-details
  ([] (game-summaries-with-incomplete-details db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * FROM game_summaries
                            WHERE NOT EXISTS
                              (SELECT * FROM plays WHERE game_summaries.game_id = plays.game_id
                                                    AND plays.play_event= 'GEND')"])))

(defn complete-game-summaries-without-statsapi-game
  ([] (complete-game-summaries-without-statsapi-game db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * FROM game_summaries
                            LEFT JOIN statsapi_games ON statsapi_games.game_id = game_summaries.game_id
                            WHERE game_summaries.season > 2016
                            AND statsapi_games.game_json IS NULL
                            AND game_summaries.complete = true"])))

(defn incomplete-game-summaries
  ([] (incomplete-game-summaries  db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * FROM game_summaries
                            WHERE
                              ((game_start IS NULL AND game_date < now())
                               OR game_start < now())
                              AND (complete = false
                               OR regulation_win is NULL)"])))

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

(defn replace-home-team-db-id
  ([old_id new_id] (replace-home-team-db-id old_id new_id db-pg/pg-datasource))
  ([old_id new_id datasource]
    (db-general/update-values {:home_team_db_id new_id} "home_team_db_id" old_id "game_summaries" datasource)))

(defn replace-visiting-team-db-id
  ([old_id new_id] (replace-visiting-team-db-id old_id new_id db-pg/pg-datasource))
  ([old_id new_id datasource]
    (db-general/update-values {:visiting_team_db_id new_id} "visiting_team_db_id" old_id "game_summaries" datasource)))

(defn replace-team-id
  ([old_id new_id] (replace-team-id old_id new_id db-pg/pg-datasource))
  ([old_id new_id datasource]
    (replace-home-team-db-id old_id new_id datasource)
    (replace-visiting-team-db-id old_id new_id datasource)))

(defn insert-statsapi-game
  ([game-id json] (insert-statsapi-game game-id json db-pg/pg-datasource))
  ([game-id json datasource]
    (db-general/insert-values {:game_id (str game-id) :game_json json} "statsapi_games" datasource)))

(defn update-statsapi-game
  ([game-id json] (update-statsapi-game game-id json db-pg/pg-datasource))
  ([game-id json datasource]
    (db-general/update-values {:game_json json} "game_id" (str game-id) "statsapi_games" datasource)))

(defn get-statsapi-game
  ([game-id] (get-statsapi-game game-id db-pg/pg-datasource))
  ([game-id datasource]
    (db-general/get-first-by-values {:game_id (str game-id)} "db_id DESC" "statsapi_games" datasource)))
