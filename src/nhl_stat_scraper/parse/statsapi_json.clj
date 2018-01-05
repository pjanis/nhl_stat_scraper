(ns nhl-stat-scraper.parse.statsapi-json
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clj-http.client :as client]
            [clj-time.coerce]
            [clj-time.core]
            [nhl-stat-scraper.common.parse :as common-parse]
            [nhl-stat-scraper.database.games]
            [nhl-stat-scraper.database.teams]))

(def base-url "https://statsapi.web.nhl.com")

(def schedule-api "/api/v1/schedule")

(def teams-api "/api/v1/teams")

(defn live-score-api [game-id] (str "/api/v1/game/" game-id "/feed/live"))

(defn process-request-params [params]
  (->> params
       (map (fn [[k v]] (string/join "=" [k v])))
       (string/join "&")))

(defn raw-request
  ([api-path] (raw-request api-path []))
  ([api-path params]
    (if (not (empty? params))
      (client/get (string/join "?" [(str base-url api-path) (process-request-params params)]))
      (client/get (str base-url api-path)))))

(defn request-json
  ([api-path] (request-json api-path []))
  ([api-path params]
    (-> (raw-request api-path params)
        (get :body)
        (json/read-str))))

(defn future-game? [game-json]
  (= "Scheduled" (get-in game-json ["status" "detailedState"])))

(defn postponed-game? [game-json]
  (= "Postponed" (get-in game-json ["status" "detailedState"])))

;leaving everything as org.joda.timeDateTime, but creating it from the NY date
(defn ny-date [datetime]
  (let [ny-datetime (clj-time.core/to-time-zone datetime (clj-time.core/time-zone-for-id "America/New_York"))]
    (clj-time.coerce/to-date-time
      (clj-time.core/date-midnight
        (clj-time.core/year ny-datetime) (clj-time.core/month ny-datetime) (clj-time.core/day ny-datetime)))))

(defn game-summary-from-schedule
  "Returns a hash-map for db with data from the schedule-api
   schedule-api DOES NOT contain regulation/overtime/shootout information"
  [game-json]
  (let [season (common-parse/parse-int (subs (get game-json "season") 0 4))
        datetime (clj-time.coerce/to-date-time (get game-json "gameDate"))
        complete (= "final" (string/lower-case (get-in game-json ["status" "detailedState"])))]
    (hash-map
      :game_id (get game-json "gamePk")
      :season season
      :preseason (= "PR" (get game-json "gameType"))
      :regular_season (= "R" (get game-json "gameType"))
      :postseason (= "P" (get game-json "gameType"))
      :game_date (ny-date datetime)
      :game_start datetime
      :home_team_db_id (get (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                              (get-in game-json ["teams" "home" "team" "id"]))
                            :db_id)
      :visiting_team_db_id (get (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                              (get-in game-json ["teams" "away" "team" "id"]))
                                :db_id)
      :home_team_score (if (or (future-game? game-json) (postponed-game? game-json)) nil (get-in game-json ["teams" "home" "score"]))
      :visiting_team_score (if (or (future-game? game-json) (postponed-game? game-json)) nil (get-in game-json ["teams" "away" "score"]))
      :complete complete
    )))

(defn game-summaries-between-dates
  "Returns all game summaries between dates
   Dates should be iso format string i.e. 2017-10-04
   DOES NOT contain regulation/over/shootout information"
  [start-date stop-date]
  (let [schedule-json (request-json schedule-api {"startDate" start-date "endDate" stop-date})
        games-json (into [] (apply concat (map #(get % "games") (get schedule-json "dates"))))]
    (map game-summary-from-schedule games-json)))

(defn game-summaries-on [game-date] (game-summaries-between-dates game-date game-date))

(defn full-game-json [game-id]
  (let [db-statsapi-game (nhl-stat-scraper.database.games/get-statsapi-game game-id)]
    (if (nil? db-statsapi-game)
      (let [game-json (request-json (live-score-api game-id))]
        (nhl-stat-scraper.database.games/insert-statsapi-game game-id game-json)
        game-json)
      (get db-statsapi-game :game_json))))

(defn update-full-game-json [game-id]
  (let [game-json (request-json (live-score-api game-id))]
    (if (nil? (nhl-stat-scraper.database.games/get-statsapi-game game-id))
      (nhl-stat-scraper.database.games/insert-statsapi-game game-id game-json)
      (nhl-stat-scraper.database.games/update-statsapi-game game-id game-json))
    game-json))


(defn game-summary-from-game-id
  "Returns map for db based on a game-id"
  [game-id]
  (let [game-json (full-game-json game-id)
        datetime (clj-time.coerce/to-date-time (get-in game-json ["gameData" "datetime" "dateTime"]))
        season (common-parse/parse-int (subs (get-in game-json ["gameData" "game" "season"]) 0 4))
        complete (= "final" (string/lower-case (get-in game-json ["gameData" "status" "detailedState"])))]
    (hash-map
      :game_id game-id
      :season season
      :preseason (= "PR" (get-in game-json ["gameData" "game" "type"]))
      :regular_season (= "R" (get-in game-json ["gameData" "game" "type"]))
      :postseason (= "P" (get-in game-json ["gameData" "game" "type"]))
      :game_date (ny-date datetime)
      :game_start datetime
      :home_team_db_id (get (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                              (get-in game-json ["gameData" "teams" "home" "id"]))
                            :db_id)
      :visiting_team_db_id (get (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                              (get-in game-json ["gameData" "teams" "away" "id"]))
                                :db_id)
      :home_team_score (get-in game-json ["liveData" "linescore" "teams" "home" "goals"])
      :visiting_team_score (get-in game-json ["liveData" "linescore" "teams" "away" "goals"])
      :regulation_win (and complete (= 3 (get-in game-json  ["liveData" "linescore" "currentPeriod"])))
      :overtime_win (and complete (= 4 (get-in game-json  ["liveData" "linescore" "currentPeriod"])))
      :complete complete
    )))

(defn teams []
  (let [teams-json (request-json teams-api)]
    (->> (get teams-json "teams")
         (map (fn [team-json]
                (hash-map
                  :name (common-parse/remove-accents (string/lower-case (get team-json "shortName")))
                  :common_name (string/lower-case (get team-json "teamName"))
                  :abreviation (string/lower-case (get team-json "abbreviation"))
                  :statsapi_id (get team-json "id")))))))

