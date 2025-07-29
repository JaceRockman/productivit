(ns data.database
  (:require [reagent.core :as r]
            ["react-native" :as rn]
            [datascript.core :as ds]
            [cljs-time.core :as t]))

(def schema
  {:primary-sub-node {:db/cardinality :db.cardinality/one
                      :db/valueType   :db.type/ref
                      :db/isComponent true}
   :sub-nodes        {:db.cardinality :db.cardinality/many
                      :db/valueType   :db.type/ref
                      :db/isComponent true}})

(def example-text-node
  {:text-value "Pomodoros"
   :primary-sub-node :tracker-node
   :sub-nodes [:tracker-node :time-node-1 :time-node-2 :time-node-3 :time-node-4]})

(defn time-start
  []
  (println "Time started!"))

(defn time-end
  []
  (println "Time ended!"))

(def example-time-node
  {:primary-sub-node :task-node
   :sub-nodes [:task-node]
   :start-time t/now
   :end-time (t/plus (t/now) (t/minutes 1))
   :on-time-start 'time-start
   :on-time-end 'time-end})

(defn task-toggled
  []
  (println "Task toggled!"))

(def example-task-node
  {:primary-sub-node :tracker-node
   :sub-nodes [:tracker-node :task-node-1 :task-node-2 :task-node-3]
   :task-value false
   :on-task-toggled 'task-toggled})

(defn tracker-increase
  []
  (println "Tracker increased!"))

(defn tracker-decrease
  []
  (println "Tracker decreased!"))

(def example-tracker-node
  {:primary-sub-node :task-node
   :sub-nodes [:task-node]
   :tracker-value 0
   :tracker-max-value 5
   :on-tracker-increase 'tracker-increase
   :on-tracker-decrease 'tracker-decrease})

(def simple-example
  [{:db/id "modal-state"
    :entity-type "modal"
    :valid-input? true}
   {:db/id "time-example"
    :entity-type      "node"
    :start-time       (t/now)
    :end-time         (t/plus (t/now) (t/minutes 1))}
   {:db/id             "tracker-example"
    :entity-type      "node"
    :tracker-value     0
    :tracker-min-value 0
    :tracker-max-value 5
    :increments [-5 -3 -1 1 3 5]}
   {:db/id            "1"
    :entity-type      "node"
    :text-value       "1"
    :sub-nodes        []}
   {:db/id            "2"
    :entity-type      "node"
    :text-value       "2"
    :sub-nodes        ["2.1" "2.2"]}
   {:db/id            "2.1"
    :entity-type      "node"
    :text-value       "2.1"
    :sub-nodes        ["2.1.1" "2.1.2"]}
   {:db/id            "2.2"
    :entity-type      "node"
    :text-value       "2.2"
    :sub-nodes        []}
   {:db/id            "2.1.1"
    :entity-type      "node"
    :text-value       "2.1.1"
    :sub-nodes        []}
   {:db/id            "2.1.2"
    :entity-type      "node"
    :text-value       "2.1.2"
    :sub-nodes        []}
   {:db/id            "3"
    :entity-type      "node"
    :text-value       "3"
    :sub-nodes        ["2.1"]}])

(def example-group
  [{:db/id            "top-level-solo"
    :entity-type      "node"
    :text-value       "I'm top level"
    :sub-nodes        ["text-ui"]}
   {:db/id            "not-top-level-solo"
    :entity-type      "node"
    :text-value       "I'm not top level"
    :sub-nodes        ["text-ui"]}
   {:db/id            "group"
    :entity-type      "node"
    :text-value       "ProductiviT"
    :primary-sub-node "progress-tracker"
    :sub-nodes        ["not-top-level-solo" "progress-tracker" "time-example" "text-ui" "time-ui" "task-ui" "tracker-ui"]}
   {:db/id             "progress-tracker"
    :entity-type      "node"
    :tracker-value     0
    :tracker-max-value 4}
   {:db/id            "text-ui"
    :entity-type      "node"
    :text-value       "Text UI"
    :primary-sub-node "text-progress-tracker"
    :sub-nodes        ["text-progress-tracker" "text-task-1" "text-task-2" "text-task-3"]}
   {:db/id             "text-progress-tracker"
    :entity-type      "node"
    :tracker-value     0
    :tracker-max-value 3}
   {:db/id      "text-task-1"
    :entity-type      "node"
    :task-value false}
   {:db/id      "text-task-2"
    :entity-type      "node"
    :task-value false}
   {:db/id      "text-task-3"
    :entity-type      "node"
    :task-value false}
   {:db/id "time-example"
    :entity-type      "node"
    :start-time       (t/now)
    :end-time         (t/plus (t/now) (t/minutes 1))}

   {:db/id            "time-ui"
    :entity-type      "node"
    :text-value       "Time UI"
    :primary-sub-node "time-progress-tracker"
    :sub-nodes        ["time-progress-tracker" "time-task-1" "time-task-2" "time-task-3"]}
   {:db/id             "time-progress-tracker"
    :entity-type      "node"
    :tracker-value     0
    :tracker-max-value 3}
   {:db/id      "time-task-1"
    :entity-type      "node"
    :task-value false}
   {:db/id      "time-task-2"
    :entity-type      "node"
    :task-value false}
   {:db/id      "time-task-3"
    :entity-type      "node"
    :task-value false}

   {:db/id            "task-ui"
    :entity-type      "node"
    :text-value       "Task UI"
    :primary-sub-node "task-progress-tracker"
    :sub-nodes        ["task-progress-tracker" "task-task-1" "task-task-2" "task-task-3"]}
   {:db/id             "task-progress-tracker"
    :entity-type      "node"
    :tracker-value     0
    :tracker-max-value 3}
   {:db/id      "task-task-1"
    :entity-type      "node"
    :task-value false}
   {:db/id      "task-task-2"
    :entity-type      "node"
    :task-value false}
   {:db/id      "task-task-3"
    :entity-type      "node"
    :task-value false}

   {:db/id            "tracker-ui"
    :entity-type      "node"
    :text-value       "Tracker UI"
    :primary-sub-node "tracker-progress-tracker"
    :sub-nodes        ["tracker-progress-tracker" "tracker-task-1" "tracker-task-2" "tracker-task-3"]}
   {:db/id             "tracker-progress-tracker"
    :entity-type      "node"
    :tracker-value     0
    :tracker-max-value 3}
   {:db/id      "tracker-task-1"
    :entity-type      "node"
    :task-value false}
   {:db/id      "tracker-task-2"
    :entity-type      "node"
    :task-value false}
   {:db/id      "tracker-task-3"
    :entity-type      "node"
    :task-value false}])