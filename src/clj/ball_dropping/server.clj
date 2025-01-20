(ns ball-dropping.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]]
            [reitit.ring :as ring]
            [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [cognitect.aws.client.api :as aws]))

(def dynamodb (aws/client {:api :dynamodb :region "us-east-1"}))

(defn to-dynamo [value]
  (if (string? value)
    {:S value}
    value))


(defn from-dynamo [value]
  (if (map? value)
    (:S value)
    value))

(defn get-all-balls []
  (let [result (aws/invoke dynamodb {:op :Scan :request {:TableName "balls"}})]
    (->> result
         :Items
         (filter #(-> % :id :S))
         (map (fn [item]
                {:id (-> item :id from-dynamo)
                 :name (-> item :name from-dynamo)
                 :responsible (-> item :responsible from-dynamo)
                 :ticket (-> item :ticket from-dynamo)
                 :importance (-> item :importance from-dynamo)
                 :due-date (-> item :due-date from-dynamo)
                 :last-checked (-> item :last-checked from-dynamo)
                 :resolved (-> item :resolved from-dynamo #{"true"})})))))

(defn parse-ball [ball]
  {:id (to-dynamo (:id ball))
   :name (to-dynamo (:name ball))
   :responsible (to-dynamo (:responsible ball))
   :ticket (to-dynamo (or (:ticket ball) ""))
   :importance (to-dynamo (:importance ball))
   :due-date (to-dynamo (:due-date ball))
   :last-checked (to-dynamo (or (:last-checked ball) ""))
   :resolved (to-dynamo (str (:resolved ball)))})


(defn save-ball [ball]
  (aws/invoke dynamodb {:op :PutItem :request {:TableName "balls" :Item ball}}))

(defn save-all-balls [balls]
    (->> balls
         (map parse-ball)
         (map save-ball)
         doall))

(defn get-balls [_] {:status 200 :body (get-all-balls)})

(defn save-balls [{:keys [body-params]}]
  (save-all-balls body-params)
  {:status 200 :body (get-all-balls)})

(defn delete-ball [id] (aws/invoke dynamodb {:op :DeleteItem :request {:TableName "balls" :Key {:id {:S id}}}}))

(defn handle-delete-ball [{:keys [path-params]}]
  (delete-ball (:id path-params))
  {:status 200 :body (get-all-balls)})

(def app
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/balls" {:get get-balls
                 :post save-balls}]
      ["/balls/:id" {:delete handle-delete-ball}]]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware
                        [wrap-cors
                         :access-control-allow-origin [#"http://localhost:8000"]
                         :access-control-allow-methods [:get :post :options :delete]
                         :access-control-allow-credentials "true"
                         :access-control-allow-headers ["Content-Type" "Accept"]]]}})
   (ring/create-default-handler)))

(defn start-server []
  (jetty/run-jetty #'app {:port 3000
                          :join? false}))

(defn -main [& _]
  (start-server)
  (println "Server running on http://localhost:3000")) 