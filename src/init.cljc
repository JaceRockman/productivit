(ns init
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            ["react-native" :as rn]
            [datascript.core :as ds]
            [data :as data]
            [data.queries :as queries]))

(defn node->init-state
  [[{:keys [:db/id :sub-nodes] :as db-node-data}]]
  (if-let [sub-nodes (when sub-nodes (mapv node->init-state (map vector sub-nodes)))]
    {:db/id (ds/tempid :db/id)
     :entity-type "node"
     :db-ref (:db/id db-node-data)
     :sub-nodes (vec sub-nodes)
     :show-children true}
    {:db/id (ds/tempid :db/id)
     :entity-type "node"
     :db-ref (:db/id db-node-data)
     :show-children true}))

(defn initialize-app-state
  [db-conn state-conn]
  (loop [index 1]
    (let [db-nodes (ds/q '[:find (pull ?e [:db/id {:sub-nodes ...}])
                           :where [?e :entity-type "node"]]
                         @db-conn)]
      (if (empty? db-nodes)
        (if (< 100 index)
          (do
            (println "initial db state not loaded. Exiting")
            :failure)
          (do
            (println "initial db state not loaded. Retrying #" index)
            (recur (inc index))))
        (ds/transact! state-conn (map node->init-state (queries/find-top-level-nodes db-conn))))))
  (println "Initial db state loaded. Success" #_(ds/q '[:find (pull ?e [:db/id {:sub-nodes ...}])
                                                        :where [?e :entity-type "node"]]
                                                      @state-conn)))

(defn initialize-db-and-state
  [db-conn state-conn]
  (ds/transact! db-conn data/simple-example)
  (initialize-app-state db-conn state-conn)
  (println "Initialized db and state"))
  
