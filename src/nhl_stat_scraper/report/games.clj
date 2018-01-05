(ns nhl-stat-scraper.report.games
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [clj-time.format]
    [nhl-stat-scraper.database.games :as db-games]
    [nhl-stat-scraper.database.teams :as db-teams]))

(defn game-points [game-summary team-score-field other-team-score-field]
  (if (> (get game-summary team-score-field) (get game-summary other-team-score-field))
    2
    (if (not (get game-summary :regulation_win)) 1 0)))

(defn shootout [game-summary team-score-field other-team-score-field]
  (not (or (get game-summary :regulation_win) (get game-summary :overtime_win))))

(defn overtime-win [game-summary team-score-field other-team-score-field]
  (and (> (get game-summary team-score-field) (get game-summary other-team-score-field))
       (get game-summary :overtime_win)))

(defn ongoing [game-summary]  ;score is nil for games that haven't started
  (boolean (get game-summary :home_team_score)))

(defn game-date-string [game-summary]
  (clj-time.format/unparse (clj-time.format/formatters :date) (get game-summary :game_date)))

(defn game-result [game-summary team-score-field other-team-score-field]
  (if (get game-summary :complete)
    (hash-map :game-id (get game-summary :game_id)
              :game-date (game-date-string game-summary)
              :complete true
              :ongoing false
              :points (game-points game-summary team-score-field other-team-score-field)
              :overtime-win (overtime-win game-summary team-score-field other-team-score-field)
              :shootout (shootout game-summary team-score-field other-team-score-field)
              :home (= :home_team_score team-score-field))
    (hash-map :game-id (get game-summary :game_id)
              :game-date (game-date-string game-summary)
              :complete false
              :ongoing (ongoing game-summary)
              :points 0
              :overtime-win false
              :shootout false
              :home (= :home_team_score team-score-field))))

(defn team-home-game-results [team-id season season-part]
  (map #(game-result % :home_team_score :visiting_team_score) (db-games/team-home-games team-id season season-part)))

(defn team-away-game-results [team-id season season-part]
  (map #(game-result % :visiting_team_score :home_team_score) (db-games/team-away-games team-id season season-part)))

(defn team-game-results [team-id season season-part]
  (map-indexed (fn [index item] (assoc item :bout (+ index 1)))
               (sort-by
                 (juxt :game-date :game-id)
                 (concat (team-home-game-results team-id season season-part) (team-away-game-results team-id season season-part)))))


(defn team-all-game-results [team-id season] ;depreciated, use team-game-results
  (team-game-results team-id season ""))

(defn team-regular-season-game-results [team-id season] ;depreciated, use team-game-results
  (team-game-results team-id season "regular"))

(defn team-points
  "Semi-private: Used to do the actual calculation"
  ([team-id season season-part results-function] (apply + (map :points (results-function team-id season season-part)))))

(defn team-home-points
  ([team-id] (team-home-points team-id 2015))
  ([team-id season] (team-home-points team-id season "regular"))
  ([team-id season season-part] (team-points team-id season season-part team-home-game-results)))

(defn team-away-points
  ([team-id] (team-away-points team-id 2015))
  ([team-id season] (team-away-points team-id season "regular"))
  ([team-id season season-part] (team-points team-id season season-part team-away-game-results)))

(defn team-total-points
  ([team-id] (team-total-points team-id 2015))
  ([team-id season] (team-total-points team-id season "regular"))
  ([team-id season season-part] (team-points team-id season season-part team-game-results)))

(defn team-wins
  ([team-id] (team-wins team-id 2015))
  ([team-id season] (team-wins team-id season "regular"))
  ([team-id season season-part] (->> (team-game-results team-id season season-part)
                                     (filter #(and (:complete %) (= (:points %) 2) (not (:shootout %))))
                                     (count))))


(defn results-record [game-results]
  (map count (map (group-by :points (filter :complete game-results)) [2 0 1])))

(defn team-home-record
  ([team-id] (team-home-record team-id 2015))
  ([team-id season] (team-home-record team-id season "regular"))
  ([team-id season season-part] (results-record (team-home-game-results team-id season season-part))))

(defn team-away-record
  ([team-id] (team-away-record team-id 2015))
  ([team-id season] (team-away-record team-id season "regular"))
  ([team-id season season-part] (results-record (team-away-game-results team-id season season-part))))

(defn team-record
  ([team-id] (team-record team-id 2015))
  ([team-id season] (team-record team-id season "regular"))
  ([team-id season season-part] (results-record (team-game-results team-id season season-part))))

(defn standings-comparison ;Ignores games played since ROW is first tiebreaker at season end
  ([team-1-id team-2-id] (standings-comparison team-1-id team-2-id 2015 "regular"))
  ([team-1-id team-2-id season season-part]
    (let [team-1-points (team-total-points team-1-id season season-part)
          team-2-points (team-total-points team-2-id season season-part)]
      (cond
        (> team-1-points team-2-points) true
        (< team-1-points team-2-points) false
        (> (team-wins team-1-id season season-part) (team-wins team-2-id season season-part)) true
        (< (team-wins team-1-id season season-part) (team-wins team-2-id season season-part)) false
        :else true)))) ;TODO add head-to-head and goal differential tie breakers


(defn html-teams-report
  ([] (html-teams-report 2015 "regular"))
  ([season season-part]
    (map (fn [team]
           (let [team-id (get team :db_id)]
             (hash-map :db_id team-id
                       :record (team-record team-id season season-part)
                       :points (team-total-points team-id season season-part))))
         (db-teams/get-teams))))

(defn game-state [game-summary]
  (cond
    (and (not (:complete game-summary)) (ongoing game-summary)) "in progress"
    (and (not (:complete game-summary)) (not (ongoing game-summary))) "scheduled"
    (:regulation_win game-summary) "final"
    (:overtime_win game-summary) "final/ot"
    :else "final/shootout"))

(defn games-exist
  ([season] (games-exist season "regular"))
  ([season season-part]
    (db-games/game-summaries-exist season season-part)))

(defn season-games [season season-part]
  (map (fn [game-summary]
         (hash-map :game-id (:game_id game-summary)
                   :game-date (game-date-string game-summary)
                   :home-team-name (db-teams/team-name (:home_team_db_id game-summary))
                   :home-team-score (:home_team_score game-summary)
                   :away-team-name (db-teams/team-name (:visiting_team_db_id game-summary))
                   :away-team-score (:visiting_team_score game-summary)
                   :game-state (game-state game-summary)))
       (db-games/db-game-summaries season season-part)))
