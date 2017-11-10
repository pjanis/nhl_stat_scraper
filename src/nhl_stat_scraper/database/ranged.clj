(ns nhl-stat-scraper.database.ranged
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [clj-time.format]
    [nhl-stat-scraper.common.ranged :as common-ranged]
    [nhl-stat-scraper.database.ranged-types :as ranged-types]
    [nhl-stat-scraper.database.postgres :as db-pg]))

(defn update-values
  ([values key-values ranged-values table-name] (update-values values key-values ranged-values table-name db-pg/pg-datasource))
  ([values key-values ranged-values table-name datasource]
    (let [key-keys (keys key-values)
          ranged-keys (keys ranged-values)]
      (do
        (jdbc/update! datasource
                      (keyword table-name)
                      values
                      (concat [(string/join " AND " (concat (map #(str (name %) " = ?") key-keys) (map #(str (name %) " && ?") ranged-keys)))]
                        (map #(% key-values) key-keys)
                        (map #(get ranged-values %) ranged-keys)))
        (jdbc/query datasource
                    (concat [(str "SELECT * FROM " table-name " WHERE "
                        (string/join " AND " (concat (map #(str (name %) " = ?") key-keys) (map #(str (name %) " && ?") ranged-keys))))]
                        (map #(% key-values) key-keys)
                        (map #(get ranged-values %) ranged-keys)))))))

(defn get-all-by-ranged
  ([ranged-values table-name] (get-all-by-ranged ranged-values table-name db-pg/pg-datasource))
  ([ranged-values table-name datasource]
    (let [ranged-keys (keys ranged-values)]
      (jdbc/query datasource (concat [(str "SELECT * FROM " table-name
                                          " WHERE " (string/join " AND " (map #(str (name %) " && ?") ranged-keys)))]
                                    (map #(get ranged-values %) ranged-keys))))))

(defn get-first-by-ranged
  ([ranged-values order-by table-name] (get-first-by-ranged ranged-values order-by table-name db-pg/pg-datasource))
  ([ranged-values order-by table-name datasource]
    (let [ranged-keys (keys ranged-values)]
      (first (jdbc/query datasource (concat [(str "SELECT * FROM " table-name
                                                  " WHERE " (string/join " AND " (map #(str (name %) " && ?") ranged-keys))
                                                  " ORDER BY " order-by
                                                  " LIMIT 1")]
                                            (map #(get ranged-values %) ranged-keys)))))))

(defn get-all-by-ranged-and-values
  ([ranged-values values table-name] (get-all-by-ranged-and-values ranged-values values table-name db-pg/pg-datasource))
  ([ranged-values values table-name datasource]
    (let [values-keys (keys values)
          ranged-keys (keys ranged-values)]
      (jdbc/query datasource (concat
                               [(str "SELECT * FROM " table-name
                                           " WHERE " (string/join " AND "
                                                                  (concat (map #(str (name %) " && ?") ranged-keys)
                                                                          (map #(str (name %) " = ?") values-keys))))]
                               (map #(get ranged-values %) ranged-keys)
                               (map #(get values %) values-keys))))))

(defn get-all-by-ranged-and-values-with-joins
  ([ranged-values values joins select table-name]
    (get-all-by-ranged-and-values-with-joins ranged-values values joins select table-name db-pg/pg-datasource))
  ([ranged-values values joins select table-name datasource]
    (let [values-keys (keys values)
          ranged-keys (keys ranged-values)]
      (jdbc/query datasource (concat
                               [(str "SELECT " select " FROM " table-name " "
                                     (string/join " " joins)
                                     " WHERE " (string/join " AND "
                                                            (concat (map #(str (name %) " && ?") ranged-keys)
                                                                    (map #(str (name %) " = ?") values-keys))))]
                               (map #(get ranged-values %) ranged-keys)
                               (map #(get values %) values-keys))))))

(defn get-first-by-ranged-and-values
  ([ranged-values values order-by table-name]
    (get-first-by-ranged-and-values ranged-values values order-by table-name db-pg/pg-datasource))
  ([ranged-values values order-by table-name datasource]
    (let [values-keys (keys values)
          ranged-keys (keys ranged-values)]
      (first (jdbc/query datasource (concat
                                     [(str "SELECT * FROM " table-name
                                           " WHERE " (string/join " AND "
                                                                  (concat (map #(str (name %) " && ?") ranged-keys)
                                                                          (map #(str (name %) " = ?") values-keys)))
                                           " ORDER BY " order-by
                                           " LIMIT 1")]
                                     (map #(get ranged-values %) ranged-keys)
                                     (map #(get values %) values-keys)))))))

(defn get-first-by-ranged-and-values-with-joins
  ([ranged-values values joins order-by select table-name]
    (get-first-by-ranged-and-values-with-joins ranged-values values joins order-by select table-name db-pg/pg-datasource))
  ([ranged-values values joins order-by select table-name datasource]
    (let [values-keys (keys values)
          ranged-keys (keys ranged-values)]
      (first (jdbc/query datasource (concat
                               [(str "SELECT " select " FROM " table-name " "
                                     (string/join " " joins)
                                     " WHERE " (string/join " AND "
                                                            (concat (map #(str (name %) " && ?") ranged-keys)
                                                                    (map #(str (name %) " = ?") values-keys)))
                                     " ORDER BY " order-by
                                     " LIMIT 1")]
                               (map #(get ranged-values %) ranged-keys)
                               (map #(get values %) values-keys)))))))

(defmulti to-date class)
(defmethod to-date org.joda.time.DateTime [raw-date]
  (ranged-types/create-date (clj-time.format/unparse (clj-time.format/formatters :date) raw-date)))
(defmethod to-date java.lang.String [raw-date] (ranged-types/create-date raw-date))
(defmethod to-date :default [raw-date] raw-date)

(defmulti to-date-range class)
(defmethod to-date-range clojure.lang.PersistentVector [date-vec]
  (ranged-types/create-date-range (.date-str (to-date (first date-vec))) (.date-str (to-date (last date-vec)))))
(defmethod to-date-range :default [raw-range] raw-range)

(defmulti to-season class)
(defmethod to-season java.lang.Number [raw-season] (ranged-types/create-season raw-season))
(defmethod to-season :default [raw-season] raw-season)

(defmulti to-season-range class)
(defmethod to-season-range clojure.lang.PersistentVector [seasons-vec] (ranged-types/create-season-range (first seasons-vec) (last seasons-vec)))
(defmethod to-season-range :default [raw-range] raw-range)

(defn filter-to
  "Applies to-method to the value specified by the range-key. Returns values if range key is missing"
  [to-method values range-key]
  (if (nil? (get values range-key)) values (merge values {range-key (to-method (get values range-key))})))

(defn filter-date-range [values range-key] (filter-to to-date-range values range-key))
(defn filter-date [values range-key] (filter-to to-date values range-key))
(defn filter-season-range [values range-key] (filter-to to-season-range values range-key))
(defn filter-season [values range-key] (filter-to to-season values range-key))

