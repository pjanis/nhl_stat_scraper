(ns nhl-stat-scraper.serve.develop
  (:require [clojure.java.io :as io]
            [org.httpkit.server :as server]
            [compojure.core :as core]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [nhl-stat-scraper.report.html :as report-html]))

(defn load-main-file [req]
  {:status  200
    :headers {"Content-Type" "text/html"}
    :body    (io/file (io/resource "public/index.html"))})

(defonce dev-server (atom nil))

(core/defroutes app-routes
  (core/GET "/" [] load-main-file )
  (route/resources "/")
  (route/not-found "<p>Page not found.</p>"))

(defn start-server []
  (reset! dev-server (server/run-server (handler/site #'app-routes) {:port 8080})))

(defn stop-server []
  (when-not (nil? @dev-server)
    (reset! dev-server (@dev-server))))

