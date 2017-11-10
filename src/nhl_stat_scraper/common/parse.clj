(ns nhl-stat-scraper.common.parse
  (:refer-clojure :exclude [slurp])
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clj-time.core]
            [clj-time.format]
            [dire.core]))

;at compile time
(defmacro slurp [file]
  (clojure.core/slurp file))

(defn slurp-json [file]
  (json/read-str (clojure.core/slurp file)))

(defn parse-int [s]
  (Integer/parseInt (re-find #"\A-?\d+" s)))

(defn us-date-str-to-iso-date-str [date-str]
  (let [raw-date (string/split date-str #"/")]
    (string/join "-" (cons (last raw-date) (butlast raw-date)))))

(defn string-to-date [date-str]
  (clj-time.format/parse (clj-time.format/formatters :date) date-str))

(dire.core/with-handler! #'string-to-date
  java.lang.IllegalArgumentException
  (fn [e & args]
      (clj-time.format/parse (clj-time.format/formatter "MMMM dd, YYYYY") (first args))))

(defn string-to-time [time-str]
  (clj-time.format/parse (clj-time.format/formatter "mm:ss") time-str))

(dire.core/with-handler! #'string-to-time
  java.lang.IllegalArgumentException
  (fn [e & args] (string-to-time "00:00")))

(defn pg-time-iterval [start-time stop-time]
  (org.postgresql.util.PGInterval. 0 0 0 0 0 (clj-time.core/in-seconds (clj-time.core/interval start-time stop-time))))

;minutes:seconds only
(defn string-to-interval [time-str]
  (org.postgresql.util.PGInterval. (str "00:" time-str)))

(defn remove-non-word [raw-string]
  (apply str (filter #(Character/isLetter %) raw-string)))

(defn clean-input-string [raw-string]
  (-> raw-string
    (clojure.string/replace "\u00A0" "\u0020")))

;; https://gist.github.com/maio/e5f85d69c3f6ca281ccd
(defn remove-accents [raw-string]
  (let [normalized (java.text.Normalizer/normalize raw-string java.text.Normalizer$Form/NFD)]
    (clojure.string/replace normalized #"\p{InCombiningDiacriticalMarks}+" "")))
