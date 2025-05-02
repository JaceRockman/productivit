(ns data.queries
  (:require [datascript.core :as ds]))

(defn find-top-level-nodes
  [conn]
  (let [nodes (ds/q '[:find (pull ?e [* [:_sub-nodes :as :parents]])
                      :in $
                      :where [?e :entity-type "node"]]
                    @conn)]
    ;; (println "nodes" nodes)
    (filter #(-> % first :parents nil?) nodes)))

(defn find-top-level-node-ids
  [conn]
  (let [nodes (find-top-level-nodes conn)]
    (map #(-> % first :db/id) nodes)))

(defn get-children
  [db-conn node]
  (-> (ds/pull @db-conn '[{:sub-nodes [:db/id]}] node)
      :sub-nodes
      (->> (map :db/id))))

(defn get-shown-children
  [db-conn state-conn node]
  (if (:show-children (ds/pull @state-conn '[:id-ref :show-children] node))
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

(defn derive-node-height
  [node-data]
  (loop [nodes [node-data]
         node-height (if (:show-menu node-data) 84 42)]
    (let [next-sub-nodes (apply concat (remove nil? (map #(when (:show-children %) (:sub-nodes %)) nodes)))
          next-nodes-height (apply + (map #(if (:show-menu %) 84 42) next-sub-nodes))]
      (if (empty? next-sub-nodes)
        node-height
        (recur next-sub-nodes
               (+ node-height next-nodes-height))))))

(defn get-state-node-parent
  [state-conn state-eid]
  (ffirst
   (ds/q '[:find (pull ?p [*])
           :in $ ?c
           :where [?p :sub-nodes ?c]]
         @state-conn
         state-eid)))

(defn remove-from-parent-node
  [conn eid]
  (let [{:keys [:db/id] :as parent-node} (get-state-node-parent conn eid)
        new-sub-nodes (remove #(= (:db/id %) eid) (:sub-nodes parent-node))]
    (ds/transact! conn [{:db/id id :sub-nodes new-sub-nodes}])))

(defn get-db-node-parents
  [db-conn db-eid]
  (ds/q '[:find (pull ?p [*])
          :in $ ?c
          :where [?p :sub-nodes ?c]]
        @db-conn
        db-eid))

(defn get-modal-state
  [ds-conn]
  (ffirst (ds/q '[:find (pull ?e [*])
                  :in $
                  :where [?e :entity-type "modal"]]
                @ds-conn)))

(defn update-modal-state
  [ds-conn k v]
  (println "update-modal-state" k v)
  (let [modal-state (get-modal-state ds-conn)]
    (ds/transact! ds-conn [{:db/id (:db/id modal-state)
                            (keyword k) v}])))

(defn open-node-edit-modal
  [ds-conn {:keys [db/id] :as node-data}]
  (let [modal-state (get-modal-state ds-conn)]
    (ds/transact! ds-conn [{:db/id (:db/id modal-state)
                            :node-ref id
                            :display true}])))

(defn toggle-modal
  [ds-conn]
  (let [{:keys [db/id display]} (get-modal-state ds-conn)]
    (ds/transact! ds-conn [{:db/id id
                            :display (not display)}])))

