(ns nhl-stat-scraper.database.teams
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [nhl-stat-scraper.common.parse :as common-parse]
    [nhl-stat-scraper.common.ranged :as common-ranged]
    [nhl-stat-scraper.database.general :as db-general]
    [nhl-stat-scraper.database.ranged :as db-ranged]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(def divisions-join-clauses [" INNER JOIN division_teams ON teams.db_id = division_teams.team_id ",
                            " INNER JOIN divisions ON division_teams.division_id = divisions.db_id "])

(def conferences-join-clauses (concat divisions-join-clauses
                                     [" INNER JOIN conference_divisions ON divisions.db_id = conference_divisions.division_id ",
                                      " INNER JOIN conferences ON conference_divisions.conference_id = conferences.db_id "]))

(defn insert-team
  ([team-name common-name abreviation seasons-active]
    (insert-team team-name common-name abreviation seasons-active db-pg/pg-datasource))
  ([team-name common-name abreviation seasons-active datasource]
    (db-general/insert-values {:name team-name
                               :common_name common-name
                               :abreviation abreviation
                               :seasons_active (db-ranged/to-season-range seasons-active)}
                              "teams"
                              datasource)))

(defn update-team
  ([db-id values]
    (update-team db-id values db-pg/pg-datasource))
  ([db-id values datasource]
    (db-general/update-values (db-ranged/filter-season-range values :seasons_active) "db_id" db-id "teams" datasource)))

(defn get-teams
  ([] (get-teams db-pg/pg-datasource))
  ([datasource]
    (db-general/get-table "teams" datasource)))

(defn get-teams-by-name
  ([team-name] (get-teams-by-name team-name db-pg/pg-datasource))
  ([team-name datasource] (db-general/get-all-by-values {:name team-name} "teams" datasource)))

(defn get-teams-by-season
  ([season] (get-teams-by-season season db-pg/pg-datasource))
  ([season datasource] (db-ranged/get-all-by-ranged {"seasons_active" (db-ranged/to-season season)} "teams" datasource)))

(defn get-teams-by-season-with-division-and-conference
  ([season] (get-teams-by-season-with-division-and-conference season db-pg/pg-datasource))
  ([season datasource]
    (db-ranged/get-all-by-ranged-and-values-with-joins {"teams.seasons_active" (db-ranged/to-season season)
                                                          "division_teams.seasons_active" (db-ranged/to-season season)
                                                          "divisions.seasons_active" (db-ranged/to-season season)
                                                          "conference_divisions.seasons_active" (db-ranged/to-season season)
                                                          "conferences.seasons_active" (db-ranged/to-season season)}
                                                         {}
                                                         conferences-join-clauses
                                                         (string/join " , "
                                                           ["teams.*"
                                                            "divisions.db_id as division_id"
                                                            "divisions.name as division_name"
                                                            "conferences.db_id as conference_id"
                                                            "conferences.name as conference_name"])
                                                         "teams"
                                                         datasource)))

(defn get-team-by-id
  ([db-id] (get-team-by-id db-id db-pg/pg-datasource))
  ([db-id datasource] (db-general/get-first-by-values {:db_id db-id} "db_id DESC" "teams" datasource)))

(defn get-team-by-statsapi-id
  ([statsapi-id] (get-team-by-statsapi-id statsapi-id db-pg/pg-datasource))
  ([statsapi-id datasource] (db-general/get-first-by-values {:statsapi_id statsapi-id} "db_id DESC" "teams" datasource)))

(defn get-team-by-name-and-season
  ([team-name season] (get-team-by-name-and-season team-name season db-pg/pg-datasource))
  ([team-name season datasource] (db-ranged/get-first-by-ranged-and-values {"seasons_active" (db-ranged/to-season season)}
                                                                           {:name team-name}
                                                                           "db_id ASC"
                                                                           "teams"
                                                                           datasource)))
(defn get-team-by-abreviation-and-season
  ([abreviation season] (get-team-by-abreviation-and-season abreviation season db-pg/pg-datasource))
  ([abreviation season datasource] (db-ranged/get-first-by-ranged-and-values {"seasons_active" (db-ranged/to-season season)}
                                                                             {:abreviation abreviation}
                                                                             "db_id ASC"
                                                                             "teams"
                                                                             datasource)))

(defn set-all-seasons
  "Sets seasons_active for all teams. Used when updating league structure from season-less teams"
  ([seasons-active] (set-all-seasons seasons-active db-pg/pg-datasource))
  ([seasons-active datasource]
    (let [teams (get-teams datasource)]
      (doseq [team teams] (update-team (get team :db_id) {:seasons_active (db-ranged/to-season-range seasons-active)})))))

(defn add-season-to-team
  ([db-id season] (add-season-to-team db-id season db-pg/pg-datasource))
  ([db-id season datasource]
    (if-let [team (get-team-by-id db-id)]
      (first (db-general/update-values {:seasons_active (db-ranged/to-season-range (common-ranged/add-to-int-range season (:seasons_active team)))}
                                "db_id"
                                db-id
                                "teams"
                                datasource)))))

(defn add-new-season
  ([season team-names] (add-new-season season team-names db-pg/pg-datasource))
  ([season team-names datasource]
    (doall
      (map (fn[team-name]
             (if-let [team (get-team-by-name-and-season team-name (dec season))]
               (add-season-to-team (:db_id team) season datasource)
               (insert-team team-name nil nil [season season] datasource)))
           team-names))))

;Returns true for color that's not black or white.
(defn is-display-color [color]
  (not (or (= "#ffffff" (string/lower-case color)) (= "#000000" (string/lower-case color)))))

(defn populate-team-colors [datasource team-info]
    (let [teams (get-teams)]
      (apply jdbc/insert! datasource :team_colors
        (->> team-info
            (map (fn [team]
                   (map (fn [color]
                          (hash-map
                            :team_id (get (last (get-teams-by-name (get team :team))) :db_id)
                            :color color
                            :display (is-display-color color)))
                        (get team :colors))))
            (flatten))
        )))

(defn display-colors
  ([team-id] (display-colors team-id db-pg/pg-datasource))
  ([team-id datasource]
    (jdbc/query datasource [(format "SELECT * FROM team_colors WHERE team_id=%d AND display=TRUE" team-id)])))

(defn get-divisions-by-id
  ([team-id] (get-divisions-by-id team-id db-pg/pg-datasource))
  ([team-id datasource]
    (db-general/get-all-by-values-with-joins {"teams.db_id" team-id}
                                             divisions-join-clauses
                                             "divisions.*"
                                             "teams"
                                             datasource)))

(defn get-division-by-id-and-season
  ([team-id season] (get-division-by-id-and-season team-id season db-pg/pg-datasource))
  ([team-id season datasource]
    (db-ranged/get-first-by-ranged-and-values-with-joins {"teams.seasons_active" (db-ranged/to-season season)
                                                          "division_teams.seasons_active" (db-ranged/to-season season)
                                                          "divisions.seasons_active" (db-ranged/to-season season)}
                                                         {"teams.db_id" team-id}
                                                         divisions-join-clauses
                                                         "teams.db_id DESC"
                                                         "divisions.*"
                                                         "teams"
                                                         datasource)))

(defn get-conferences-by-id
  ([team-id] (get-conferences-by-id team-id db-pg/pg-datasource))
  ([team-id datasource]
    (db-general/get-all-by-values-with-joins {"teams.db_id" team-id}
                                             conferences-join-clauses
                                             "conferences.*"
                                             "teams"
                                             datasource)))

(defn get-conference-by-id-and-season
  ([team-id season] (get-conference-by-id-and-season team-id season db-pg/pg-datasource))
  ([team-id season datasource]
    (db-ranged/get-first-by-ranged-and-values-with-joins {"teams.seasons_active" (db-ranged/to-season season)
                                                          "division_teams.seasons_active" (db-ranged/to-season season)
                                                          "divisions.seasons_active" (db-ranged/to-season season)
                                                          "conference_divisions.seasons_active" (db-ranged/to-season season)
                                                          "conferences.seasons_active" (db-ranged/to-season season)}
                                                         {"teams.db_id" team-id}
                                                         conferences-join-clauses
                                                         "teams.db_id DESC"
                                                         "conferences.*"
                                                         "teams"
                                                         datasource)))

(defn team-name
  ([team-id] (team-name team-id db-pg/pg-datasource))
  ([team-id datasource] (get (get-team-by-id team-id datasource) :name)))

(defn get-division-team
  ([team-id division-id season] (get-division-team team-id division-id season db-pg/pg-datasource))
  ([team-id division-id season datasource]
    (db-ranged/get-first-by-ranged-and-values {:seasons_active (db-ranged/to-season season)}
                                              {:team_id team-id :division_id division-id}
                                              "team_id ASC"
                                              "division_teams"
                                              datasource)))

(defn insert-division-team
  ([team-id division-id seasons-active]
    (insert-division-team team-id division-id seasons-active db-pg/pg-datasource))
  ([team-id division-id seasons-active datasource]
    (db-general/insert-values {:team_id team-id :division_id division-id :seasons_active (db-ranged/to-season-range seasons-active)}
                              "division_teams"
                              datasource)))

(defn update-division-team
  ([team-id division-id previous-active-season seasons-active]
    (update-division-team team-id division-id previous-active-season seasons-active db-pg/pg-datasource))
  ([team-id division-id previous-active-season seasons-active datasource]
    (db-ranged/update-values {:seasons_active (db-ranged/to-season-range seasons-active)} {:team_id team-id :division_id division-id} {:seasons_active (db-ranged/to-season previous-active-season)} "division_teams" datasource)))

(defn add-season-to-division-team
  ([season division-id team-id] (add-season-to-division-team season division-id team-id db-pg/pg-datasource))
  ([season division-id team-id datasource]
    (if-let [division-team (get-division-team team-id division-id (dec season))]
      (first (update-division-team team-id
                            division-id
                            (dec season)
                            (common-ranged/add-to-int-range season (:seasons_active division-team))
                            datasource)))))

(defn add-new-season-to-division-teams
  ([season division-id team-ids] (add-new-season-to-division-teams season division-id team-ids db-pg/pg-datasource))
  ([season division-id team-ids datasource]
    (doseq [team-id team-ids]
      (if-let [division-team (get-division-team team-id division-id (dec season) datasource)]
        (add-season-to-division-team season division-id team-id datasource)
        (insert-division-team team-id division-id [season season] datasource)))))
