(ns nhl-stat-scraper.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [nhl-stat-scraper.database.populate :as db-populate]
            [nhl-stat-scraper.database.postgres :as db-pg]
            [nhl-stat-scraper.serve.develop :as serve-develop]
            [nhl-stat-scraper.report.html :as report-html])
  (:gen-class))

(defn update-standings
  ([] (update-standings {:season 2016}))
  ([options]
    (db-populate/update-game-summaries false)
    (report-html/create-index (:season options) "regular" (str (:directory options) (:season options) "/index.html"))))

(defn update-dev-standings
  ([] (update-dev-standings {:season 2016}))
  ([options]
    (db-populate/update-game-summaries false)
    (report-html/create-dev-index (:season options) "regular" (str "resources/public/" (:season options) "/index.html"))))

(defn update-play-data [_options]
  (print "NOT YET IMPLEMENTED"))

(defn start-dev-server []
  (serve-develop/start-server))

(defn stop-dev-server []
  (serve-develop/stop-server))

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

(defn bootstrap [options]  ;TODO add season options
  (bootstrap-all))

;based on clojure.tool.cli example
(def cli-options
  [;; First three strings describe a short-option, long-option with optional
  ;; example argument description, and a description. All three are optional
  ;; and positional.
  ["-s" "--season YEAR" "Season to update"
    :default 2016 ;TODO make smart year look up for spring/fall
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 2013 % 2018) "Must be between 2013 and 2017"]]
  ["-d" "--directory DIR" "Site's root directory"
    :default "public/"]
  ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Stat scraper for NHL data"
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
    "  update-standings  Update standing information and create new index files"
    "  update-play-data  Updates all play, shift, and roster data"
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
      "migrate" (migrate)
      "rollback" (rollback)
      "bootstrap" (bootstrap options)
      "populate-season" (populate-season options)
      "run-corrections" (run-corrections!)
      "update-standings" (update-standings options)
      "update-structure" (update-structure)
      "update-play-data" (update-play-data options)
      (exit 1 (usage summary)))))

;TODO add complete update for arbitary date

