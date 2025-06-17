(ns data.queries
  (:require [datascript.core :as ds]))

(defn get-currently-selected-node
  [conn]
  (ffirst (ds/q '[:find (pull ?node-id [*])
                  :in $
                  :where [?e :selected-node ?node-id]]
                @conn)))

(defn get-node-selection-db-id
  [conn]
  (ffirst (ds/q '[:find ?e
                  :where [?e :selected-node ?node-id]]
                @conn)))

(defn select-node
  [conn node-id]
  (if (nil? node-id)
    (ds/transact! conn [[:db.fn/retractEntity (get-node-selection-db-id conn)]])
    (if-let [node-selection-db-id (get-node-selection-db-id conn)]
      (ds/transact! conn [{:db/id node-selection-db-id :selected-node node-id}])
      (ds/transact! conn [{:db/id (ds/tempid :db/id) :selected-node node-id}]))))

(defn find-top-level-nodes
  [conn]
  (let [nodes (map first (ds/q '[:find (pull ?e [* [:_sub-nodes :as :parents]])
                                 :in $
                                 :where [?e :entity-type "node"]]
                               @conn))]
    (filterv #(-> % :parents nil?) nodes)))

(defn find-top-level-node-ids
  [conn]
  (let [nodes (find-top-level-nodes conn)]
    (map #(-> % first :db/id) nodes)))

(defn get-immediate-children
  [conn node-id]
  (ds/pull @conn '[{:sub-nodes [*]}] node-id))

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

(defn derive-node-item-height
  [node-data]
  (if (:show-menu node-data)
    84
    42))

(defn conditionally-recursive-get-attribute
  [query-result predicate nesting-key attribute-key map-of-existing-values]
  (let [updated-map-of-values (assoc map-of-existing-values (:db/id query-result) (get query-result attribute-key))
        nested-result (when (predicate query-result) (get query-result nesting-key))]
    (if (and (coll? nested-result) (not (empty? nested-result)))
      (apply merge
             (map #(conditionally-recursive-get-attribute % predicate nesting-key attribute-key updated-map-of-values) nested-result))
      updated-map-of-values)))

(defn get-node-group-height
  [db-conn node-id]
  (let [pull-result (ds/pull @db-conn '[*] node-id)
        item-heights (conditionally-recursive-get-attribute
                      pull-result #(get % :show-children) :sub-nodes :item-height {})]
    (apply + (vals item-heights))))

(defn get-node-parent
  [conn eid]
  (ffirst
   (ds/q '[:find (pull ?p [*])
           :in $ ?c
           :where [?p :sub-nodes ?c]]
         @conn
         eid)))

(defn remove-from-parent-node
  [conn eid]
  (let [{:keys [:db/id] :as parent-node} (get-node-parent conn eid)
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

(defn get-new-node-data
  [ds-conn]
  (ffirst (ds/q '[:find (pull ?e [*])
                  :in $
                  :where [?e :entity-type "new node"]]
                @ds-conn)))

(defn set-new-node-data
  [ds-conn {:keys [node-type]}]
  (let [new-node-data (get-new-node-data ds-conn)
        new-node-data-id (or (:db/id new-node-data) (ds/tempid :db/id))]
    (if node-type
      (ds/transact! ds-conn [{:db/id new-node-data-id :entity-type "new node" :node-type node-type}])
      (ds/transact! ds-conn [[:db/retract new-node-data-id :node-type]]))))

(defn open-node-creation-modal
  [ds-conn parent-id]
  (let [modal-id (:db/id (get-modal-state ds-conn))]
    (ds/transact! ds-conn [{:db/id modal-id
                            :parent-id parent-id
                            :modal-type "node-creation"
                            :display true}])))

(defn open-node-edit-modal
  [ds-conn {:keys [db/id] :as node-data}]
  (let [modal-id (:db/id (get-modal-state ds-conn))]
    (ds/transact! ds-conn [{:db/id modal-id
                            :node-ref id
                            :modal-type "node-edit"
                            :display true}])))

(defn toggle-modal
  [ds-conn]
  (let [{:keys [db/id display]} (get-modal-state ds-conn)]
    (ds/transact! ds-conn [{:db/id id
                            :display (not display)}])))

