(ns nhl-stat-scraper.database.players
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [clj-time.jdbc]
    [dire.core]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn today []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (new java.util.Date)))

(defn team-players
  "Returns player db ids"
  ([team-id] (team-players team-id (today)))
  ([team-id date] (team-players team-id date db-pg/pg-datasource))
  ([team-id date datasource]
    (jdbc/query datasource
                [(format "SELECT player_id FROM player_teams
                            WHERE team_id=%d
                            AND (start_date <= '%s' OR start_date is NULL)
                            AND (stop_date >= '%s' OR stop_date is NULL)" team-id date date)])))

(defn team-player-names
  "Returns all player_id, player_number, and player_name"
  ([team-id] (team-player-names team-id (today)))
  ([team-id date] (team-player-names team-id date db-pg/pg-datasource))
  ([team-id date datasource]
    (jdbc/query datasource
                [(format "SELECT player_teams.player_id,player_teams.player_number,player_names.player_name
                            FROM player_teams
                            INNER JOIN player_names ON player_teams.player_id = player_names.player_id
                            WHERE team_id=%d
                            AND (start_date <= '%s' OR start_date is NULL)
                            AND (stop_date >= '%s' OR stop_date is NULL)" team-id date date)])))

(defn team-player-numbers
  ([team-id] (team-player-numbers team-id (today)))
  ([team-id date] (team-player-numbers team-id date db-pg/pg-datasource))
  ([team-id date datasource]
    (map :player_number (jdbc/query datasource
                                    [(format "SELECT player_number FROM player_teams
                                                WHERE team_id=%d
                                                AND (start_date <= '%s' OR start_date is NULL)
                                                AND (stop_date >= '%s' OR stop_date is NULL)" team-id date date)]))))

(defn player-id-by-team-and-number
  "Returns player's db id"
  ([team-id player-number] (player-id-by-team-and-number team-id player-number (today)))
  ([team-id player-number date] (player-id-by-team-and-number team-id player-number date db-pg/pg-datasource))
  ([team-id player-number date datasource]
    (first (map :player_id
                (jdbc/query datasource
                            [(format
                               "SELECT player_id FROM player_teams WHERE team_id=%d
                                  AND player_number=%d
                                  AND (start_date <= '%s' OR start_date is NULL)
                                  AND (stop_date >= '%s' OR stop_date is NULL)"
                               team-id player-number date date)])))))

(defn player-id-by-team-number-and-name
  "Returns player's db id"
  ([team-id player-number player-name] (player-id-by-team-number-and-name team-id player-number player-name (today)))
  ([team-id player-number player-name date] (player-id-by-team-number-and-name team-id player-number player-name date db-pg/pg-datasource))
  ([team-id player-number player-name date datasource]
    (first (map :player_id
                (jdbc/query datasource
                            [(format
                               "SELECT player_teams.player_id FROM player_teams INNER JOIN player_names
                                  ON player_teams.player_id=player_names.player_id
                                  WHERE team_id=%d
                                  AND player_number=%d
                                  AND player_names.player_name LIKE '%%%s%%'
                                  AND (start_date <= '%s' OR start_date is NULL)
                                  AND (stop_date >= '%s' OR stop_date is NULL)"
                               team-id player-number player-name date date)])))))

(defn player-game-shifts
  ([player-id game-id] (player-game-shifts player-id game-id db-pg/pg-datasource))
  ([player-id game-id datasource]
    (jdbc/query datasource [(format "SELECT * FROM player_shifts WHERE player_id=%d AND game_id='%s'" player-id game-id)])))

(defn player-names
  ([player-id] (player-names player-id db-pg/pg-datasource))
  ([player-id datasource]
    (map :player_name (jdbc/query datasource [(format "SELECT player_name FROM player_names WHERE player_id=%d " player-id)]))))

(defn player-teams
  "Returns a players teams and numbers with those teams, along with an optional start/stop date for that team & number combination"
  ([player-id] (player-teams player-id db-pg/pg-datasource))
  ([player-id datasource]
    (jdbc/query datasource [(format "SELECT * FROM player_teams WHERE player_id=%d " player-id)])))

(defn player-ids-with-multiple-different-names
  ([] (player-ids-with-multiple-different-names db-pg/pg-datasource))
  ([datasource]
    (map :player_id (jdbc/query datasource ["SELECT player_id FROM player_names GROUP BY player_id HAVING count(*) >1"]))))

(defn add-player-name
  ([player-id player-name] (add-player-name player-id player-name db-pg/pg-datasource))
  ([player-id player-name datasource]
    (jdbc/insert! datasource :player_names {:player_id player-id :player_name player-name} )))

(defn add-player-team
  ([player-id team-id player-number] (add-player-team player-id team-id player-number db-pg/pg-datasource))
  ([player-id team-id player-number datasource]
    (jdbc/insert! datasource :player_teams {:player_id player-id :team_id team-id :player_number player-number} )))

(defn add-player
  ([] (add-player db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["INSERT INTO players DEFAULT VALUES RETURNING id"])))

(defn add-new-player-to-team
  ([team-id player-number player-name] (add-new-player-to-team team-id player-number player-name db-pg/pg-datasource))
  ([team-id player-number player-name datasource]
    (let [player-id (get (first (add-player datasource)) :id)]
      (add-player-name player-id player-name datasource)
      (add-player-team player-id team-id player-number datasource))))

(defn update-player-name
  ([player-id player-name] (update-player-name player-id player-name db-pg/pg-datasource))
  ([player-id player-name datasource]
    (if (nil? (some #{player-name} (player-names player-id datasource)))
        (add-player-name player-id player-name datasource))))

(defn update-player-on-team
  "Adds player to team if the player number is missing and adds new player names to player.
   ASSUMES player-number uniquely identifies a player"
  ([team-id player-number player-name] (update-player-on-team team-id player-number player-name (today)))
  ([team-id player-number player-name date] (update-player-on-team team-id player-number player-name date db-pg/pg-datasource))
  ([team-id player-number player-name date datasource]
    (if (nil? (some #{player-number} (team-player-numbers team-id date datasource)))
        (add-new-player-to-team team-id player-number player-name datasource)
        (if-let [player-id (player-id-by-team-and-number team-id player-number date datasource)]
          (update-player-name player-id player-name datasource)))))

(defn update-players-on-team [team-id players-list]
  (doseq [player-data players-list]
    (apply update-player-on-team team-id (remove nil? (map player-data [:number :name])))))

(defn add-game-player
  ([game-id team-id player-id active] (add-game-player game-id team-id player-id active nil))
  ([game-id team-id player-id active position] (add-game-player game-id team-id player-id active position false false))
  ([game-id team-id player-id active position captain assistant]
    (add-game-player game-id team-id player-id active position false false db-pg/pg-datasource))
  ([game-id team-id player-id active position captain assistant datasource]
    (jdbc/insert! datasource :game_players {:player_id player-id
                                            :team_id team-id
                                            :game_id game-id
                                            :active active
                                            :position position
                                            :captian captain
                                            :assistant assistant} )))

(dire.core/with-handler! #'add-game-player
  org.postgresql.util.PSQLException
   (fn [e & args] (println (str e))))

(defn add-game-players [game-id team-id players-list]
  (doseq [player-data players-list]
    (let [player-id (player-id-by-team-and-number team-id (get player-data :number))]
      (apply add-game-player game-id team-id player-id (remove nil? (map player-data [:active :position :captain :assistant]))))))

(defn delete-game-players
  ([game-id] (delete-game-players game-id db-pg/pg-datasource))
  ([game-id datasource]
    (jdbc/delete! datasource :game_players [(format "game_id='%s'" game-id)])))

(defn update-players-and-roster [game-id team-id players-list]
  (update-players-on-team team-id players-list)
  (add-game-players game-id team-id players-list))

(defn add-player-shift
  ([player-id team-id game-id shift-number period start-time stop-time duration event-code]
    (add-player-shift player-id team-id game-id shift-number period start-time stop-time duration event-code db-pg/pg-datasource))
  ([player-id team-id game-id shift-number period start-time stop-time duration event-code datasource]
    (jdbc/insert! datasource :player_shifts {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :shift_number shift-number
                                             :period period
                                             :start_time start-time
                                             :stop_time stop-time
                                             :duration duration
                                             :event_code event-code})))

(defn add-game-player-shifts
  ([game-id team-id player-shifts-list] (add-game-player-shifts game-id team-id player-shifts-list db-pg/pg-datasource))
  ([game-id team-id player-shifts-list datasource]
    (doseq [[player-number player-shifts] player-shifts-list]
      (doseq [player-shift player-shifts]
        (let [player-id (player-id-by-team-and-number team-id player-number)]
          (apply add-player-shift player-id team-id game-id player-shift))))))

(defn delete-game-player-shifts
  ([game-id] (delete-game-player-shifts game-id db-pg/pg-datasource))
  ([game-id datasource]
    (jdbc/delete! datasource :player_shifts [(format "game_id='%s'" game-id)])))

(defn add-player-shot
  ([player-id team-id game-id play-id shot-type-id goal period play-time strength]
    (add-player-shot player-id team-id game-id play-id shot-type-id goal period play-time strength db-pg/pg-datasource))
  ([player-id team-id game-id play-id shot-type-id goal period play-time strength datasource]
    (jdbc/insert! datasource :player_shots {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :shot_type_id shot-type-id
                                             :goal goal
                                             :period period
                                             :play_time play-time
                                             :strength strength})))

(defn add-player-block
  ([player-id team-id game-id play-id period play-time strength]
    (add-player-block player-id team-id game-id play-id period play-time strength db-pg/pg-datasource))
  ([player-id team-id game-id play-id period play-time strength datasource]
    (jdbc/insert! datasource :player_blocks {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :period period
                                             :play_time play-time
                                             :strength strength})))

(defn add-player-faceoff
  ([player-id team-id game-id play-id period play-time strength zone won]
    (add-player-faceoff player-id team-id game-id play-id period play-time strength zone won db-pg/pg-datasource))
  ([player-id team-id game-id play-id period play-time strength zone won datasource]
    (jdbc/insert! datasource :player_faceoffs {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :period period
                                             :play_time play-time
                                             :strength strength
                                             :zone zone
                                             :won won})))

(defn add-player-giveaway
  ([player-id team-id game-id play-id period play-time strength zone]
    (add-player-giveaway player-id team-id game-id play-id period play-time strength zone db-pg/pg-datasource))
  ([player-id team-id game-id play-id period play-time strength zone datasource]
    (jdbc/insert! datasource :player_giveaways {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :period period
                                             :play_time play-time
                                             :strength strength
                                             :zone zone})))

(defn add-player-takeaway
  ([player-id team-id game-id play-id period play-time strength zone]
    (add-player-takeaway player-id team-id game-id play-id period play-time strength zone db-pg/pg-datasource))
  ([player-id team-id game-id play-id period play-time strength zone datasource]
    (jdbc/insert! datasource :player_takeaways {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :period period
                                             :play_time play-time
                                             :strength strength
                                             :zone zone})))

(defn add-player-hit
  ([player-id team-id game-id play-id period play-time strength zone hitting-player]
    (add-player-hit player-id team-id game-id play-id period play-time strength zone hitting-player db-pg/pg-datasource))
  ([player-id team-id game-id play-id period play-time strength zone hitting-player datasource]
    (jdbc/insert! datasource :player_hits {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :period period
                                             :play_time play-time
                                             :strength strength
                                             :zone zone
                                             :hitting_player hitting-player})))

(defn add-player-penalty
  ([player-id team-id game-id play-id period play-time strength zone description major duration served-by]
    (add-player-penalty player-id team-id game-id play-id period play-time strength zone description major duration served-by db-pg/pg-datasource))
  ([player-id team-id game-id play-id period play-time strength zone description major duration served-by datasource]
    (jdbc/insert! datasource :player_penalties {:player_id player-id
                                             :team_id team-id
                                             :game_id game-id
                                             :play_id play-id
                                             :period period
                                             :play_time play-time
                                             :strength strength
                                             :zone zone
                                             :description description
                                             :major major
                                             :duration duration
                                             :served_by served-by})))

(defn add-player-drawn-penalty
  ([player-id penalty-id]
    (add-player-drawn-penalty player-id penalty-id db-pg/pg-datasource))
  ([player-id penalty-id datasource]
    (jdbc/insert! datasource :player_drawn_penalties {:player_id player-id
                                             :penalty_id penalty-id})))
