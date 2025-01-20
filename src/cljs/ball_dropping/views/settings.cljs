(ns ball-dropping.views.settings
  (:require
   [ball-dropping.app-state :as app-state]
   [ball-dropping.state :as state]
   [reagent.core :as r]))

(defn settings-view []
(let [form-data (r/atom(:settings @app-state/app-state))]
    (add-watch app-state/app-state :settings-watcher
      (fn [_ _ old-state new-state]
        (when (not= (:settings old-state) (:settings new-state))
          (reset! form-data (or (:settings new-state) {})))))
    (fn [] 
      (when (:show-settings @app-state/app-state)
      [:div.settings-panel
       [:h2 "Settings"]
       [:div.form-group
        [:label "Backend URL"]
        [:input {:type "text"
                 :value (:api-url @form-data)
                 :on-change #(swap! form-data assoc :api-url (.. % -target -value))}]]
       [:div.form-buttons
        [:button.save-button 
         {:on-click #(do
                       (app-state/save-settings! @form-data)
                       (state/init!)
                       (swap! app-state/app-state update :show-settings not))}
         "Save & Reload"]]]))))