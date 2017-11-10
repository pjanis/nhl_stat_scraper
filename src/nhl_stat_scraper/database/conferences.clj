(ns nhl-stat-scraper.database.conferences
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [nhl-stat-scraper.common.ranged :as common-ranged]
    [nhl-stat-scraper.database.general :as db-general]
    [nhl-stat-scraper.database.ranged :as db-ranged]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn insert-conference
  ([conference-name seasons-active] (insert-conference conference-name seasons-active db-pg/pg-datasource))
  ([conference-name seasons-active datasource]
   (db-general/insert-values {:name conference-name :seasons_active (db-ranged/to-season-range seasons-active)} "conferences" datasource)))

(defn update-conference
  ([db-id values] (update-conference db-id values db-pg/pg-datasource))
  ([db-id values datasource]
    (db-general/update-values (db-ranged/filter-season-range values :season_active) "db_id" db-id "conferences" datasource)))

(defn get-conferences
  ([] (get-conferences  db-pg/pg-datasource))
  ([datasource] (db-general/get-table "conferences" datasource)))

(defn get-conferences-by-season
  ([season] (get-conferences-by-season season db-pg/pg-datasource))
  ([season datasource]
    (db-ranged/get-all-by-ranged {"seasons_active" (db-ranged/to-season season)} "conferences" datasource)))

(defn get-conferences-by-name
  ([conference-name] (get-conferences-by-name conference-name db-pg/pg-datasource))
  ([conference-name datasource] (db-general/get-all-by-values {:name conference-name} "conferences" datasource)))

(defn get-conference
  ([db-id] (get-conference db-id db-pg/pg-datasource))
  ([db-id datasource] (db-general/get-first-by-values {:db_id db-id} "db_id DESC" "conferences" datasource)))

(defn get-conference-by-name-and-season
  ([conference-name season] (get-conference-by-name-and-season conference-name season db-pg/pg-datasource))
  ([conference-name season datasource]
    (db-ranged/get-first-by-ranged-and-values {"seasons_active" (db-ranged/to-season season)}
                                              {:name conference-name}
                                              "db_id ASC"
                                              "conferences"
                                              datasource)))

(defn add-season-to-conference
  ([db-id season] (add-season-to-conference db-id season db-pg/pg-datasource))
  ([db-id season datasource]
    (if-let [conference (get-conference db-id)]
      (db-general/update-values {:seasons_active (db-ranged/to-season-range (common-ranged/add-to-int-range season (:seasons_active conference)))}
                                "db_id"
                                db-id
                                "conferences"
                                datasource))))

(defn add-new-season
  ([season conference-names] (add-new-season season conference-names db-pg/pg-datasource))
  ([season conference-names datasource]
    (doall
      (map (fn[conference-name]
             (if-let [conference (get-conference-by-name-and-season conference-name (dec season))]
               (do
                 (add-season-to-conference (:db_id conference) season datasource)
                 conference)
               (insert-conference conference-name [season season] datasource)))
           conference-names))))

