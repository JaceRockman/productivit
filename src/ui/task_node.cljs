(ns ui.task-node
    (:require [ui.node :as node]
              [datascript.core :as ds]
              ["react-native" :as rn]))

(defn task-node-component
  [state-conn
   db-conn
   {:keys [:db/id primary-sub-node sub-nodes task-value on-task-toggled] :as node-data}
   nesting-depth
   child-nodes]
   (node/animated-node
    state-conn
    db-conn
    (fn [state-conn node-data nesting-depth]
      [:> rn/Switch {:key id
                     :style (merge (node/node-style nesting-depth) 
                                    {:transform [{:scale 0.8}]})
                     :on-value-change #(ds/transact! state-conn [[:db.fn/call node/toggle-state (:db/id node-data) :task-value]])
                     :value (:task-value (ds/entity @state-conn (:db/id node-data)))
                     :trackColor {:false "#ffcccc"
                                  :true  "#ccffcc"}
                     :thumbColor (if (:task-value (ds/entity @state-conn (:db/id node-data))) "#66ff66" "#ff6666")}])
    node-data nesting-depth child-nodes))