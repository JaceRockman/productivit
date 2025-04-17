(ns data.queries
  (:require [datascript.core :as ds]))

(defn find-top-level-node-ids
  [db-conn]
  (let [nodes (ds/q '[:find (pull ?e [:db/id [:_sub-nodes :as :parents]])
                      :in $
                      :where [?e :entity-type "node"]]
                    @db-conn)]
    (map #(-> % first :db/id) (filter #(-> % first :parents nil?) nodes))))

(defn get-children
 [db-conn node]
 (-> (ds/pull @db-conn '[{:sub-nodes [:db/id]}] node)
     :sub-nodes
     (->> (map :db/id))))

(defn get-shown-children
 [db-conn state-conn node]
 (if (:show-children (ds/pull @state-conn '[:db/id :show-children] node))
     (get-children db-conn node)
     []))

(defn recursively-get-displayed-sub-node-ids
  [db-conn state-conn node-ids]
  (loop [displayed-nodes (if (seq? node-ids) node-ids [node-ids])
         next-children (flatten (map #(get-shown-children db-conn state-conn %) displayed-nodes))]
         (if (empty? next-children)
         displayed-nodes
         (recur (concat displayed-nodes next-children)
                (flatten (map #(get-shown-children db-conn state-conn %) next-children))))))


(defn get-parent-nodes
  [db-conn node]
  (map first (ds/q '[:find ?p
                     :in $ ?c
                     :where [?p :sub-nodes ?c]]
                    @db-conn
                    node)))