(ns nhl-stat-scraper.parse.json
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clj-http.client :as client]
            [clj-time.local]
            [nhl-stat-scraper.common.parse :as common-parse]
            [nhl-stat-scraper.database.teams]))

(defn raw-scoreboard-request [date-str]
  (client/get (string/join ["http://live.nhle.com/GameData/GCScoreboard/" date-str ".jsonp"])))

;Removes loadScoreboard js command from body
(defn json-string [raw-request]
  (subs (raw-request :body) (+ (.indexOf (raw-request :body) "(") 1) (.lastIndexOf (raw-request :body) ")")))

(defn parsed-date-json [date-str]
  (json/read-str (json-string (raw-scoreboard-request date-str))))

(defn games-on [date-str]
  (get (parsed-date-json date-str) "games"))

(defn filter-parsed-summaries [parsed-game-summaries]
  (->> parsed-game-summaries
    (filter #(not (boolean (re-matches #"\d{4}04\d{4}" (str (get % "id")))))))) ;remove all-star games

(defn next-date-from-parsed-json [parsed-json]
  (common-parse/us-date-str-to-iso-date-str (get parsed-json "nextDate")))

(defn next-date [date-str]
  (next-date-from-parsed-json (parsed-date-json date-str)))

(defn home-teams [json-games]
  (into #{}
    (map
      #(hash-map :common_name (string/lower-case (get % "htcommon")) :name (string/lower-case (get % "htn")) :abreviation (string/lower-case (get % "hta")))
      json-games)))

(defn away-teams [json-games]
  (into #{}
    (map
      #(hash-map :common_name (string/lower-case (get % "atcommon")) :name (string/lower-case (get % "atn")) :abreviation (string/lower-case (get % "ata")))
      json-games)))

(defn teams [json-games]
  (clojure.set/union (home-teams json-games) (away-teams json-games)))

(defn teams-on [date-str]
    (teams (games-on date-str)))

(defn all-teams
  ([] (all-teams 30 "2015-10-07" #{}))
  ([number-of-teams day found-teams]
    (if (< (count found-teams) number-of-teams)
      (all-teams number-of-teams (next-date day) (clojure.set/union found-teams (teams-on day)))
      (if (= (count found-teams) number-of-teams)
        found-teams
        (throw (Exception. (format "Too many teams found. Looking for %d" number-of-teams)))))))

(defn game-summaries-from-parsed-json
  ([parsed-json] (game-summaries-from-parsed-json parsed-json (nhl-stat-scraper.database.teams/db-teams)))
  ([parsed-json db-teams]
    (let [date-str (common-parse/us-date-str-to-iso-date-str (get parsed-json "currentDate"))]
      (->> (get parsed-json "games")
           (filter-parsed-summaries)
           (map #(hash-map
                  :game_id (str (get % "id"))
                  :season (common-parse/parse-int (subs (str (get % "id")) 0 4))
                  :preseason (= "01" (subs (str (get % "id")) 4 6))
                  :regular_season (= "02" (subs (str (get % "id")) 4 6))
                  :postseason (= "03" (subs (str (get % "id")) 4 6))
                  :game_date (clj-time.local/to-local-date-time date-str)
                  :home_team_db_id (nhl-stat-scraper.database.teams/find-team-id-by-abreviation (string/lower-case (get % "hta")) db-teams)
                  :visiting_team_db_id (nhl-stat-scraper.database.teams/find-team-id-by-abreviation (string/lower-case (get % "ata")) db-teams)
                  :home_team_score (let [score (get % "hts")] (if (integer? score) score nil))
                  :visiting_team_score (let [score (get % "ats")] (if (integer? score) score nil))
                  :regulation_win (= "final" (string/lower-case (get % "bs")))
                  :overtime_win (= "final ot" (string/lower-case (get % "bs")))
                  :complete (= "final" (string/lower-case (get % "bsc")))
                ))))
  ))

(defn game-summaries-on
  ([date-str] (game-summaries-on date-str (nhl-stat-scraper.database.teams/db-teams)))
  ([date-str db-teams] (game-summaries-from-parsed-json (parsed-date-json date-str) db-teams)))
