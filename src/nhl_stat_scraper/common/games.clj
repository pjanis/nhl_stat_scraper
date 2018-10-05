(ns nhl-stat-scraper.common.games)

(defn parse-int [s]
  (Integer/parseInt (re-find #"\A-?\d+" s)))

(defn season-from-id [id]
  (parse-int (subs (str id) 0 4)))

(defn in? [collection element]
  (some #(= element %) collection))
