(ns ui.text-node
  (:require [datascript.core :as ds]
            ["react-native" :as rn]
            [data.queries :as queries]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(defn text-node-content
  [ds-conn {:keys [db/id primary-sub-node sub-nodes text-value] :as node-data} nesting-depth
   & {:keys [style-override]}]
  [:> rn/Text {:key id :style (merge (node-style nesting-depth) style-override)}
   (str "Text: " text-value " node-id: " id)])

(defn text-node-modal
  [ds-conn {:keys [text-value] :as node-data}]
  [:> rn/View {:style {:background-color :white :gap 10 :align-items :center}}
   [:> rn/Text "Text Node Creation Window"]
   [:> rn/TextInput {:placeholder "Enter text"
                     :on-change-text (fn [text]
                                       (queries/update-node-data ds-conn {:db/id (:db/id node-data) :text-value text}))}]])


