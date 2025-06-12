(ns ui.time-node
  (:require [ui.node :as node]
            [cljs-time.format :as t-format]
            ["react-native" :as rn]))

(def standard-time-format
  (t-format/formatter "MMM' 'dd', 'YYYY"))

(defn time-node-content
  [ds-conn {:keys [:db/id primary-sub-node sub-nodes start-time end-time on-time-start on-time-end] :as node-data} nesting-depth
   & {:keys [style-override]}]
  [:> rn/Text {:key id :style (merge (node/node-style nesting-depth) style-override)}
   (str "Time Value: "
        (t-format/unparse standard-time-format start-time))])
