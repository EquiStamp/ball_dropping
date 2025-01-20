(ns ball-dropping.state
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [ball-dropping.app-state :refer [app-state]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- generate-id []
  (.toString (random-uuid)))

(defn- make-ball [data]
  (assoc data
         :id (generate-id)
         :created-at (.toISOString (js/Date.))
         :last-checked nil
         :resolved false))

(defn- add-to-history! [action previous-state]
  (swap! app-state (fn [state]
                     (-> state
                         (update :history conj {:action action
                                              :previous-state previous-state})
                         (assoc :redo-stack [])))))

;; API Functions
(defn- fetch-balls! []
  (go (let [response (<! (http/get (str (-> @app-state :settings :api-url) "/balls")
                                  {:with-credentials? true}))]
        (when (:success response)
          (swap! app-state assoc :balls (:body response))))))

(defn- save-balls! [balls]
  (go (let [response (<! (http/post (str (-> @app-state :settings :api-url) "/balls")
                                   {:with-credentials? true
                                    :json-params balls}))]
        (when (:success response)
          (swap! app-state assoc :balls (:body response))))))

(defn- delete-ball! [id]
  (go (let [response (<! (http/delete (str (-> @app-state :settings :api-url) "/balls/" id)
                                      {:with-credentials? true}))]
        (when (:success response)
          (swap! app-state assoc :balls (:body response))))))

;; Public Functions
(defn init! []
  (reset! app-state (assoc @app-state :balls []))
  (fetch-balls!))

(defn add-ball! [data]
  (let [new-ball (make-ball data)]
    (add-to-history! :add nil)
    (swap! app-state update :balls conj new-ball)
    (save-balls! (:balls @app-state))))

(defn check-ball! [id]
  (let [balls (:balls @app-state)
        ball-index (.findIndex (clj->js balls) #(= (.-id %) id))
        old-ball (get balls ball-index)
        new-ball (assoc old-ball :last-checked (.toISOString (js/Date.)))]
    (when (>= ball-index 0)
      (add-to-history! :check old-ball)
      (swap! app-state update :balls assoc ball-index new-ball)
      (save-balls! (:balls @app-state)))))

(defn resolve-ball! [id]
  (let [balls (:balls @app-state)
        ball-index (.findIndex (clj->js balls) #(= (.-id %) id))
        old-ball (get balls ball-index)
        new-ball (update old-ball :resolved not)]
    (when (>= ball-index 0)
      (add-to-history! :resolve old-ball)
      (swap! app-state update :balls assoc ball-index new-ball)
      (save-balls! (:balls @app-state)))))

(defn remove-ball! [id]
  (let [balls (:balls @app-state)
        ball-index (.findIndex (clj->js balls) #(= (.-id %) id))
        old-ball (get balls ball-index)]
    (when (>= ball-index 0)
      (add-to-history! :delete old-ball)
      (swap! app-state update :balls #(vec (concat (subvec % 0 ball-index)
                                                  (subvec % (inc ball-index)))))
      (delete-ball! id))))

(defn start-edit! [id]
  (let [balls (:balls @app-state)
        ball (->> balls
                  (filter #(= (:id %) id))
                  first)]
    (println "ball" ball id)
    (when ball
      (add-to-history! :edit ball)
      (swap! app-state assoc :editing ball))))

(defn update-ball! [updated-ball]
  (let [balls (:balls @app-state)
        ball-index (.findIndex (clj->js balls) #(= (.-id %) (:id updated-ball)))]
    (when (>= ball-index 0)
      (swap! app-state update :balls assoc ball-index updated-ball)
      (swap! app-state dissoc :editing)
      (save-balls! (:balls @app-state)))))

(defn undo! []
  (when-let [last-action (peek (:history @app-state))]
    (let [{:keys [action previous-state]} last-action
          balls (:balls @app-state)
          ball-index (when previous-state 
                      (.findIndex (clj->js balls) #(= (.-id %) (:id previous-state))))]
      (case action
        :add (do
               (swap! app-state update :balls pop)
               (swap! app-state update :history pop)
               (save-balls! (:balls @app-state)))
        :delete (do
                 (swap! app-state update :balls conj previous-state)
                 (swap! app-state update :history pop)
                 (save-balls! (:balls @app-state)))
        (:check :resolve :edit)
        (when (>= ball-index 0)
          (swap! app-state update :balls assoc ball-index previous-state)
          (swap! app-state update :history pop)
          (save-balls! (:balls @app-state)))))))

(defn redo! []
  (when-let [next-action (peek (:redo-stack @app-state))]
    (let [{:keys [action next-state]} next-action
          balls (:balls @app-state)
          ball-index (.findIndex (clj->js balls) #(= (.-id %) (:id next-state)))]
      (case action
        :delete (do
                 (swap! app-state update :balls #(vec (remove (fn [b] (= (:id b) (:id next-state))) %)))
                 (swap! app-state update :redo-stack pop)
                 (save-balls! (:balls @app-state)))
        (:check :resolve :edit)
        (when (>= ball-index 0)
          (swap! app-state update :balls assoc ball-index next-state)
          (swap! app-state update :redo-stack pop)
          (save-balls! (:balls @app-state))))))) 