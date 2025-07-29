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

(defn reset-modal-state
  [ds-conn]
  (let [{:keys [db/id] :as modal-state} (get-modal-state ds-conn)]
    (ds/transact! ds-conn [[:db/retract id :node-ref :modal-type :parent-id :node-type]])
    (ds/transact! ds-conn [{:db/id id :display false}])))

(defn update-modal-state
  [ds-conn k v]
  (println "update-modal-state" k v)
  (let [modal-state (get-modal-state ds-conn)]
    (ds/transact! ds-conn [{:db/id (:db/id modal-state)
                            (keyword k) v}])))

(defn get-temp-node-data
  [ds-conn]
  (ffirst (ds/q '[:find (pull ?e [*])
                  :in $
                  :where [?e :entity-type "new node"]]
                @ds-conn)))

(defn set-new-node-data
  [ds-conn txn-data]
  (let [new-node-data (get-temp-node-data ds-conn)
        new-node-data-id (or (:db/id new-node-data) (ds/tempid :db/id))
        new-node-data-txn (merge {:db/id new-node-data-id :entity-type "new node"} txn-data)]
    (ds/transact! ds-conn [new-node-data-txn])))

(defn initialize-temp-node-data
  [ds-conn & node-data]
  (println (first node-data))
  (ds/transact! ds-conn [(merge (first node-data) {:db/id (ds/tempid :db/id) :entity-type "new node"})]))

(defn open-node-creation-modal
  [ds-conn parent-id]
  (let [modal-id (:db/id (get-modal-state ds-conn))
        _ (when (empty? (get-temp-node-data ds-conn)) (initialize-temp-node-data ds-conn))
        temp-node-id (:db/id (get-temp-node-data ds-conn))]
    (when parent-id
      (ds/transact! ds-conn [{:db/id temp-node-id
                              :parent-id parent-id}]))
    (ds/transact! ds-conn [{:db/id modal-id
                            :modal-type "node-creation"
                            :display true}])))

(defn reset-node-creation-data
  [ds-conn]
  (when-let [new-node-data (get-temp-node-data ds-conn)]
    (ds/transact! ds-conn [[:db/retractEntity (:db/id new-node-data)]])))

(defn open-node-edit-modal
  [ds-conn {:keys [db/id] :as node-data}]
  (initialize-temp-node-data ds-conn node-data)
  (let [modal-id (:db/id (get-modal-state ds-conn))]
    (ds/transact! ds-conn [{:db/id modal-id
                            :node-ref id
                            :modal-type "node-edit"
                            :display true}])))

(defn toggle-modal
  [ds-conn]
  (let [{:keys [db/id display]} (get-modal-state ds-conn)]
    (if display
      (reset-modal-state ds-conn)
      (ds/transact! ds-conn [{:db/id id
                              :display true}]))))

(defn transact-new-node
  [ds-conn temp-node-data]
  (println "transact-new-node" temp-node-data)
  (let [new-node-id (:db/id temp-node-data)
        new-node-txn (merge (dissoc temp-node-data :parent-id :node-ref)
                            {:db/id new-node-id :entity-type "node"})]
    (ds/transact! ds-conn [new-node-txn])
    (when (:parent-id temp-node-data)
      (ds/transact! ds-conn [{:db/id (:parent-id temp-node-data) :sub-nodes new-node-id}]))
    (reset-modal-state ds-conn)
    (reset-node-creation-data ds-conn)))

(defn update-node-data
  [ds-conn temp-node-data]
  (let [node-ref-data (ds/pull @ds-conn '[*] (:node-ref temp-node-data))]
    (ds/transact! ds-conn [(assoc (dissoc temp-node-data :node-ref :parent-id)
                                  :db/id (:db/id node-ref-data))])
    (reset-modal-state ds-conn)
    (reset-node-creation-data ds-conn)))