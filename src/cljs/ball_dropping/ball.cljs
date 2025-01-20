(ns ball-dropping.ball
  (:require [clojure.string :as str]))

(def importance-multipliers
  {"low" 1
   "medium" 2
   "high" 3
   "critical" 4})

(defn get-importance-multiplier [importance]
  (case importance
    "critical" 4
    "high" 3
    "medium" 2
    "low" 1))

(defn get-urgency-score [{:keys [importance due-date last-checked resolved]}]
  (if resolved
    -1  ; Resolved items go to the bottom
    (let [now (js/Date.)
          ; Base score from importance (400-100)
          importance-score (* 100 (get-importance-multiplier importance))
          
          ; Due date impact (-inf to +200)
          due-date-obj (when due-date (js/Date. due-date))
          days-until-due (when due-date-obj 
                          (/ (- (.getTime due-date-obj) (.getTime now)) 
                             (* 1000 60 60 24)))
          due-date-score (when days-until-due
                          (cond 
                            (< days-until-due 0) (* 200 (Math/abs days-until-due)) ; Overdue items get higher priority
                            (< days-until-due 7) (* 20 (- 7 days-until-due))      ; Due soon gets medium priority
                            :else 0))
          
          ; Time since last check provides a gradual boost
          last-checked-obj (when last-checked (js/Date. last-checked))
          days-since-checked (when last-checked-obj
                             (/ (- (.getTime now) (.getTime last-checked-obj))
                                (* 1000 60 60 24)))
          check-boost (if days-since-checked
                       (* 50 days-since-checked)  ; Each day unchecked adds 50 to score
                       1000)]  ; Unchecked items get a significant boost
      
      (+ importance-score 
         (or due-date-score 0)
         check-boost))))

(defn get-status-class [last-checked]
  (when last-checked
    (let [hours-since (/ (- (.getTime (js/Date.)) (.getTime (js/Date. last-checked)))
                        (* 1000 60 60))]
      (cond
        (< hours-since 24) "checked-today"
        (< hours-since 48) "checked-yesterday"
        :else "needs-check"))))

(defn format-time-since [timestamp]
  (if timestamp
    (let [diff (- (.getTime (js/Date.)) (.getTime (js/Date. timestamp)))
          seconds (Math/floor (/ diff 1000))
          minutes (Math/floor (/ seconds 60))
          hours (Math/floor (/ minutes 60))
          days (Math/floor (/ hours 24))]
      (cond
        (< seconds 60) "just now"
        (< minutes 60) (str minutes " minutes ago")
        (< hours 24) (str hours " hours ago")
        :else (str days " days ago")))
    "never"))

(defn is-overdue? [due-date]
  (when due-date  ; Early return nil (falsy) if no due date
    (let [now (js/Date.)
          due-date-obj (js/Date. due-date)]
      (< (.getTime due-date-obj) (.getTime now)))))

(defn format-due-date [due-date]
  (when due-date  ; Early return nil if no due date
    (let [now (js/Date.)
          due-date-obj (js/Date. due-date)
          days-until-due (Math/floor (/ (- (.getTime due-date-obj) (.getTime now)) 
                                      (* 1000 60 60 24)))]
      (cond
        (< days-until-due 0) (str "Overdue by " (Math/abs days-until-due) " days")
        (= days-until-due 0) "Due today"
        (= days-until-due 1) "Due tomorrow"
        :else (str "Due in " days-until-due " days"))))) 