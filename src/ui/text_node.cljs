(ns ui.text-node
  (:require [ui.node :as node]
            [datascript.core :as ds]
            ["react-native" :as rn]))

(defn text-node-content
  [ds-conn {:keys [db/id primary-sub-node sub-nodes text-value] :as node-data} nesting-depth
   & {:keys [style-override]}]
  [:> rn/Text {:key id :style (merge (node/node-style nesting-depth) style-override)}
   (str "Text: " text-value " node-id: " id)])