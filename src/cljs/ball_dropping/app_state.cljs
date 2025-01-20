(ns ball-dropping.app-state
  (:require [reagent.core :as r]))

(def storage-key "ball-dropping-settings")

(def default-api-url 
  (if (= js/window.location.hostname "localhost")
    "http://localhost:3000/api"
    (str js/window.location.protocol "//" "REPLACE_WITH_API_URL")))

(defn load-settings []
  (if-let [stored (.getItem js/localStorage storage-key)]
    (js->clj (.parse js/JSON stored) :keywordize-keys true)
    {:api-url default-api-url}))

(def app-state (r/atom {:balls []
                       :history []
                       :redo-stack []
                       :settings (load-settings)}))

(defn save-settings! [new-settings]
  (swap! app-state assoc :settings new-settings)
  (.setItem js/localStorage storage-key (.stringify js/JSON (clj->js new-settings)))) 