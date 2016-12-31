(defproject nhl_stat_scraper "0.3.10-SNAPSHOT"
  :description "App to create database with NHL game data"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-http "2.0.0"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.7.4"]
 ;                [com.fzakaria/slf4j-timbre "0.3.1"]
                 [compojure "1.4.0"]
                 [dire "0.5.4"]
                 [enlive "1.1.6"]
                 [hikari-cp "1.6.1"]
                 [http-kit "2.1.18"]
                 [javax.servlet/servlet-api "2.5"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.postgresql/postgresql "9.4-1205-jdbc42"]
                 [ragtime "0.5.2"]]
  :main ^:skip-aot nhl-stat-scraper.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["src/main/resources/public"]}}
  :jvm-opts ["-Duser.timezone=UTC"])
