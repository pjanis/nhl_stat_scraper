(ns nhl-stat-scraper.report.game-html
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-time.core]
            [clj-time.format]
            [clj-time.local]
            [nhl-stat-scraper.common.parse :as common-parse]))

(def svg-select [[:svg (enlive/nth-of-type 1)]])

(def header-select [[:header (enlive/nth-of-type 1)] :> :*])

(defn period-name [period]
  (case period
    1 "1st"
    2 "2nd"
    3 "3rd"
    4 "overtime"
    5 "shootout"))

(enlive/defsnippet header-content (io/resource "templates/public/game-index.html") header-select
  [{:keys [home-team home-score away-team away-score final game-time period date]}]
  [:div#game_status :div.date] (enlive/content (string/lower-case (clj-time.format/unparse (clj-time.format/formatter "E, dd MMM") date)))
  [:div#game_status :div.time] (enlive/content (if final "final" game-time))
  [:div#game_status :div.period] (enlive/content (if-not (and final (>= 3 period)) (period-name period)))
  [:div#home_overview :div.team_name] (enlive/content home-team)
  [:div#home_overview :div.team_score] (enlive/content (str home-score))
  [:div#away_overview :div.team_name] (enlive/content away-team)
  [:div#away_overview :div.team_score] (enlive/content (str away-score)))

(defn include-js [src]
  (first (enlive/html [:script {:src src}])))

(defn include-css [href]
  (first (enlive/html [:link {:href href :rel "stylesheet"}])))

(enlive/defsnippet period-timeline (io/resource "templates/public/period-timeline.html") [:div.period_timeline]
  [{:keys [plays period completed-time]}]
  [[:line.timeline (enlive/but :line.incomplete)]] (if (< completed-time 1201) (enlive/set-attr :x2 (str completed-time)))
  [[:line.stop_mark (enlive/but :line.incomplete)]] (if (< completed-time 1201) nil)
  [:div.period_name] (enlive/content (period-name period))
  [:svg.period_timeline] (enlive/do->
                           (apply enlive/append
                                  (map #(enlive/html [:line {:x1 (get % :period-time)
                                                             :y1 "0"
                                                             :x2 (get % :period-time)
                                                             :y2 "30"
                                                             :style "stroke:rgb(0,0,0);stroke-width:1"
                                                             :class (str "play play_id_" (get % :play-id "missing"))}])
                                        (filter (fn[play] (= (get play :period) period)) plays)))))

(defn determine-timeline [period]
  (case period
    (1 2 3) period-timeline
    4 period-timeline ;TODO overtime timeline
    5 period-timeline)) ;TODO shootout timeline

(defn completed-time [period current-period period-time]
  (cond
    (< period current-period) (case period
                                (1 2 3) 1200
                                4 300)
    (= period current-period) period-time
    (> period current-period) 0))

(enlive/deftemplate index (io/resource "templates/public/game-index.html")
  [{:keys [title plays home-team-name home-score away-team-name away-score period period-time final]}]
  [:head] (enlive/append (map include-css ["/game.css"]))
  [:head] (enlive/append (map include-js ["/app.js"]))
  [:title] (enlive/content title)
  [:header] (enlive/content (header-content {:home-team home-team-name
                                             :home-score home-score
                                             :away-team away-team-name
                                             :away-score away-score
                                             :final final
                                             :game-time (str (quot period-time 60) ":" (format "%02d" (rem period-time 60)))
                                             :period period}))
  [:div#timeline_display]   (enlive/content (map #((determine-timeline %)
                                                  {:plays plays
                                                   :period %
                                                   :completed-time (completed-time % period period-time)})
                                                 (range period 0 -1))))

(defn write-game-html
  ([game-template game-data] (write-game-html game-template game-data (io/resource "public/game.html")))
  ([game-template game-data file-path]
    (io/make-parents file-path)
    (with-open [wrtr (io/writer (io/file file-path))]
      (doseq [line (game-template game-data)]
        (.write wrtr line)))))

(defn create-games [game-ids games-dir] nil) ;TODO
(defn update-team-links [game-ids team-games-dir] nil) ;TODO

