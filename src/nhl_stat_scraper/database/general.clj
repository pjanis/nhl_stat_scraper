(ns nhl-stat-scraper.database.general
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn insert-values
  ([values table-name] (insert-values values table-name db-pg/pg-datasource))
  ([values table-name datasource]
    (first (jdbc/insert! datasource (keyword table-name) values))))

(defn update-values
  ([values db-id-column db-id table-name] (update-values values db-id-column db-id table-name db-pg/pg-datasource))
  ([values db-id-column db-id table-name datasource]
    (do
      (jdbc/update! datasource (keyword table-name) values [(str db-id-column " = ?") db-id])
      (jdbc/query datasource [(str "SELECT * FROM " table-name " WHERE " db-id-column " = ?") db-id]))))

(defn update-values-complex-key
  ([values key-values table-name] (update-values-complex-key values key-values table-name db-pg/pg-datasource))
  ([values key-values table-name datasource]
    (let [key-keys (keys key-values)]
      (do
        (jdbc/update! datasource (keyword table-name) values (concat [(string/join " AND " (map #(str (name %) " = ?") key-keys))]
                                 (map #(get key-values %) key-keys)))
        (jdbc/query datasource (concat [(str "SELECT * FROM "
                                             table-name
                                             " WHERE " (string/join " AND " (map #(str (name %) " = ?") key-keys)))]
                                 (map #(get key-values %) key-keys)))))))

(defn clear-table
  ([table-name] (clear-table table-name db-pg/pg-datasource))
  ([table-name datasource]
    (jdbc/delete! datasource table-name [])))

(defn delete-row
  ([db-id-column db-id table-name] (delete-row db-id-column db-id table-name db-pg/pg-datasource))
  ([db-id-column db-id table-name datasource]
    (jdbc/delete! datasource table-name [(str db-id-column " = ?") db-id])))

(defn reset-table
  ([table-name] (reset-table table-name db-pg/pg-datasource))
  ([table-name datasource]
    (jdbc/execute! datasource [(str "TRUNCATE TABLE " table-name " RESTART IDENTITY")])))

(defn get-table
  ([table-name] (get-table table-name db-pg/pg-datasource))
  ([table-name datasource]
    (jdbc/query datasource [(str "SELECT * FROM " table-name)])))

(defn get-all-by-values
  ([values table-name] (get-all-by-values values table-name db-pg/pg-datasource))
  ([values table-name datasource]
    (let [values-keys (keys values)]
      (jdbc/query datasource (concat
                               [(str "SELECT * FROM " table-name
                                     " WHERE " (string/join " AND " (map #(str (name %) " = ?") values-keys)))]
                               (map #(get values %) values-keys))))))

(defn get-all-by-values-with-joins
  ([values joins select table-name] (get-all-by-values-with-joins values joins select table-name db-pg/pg-datasource))
  ([values joins select table-name datasource]
    (let [values-keys (keys values)]
      (jdbc/query datasource (concat
                               [(str "SELECT " select " FROM " table-name " "
                                     (string/join " " joins)
                                     " WHERE " (string/join " AND " (map #(str (name %) " = ?") values-keys)))]
                               (map #(get values %) values-keys))))))

(defn get-first-by-values
  ([values order-by table-name] (get-first-by-values values order-by table-name db-pg/pg-datasource))
  ([values order-by table-name datasource]
    (let [values-keys (keys values)]
      (first (jdbc/query datasource (concat
                                      [(str "SELECT * FROM " table-name
                                            " WHERE "
                                            (string/join " AND " (map #(str (name %) " = ?") values-keys))
                                            " ORDER BY " order-by
                                            " LIMIT 1")]
                                      (map #(get values %) values-keys)))))))

(defn get-first-by-values-with-joins
  ([values joins order-by select table-name] (get-first-by-values-with-joins values joins order-by select table-name db-pg/pg-datasource))
  ([values joins order-by select table-name datasource]
    (let [values-keys (keys values)]
      (first (jdbc/query datasource (concat
                                      [(str "SELECT " select " FROM " table-name " "
                                            (string/join " " joins)
                                            " WHERE "
                                            (string/join " AND " (map #(str (name %) " = ?") values-keys))
                                            " ORDER BY " order-by
                                            " LIMIT 1")]
                                      (map #(get values %) values-keys)))))))
