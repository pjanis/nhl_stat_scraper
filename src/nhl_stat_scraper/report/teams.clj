(ns nhl-stat-scraper.report.teams
  (:require [nhl-stat-scraper.database.teams :as db-teams]
            [nhl-stat-scraper.database.games :as db-games]))

(def default-season 2016) ;temporary until config defaults are added.

(defn list-unique-remap
  "Remaps the list (of maps) to a map with keys determined by map-key. Map-key values must be unique to prevent being overriden"
  [list-to-remap map-key]
  (into {} (map (fn [item] [(get item map-key) item]) list-to-remap)))

(defn list-remap
  "Remaps the list (of maps) to a map with keys determined by map-key. New map values are lists"
  [list-to-remap map-key]
  (apply merge-with concat (map (fn [item] {(get item map-key) [item]}) list-to-remap)))

(defn join-teams
  "Merges all the values for the teams in the teams-list"
  [& teams-lists]
  (let [id-maps (map #(list-unique-remap % :db_id) teams-lists)]
    (vals (apply merge-with merge id-maps))))


(defn html-conference-division-teams [season datasource]
  (let [teams-plus (db-teams/get-teams-by-season-with-division-and-conference season datasource)
        team-conferences (group-by #(get % :conference_name) teams-plus)]
    (map (fn [conference]
           (hash-map :conference-name conference
                     :conference-divisions (let [team-divisions (group-by #(get % :division_name) (get team-conferences conference))]
                                            (map (fn [division]
                                                  (hash-map :division-name division
                                                            :division-teams (map (fn [team]
                                                                                   (clojure.set/rename-keys team {:name :team-name
                                                                                                                  :db_id :team-id}))
                                                                                 (get team-divisions division))))
                                                (keys team-divisions)))))
         (keys team-conferences))))

(defn team-name [team-id]
  (db-teams/team-name team-id))
