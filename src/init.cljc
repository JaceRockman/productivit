(ns init
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            ["react-native" :as rn]
            [datascript.core :as ds]
            [datascript.impl.entity :as de]
            [data :as data]))

(defn db-node-to-node-state-ids
  "Given a db-node, returns the node id as well as recursively discovering all sub-nodes"
  [[{:keys [:db/id :sub-nodes] :as db-node}]]
  (if sub-nodes
    (let [sub-node-ids (map db-node-to-node-state-ids [sub-nodes])]
      (concat [id] sub-node-ids))
    [id]))

(defn node-ids-to-node-states
  [node-id]
  {:id-ref node-id :show-children true})

(defn initialize-app-state
  [db-conn state-conn]
  (loop [index 1]
  (let [db-nodes (ds/q '[:find (pull ?e [:db/id :sub-nodes])
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
      (ds/transact! state-conn (->> db-nodes
                                    (map db-node-to-node-state-ids)
                                    (flatten)
                                    (map node-ids-to-node-states))))))
  :success)

(defn initialize-db-and-state
  [db-conn state-conn]
  (ds/transact! db-conn data/example-group)
  (initialize-app-state db-conn state-conn)
  (println "Initialized db and state"))
  
