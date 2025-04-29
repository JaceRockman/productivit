(ns init
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            ["react-native" :as rn]
            [datascript.core :as ds]
            [data :as data]
            [data.queries :as queries]))

(defn node->init-state
  [[{:keys [db/id sub-nodes] :as db-node-data}]]
  (let [result (if-let [sub-nodes (when sub-nodes (mapv node->init-state (map vector sub-nodes)))]
                 (assoc db-node-data
                        :sub-nodes sub-nodes
                        :show-children true)
                 (assoc db-node-data
                        :show-children true))]
    ;; (println "result" result)
    result))

;; (defn initialize-app-state
;;   [db-conn state-conn]
;;   (loop [index 1]
;;     (let [db-nodes (ds/q '[:find (pull ?e [:db/id {:sub-nodes ...}])
;;                            :where [?e :entity-type "node"]]
;;                          @db-conn)]
;;       (if (empty? db-nodes)
;;         (if (< 100 index)
;;           (do
;;             (println "initial db state not loaded. Exiting")
;;             :failure)
;;           (do
;;             (println "initial db state not loaded. Retrying #" index)
;;             (recur (inc index))))
;;         (ds/transact! state-conn (map node->init-state (queries/find-top-level-nodes db-conn))))))
;;   (println "Initial db state loaded. Success" #_(ds/q '[:find (pull ?e [:db/id {:sub-nodes ...}])
;;                                                         :where [?e :entity-type "node"]]
;;                                                       @state-conn)))

;; (defn initialize-db-and-state
;;   [db-conn state-conn]
;;   (ds/transact! db-conn data/simple-example)
;;   (initialize-app-state db-conn state-conn)
;;   (println "Initialized db and state"))

(defn initialize-app-state-from-example
  [ds-conn]
  (loop [index 1]
    (let [nodes (ds/q '[:find (pull ?e [*])
                        :where [?e :entity-type "node"]]
                      @ds-conn)]
      (if (empty? nodes)
        (if (< 100 index)
          (do
            (println "initial db state not loaded. Exiting")
            :failure)
          (do
            (println "initial db state not loaded. Retrying #" index)
            (recur (inc index))))
        (ds/transact! ds-conn (map node->init-state (queries/find-top-level-nodes ds-conn))))))
  (println "Initial db state loaded. Success" #_(ds/q '[:find (pull ?e [:db/id {:sub-nodes ...}])
                                                        :where [?e :entity-type "node"]]
                                                      @ds-conn)))

(defn initialize-example-ds-conn
  [ds-conn]
  (ds/transact ds-conn data/simple-example)
  (initialize-app-state-from-example ds-conn)
  (println "Initialized ds state"))
