(ns ui.task-node
  (:require [datascript.core :as ds]
            ["react-native" :as rn]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(defn toggle-task
  [ds-conn id]
  (let [old-task-value (ds/entity ds-conn id :task-value)]
    (ds/transact! ds-conn [{:db/id id :task-value (not old-task-value)}])))

(defn task-node-content
  [ds-conn {:keys [:db/id primary-sub-node sub-nodes task-value on-task-toggled] :as node-data} nesting-depth
   & {:keys [style-override]}]
  [:> rn/Switch {:key id
                 :style (merge (node-style nesting-depth)
                               {:transform [{:scale 0.8}]}
                               style-override)
                 :on-value-change (toggle-task ds-conn id)
                 :value (:task-value node-data)
                 :trackColor {:false "#ffcccc"
                              :true  "#ccffcc"}
                 :thumbColor (if (:task-value node-data) "#66ff66" "#ff6666")}])

(def modal-style {:width "80vw" :height "80vh"
                  :background-color :white :opacity 1 :color :black
                  :text-align :center :font-size 20 :font-weight :bold})

(defn task-node-modal
  [ds-conn {:keys [task-value] :as node-data}]
  [:> rn/View {:style modal-style}
   [:> rn/Text "Task node"]
   [:> rn/Text (str "Task value: " task-value)]])
