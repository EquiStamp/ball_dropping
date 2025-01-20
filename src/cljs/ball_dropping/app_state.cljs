(ns ball-dropping.app-state
  (:require [reagent.core :as r]))

(def app-state (r/atom {:balls []
                       :history []
                       :redo-stack []})) 