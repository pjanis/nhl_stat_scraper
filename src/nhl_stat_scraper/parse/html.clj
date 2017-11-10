(ns nhl-stat-scraper.parse.html
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]
            [dire.core]
            [nhl-stat-scraper.common.parse :as common-parse]
            [nhl-stat-scraper.database.players :as db-players]
            [nhl-stat-scraper.database.teams :as db-teams]
            [taoensso.timbre :as timbre]))

(defn fetch-url [url]
  (enlive/html-resource (java.net.URL. url)))

(dire.core/with-handler! #'fetch-url
  java.io.FileNotFoundException
  (fn [e & args ] (do (timbre/warn (str "File Not Found: " (first args))) nil)))

(dire.core/with-handler! #'fetch-url
  java.io.IOException
  (fn [e & args ] (do (timbre/warn (str "File Forbidden: " (first args))) nil)))

(def wikipedia-nhl-url "https://en.wikipedia.org/wiki/National_Hockey_League")
(defn wikipedia-season-url [season] (str "https://en.wikipedia.org/wiki/" season "%E2%80%93" (+ (mod season 100) 1) "_NHL_season"))

(defn wikipedia-team-urls []
  (-> (fetch-url wikipedia-nhl-url)
      (enlive/select [:table.wikitable])
      (first)
      (enlive/select [:tr])
      (->> (map #(enlive/select % [:td])))
      (->> (map first))
      (->> (remove nil?))
      (->> (map #(enlive/select % [:a])))
      (->> (map first))
      (->> (map
             #(hash-map
                :team (string/lower-case (enlive/text %))
                :wiki-url (format "https://en.wikipedia.org%s" (get-in % [:attrs :href])))))
  ))

(defn wikipedia-infobox-row-header [row]
  (-> row
      (enlive/select [:th])
      (first)
      (get :content)
      (->> (apply str))))

(defn wikipedia-season-dates [season]
  (-> season
      (wikipedia-season-url)
      (fetch-url)
      (enlive/select [:table.infobox :tr])
      (->> (map #(wikipedia-infobox-row-header %)))))

(defn team-infobox-rows [team-wiki-url]
  (-> (fetch-url team-wiki-url)
      (enlive/select [:table.infobox :tr])
  ))

(defn wikipedia-team-infobox-rows
  ([] (wikipedia-team-infobox-rows (wikipedia-team-urls)))
  ([team-urls]
    (map #(hash-map :team (get % :team) :infobox-rows (team-infobox-rows (get % :wiki-url))) team-urls)))

(defn find-infobox-value [row-name infobox-rows]
  (let [match-row (first (filter #(= row-name (enlive/text (first (enlive/select % [:th])))) infobox-rows))]
    (first (enlive/select match-row [:td]))))

(defn element-style-subvalue [element subvalue]
  (as-> element x
      (get-in x [:attrs :style])
      (if x x "")
      (string/split x #";")
      (map string/trim x)
      (filter #(.contains % subvalue) x)
      (first x)
      (if x x "")
      (string/split x #":")
      (map string/trim x)
      (filter not-empty x)
      (last x)
  ))

(defn infobox-team-colors [infobox-rows]
  (-> (enlive/select (find-infobox-value "Colors" infobox-rows) [:span])
      (concat (enlive/select (find-infobox-value "Colours" infobox-rows) [:span]))
      (->> (map #(element-style-subvalue % "background-color")))
      (->> (remove nil?))
  ))

(defn infobox-team-division [infobox-rows]
  (string/lower-case (enlive/text (find-infobox-value "Division" infobox-rows))))

(defn infobox-team-conference [infobox-rows]
  (string/lower-case (enlive/text (find-infobox-value "Conference" infobox-rows))))

(defn wikipedia-team-info
  ([] (wikipedia-team-info (wikipedia-team-infobox-rows)))
  ([team-infobox-rows]
    (map #(hash-map
            :team (get % :team)
            :colors (infobox-team-colors (get % :infobox-rows))
            :division (infobox-team-division (get % :infobox-rows))
            :conference (infobox-team-conference (get % :infobox-rows)))
         team-infobox-rows
         )))

;Maybe move to app library
(defn parse-int [s]
    (Integer/parseInt (re-find #"\A-?\d+" s)))

(defn game-roster-url [game-id]
  (let [season (parse-int (subs game-id 0 4))
        sub-game-id (subs game-id 4)]
    (str "http://www.nhl.com/scores/htmlreports/" season (str (+ season 1)) "/RO" sub-game-id ".HTM")))

(defn game-roster-raw [game-id]
  (fetch-url (game-roster-url game-id)))

(defn roster-table [html-table]
  (as-> html-table x
      (enlive/select x [:tr])
      (map (fn [row]
            (apply concat (map (fn [td] (td :content)) (enlive/select row [:td]))))
           x)))

(defn away-active-players [raw-roster]
  (rest (roster-table (nth (enlive/select raw-roster [:table]) 10))))
(defn home-active-players [raw-roster]
  (rest (roster-table (nth (enlive/select raw-roster [:table]) 11))))
(defn away-scratched-players [raw-roster]
  (rest (roster-table (nth (enlive/select raw-roster [:table]) 12))))
(defn home-scratched-players [raw-roster]
  (rest (roster-table (nth (enlive/select raw-roster [:table]) 13))))
(defn away-coach [raw-roster]
  (roster-table (nth (enlive/select raw-roster [:table]) 14)))
(defn home-coach [raw-roster]
  (roster-table (nth (enlive/select raw-roster [:table]) 15)))

(defn clean-roster-name [raw-name]
  (if (.contains raw-name "(")
    (string/trim (subs raw-name 0 (.indexOf raw-name "(")))
    (string/trim raw-name)))

(defn is-captain? [raw-name]
  (.contains raw-name "(C)"))

(defn is-assistant? [raw-name]
  (.contains raw-name "(A)"))

(defn player-list [raw-roster active-player-function scratched-player-function]
  (concat
    (map #(hash-map
            :number (parse-int (nth % 0))
            :position (nth % 1)
            :name (clean-roster-name (nth % 2))
            :active true
            :captain (is-captain? (nth % 2))
            :assistant (is-assistant? (nth % 2)))
         (active-player-function raw-roster))
    (map #(hash-map
            :number (parse-int (nth % 0))
            :position (nth % 1)
            :name (clean-roster-name (nth % 2))
            :active false
            :captain false
            :assistant false)
         (scratched-player-function raw-roster))))

(defn home-player-list [raw-roster]
  (player-list raw-roster home-active-players home-scratched-players))
(defn away-player-list [raw-roster]
  (player-list raw-roster away-active-players away-scratched-players))

(defn home-player-shifts-url [game-id]
  (let [season (parse-int (subs game-id 0 4))
        sub-game-id (subs game-id 4)]
    (str "http://www.nhl.com/scores/htmlreports/" season (str (+ season 1)) "/TH" sub-game-id ".HTM")))
(defn away-player-shifts-url [game-id]
  (let [season (parse-int (subs game-id 0 4))
        sub-game-id (subs game-id 4)]
    (str "http://www.nhl.com/scores/htmlreports/" season (str (+ season 1)) "/TV" sub-game-id ".HTM")))

(defn home-player-shifts-raw [game-id]
  (fetch-url (home-player-shifts-url game-id)))
(defn away-player-shifts-raw [game-id]
  (fetch-url (away-player-shifts-url game-id)))

(defn shift-rows [shift-html]
  (enlive/select shift-html [:div.pageBreakAfter :> :table :> [:tr (enlive/nth-child 4)] :> :td :> :table :> :tr]))

(defn parse-shift-time [time-string]
  (common-parse/string-to-time (string/trim (subs time-string 0 (.indexOf time-string "/")))))

(defn shift-data-cleaning-function [idx]
  (case idx
    0 parse-int                         ;shift number
    1 identity                          ;period
    2 parse-shift-time                  ;shift start time
    3 parse-shift-time                  ;shift stop time
    4 common-parse/string-to-interval   ;duration
    5 identity                          ;event code
  ))

(defn clean-shift-row-data [raw-row]
  (map-indexed (fn [idx data] ((shift-data-cleaning-function idx) data)) raw-row))

(defn add-folded-shift-row [[folded-rows last-player] shift-row]
  (if-not (empty? (enlive/select shift-row [:.playerHeading]))
    (let [player-number (common-parse/parse-int (first ((first (enlive/select shift-row [:td])) :content)))]
      (if-not (empty? last-player)
        [(conj folded-rows last-player) [player-number []]]
        [folded-rows [player-number []]]))
    (if-not (empty? (enlive/select shift-row #{[:> :.oddColor] [:> :.evenColor]}))
      [folded-rows [(first last-player)
                    (conj
                      (last last-player)
                      (clean-shift-row-data (map first (map :content (enlive/select shift-row [:td])))))]]
      [folded-rows last-player])))

(defn fold-shift-rows [shift-rows]
  (apply conj
    (reduce
      add-folded-shift-row
      [[] []]
      shift-rows)))

;each player is identified by "number last name, first name"
;each shift has (shift #,period, start time, stop time, duration, event)
(defn home-player-shifts [game-id]
  (let [shifts-raw (home-player-shifts-raw game-id)]
    (if (not (nil? shifts-raw))
      (fold-shift-rows (shift-rows (home-player-shifts-raw game-id)))
      nil)))
(defn away-player-shifts [game-id]
  (let [shifts-raw (away-player-shifts-raw game-id)]
    (if (not (nil? shifts-raw))
      (fold-shift-rows (shift-rows (away-player-shifts-raw game-id)))
      nil)))

(defn game-plays-url [game-id]
  (let [season (parse-int (subs game-id 0 4))
        sub-game-id (subs game-id 4)]
    (str "http://www.nhl.com/scores/htmlreports/" season (str (+ season 1)) "/PL" sub-game-id ".HTM")))

(defn game-plays-raw [game-id]
  (fetch-url (game-plays-url game-id)))

(defn return-nil [ & args]
  nil)


(defn plays-cell-player-numbers [cell]
  (remove empty?
          (map
            (fn [cell-content] (first (:content cell-content)))
            (enlive/select cell [:font]))))

(defn plays-cell-content [cell]
  (if (nil? cell)
    nil
    (if (not (= "\n" (first cell)))  ;player numbers table starts with \n
      (first cell)
      (plays-cell-player-numbers cell))))

(defn plays-row-content [row]
  (remove empty? (map (fn [row-content] (plays-cell-content (:content row-content))) row)))

(defn plays-data-cleaning-function [idx]
  (case idx
    0 parse-int                         ;play number
    1 identity                          ;period
    2 string/lower-case                 ;strength
    3 common-parse/string-to-time       ;play time
    4 identity                          ;play event
    5 common-parse/clean-input-string   ;play_description
    6 (partial string/join ",")         ;home-players
    7 (partial string/join ",")         ;away-players
  ))

(defn game-plays [game-id]
  (if-let [plays-raw (game-plays-raw game-id)]
    (map (fn [row] (remove nil? (map-indexed
                     (fn [idx play-data] ((plays-data-cleaning-function idx) play-data))
                     (plays-row-content row))))
         (map :content (enlive/select plays-raw [:tr.evenColor])))
    nil))

(defn play-zone [play-description]
  (if (.contains play-description "Off. Zone")
    "offensive"
    (if (.contains play-description "Def. Zone")
      "defensive"
      (if (.contains play-description "Neu. Zone")
        "neutral"
        nil))))

(defn flip-zone [zone]
  (case zone
    "offensive" "defensive"
    "defensive" "offensive"
    zone))

(defn flip-strength [strength]
  (case strength
    "sh" "pp"
    "pp" "sh"
    strength))

(defn play-primary-team-id [play-description]
  [play-description]
  (let [team-abreviation (last (re-find #"^([\w.]{3})" play-description))]
    (if (some? team-abreviation)
      ;TODO handle seasons in plays
      ;((db-teams/find-team-by-abreviation team-abreviation) :db_id)
      nil
      nil)))

(defn play-player-and-team-ids [play-description]
  (map (fn [[full-match team-abreviation player-number]]
         ;TODO fix with seasons
         (let [team-id (:db_id (db-teams/get-team-by-abreviation-and-season team-abreviation 2016))]
           [(db-players/player-id-by-team-and-number team-id (common-parse/parse-int player-number))
            team-id]))
       (re-seq #"([\w.]{3}) #(\d+)" play-description)))

(defn play-player-ids [play-description]
  (map first (play-player-and-team-ids play-description)))

(defn play-single-player-and-team
  "For play descriptions that start with the team aberviation and identify the player by number"
  [play-description]
  (let [team-abreviation (last (re-find #"^([\w.]{3})" play-description))
        player-number (last (re-find #"#(\d+)" play-description))]
    (if (and (some? team-abreviation) (some? player-number))
      ;TODO fix with seasons
      (let [team-id (:db_id (db-teams/get-team-by-abreviation-and-season team-abreviation 2016))]
        [(db-players/player-id-by-team-and-number team-id (common-parse/parse-int player-number))
        team-id])
      nil)))

(defn play-shot-type [play-description]
  (if-let [raw-shot-type (last (re-find #",\s*([\w\s-]+)\s*," play-description))]
    (string/lower-case raw-shot-type)
    nil))

(defn play-shot-distance [play-description]
  (if-let [distance-str (last (re-find #",\s*(\d+)\s*ft" play-description))]
    (common-parse/parse-int distance-str)
    nil))

(defn play-shot-miss-location [play-description]
  (last (re-find #",\s*[\w\s]+\s*,\s*([\w\s]+)\s*," play-description)))

(defn play-winning-team-id [play-description]
  (if-let [winning-team-abreviation (last (re-find #"(\w{3}) won" play-description))]
        ;TODO fix with seasons
         (:db_id (db-teams/get-team-by-abreviation-and-season winning-team-abreviation 2016))
         nil))

(defn play-penalty-description [play-description]
  (last (re-find #"[A-Z#]\s+([A-Z][\w\s-\\\/.]*)\(" play-description)))

(defn play-penalty-major [play-description]
  (.contains play-description "(maj)"))

(defn play-penalty-duration [play-description]
  (if-let [raw-duration (last (re-find #"\((\d+) min\)" play-description))]
    (common-parse/string-to-interval (str (format "%02d" (common-parse/parse-int raw-duration)) ":00"))
    nil))

(defn play-serving-player-and-team [play-description]
  (if (boolean (re-matches #"(?i).*served by.*" play-description))
    (let [team-id (play-primary-team-id play-description)
          player-number (last (re-find #"(?i)Served By:\s*#(\d+)" play-description))]
        [(db-players/player-id-by-team-and-number team-id (common-parse/parse-int player-number))
         team-id])
    [nil,nil]))
