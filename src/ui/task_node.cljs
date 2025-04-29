(ns ui.task-node
  (:require [ui.node :as node]
            [datascript.core :as ds]
            ["react-native" :as rn]))

(defn toggle-task
  [ds-conn id]
  #(ds/transact! ds-conn [[:db.fn/call node/toggle-state id :task-value]]))

(defn task-node-content
  [ds-conn {:keys [:db/id primary-sub-node sub-nodes task-value on-task-toggled] :as node-data} nesting-depth]
  [:> rn/Switch {:key id
                 :style (merge (node/node-style nesting-depth)
                               {:transform [{:scale 0.8}]})
                 :on-value-change (toggle-task ds-conn id)
                 :value (:task-value node-data)
                 :trackColor {:false "#ffcccc"
                              :true  "#ccffcc"}
                 :thumbColor (if (:task-value node-data) "#66ff66" "#ff6666")}])
