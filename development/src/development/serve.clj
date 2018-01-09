(ns development.serve
  (:require [clojure.java.io :as io]
            [compojure.core :as core]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.adapter.jetty :as ring-jetty]
            [nhl-stat-scraper.report.html :as report-html]))

(defn load-main-file [req]
  {:status  200
    :headers {"Content-Type" "text/html"}
    :body    (io/file "/development/public/index.html")})

(core/defroutes app-routes
  (core/GET "/" [] load-main-file )
  (route/resources "/")
  (route/not-found "<p>Page not found.</p>"))

(defonce dev-server (ring-jetty/run-jetty #'app-routes {:port 8080 :join? false}))

(defn start-server []
  (.start dev-server))

(defn stop-server []
  (.stop dev-server))

