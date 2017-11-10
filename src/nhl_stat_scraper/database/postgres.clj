(ns nhl-stat-scraper.database.postgres
  (:require
    [clojure.edn]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [clj-time.core]
    [clj-time.format]
    [hikari-cp.core]
    [ragtime.jdbc]
    [ragtime.repl]
    [nhl-stat-scraper.database.ranged-types :as ranged-types]
    [nhl-stat-scraper.common.parse :as common-parse]))

(def default-datasource-options {:auto-commit        true
                        :read-only          false
                        :connection-timeout 30000
                        :validation-timeout 5000
                        :idle-timeout       600000
                        :max-lifetime       1800000
                        :minimum-idle       10
                        :maximum-pool-size  10
                        :pool-name          "db-pool"
                        :adapter            "postgresql"
                        :username           ""
                        :password           nil
                        :database-name      "nhl_stat"
                        :server-name        "localhost"
                        :port-number        5432
                        :register-mbeans    false})

(def config-datasource-options
    (if (.exists (io/as-file "private/config.edn"))
      (with-open [r (io/reader "private/config.edn")]
        (clojure.edn/read (java.io.PushbackReader. r)))
      nil))

(def pg-datasource {:datasource (hikari-cp.core/make-datasource (merge default-datasource-options config-datasource-options))})

(def ragtime-config {:datastore  (ragtime.jdbc/sql-database pg-datasource)
                     :migrations (ragtime.jdbc/load-resources "migrations")})

(def corrections-config {:datastore  (ragtime.jdbc/sql-database pg-datasource {:migrations-table "corrections"})
                     :migrations (ragtime.jdbc/load-resources "corrections")})
(defn migrate
  ([] (migrate ragtime-config))
  ([config]
    (ragtime.repl/migrate config)))

(defn rollback
  ([] (rollback ragtime-config))
  ([config]
    (ragtime.repl/rollback config)))

(defn run-corrections!
  ([] (run-corrections! corrections-config))
  ([config] (ragtime.repl/migrate config)))

(defn find-or-insert [find-function insert-function & args]
  (let [find-results (apply find-function args)]
    (if (empty? find-results)
      (apply insert-function args)
      find-results)))

(defn- to-pg-object [data pg-type]
  (doto (org.postgresql.util.PGobject.)
    (.setType pg-type)
    (.setValue data)))

(extend-protocol jdbc/ISQLValue
  nhl_stat_scraper.database.ranged_types.date
  (sql-value [v]
      (to-pg-object (string/join ["[" (.date-str v) "," (.date-str v) "]"]) "daterange")))

(extend-protocol jdbc/ISQLValue
  nhl_stat_scraper.database.ranged_types.date-range
  (sql-value [v]
      (to-pg-object (string/join ["[" (.start v) "," (.stop v) "]"]) "daterange")))

(extend-protocol jdbc/ISQLValue
  nhl_stat_scraper.database.ranged_types.season
  (sql-value [v]
      (to-pg-object (string/join ["[" (.start-year v) "," (.start-year v) "]"]) "int4range")))

(extend-protocol jdbc/ISQLValue
  nhl_stat_scraper.database.ranged_types.season-range
  (sql-value [v]
      (to-pg-object (string/join ["[" (.first-season v) "," (.last-season v) "]"]) "int4range")))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [v]
    (to-pg-object (json/write-str v) "jsonb")))

(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [v _ _]
    (case (.getType v)
      "int4range" (-> v
                      (.toString)
                      (string/replace #"[\[\]\(\)\s]" "")
                      (string/split #",")
                      (#(if (empty? %) [nil, nil] %))
                      (->>
                        (map #(if (empty? %) nil (common-parse/parse-int %)))
                        (map #(if (nil? %2) nil (+ (int %1) (int %2))) [0 -1])
                        (vec)))
      "daterange" (-> v
                      (.toString)
                      (string/replace #"[\[\]\(\)\s]" "")
                      (string/split #",")
                      (#(if (empty? %) [nil, nil] %))
                      (->>
                        (map #(if (empty? %) nil (clj-time.format/parse %)))
                        (map #(if (nil? %2) nil (clj-time.core/plus %2 (clj-time.core/days %1))) [0 -1])
                        (vec)))
      "jsonb" (-> v
                  (.toString)
                  (json/read-str))
      v)))

