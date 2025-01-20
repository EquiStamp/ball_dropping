(ns ball-dropping.app-state
  (:require [reagent.core :as r]))

(def storage-key "ball-dropping-settings")

(defn load-settings []
  (if-let [stored (.getItem js/localStorage storage-key)]
    (js->clj (.parse js/JSON stored) :keywordize-keys true)
    {:api-url "http://localhost:3000/api"}))

(def app-state (r/atom {:balls []
                       :history []
                       :redo-stack []
                       :settings (load-settings)}))

(defn save-settings! [new-settings]
  (swap! app-state assoc :settings new-settings)
  (.setItem js/localStorage storage-key (.stringify js/JSON (clj->js new-settings)))) 