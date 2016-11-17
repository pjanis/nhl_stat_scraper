(ns nhl-stat-scraper.database.divisions
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn populate-divisions
  ([] (populate-divisions db-pg/pg-datasource))
  ([datasource]
    (jdbc/insert! datasource :divisions
          {:name "atlantic"}
          {:name "central"}
          {:name "metropolitan"}
          {:name "pacific"} )))

(defn db-divisions
  ([] (db-divisions db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * from divisions"])))
