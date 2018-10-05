(ns nhl-stat-scraper.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [nhl-stat-scraper.database.populate :as db-populate]
            [nhl-stat-scraper.database.postgres :as db-pg]
            [nhl-stat-scraper.report.standings-html :as standings-html]
            [nhl-stat-scraper.report.game-html :as game-html]
            [taoensso.timbre :as timbre])
  (:gen-class))

(timbre/set-level! :warn)

(def default-season 2018)

(defn update-standings
  ([] (update-standings {:season default-season}))
  ([options]
    (cond
      (get options :html) nil
      (get options :full) (db-populate/update-full-game-summaries (:season options))
      (get options :from) (db-populate/update-game-summaries-from (:from options))
      (get options :on)   (db-populate/update-game-summaries-on (:on options))
      :else (db-populate/update-incomplete-game-summaries))
    (standings-html/create-index (:season options) "regular" (str (:directory options) (:season options) "/index.html"))))

(defn update-dev-standings
  ([] (update-dev-standings {:season default-season}))
  ([options]
    (db-populate/update-incomplete-game-summaries)
    (standings-html/create-dev-index (:season options) "regular" (str "development/public/" (:season options) "/index.html"))))

(defn migrate []
  (db-pg/migrate))

(defn rollback []
  (db-pg/rollback))

(defn run-corrections! []
  (db-pg/run-corrections!))

(defn populate-season [options]
  (db-populate/populate-season-game-summaries (:season options)))

(defn bootstrap-all []
  (migrate)
  (db-populate/populate-league-structure)
  (db-populate/populate-team-colors)
  (db-populate/populate-season-game-summaries 2017)
  (run-corrections!))

(defn update-structure []
  (migrate)
  (db-populate/update-league-structure))

(defn update-calendar [options]
  (cond
    (get options :html) nil
    (get options :full) (db-populate/update-full-calendar (:season options))
    (get options :from) (db-populate/update-calendar-from (:from options))
    (get options :on)   (db-populate/update-calendar-on (:on options))
    :else (db-populate/update-incomplete-calendar)))

(defn update-games
  ([] (update-games {}))
  ([options]
    (let [updated-game-ids (cond
                             (get options :full) (db-populate/update-full-games (:season options))
                             (get options :from) (db-populate/update-games-from (:from options))
                             (get options :on)   (db-populate/update-games-on (:on options))
                             :else (db-populate/update-incomplete-games))]
    (game-html/create-games updated-game-ids (str (:directory options) (:season options)))
    (game-html/update-team-links updated-game-ids (str (:directory options) "games/"))
    )))

(defn bootstrap [options]  ;TODO add season options
  (bootstrap-all))

;based on clojure.tool.cli example
(def cli-options
  [;; First three strings describe a short-option, long-option with optional
  ;; example argument description, and a description. All three are optional
  ;; and positional.
  ["-d" "--directory DIR" "Site's root directory"
    :default "public/"]
  ["-f" "--full" "Updates all data for the season (slow, use rarely)"]
  [nil "--from DATE" "Updates from a particular date (as ISO, i.e. 2018-10-01)"] ;TODO add validation for dates
  [nil "--html" "Updates only html output, doesn't updated database"]
  [nil "--on DATE" "Updates on a particular date (as ISO, i.e. 2018-10-01)"]
  ["-s" "--season YEAR" "Season to update"
    :default default-season
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 2013 % 2019) "Must be between 2013 and 2018"]]
  ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Stat scraper and page generator for NHL data"
    ""
    "Usage: nhl-stat [options] action"
    ""
    "Options:"
    options-summary
    ""
    "Actions:"
    "  migrate           Run all new migrations"
    "  rollback          Rollback one migration"
    "  bootstrap         Setup database and fetch all data"
    "  populate-season   Adds season game summaries to database (must be run once to make update possible)"
    "  update-structure  Adds seasons to teams, divisions, and conferences"
    "  run-corrections   Run database corrections"
    "  update-calendar   Update game schedule (defaults to updating unfinished/unplayed games)"
    "  update-standings  Update standing information and create new index files (defaults to updating unfinished games)"
    "  update-games      Update full game details and generate game pages (defaults to updating unfinished games)"
    ""
    "Please refer to the manual page for more information."]
    (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
  (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
      ;; Execute program with options
    (case (first arguments)
      "bootstrap" (bootstrap options)
      "migrate" (migrate)
      "populate-season" (populate-season options)
      "rollback" (rollback)
      "run-corrections" (run-corrections!)
      "update-calendar" (update-calendar options)
      "update-games" (update-games options)
      "update-standings" (update-standings options)
      "update-structure" (update-structure)
      (exit 1 (usage summary)))))
