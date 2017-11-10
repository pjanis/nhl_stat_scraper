(ns nhl-stat-scraper.database.ranged-types)

(deftype date-range [start stop])
(deftype date [date-str])
(deftype season-range [first-season last-season])
(deftype season [start-year])

(defn create-date-range [start-str stop-str]
  (->date-range (str start-str) (str stop-str)))
(defn create-date [date-str]
  (->date (str date-str)))
(defn create-season-range [first-season last-season]
  (->season-range (int first-season) (int last-season)))
(defn create-season [start-year]
  (->season (int start-year)))

