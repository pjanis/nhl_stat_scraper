(ns nhl-stat-scraper.database.plays
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [clj-time.jdbc]
    [dire.core]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn game-plays
  ([game-id] (game-plays game-id db-pg/pg-datasource))
  ([game-id datasource]
    (jdbc/query datasource [(format "SELECT * FROM plays WHERE game_id='%s'" game-id)])))

(defn last-play
  ([] (last-play db-pg/pg-datasource))
  ([datasource]
    (first(jdbc/query datasource ["SELECT * FROM plays order by db_id desc"]))))

(defn delete-all-game-plays
  ([game-id] (delete-all-game-plays game-id db-pg/pg-datasource))
  ([game-id datasource]
    (jdbc/delete! datasource :plays [(format "game_id='%s'" game-id)])))

(defn delete-unmatched-from-player-drawn-penalties
  ([game-id] (delete-unmatched-from-player-drawn-penalties game-id db-pg/pg-datasource))
  ([game-id datasource]
    (jdbc/delete! datasource :player_drawn_penalties
                  [(format "NOT EXISTS
                            ( SELECT * FROM
                                player_penalties WHERE player_drawn_penalties.penalty_id = player_penalties.db_id)")])))

(defn delete-all-play-details-for-game
  ([game-id] (delete-all-play-details-for-game game-id db-pg/pg-datasource))
  ([game-id datasource]
    (delete-all-game-plays game-id db-pg/pg-datasource)
    (jdbc/delete! datasource :player_shots [(format "game_id='%s'" game-id)])
    (jdbc/delete! datasource :player_faceoffs [(format "game_id='%s'" game-id)])
    (jdbc/delete! datasource :player_giveaways [(format "game_id='%s'" game-id)])
    (jdbc/delete! datasource :player_takeaways [(format "game_id='%s'" game-id)])
    (jdbc/delete! datasource :player_penalties [(format "game_id='%s'" game-id)])
    (jdbc/delete! datasource :player_blocks [(format "game_id='%s'" game-id)])
    (jdbc/delete! datasource :player_hits [(format "game_id='%s'" game-id)])
    (delete-unmatched-from-player-drawn-penalties game-id db-pg/pg-datasource)))

(defn add-play
  ([game-id play-number period strength play-time play-event play-description home-numbers away-numbers]
    (add-play game-id play-number period strength play-time play-event play-description home-numbers away-numbers db-pg/pg-datasource))
  ([game-id play-number period strength play-time play-event play-description home-numbers away-numbers datasource]
    (jdbc/insert! datasource :plays {:game_id game-id
                                     :play_number play-number
                                     :period  period
                                     :strength  strength
                                     :play_time play-time
                                     :play_event  play-event
                                     :play_description  play-description
                                     :home_numbers home-numbers
                                     :away_numbers away-numbers})))

(dire.core/with-handler! #'add-play
  org.postgresql.util.PSQLException
   (fn [e & args] (println (str e))))

(defn find-shot-type
  ([shot-type on-net block miss-location zone distance]
    (find-shot-type shot-type on-net block miss-location zone distance db-pg/pg-datasource))
  ([shot-type on-net block miss-location zone distance datasource]
    (let [qry-str (string/join " "
                               ["SELECT * FROM shot_types WHERE"
                                (string/join " AND "
                                  [
                                  (if (not (nil? shot-type))
                                    (format "type='%s'" shot-type)
                                    (format "type is Null"))
                                  (format "on_net=%s" on-net)
                                  (format "block=%s" block)
                                  (if (not (nil? miss-location))
                                    (format "miss_location='%s'" miss-location)
                                    (format "miss_location is Null") )
                                  (format "zone='%s'" zone)
                                  (if (not (nil? distance))
                                    (format "distance_ft=%d" distance)
                                    (format "distance_ft is Null"))])
                               ])]
      (first (jdbc/query datasource [qry-str])))))

(defn insert-shot-type
  ([shot-type on-net block miss-location zone distance]
    (insert-shot-type shot-type on-net block miss-location zone distance db-pg/pg-datasource))
  ([shot-type on-net block miss-location zone distance datasource]
    (first (jdbc/insert! datasource :shot_types {:type shot-type
                                          :on_net on-net
                                          :block block
                                          :miss_location miss-location
                                          :zone zone
                                          :distance_ft distance}))))

(dire.core/with-handler! #'insert-shot-type
  org.postgresql.util.PSQLException
   (fn [e & args] (do
                   (println (str e))
                   (if (.contains (str e) "already exists")
                     (apply find-shot-type args)))))

(def db-shot-type (partial db-pg/find-or-insert find-shot-type insert-shot-type))

(defn add-game-plays
  ([game-id play-list] (add-game-plays game-id play-list db-pg/pg-datasource))
  ([game-id play-list datasource]
    (doseq [play play-list]
      (apply add-play game-id play))))
