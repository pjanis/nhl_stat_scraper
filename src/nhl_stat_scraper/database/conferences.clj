(ns nhl-stat-scraper.database.conferences
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn populate-conferences
  ([] (populate-conferences db-pg/pg-datasource))
  ([datasource]
    (jdbc/insert! datasource :conferences
          {:name "eastern"}
          {:name "western"})))

(defn db-conferences
  ([] (db-conferences  db-pg/pg-datasource))
  ([datasource]
    (jdbc/query datasource ["SELECT * from conferences"])))
