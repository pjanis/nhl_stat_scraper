(ns nhl-stat-scraper.database.divisions
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [nhl-stat-scraper.common.ranged :as common-ranged]
    [nhl-stat-scraper.database.general :as db-general]
    [nhl-stat-scraper.database.ranged :as db-ranged]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn insert-division
  ([division-name seasons-active] (insert-division division-name seasons-active db-pg/pg-datasource))
  ([division-name seasons-active datasource]
   (db-general/insert-values {:name division-name :seasons_active (db-ranged/to-season-range seasons-active)} "divisions" datasource)))

(defn update-division
  ([db-id values] (update-division db-id values db-pg/pg-datasource))
  ([db-id values datasource]
    (db-general/update-values (db-ranged/filter-season-range values :seasons_active) "db_id" db-id "divisions" datasource)))

(defn get-divisions
  ([] (get-divisions  db-pg/pg-datasource))
  ([datasource] (db-general/get-table "divisions" datasource)))

(defn get-divisions-by-season
  ([season] (get-divisions-by-season season db-pg/pg-datasource))
  ([season datasource]
    (db-ranged/get-all-by-ranged {"seasons_active" (db-ranged/to-season season)} "divisions" datasource)))

(defn get-divisions-by-name
  ([division-name] (get-divisions-by-name division-name db-pg/pg-datasource))
  ([division-name datasource] (db-general/get-all-by-values {:name division-name} "divisions" datasource)))

(defn get-division
  ([db-id] (get-division db-id db-pg/pg-datasource))
  ([db-id datasource] (db-general/get-first-by-values {:db_id db-id} "db_id DESC" "divisions" datasource)))

(defn get-division-by-name-and-season
  ([division-name season] (get-division-by-name-and-season division-name season db-pg/pg-datasource))
  ([division-name season datasource]
    (db-ranged/get-first-by-ranged-and-values {"seasons_active" (db-ranged/to-season season)}
                                              {:name division-name}
                                              "db_id ASC"
                                              "divisions"
                                              datasource)))

(defn set-all-seasons
  "Sets seasons_active for all divisions. Used when updating league structure from season-less divisions"
  ([seasons-active] (set-all-seasons seasons-active db-pg/pg-datasource))
  ([seasons-active datasource]
    (let [divisions (get-divisions datasource)]
      (doseq [division divisions] (update-division (get division :db_id) {:seasons_active (db-ranged/to-season-range seasons-active)})))))

(defn add-season-to-division
  ([db-id season] (add-season-to-division db-id season db-pg/pg-datasource))
  ([db-id season datasource]
    (if-let [division (get-division db-id)]
      (first (db-general/update-values {:seasons_active (db-ranged/to-season-range (common-ranged/add-to-int-range season (:seasons_active division)))}
                                "db_id"
                                db-id
                                "divisions"
                                datasource)))))

(defn add-new-season
  ([season division-names] (add-new-season season division-names db-pg/pg-datasource))
  ([season division-names datasource]
    (doall
      (map (fn[division-name]
             (if-let [division (get-division-by-name-and-season division-name (dec season))]
               (add-season-to-division (:db_id division) season datasource)
               (insert-division division-name [season season] datasource)))
           division-names))))

(defn get-conference-division
  ([division-id conference-id season] (get-conference-division division-id conference-id season db-pg/pg-datasource))
  ([division-id conference-id season datasource]
    (db-ranged/get-first-by-ranged-and-values {:seasons_active (db-ranged/to-season season)}
                                              {:division_id division-id :conference_id conference-id}
                                              "division_id ASC"
                                              "conference_divisions"
                                              datasource)))

(defn insert-conference-division
  ([division-id conference-id seasons-active]
    (insert-conference-division division-id conference-id seasons-active db-pg/pg-datasource))
  ([division-id conference-id seasons-active datasource]
    (db-general/insert-values {:division_id division-id :conference_id conference-id :seasons_active (db-ranged/to-season-range seasons-active)}
                              "conference_divisions"
                              datasource)))

(defn update-conference-division
  ([division-id conference-id previous-active-season seasons-active]
    (update-conference-division division-id conference-id previous-active-season seasons-active db-pg/pg-datasource))
  ([division-id conference-id previous-active-season seasons-active datasource]
    (db-ranged/update-values {:seasons_active (db-ranged/to-season-range seasons-active)} {:division_id division-id :conference_id conference-id} {:seasons_active (db-ranged/to-season (int previous-active-season))} "conference_divisions" datasource)))

(defn add-season-to-conference-division
  ([season conference-id division-id] (add-season-to-conference-division season conference-id division-id db-pg/pg-datasource))
  ([season conference-id division-id datasource]
    (if-let [conference-division (get-conference-division division-id conference-id (dec season))]
      (first (update-conference-division division-id
                                         conference-id
                                         (dec season)
                                         (common-ranged/add-to-int-range season (:seasons_active conference-division))
                                         datasource)))))

(defn add-new-season-to-conference-divisions
  ([season conference-id division-ids] (add-new-season-to-conference-divisions season conference-id division-ids db-pg/pg-datasource))
  ([season conference-id division-ids datasource]
    (doseq [division-id division-ids]
      (if-let [conference-division (get-conference-division division-id conference-id (dec season) datasource)]
        (add-season-to-conference-division season conference-id division-id datasource)
        (insert-conference-division division-id conference-id [season season] datasource)))))

