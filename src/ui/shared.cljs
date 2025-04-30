(ns ui.shared
  (:require
   ["react-native" :as rn]))

(defn divider
  []
  [:> rn/View {:style {:background :black :width "100%" :height 2}}])