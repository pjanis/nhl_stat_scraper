(defproject nhl_stat_scraper "0.6.0"
  :description "App to create database with NHL game data"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-http "3.5.0"]
                 [clj-time "0.13.0"]
                 [com.taoensso/timbre "4.10.0"]
 ;                [com.fzakaria/slf4j-timbre "0.3.1"]
                 [compojure "1.6.0"]
                 [dire "0.5.4"]
                 [enlive "1.1.6"]
                 [hikari-cp "1.7.5"]
                 [javax.servlet/servlet-api "2.5"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.postgresql/postgresql "42.1.1"]
                 [ragtime "0.7.1"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]]
  :main ^:skip-aot nhl-stat-scraper.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["src/main/resources/public"]}}
  :jvm-opts ["-Duser.timezone=UTC"])
