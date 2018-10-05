(defproject nhl_stat_scraper "1.1.0"
  :description "App to create database with NHL game data"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-http "3.9.1"]
                 [clj-time "0.14.4"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [dire "0.5.4"]
                 [enlive "1.1.6"]
                 [hikari-cp "2.6.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.4.0"]
                 [org.postgresql/postgresql "42.2.5"]
                 [ragtime "0.7.2"]
                 [ring/ring-core "1.7.0"]
                 [ring/ring-jetty-adapter "1.7.0"]]
  :main ^:skip-aot nhl-stat-scraper.core
  :target-path "target/%s"
  :jar-exclusions [#"templates/(?!public).*"]
  :uberjar-exclusions [#"templates/(?!public).*"]
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["development" "private"]
                   :source-paths ["development/src"]
                   :dependencies [[compojure "1.6.1"]
                                  [ring/ring-core "1.7.0"]
                                  [ring/ring-jetty-adapter "1.7.0"]]}}
  :jvm-opts ["-Duser.timezone=UTC"])
