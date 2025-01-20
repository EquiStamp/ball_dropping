(ns ball-dropping.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]]
            [reitit.ring :as ring]
            [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as muuntaja]))

;; Initialize state with hardcoded example data
(def state (atom {:balls 
  [{:id "1" :name "Fix authentication bug" :responsible "Alice" :ticket "GH-123" :importance "critical" 
    :due-date "2024-01-25T10:00:00Z" :last-checked "2024-01-19T15:30:00Z" :resolved false}
   {:id "2" :name "Update documentation" :responsible "Bob" :importance "low" 
    :due-date "2025-02-01T10:00:00Z" :last-checked "2024-01-20T09:00:00Z" :resolved false}
   {:id "3" :name "Refactor database layer" :responsible "Charlie" :ticket "GH-456" :importance "high"
    :due-date "2024-01-28T10:00:00Z" :last-checked nil :resolved false}
   {:id "4" :name "Add unit tests" :responsible "Diana" :ticket "GH-789" :importance "medium"
    :due-date "2025-01-30T10:00:00Z" :last-checked "2024-01-18T11:20:00Z" :resolved false}
   {:id "5" :name "Implement search feature" :responsible "Eve" :importance "medium"
    :due-date "2025-02-05T10:00:00Z" :last-checked "2024-01-15T16:45:00Z" :resolved true}
   {:id "6" :name "Optimize performance" :responsible "Frank" :ticket "GH-234" :importance "high"
    :due-date "2024-01-26T10:00:00Z" :last-checked "2024-01-20T14:10:00Z" :resolved false}
   {:id "7" :name "Setup CI/CD pipeline" :responsible "Grace" :importance "medium"
    :due-date "2025-02-10T10:00:00Z" :last-checked nil :resolved false}
   {:id "8" :name "Add error handling" :responsible "Henry" :ticket "GH-567" :importance "high"
    :due-date "2025-01-29T10:00:00Z" :last-checked "2024-01-19T10:30:00Z" :resolved false}
   {:id "9" :name "Update dependencies" :responsible "Alice" :importance "low"
    :due-date "2025-02-15T10:00:00Z" :last-checked "2024-01-17T09:15:00Z" :resolved true}
   {:id "10" :name "Fix mobile layout" :responsible "Bob" :ticket "GH-890" :importance "medium"
    :due-date "2024-01-27T10:00:00Z" :last-checked "2024-01-20T11:45:00Z" :resolved false}
   {:id "11" :name "Add dark mode" :responsible "Charlie" :importance "low"
    :due-date "2024-02-20T10:00:00Z" :last-checked nil :resolved false}
   {:id "12" :name "Implement caching" :responsible "Diana" :ticket "GH-345" :importance "high"
    :due-date "2024-01-31T10:00:00Z" :last-checked "2024-01-18T13:20:00Z" :resolved false}
   {:id "13" :name "Setup monitoring" :responsible "Eve" :importance "critical"
    :due-date "2024-01-24T10:00:00Z" :last-checked "2024-01-20T16:00:00Z" :resolved false}
   {:id "14" :name "Add logging" :responsible "Frank" :ticket "GH-678" :importance "medium"
    :due-date "2024-02-03T10:00:00Z" :last-checked "2024-01-16T14:30:00Z" :resolved true}
   {:id "15" :name "Security audit" :responsible "Grace" :importance "critical"
    :due-date "2024-01-25T10:00:00Z" :last-checked "2024-01-19T11:15:00Z" :resolved false}
   {:id "16" :name "Code review feedback" :responsible "Henry" :importance "medium"
    :due-date "2024-01-26T10:00:00Z" :last-checked "2024-01-20T10:00:00Z" :resolved false}
   {:id "17" :name "Update API docs" :responsible "Alice" :ticket "GH-901" :importance "low"
    :due-date "2024-02-08T10:00:00Z" :last-checked nil :resolved false}
   {:id "18" :name "Fix memory leak" :responsible "Bob" :importance "critical"
    :due-date "2024-01-23T10:00:00Z" :last-checked "2024-01-20T15:45:00Z" :resolved false}
   {:id "19" :name "Add accessibility features" :responsible "Charlie" :ticket "GH-234" :importance "high"
    :due-date "2024-01-29T10:00:00Z" :last-checked "2024-01-17T11:30:00Z" :resolved false}
   {:id "20" :name "Setup backup system" :responsible "Diana" :importance "high"
    :due-date "2024-01-30T10:00:00Z" :last-checked "2024-01-19T14:20:00Z" :resolved false}]}))

(defn get-balls [_]
  {:status 200
   :body (:balls @state)})

(defn save-balls [{:keys [body-params]}]
  (swap! state assoc :balls body-params)
  {:status 200
   :body (:balls @state)})

(def app
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/balls" {:get get-balls
                 :post save-balls}]]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware
                        [wrap-cors
                         :access-control-allow-origin [#"http://localhost:8000"]
                         :access-control-allow-methods [:get :post :options]
                         :access-control-allow-credentials "true"
                         :access-control-allow-headers ["Content-Type" "Accept"]]]}})
   (ring/create-default-handler)))

(defn start-server []
  (jetty/run-jetty #'app {:port 3000
                          :join? false}))

(defn -main [& _]
  (start-server)
  (println "Server running on http://localhost:3000")) 