(ns ball-dropping.lambda
  (:gen-class
   :name ball_dropping.lambda.Handler
   :implements [com.amazonaws.services.lambda.runtime.RequestHandler])
  (:require [ball-dropping.server :as server]
            [muuntaja.core :as m])
  (:import [com.amazonaws.services.lambda.runtime Context LambdaLogger]
           [com.amazonaws.services.lambda.runtime.events 
            APIGatewayV2HTTPResponse]
           [java.util Base64 HashMap]
           [java.io ByteArrayInputStream]))

(def ^:dynamic *lambda-logger* nil)

(defn log [& args]
  (let [msg (str (apply str args) "\n")]
    (if *lambda-logger*
      (.log ^LambdaLogger *lambda-logger* msg)
      (print msg))
    (flush)))

(defn read-stream [stream]
  (when (instance? ByteArrayInputStream stream)
    (slurp stream)))

(defn api-gateway-response [{:keys [status headers body]}]
  (let [body-str (cond
                  (instance? ByteArrayInputStream body) (read-stream body)
                  :else (str body))
        headers-str (into {} (map (fn [[k v]] [k (str v)]) headers))]
    (doto (APIGatewayV2HTTPResponse.)
      (.setStatusCode (int status))
      (.setHeaders (HashMap. (or headers-str {})))
      (.setBody body-str))))

(defn decode-params [params]
  (cond
    (map? params) (->> params
                      (map (fn [[k v]] [(keyword k) v]))
                      (into {}))
    (string? params) (try
                      (m/decode "application/json" (.getBytes params))
                      (catch Exception _
                        params))
    :else params))

(defn extract-request-from-map [event]
  (let [path (get event "rawPath")]
    {:request-method (-> (get-in event ["requestContext" "http" "method"] "GET")
                        .toLowerCase
                        keyword)
     :uri path
     :headers (get event "headers" {})
     :body-params (when-let [body (get event "body")]
                   (decode-params (if (get event "isBase64Encoded" false)
                                    (String. (.decode (Base64/getDecoder) body))
                                    body)))}))


(defn -handleRequest [this event ^Context context]
  (binding [*lambda-logger* (.getLogger context)]
    (try 
      (let [request (extract-request-from-map event)
            _ (log "Request: " (pr-str request))
            response (server/app request)] 
        (log "Response: " (pr-str response))
        (api-gateway-response response))
      (catch Exception e
        (log "=== Lambda Error ===")
        (log "Error: " (.getMessage e))
        (log "Stack trace:")
        (log (with-out-str (.printStackTrace e)))
        (throw e)))))
