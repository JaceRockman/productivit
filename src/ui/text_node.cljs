(ns ui.text-node
    (:require [ui.node :as node]
              [datascript.core :as ds]
              ["react-native" :as rn]))

(defn text-node-component
  [state-conn
   db-conn
   {:keys [db/id primary-sub-node sub-nodes text-value] :as node-data}
   nesting-depth
   child-nodes]
  (node/animated-node
    state-conn
    db-conn
    (fn [state-conn node-data nesting-depth]
      [:> rn/Text {:key id :style (node/node-style nesting-depth)} (:text-value node-data)])
    node-data nesting-depth child-nodes))