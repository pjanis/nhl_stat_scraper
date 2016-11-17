(ns nhl-stat-scraper.database.populate-helpers
  (:require
    [clojure.java.jdbc :as jdbc]
    [nhl-stat-scraper.parse.json :as parse-json]
    [nhl-stat-scraper.parse.html :as parse-html]
    [nhl-stat-scraper.database.teams :as db-teams]
    [nhl-stat-scraper.database.games :as db-games]
    [nhl-stat-scraper.database.divisions :as db-divisions]
    [nhl-stat-scraper.database.conferences :as db-conferences]
    [nhl-stat-scraper.database.plays :as db-plays]
    [nhl-stat-scraper.database.players :as db-players]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn insert-shot
  ([game-id play-id period play-time strength event description]
    (insert-shot game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [[player-id team-id] (if (= "SHOT" event)
                                (parse-html/play-single-player-and-team description)
                                (first (parse-html/play-player-and-team-ids description)))
          goal (= "GOAL" event)
          shot-type (parse-html/play-shot-type description)
          on-net (boolean (some #{event} ["GOAL" "SHOT"]))
          block (= "BLOCK" event)
          miss-location (if (= "MISS" event)
                          (if-let [loc (parse-html/play-shot-miss-location description)]
                            (clojure.string/lower-case loc)
                            nil)
                          nil)
          distance (parse-html/play-shot-distance description)
          zone (if-let [raw-zone (parse-html/play-zone description)]
                 (if (= "BLOCK" event)
                   (parse-html/flip-zone raw-zone)
                   raw-zone)
                 (if (nil? distance)
                   "offensive"    ;default to offensive zone
                   (if (< distance 70)
                     "offensive"
                     (if (< distance 118)    ;distances from survey of existing shot_types
                       "neutral"
                       "defensive"))))
          shot-type-id (:db_id (db-plays/db-shot-type shot-type on-net block miss-location zone distance))]
      (db-players/add-player-shot player-id
                                  team-id
                                  game-id
                                  play-id
                                  shot-type-id
                                  goal
                                  period
                                  play-time
                                  (if (= "BLOCK" event)
                                    (parse-html/flip-strength strength)
                                    strength)
                                  datasource))))

(defn insert-block
  ([game-id play-id period play-time strength event description]
    (insert-block game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [[player-id team-id] (last (parse-html/play-player-and-team-ids description))]
      (db-players/add-player-block player-id team-id game-id play-id period play-time strength datasource))))

(defn insert-faceoff
  ([game-id play-id period play-time strength event description]
    (insert-faceoff game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [faceoff-player-and-teams (parse-html/play-player-and-team-ids description)
          zone (parse-html/play-zone description)
          winning-team-id (parse-html/play-winning-team-id description)]
      (doseq [[player-id team-id] faceoff-player-and-teams]
             (if (= winning-team-id team-id)
                (db-players/add-player-faceoff player-id team-id game-id play-id period play-time strength zone true datasource)
                (db-players/add-player-faceoff player-id
                                               team-id
                                               game-id
                                               play-id
                                               period
                                               play-time
                                               (parse-html/flip-strength strength)
                                               (parse-html/flip-zone zone)
                                               false
                                               datasource))))))

(defn insert-giveaway
  ([game-id play-id period play-time strength event description]
    (insert-giveaway game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [[player-id team-id] (parse-html/play-single-player-and-team description)
          zone (parse-html/play-zone description)]
      (db-players/add-player-giveaway player-id team-id game-id play-id period play-time strength zone datasource))))

(defn insert-takeaway
  ([game-id play-id period play-time strength event description]
    (insert-takeaway game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [[player-id team-id] (parse-html/play-single-player-and-team description)
          zone (parse-html/play-zone description)]
      (db-players/add-player-takeaway player-id team-id game-id play-id period play-time strength zone datasource))))

(defn insert-hit
  ([game-id play-id period play-time strength event description]
    (insert-hit game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [hit-player-and-teams (parse-html/play-player-and-team-ids description)
          zone (parse-html/play-zone description)]
      [(let [[player-id team-id] (first hit-player-and-teams)]
        (db-players/add-player-hit player-id team-id game-id play-id period play-time strength zone true datasource))
      (let [[player-id team-id] (last hit-player-and-teams)]
        (db-players/add-player-hit player-id
                                   team-id
                                   game-id
                                   play-id
                                   period
                                   play-time
                                   (parse-html/flip-strength strength)
                                   (parse-html/flip-zone zone)
                                   false
                                   datasource))])))

(defn insert-penalty
  ([game-id play-id period play-time strength event description]
    (insert-penalty game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (let [penalty-player-and-teams (parse-html/play-player-and-team-ids description)
          penalty-description (parse-html/play-penalty-description description)
          major (parse-html/play-penalty-major description)
          duration (parse-html/play-penalty-duration description)
          zone (parse-html/play-zone description)
          [player-id team-id] (first penalty-player-and-teams)
          [served-by serving-team] (parse-html/play-serving-player-and-team description)
          player-penalty      (first (db-players/add-player-penalty player-id
                                                                   (if (not (nil? team-id))
                                                                     team-id
                                                                     serving-team)
                                                                   game-id
                                                                   play-id
                                                                   period
                                                                   play-time
                                                                   strength
                                                                   zone
                                                                   penalty-description
                                                                   major
                                                                   duration
                                                                   served-by
                                                                   datasource))
          [drawing-player-id drawing-team-id] (last penalty-player-and-teams)]
       (if (not (= drawing-player-id player-id))
         [player-penalty
          (db-players/add-player-drawn-penalty drawing-player-id (:db_id player-penalty) datasource)]
         player-penalty))))

(defn insert-parsed-play
  ([game-id play-id period play-time strength event description] (insert-parsed-play game-id play-id period play-time strength event description db-pg/pg-datasource))
  ([game-id play-id period play-time strength event description datasource]
    (case event
      "BLOCK" (do
                (insert-shot game-id play-id period play-time strength event description)
                (insert-block game-id play-id period play-time strength event description))
      "FAC"   (insert-faceoff game-id play-id period play-time strength event description)
      "GEND"  nil
      "GIVE"  (insert-giveaway game-id play-id period play-time strength event description)
      "GOAL"  (insert-shot game-id play-id period play-time strength event description)
      "GOFF"  nil
      "HIT"   (insert-hit game-id play-id period play-time strength event description)
      "MISS"  (insert-shot game-id play-id period play-time strength event description)
      "PEND"  nil
      "PENL"  (insert-penalty game-id play-id period play-time strength event description)
      "PSTR"  nil
      "SHOT"  (insert-shot game-id play-id period play-time strength event description)
      "STOP"  nil
      "TAKE"  (insert-takeaway game-id play-id period play-time strength event description)
      nil)))

(defn insert-parsed-db-play [game-id db-play]
  (insert-parsed-play game-id
                      (db-play :db_id)
                      (db-play :period)
                      (db-play :play_time)
                      (db-play :strength)
                      (db-play :play_event)
                      (db-play :play_description)))
