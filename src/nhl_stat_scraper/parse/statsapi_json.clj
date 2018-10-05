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

(defn update-full-game-json [game-id]
  (let [game-json (request-json (live-score-api game-id))]
    (if (nil? (nhl-stat-scraper.database.games/get-statsapi-game game-id))
      (nhl-stat-scraper.database.games/insert-statsapi-game game-id game-json)
      (nhl-stat-scraper.database.games/update-statsapi-game game-id game-json))
    game-json))


(defn game-summary-from-game-id
  "Returns map for db based on a game-id and updates game-json in the db"
  [game-id]
  (let [game-json (update-full-game-json game-id)
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

(defn parse-goal-details [play home-team away-team]
  (hash-map
    :team (if (= (get home-team :statsapi_id) (get-in play ["team" "id"])) :home :away)
    :strength (case (string/lower-case (get-in play ["result" "strength" "code"]))
                "even" :even
                "ppg" :powerplay
                "shg" :shorthanded)
    :empty-net (get-in play ["result" "emptyNet"])
    :shot-type  (string/lower-case (get-in play ["result" "secondaryType"]))
    :description (string/lower-case (get-in play ["result" "description"]))
    :scorer (-> (get-in play ["players"])
                (->> (filter #(= (string/lower-case (get-in % ["playerType"])) "scorer")))
                (first)
                (get-in ["player" "fullName"]))
    :goalie (-> (get-in play ["players"])
                (->> (filter #(= (string/lower-case (get-in % ["playerType"])) "goalie")))
                (first)
                (get-in ["player" "fullName"]))))

(defn parse-play-details [play home-team away-team]
  (case (string/lower-case (get-in play ["result" "event"]))
    "goal" (parse-goal-details play home-team away-team)
    nil))

(defn parse-plays-from-json
  ([game-json] (parse-plays-from-json game-json
                                      (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                                       (get-in game-json ["gameData" "teams" "home" "id"]))
                                      (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                                       (get-in game-json ["gameData" "teams" "away" "id"]))))
  ([game-json home-team away-team]
    (let [plays (get-in game-json ["liveData" "plays" "allPlays"])]
      (map #(hash-map
              :play-event (string/lower-case (get-in % ["result" "event"]))
              :event-details (parse-play-details % home-team away-team)
              :location [(get-in % ["coordinates" "x"]) (get-in % ["coordinates" "y"])]
              :period (get-in % ["about" "period"])
              :period-time (-> (get-in % ["about" "periodTime"])
                               (common-parse/string-to-time)
                               (clj-time.coerce/to-epoch))
              :play-id (get-in % ["about" "eventIdx"])
              )
          plays))))

(defn parse-game-data-from-json
  [game-json]
  (let [datetime (clj-time.coerce/to-date-time (get-in game-json ["gameData" "datetime" "dateTime"]))
        period (get-in game-json ["liveData" "linescore" "currentPeriod"])
        remaining-time (-> (get-in game-json ["liveData" "linescore" "currentPeriodTimeRemaining"])
                            (common-parse/string-to-time)
                            (clj-time.coerce/to-epoch))
        home-team (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                    (get-in game-json ["gameData" "teams" "home" "id"]))
        away-team (nhl-stat-scraper.database.teams/get-team-by-statsapi-id
                    (get-in game-json ["gameData" "teams" "away" "id"]))]
    (hash-map
      :game-date (ny-date datetime)
      :period period
      :period-time (case period
                     (1 2 3) (- 1200 remaining-time)
                     4 (- 300 remaining-time)
                     5 0
                     0)
      :final (= "Final" (get-in game-json ["gameData" "status" "detailedState"]))
      :home-score (get-in game-json ["liveData" "linescore" "teams" "home" "goals"])
      :away-score (get-in game-json ["liveData" "linescore" "teams" "away" "goals"])
      :home-team-db-id (get home-team :db_id)
      :away-team-db-id (get away-team :db_id)
      :home-team-name (get home-team :name)
      :away-team-name (get away-team :name)
      :plays (parse-plays-from-json game-json home-team away-team)
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

