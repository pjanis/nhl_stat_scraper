(ns nhl-stat-scraper.database.postgres
  (:require
    [clojure.edn]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [hikari-cp.core]
    [ragtime.jdbc]
    [ragtime.repl]))

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
