(ns data.init
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            ["react-native" :as rn]
            [datascript.core :as ds]
            [data.database :as database]
            [data.queries :as queries]))

(defn initialize-show-children!
  [ds-conn {:keys [db/id] :as node-data}]
  (ds/transact! ds-conn [{:db/id id :show-children false}]))

(defn empty-or-nil?
  [val]
  (or (empty? val) (nil? val)))

(defn set-initial-state-values
  [{:keys [:sub-nodes] :as node-data}]
  (let [initialized-sub-nodes (when (not (empty-or-nil? sub-nodes))
                                (map set-initial-state-values sub-nodes))]
    (assoc node-data
           :show-children false
           :show-menu false
           :item-height 42
           :sub-nodes (or (vec initialized-sub-nodes) []))))

(defn node->init-state
  [db-node-data]
  (let [{:keys [:sub-nodes] :as basic-state-node-data} (set-initial-state-values db-node-data)
        initialized-children (when (not (empty-or-nil? sub-nodes))
                               (mapv node->init-state sub-nodes))
        node-data-with-updated-children (assoc basic-state-node-data
                                               :sub-nodes (or initialized-children []))
        raw-calculated-group-height (apply + (vals (queries/conditionally-recursive-get-attribute
                                                    node-data-with-updated-children
                                                    #(get % :show-children) :sub-nodes :item-height {})))
        processed-group-height (new (.-Value rn/Animated) raw-calculated-group-height)
        result (assoc basic-state-node-data
                      :group-height processed-group-height)]
    result))

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
        (ds/transact! ds-conn (mapv node->init-state (queries/find-top-level-nodes ds-conn))))))
  (println "Initial db state loaded. Success" (ds/q '[:find (pull ?e [:db/id :show-children :item-height :group-height {:sub-nodes ...}])
                                                      :where [?e :entity-type "node"]]
                                                    @ds-conn)))

(defn initialize-example-ds-conn
  [ds-conn]
  (ds/transact ds-conn database/simple-example)
  (initialize-app-state-from-example ds-conn)
  (println "Initialized ds state"))
