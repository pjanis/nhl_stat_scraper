(ns nhl-stat-scraper.database.teams
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [nhl-stat-scraper.common.parse :as common-parse]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn populate-teams [datasource teams]
    (apply jdbc/insert! datasource :teams (sort-by :name teams) ))

(defn db-teams
  ([] (db-teams db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * from teams"])))

(defn find-team-by-name
  ([team-name] (find-team-by-name team-name (db-teams)))
  ([team-name db-teams]
    (first (filter #(.contains (str/lower-case team-name) (get % :common_name)) db-teams))))

(defn find-team-id-by-name
  ([team-name] (find-team-id-by-name team-name (db-teams)))
  ([team-name db-teams]
    (get (find-team-by-name team-name db-teams) :db_id)))

(defn find-team-by-abreviation
  ([team-abreviation] (find-team-by-abreviation team-abreviation (db-teams)))
  ([team-abreviation db-teams]
    (let [cleaned-abreviation (str/lower-case (common-parse/remove-non-word team-abreviation))]
      (if (< (count cleaned-abreviation) 3)
        (let [match-teams (filter #(boolean (re-find (re-pattern (str "^" cleaned-abreviation)) (get % :abreviation))) db-teams)]
          (if (= 1 (count match-teams))
            (first match-teams)
            nil))
        (first (filter #(= (str/lower-case (common-parse/remove-non-word team-abreviation)) (get % :abreviation)) db-teams))))))

(defn find-team-id-by-abreviation
  ([team-abreviation] (find-team-id-by-abreviation team-abreviation (db-teams)))
  ([team-abreviation db-teams]
    (get (find-team-by-abreviation team-abreviation db-teams) :db_id)))

;Returns true for color that's not black or white.
(defn is-display-color [color]
  (not (or (= "#ffffff" (str/lower-case color)) (= "#000000" (str/lower-case color)))))

(defn populate-team-colors [datasource team-info]
    (let [teams (db-teams)]
      (apply jdbc/insert! datasource :team_colors
        (->> team-info
            (map (fn [team]
                   (map (fn [color]
                          (hash-map
                            :team_id (get (find-team-by-name (get team :team)) :db_id)
                            :color color
                            :display (is-display-color color)))
                        (get team :colors))))
            (flatten))
        )))

(defn display-colors
  ([team-id] (display-colors team-id db-pg/pg-datasource))
  ([team-id datasource]
    (jdbc/query datasource [(format "SELECT * FROM team_colors WHERE team_id=%d AND display=TRUE" team-id)])))

(defn populate-division-teams [datasource division-info team-info]
  (let [teams (db-teams)]
    (apply jdbc/insert! datasource :division_teams
     (map (fn [team]
            (hash-map
              :team_id (get (find-team-by-name (get team :team)) :db_id)
              :division_id (get (first (filter (fn [division] (= (get division :name) (get team :division))) division-info)) :db_id)))
          team-info
  ))))

(defn divisions
  ([] (divisions db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource 
      ["SELECT teams.db_id,divisions.name AS division FROM teams 
       INNER JOIN division_teams ON teams.db_id= division_teams.team_id
       INNER JOIN divisions ON division_teams.division_id= divisions.db_id"])))

(defn division
  ([team-id] (division team-id db-pg/pg-datasource))
  ([team-id datasource]
    (get (first (jdbc/query datasource 
                  [(format "SELECT divisions.name FROM teams 
                           INNER JOIN division_teams ON teams.db_id= division_teams.team_id
                           INNER JOIN divisions ON division_teams.division_id= divisions.db_id
                           where team_id=%d" team-id)])) :name)))

(defn populate-conference-teams [datasource conference-info team-info]
  (let [teams (db-teams)]
    (apply jdbc/insert! datasource :conference_teams
     (map (fn [team]
            (hash-map
              :team_id (get (find-team-by-name (get team :team)) :db_id)
              :conference_id (get (first (filter (fn [conference] (= (get conference :name) (get team :conference))) conference-info)) :db_id)))
          team-info
  ))))

(defn conferences
  ([] (conferences db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource 
      ["SELECT teams.db_id,conferences.name AS conference FROM teams 
       INNER JOIN conference_teams ON teams.db_id= conference_teams.team_id
       INNER JOIN conferences ON conference_teams.conference_id= conferences.db_id"])))

(defn conference
  ([team-id] (conference team-id db-pg/pg-datasource))
  ([team-id datasource]
    (get (first (jdbc/query datasource 
                  [(format "SELECT conferences.name FROM teams 
                           INNER JOIN conference_teams ON teams.db_id= conference_teams.team_id
                           INNER JOIN conferences ON conference_teams.conference_id= conferences.db_id
                           where teams.db_id=%d" team-id)])) :name)))

(defn team-name
  ([team-id] (team-name team-id db-pg/pg-datasource))
  ([team-id datasource]
    (get (first (jdbc/query datasource 
                  [(format "SELECT teams.name FROM teams 
                           WHERE db_id=%d" team-id)])) :name)))
