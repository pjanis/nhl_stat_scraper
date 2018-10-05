(ns nhl-stat-scraper.report.plays
  "Reports play data but limited to games with statsapi playlist"
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [clj-time.format]
    [nhl-stat-scraper.database.games :as db-games]
    [nhl-stat-scraper.database.postgres :as db-pg]
    [nhl-stat-scraper.database.teams :as db-teams]))

(defn all-game-plays
  ([game-id] (all-game-plays game-id db-pg/pg-datasource))
  ([game-id datasource]
    (get-in (db-games/get-statsapi-game game-id datasource) [:game_json "liveData" "plays" "allPlays"])))



