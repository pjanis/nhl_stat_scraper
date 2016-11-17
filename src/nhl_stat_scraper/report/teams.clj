(ns nhl-stat-scraper.report.teams
  (:require [nhl-stat-scraper.database.teams :as db-teams]
            [nhl-stat-scraper.database.games :as db-games]))


(defn list-unique-remap
  "Remaps the list (of maps) to a map with keys determined by map-key. Map-key values must be unique to prevent being overriden"
  [list-to-remap map-key]
  (into {} (map (fn [item] [(get item map-key) item]) list-to-remap)))

(defn list-remap
  "Remaps the list (of maps) to a map with keys determined by map-key. New map values are lists"
  [list-to-remap map-key]
  (apply merge-with concat (map (fn [item] {(get item map-key) [item]}) list-to-remap)))

(defn join-teams [& teams-lists]
  "Merges all the values for the teams in the teams-list"
  (let [id-maps (map #(list-unique-remap % :db_id) teams-lists)]
    (vals (apply merge-with merge id-maps))))

(defn conference-teams []
  (list-remap (join-teams (db-teams/db-teams) (db-teams/divisions) (db-teams/conferences)) :conference))

(defn conference-division-teams []
  (reduce (fn [remapped [conference teams]]
            (assoc remapped conference (list-remap teams :division)))
          {}
          (conference-teams)))

(defn html-conference-division-teams []
  (let [cdt (conference-division-teams)
        conferences (keys cdt)]
    (map (fn [conference]
           (hash-map :conference-name conference
                     :conference-divisions (map (fn [division]
                                                  (hash-map :division-name division
                                                            :division-teams (map (fn [team]
                                                                                   (clojure.set/rename-keys team {:name :team-name
                                                                                                                  :db_id :team-id}))
                                                                                 (get-in cdt [conference division]))))
                                                (keys (get cdt conference)))))
         conferences)))

(defn team-name [team-id]
  (db-teams/team-name team-id))

(defn conference [team-id]
  (db-teams/conference team-id))

(defn division [team-id]
  (db-teams/division team-id))
