(ns nhl-stat-scraper.parse.html-transactions
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]
            [nhl-stat-scraper.common.parse :as common-parse]
            [nhl-stat-scraper.database.players :as db-players]
            [nhl-stat-scraper.database.teams :as db-teams]))

(defn fetch-url [url]
  (enlive/html-resource (java.net.URL. url)))

(defn wiki-transactions-url [year]
  (let [int_year (common-parse/parse-int (str year))] ;to handle both strings and int years
    (if (> int_year 1999)
      (format "https://en.wikipedia.org/wiki/%4d-%02d_NHL_transactions" int_year (- int_year 1999))
      nil)))  ;no transaction data for < 1999 available

(defn flatten-html-content [elements]
  (->> elements
    (map (fn [element]
      (if (string? element)
        (if (empty? (clojure.string/trim element))
          nil
          element)
      (flatten-html-content (element :content)))))
    (flatten)
    (remove nil?)))

(defn parse-transactions-span [element]
  (first (element :content)))

(defn parse-transactions-table [element]
  (->> (enlive/select element [:tr])
       (map #(enlive/select % #{[:td] [:th]}))
       (map flatten-html-content)))

(defn parse-transactions-element [element]
  (case (element :tag)
    :span (parse-transactions-span element)
    :table (parse-transactions-table element)
    nil))

(defn transaction-map [parsed-transactions]
  (first
    (reduce
      (fn [[trans-map current-pair] transaction-element]
        (if (string? transaction-element)
          (if (nil? current-pair)
            [trans-map [transaction-element []]]
            [(merge trans-map (apply hash-map current-pair)) [transaction-element []]])
          [trans-map [(first current-pair) (conj (last current-pair) transaction-element)]]))
      [{} nil]
      parsed-transactions)))


(defn wiki-transactions-headlines-and-tables [year]
  (-> (wiki-transactions-url year)
    (fetch-url)
    (enlive/select
      #{[:div#mw-content-text :h2 :span.mw-headline ]
;        [:div#mw-content-text :h3 :span.mw-headline]
        [:div#mw-content-text :> :table]})
    (->>
      (map parse-transactions-element))
    (transaction-map)))

(defn record-trade [] nil)

