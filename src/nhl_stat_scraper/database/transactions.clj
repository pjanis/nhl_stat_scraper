(ns nhl-stat-scraper.database.transactions
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [clj-time.jdbc]
    [dire.core]
    [nhl-stat-scraper.database.postgres :as db-pg]))


(defn add-transaction
  ([transaction-date transaction-type raw-transaction]
    (add-transaction transaction-date transaction-type raw-transaction db-pg/pg-datasource))
  ([transaction-date transaction-type raw-transaction datasource]
    (jdbc/insert! datasource :transactions {
                                     :transaction_date transaction-date
                                     :transaction_type transaction-type
                                     :raw_transaction raw-transaction})))

(dire.core/with-handler! #'add-transaction
  org.postgresql.util.PSQLException
   (fn [e & args] (println (str e))))
