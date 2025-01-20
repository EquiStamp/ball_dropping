(ns ball-dropping.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [ball-dropping.state :as state]
            [ball-dropping.ball :as ball]
            [ball-dropping.app-state :refer [app-state]]
            [ball-dropping.views.settings :refer [settings-view]]
            [clojure.string :as str]))

;; -------------------------
;; Components
(defn ball-form []
  (let [form-data (r/atom {:name ""
                          :responsible ""
                          :ticket ""
                          :importance "low"
                          :due-date ""})]
    (fn []
      [:div.add-form
       [:input {:type "text"
                :placeholder "Name"
                :value (:name @form-data)
                :on-change #(swap! form-data assoc :name (.. % -target -value))}]
       [:input {:type "text"
                :placeholder "Responsible"
                :value (:responsible @form-data)
                :on-change #(swap! form-data assoc :responsible (.. % -target -value))}]
       [:input {:type "text"
                :placeholder "Ticket (optional)"
                :value (:ticket @form-data)
                :on-change #(swap! form-data assoc :ticket (.. % -target -value))}]
       [:select {:value (:importance @form-data)
                :on-change #(swap! form-data assoc :importance (.. % -target -value))}
        [:option {:value "low"} "Low"]
        [:option {:value "medium"} "Medium"]
        [:option {:value "high"} "High"]
        [:option {:value "critical"} "Critical"]]
       [:input {:type "datetime-local"
                :value (:due-date @form-data)
                :on-change #(swap! form-data assoc :due-date (.. % -target -value))}]
       [:button {:on-click #(when (and (not-empty (:name @form-data))
                                     (not-empty (:responsible @form-data)))
                            (state/add-ball! @form-data)
                            (reset! form-data {:name ""
                                             :responsible ""
                                             :ticket ""
                                             :importance "low"
                                             :due-date ""}))}
        "Add Item"]])))

(defn ball-item [{:keys [id name responsible ticket importance due-date last-checked resolved]}]
  [:div.ball {:class [(str "importance-" importance)
                     (when resolved "resolved")
                     (ball/get-status-class last-checked)]}
   [:div.ball-info
    [:div.ball-name name]
    [:div.ball-meta
     [:div.ball-meta-line
      [:span responsible]
      (when ticket
        [:span [:a {:href ticket
                   :target "_blank"} ticket]])
      [:span.importance-badge {:class (str "importance-" importance)} importance]]
     (when (and due-date (-> due-date str/blank? not))
       [:div.ball-meta-line 
        [:span {:class (when (ball/is-overdue? due-date) "overdue")}
         (ball/format-due-date due-date)]])
     (when-not resolved 
       [:div.ball-meta-line
       [:span (str "Last checked " (ball/format-time-since last-checked))]
       ])]]
   [:div.ball-actions
    [:div.edit-icon {:on-click #(state/start-edit! id)
                     :data-tooltip "Edit"} "✎"]
    [:div.check-icon {:on-click #(state/check-ball! id)
                     :data-tooltip "Mark as checked"} "✓"]
    [:div.resolve-icon {:on-click #(state/resolve-ball! id)
                       :data-tooltip (if resolved "Mark as unresolved" "Mark as resolved")} 
     (if resolved "⚐" "⚑")]
    [:div.delete-icon {:on-click #(state/remove-ball! id)
                      :data-tooltip "Delete"} "×"]]])

(defn ball-list []
  [:div.ball-list
   (for [ball (->> (:balls @app-state)
                   (sort-by (juxt :resolved (comp - ball/get-urgency-score))))]
     ^{:key (:id ball)} [ball-item ball])])

(defn controls []
  [:div.controls
   [:button.options-button {:on-click #(state/undo!)
                           :disabled (empty? (:history @app-state))} "↺"]
   [:button.options-button {:on-click #(swap! app-state update :show-settings not)} "⚙"]])

(defn edit-form []
  (let [form-data (r/atom {})]
    (add-watch app-state :editing-watcher
      (fn [_ _ old-state new-state]
        (when (not= (:editing old-state) (:editing new-state))
          (reset! form-data (or (:editing new-state) {})))))
    (fn []
      (let [editing-ball (:editing @app-state)]
        [:div.edit-form-container
         (when editing-ball
           [:div.edit-form
            [:input {:type "text"
                    :placeholder "Name"
                    :value (:name @form-data)
                    :on-change #(swap! form-data assoc :name (.. % -target -value))}]
            [:input {:type "text"
                    :placeholder "Responsible"
                    :value (:responsible @form-data)
                    :on-change #(swap! form-data assoc :responsible (.. % -target -value))}]
            [:input {:type "text"
                    :placeholder "Ticket (optional)"
                    :value (:ticket @form-data)
                    :on-change #(swap! form-data assoc :ticket (.. % -target -value))}]
            [:select {:value (:importance @form-data)
                     :on-change #(swap! form-data assoc :importance (.. % -target -value))}
             [:option {:value "low"} "Low"]
             [:option {:value "medium"} "Medium"]
             [:option {:value "high"} "High"]
             [:option {:value "critical"} "Critical"]]
            [:input {:type "datetime-local"
                    :value (:due-date @form-data)
                    :on-change #(swap! form-data assoc :due-date (.. % -target -value))}]
            [:div.edit-form-buttons
             [:button.save-button {:on-click #(when (and (not-empty (:name @form-data))
                                                        (not-empty (:responsible @form-data)))
                                              (state/update-ball! @form-data))} "Save"]
             [:button.cancel-button {:on-click #(swap! app-state dissoc :editing)} "Cancel"]]])]))))

(defn main-page []
  [:div
   [controls]
   [settings-view]
   [:div
     [ball-form]
     [edit-form]
     [ball-list]]])

;; -------------------------
;; Initialize app
(defn mount-root []
  (rdom/render [main-page] (.getElementById js/document "app")))

(defn init []
  (state/init!)
  (mount-root))

(defn reload! []
  (mount-root)) 