(ns nhl-stat-scraper.report.html
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-time.core]
            [clj-time.local]
            [nhl-stat-scraper.common.parse :as common-parse]
            [nhl-stat-scraper.database.postgres :as db-pg]
            [nhl-stat-scraper.report.teams :as teams]
            [nhl-stat-scraper.report.games :as games]))

(def svg-select [[:svg (enlive/nth-of-type 1)]])

(defmacro game-snippet-model [model-name]
  `(def ~(symbol (str model-name "-model")) (enlive/snippet (io/resource ~(str "templates/public/" model-name ".html")) svg-select ~[{:keys ['bout 'game-id]}]
    [:svg] (enlive/do->
             (enlive/add-class (str "bout_" ~(symbol 'bout)))
             (enlive/add-class (str "game_" ~(symbol 'game-id)))))))

(game-snippet-model home-loss)
(game-snippet-model home-incomplete)
(game-snippet-model home-ongoing)
(game-snippet-model home-ot-loss)
(game-snippet-model home-ot-win)
(game-snippet-model home-so-loss)
(game-snippet-model home-so-win)
(game-snippet-model home-upcoming)
(game-snippet-model home-win)
(game-snippet-model away-loss)
(game-snippet-model away-incomplete)
(game-snippet-model away-ongoing)
(game-snippet-model away-ot-loss)
(game-snippet-model away-ot-win)
(game-snippet-model away-so-loss)
(game-snippet-model away-so-win)
(game-snippet-model away-upcoming)
(game-snippet-model away-win)

(defn game-result [game-data]
  (case (get game-data :points)
    0 (cond
        (get game-data :complete) "loss"
        (get game-data :ongoing) "ongoing"
        (= (get game-data :game-date)
           (clj-time.local/format-local-time
             (clj-time.core/to-time-zone
               (clj-time.local/local-now)
               (clj-time.core/time-zone-for-offset -8))
             :date)) "upcoming"
        :else "incomplete")
    1 (if (get game-data :shootout) "so-loss" "ot-loss")
    2 (if (get game-data :overtime-win) "ot-win" (if (get game-data :shootout) "so-win" "win"))))

(defn game-result-with-location [game-data]
  (string/join "-" [(if (get game-data :home) "home" "away") (game-result game-data)]))

(defn game-result-model [game-data]
  (case (game-result-with-location game-data)
    "home-loss" (home-loss-model game-data)
    "home-incomplete" (home-incomplete-model game-data)
    "home-ongoing" (home-ongoing-model game-data)
    "home-ot-loss" (home-ot-loss-model game-data)
    "home-ot-win" (home-ot-win-model game-data)
    "home-so-loss" (home-so-loss-model game-data)
    "home-so-win" (home-so-win-model game-data)
    "home-upcoming" (home-upcoming-model game-data)
    "home-win" (home-win-model game-data)
    "away-loss" (away-loss-model game-data)
    "away-incomplete" (away-incomplete-model game-data)
    "away-ongoing" (away-ongoing-model game-data)
    "away-ot-loss" (away-ot-loss-model game-data)
    "away-ot-win" (away-ot-win-model game-data)
    "away-so-loss" (away-so-loss-model game-data)
    "away-so-win" (away-so-win-model game-data)
    "away-upcoming" (away-upcoming-model game-data)
    "away-win" (away-win-model game-data))
  )

(def game-divider (enlive/html-snippet (clojure.string/trim (slurp (io/resource "templates/public/game-divider.html")))))

(def team-select [[:div.team (enlive/nth-of-type 1)]])

(enlive/defsnippet team-model (io/resource "templates/public/team.html.snippet") team-select
  [{:keys [team-id]} division-name conference-name season season-part]
  [:div.team] (enlive/do->
                (enlive/add-class division-name conference-name))
  [:div.name] (enlive/do->
        (enlive/content (teams/team-name team-id)))
  [:div.pts] (enlive/do->
        (enlive/content (str (games/team-total-points team-id season season-part))))
  [:div.record] (enlive/do->
        (enlive/content (string/join "-" (games/team-record team-id season season-part))))
  [:div.results] (enlive/do->
        (apply enlive/content
          (interpose game-divider
            (for [game-set  (partition 10 10 [] (games/team-game-results team-id season season-part))]
              (map game-result-model game-set)))))
  )

(def division-select [[:div.division (enlive/nth-of-type 1)]])

(enlive/defsnippet division-model (io/resource "templates/public/division.html.snippet") division-select
  [{:keys [division-name division-teams]} conference-name season season-part]
  [:div.name] (enlive/do->
        (enlive/content division-name))
  [:div.teams] (enlive/content (map #(team-model % division-name conference-name season season-part) (sort-by :team-id #(games/standings-comparison %1 %2 season season-part) division-teams)))
  )

(def conference-select [[:div.conference (enlive/nth-of-type 1)]])

(enlive/defsnippet conference-model (io/resource "templates/public/conference.html.snippet") conference-select
  [{:keys [conference-name conference-divisions]} season season-part]
  [:div.name] (enlive/do->
        (enlive/content conference-name))
  [:div.divisions] (enlive/content (map #(division-model % conference-name season season-part) (sort-by :division-name conference-divisions)))
  )

(def detail-select [[:div.game_detail (enlive/nth-of-type 1)]])

(enlive/defsnippet detail-model (io/resource "templates/public/game-detail.html.snippet") detail-select
  [{:keys [game-id game-date home-team-name home-team-score away-team-name away-team-score game-state]}]
  [:div.game_detail] (enlive/do-> (enlive/add-class (str "game_" game-id)))
  [:div.detail_date] (enlive/do-> (enlive/content game-date))
  [:div.result_type] (enlive/do-> (enlive/content game-state))
  [:div.detail_home] (enlive/do-> (enlive/content home-team-name))
  [:div.detail_score] (enlive/do-> (enlive/content (str home-team-score "-" away-team-score)))
  [:div.detail_away] (enlive/do-> (enlive/content away-team-name))
  )

(defn header-content [season season-part]
  (str
    (if (games/games-exist (- season 1) season-part)
      (format "<a href=\"/%d/index.html\">&larr;</a>" (- season 1))
      "")
    (format "%d" season)
    (if (games/games-exist (+ season 1) season-part)
      (format "<a href=\"/%d/index.html\">&rarr;</a>" (+ season 1))
      "")))

(defn include-js [src]
        (first (enlive/html [:script {:src src}])))

(defn include-css [href]
        (first (enlive/html [:link {:href href :rel "stylesheet"}])))

(enlive/deftemplate index (io/resource "templates/public/index.html")
  [{:keys [title conferences season season-part]}]
  [:head] (enlive/append (map include-css ["/app.css"]))
  [:head] (enlive/append (map include-js ["/app.js"]))
  [:title] (enlive/content title)
  [:div#year_select] (enlive/html-content (header-content season season-part))
  [:div#conferences]   (enlive/content (map #(conference-model % season season-part) (sort-by :conference-name conferences)))
  [:script#game_data] (enlive/content (clojure.data.json/write-str (games/season-games season season-part))))


(defn write-teams-to-index
  ([index-template index-data] (write-teams-to-index index-template index-data "public/index.html"))
  ([index-template index-data file-path]
    (io/make-parents file-path)
    (with-open [wrtr (io/writer (io/file file-path))]
      (doseq [line (index-template index-data)]
        (.write wrtr line)))))

(defn update-css-file [css-file]
  (io/copy
    (io/file (io/as-relative-path (str "src/nhl_stat_scraper/templates/public/" css-file)))
    (io/file (io/as-relative-path (str "resources/public/" css-file))))
  (io/copy
    (io/file (io/as-relative-path (str "src/nhl_stat_scraper/templates/public/" css-file ".map")))
    (io/file (io/as-relative-path (str "resources/public/" css-file ".map")))))

(defn update-css-files []
  (as-> (file-seq (clojure.java.io/file "src/nhl_stat_scraper/templates/public")) files
      (filter #(.isFile %) files)
      (map #(.getName %) files)
      (filter #(re-matches #".*\.css" %) files)
      (doseq [file files] (update-css-file file))))

(defn update-js-file [js-file]
  (io/copy
    (io/file (io/as-relative-path (str "src/nhl_stat_scraper/templates/public/" js-file)))
    (io/file (io/as-relative-path (str "resources/public/" js-file))))
  (io/copy
    (io/file (io/as-relative-path (str "src/nhl_stat_scraper/templates/public/" js-file ".map")))
    (io/file (io/as-relative-path (str "resources/public/" js-file ".map")))))

(defn update-js-files []
  (as-> (file-seq (clojure.java.io/file "src/nhl_stat_scraper/templates/public")) files
      (filter #(.isFile %) files)
      (map #(.getName %) files)
      (filter #(re-matches #".*\.js" %) files)
      (doseq [file files] (update-js-file file))))

(defn create-index ;rewrite to standings
  ([] (create-index 2015 "regular" "public/2015/index.html"))
  ([season season-part file-path]
    (write-teams-to-index index
                          {:title "Standings"
                           :conferences (teams/html-conference-division-teams season db-pg/pg-datasource)
                           :season season
                           :season-part season-part}
                          file-path )))

(defn create-dev-index ;rewrite to standings
  ([] (create-dev-index 2015 "regular" (io/resource "public/2015/index.html")))
  ([season season-part file-path]
    (update-css-files)
    (update-js-files)
    (create-index season season-part file-path)))

