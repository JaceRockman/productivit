(ns main
  (:require
   [reagent.core :as r]
   ["react-native" :as rn]
   [datascript.core :as ds]
   [expo.root :as expo-root]
   [init :as init]
   [data :as data])
  (:require-macros
   [macros :refer [profile]]))

(def app-db-conn (ds/create-conn data/schema))
(def app-state-conn (ds/create-conn {}))

(defn raw-text-node
  [node]
  [:> rn/Text {:key (str (:db/id node))} (str node)])

(declare determine-node-function)
(declare render-node)

(defn divider
  []
  [:> rn/View {:style {:background :black :width "100%" :height 2}}])

(defn get-component-state
  ([conn entity-id]
   (ds/entity conn entity-id))
  ([conn entity-id attribute]
   (ffirst
    (ds/q '[:find ?v
            :in $ ?e ?a
            :where [?e ?a ?v]]
          @conn entity-id attribute))))

(defn toggle-state
  [app-state entity-id attribute]
  (let [[current-value] (first (ds/q '[:find ?v
                                     :in $ ?e ?a
                                     :where [?e ?a ?v]]
                                     app-state entity-id attribute))]
    (if (not (nil? current-value))
      [{:db/id entity-id attribute (not current-value)}]
      (println (str "No entity with id and attribute: " entity-id " " attribute)))))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 10 nesting-depth)})

(defn text-node-component
  [state-conn
   {:keys [db/id
           primary-sub-node sub-nodes
           text-value]}
   nesting-depth]
  [:> rn/Pressable {:key   (str (random-uuid))
                    ;; :style (node-style nesting-depth)
                    :on-press #(ds/transact! state-conn [[:db.fn/call toggle-state id :show-children]])}
   [:> rn/Text {:style (node-style nesting-depth)} text-value]
   (divider)
   (when (and (not-empty sub-nodes) (get-component-state state-conn id :show-children))
     (map render-node (repeat state-conn) sub-nodes (repeat (inc nesting-depth))))])

(defn time-node-component
  [state-conn
   {:keys [:db/id
           primary-sub-node sub-nodes 
           start-time end-time
           on-time-start on-time-end]}
   nesting-depth]
  [:> rn/Pressable {:key      (str (random-uuid))
                    ;; :style (node-style nesting-depth)
                    :on-press #(ds/transact! state-conn [[:db.fn/call toggle-state id :show-children]])}
   [:> rn/Text {:style (node-style nesting-depth)} (str "Time Value: " start-time)]
   (divider)
   (when (and (not-empty sub-nodes) (get-component-state state-conn id :show-children))
     (map render-node (repeat state-conn) sub-nodes (repeat (inc nesting-depth))))])

(defn task-node-component
  [state-conn
   {:keys [:db/id
           primary-sub-node sub-nodes 
           task-value on-task-toggled]}
   nesting-depth]
  [:> rn/Pressable {:key      (str (random-uuid))
                    ;; :style (node-style nesting-depth)
                    :on-press #(ds/transact! state-conn [[:db.fn/call toggle-state id :show-children]])}
   [:> rn/Text {:style (node-style nesting-depth)} (str "Task Value: " task-value)]
   (divider)
   (when (and (not-empty sub-nodes) (get-component-state state-conn id :show-children))
     (map render-node (repeat state-conn) sub-nodes (repeat (inc nesting-depth))))])

(defn tracker-node-component
  [state-conn
   {:keys [:db/id
           primary-sub-node sub-nodes
           tracker-value tracker-max-value
           on-tracker-increase on-tracker-decrease]}
   nesting-depth]
  [:> rn/Pressable {:key      (str (random-uuid))
                    ;; :style (node-style nesting-depth)
                    :on-press #(ds/transact! state-conn [[:db.fn/call toggle-state id :show-children]])}
   [:> rn/Text {:style (node-style nesting-depth)} (str "Tracker Value: " tracker-value)]
   (divider)
   (when (and (not-empty sub-nodes) (get-component-state state-conn id :show-children))
     (map render-node (repeat state-conn) sub-nodes (repeat (inc nesting-depth))))])

(defn determine-node-function
  [{:keys [text-value start-time task-value tracker-value] :as node}]
  (cond
    (some? text-value)    text-node-component
    (some? start-time)    time-node-component
    (some? task-value)    task-node-component
    (some? tracker-value) tracker-node-component
    :else                 #(println "Unknown node:" %2)))

(defn render-node
  [state-conn node-data nesting-depth]
  (let [node-fn (determine-node-function node-data)]
    (node-fn state-conn node-data nesting-depth)))

(defn example-group-component
  [db-conn state-conn]
  (let [ex-data (ffirst (ds/q '[:find (pull ?e [*])
                                :where [?e :text-value "ProductiviT"]]
                              @db-conn))]
    [:> rn/View {:style {:width "100%"}}
     ;; [:> rn/Text (:text-value ex-data)]
     ;; [:> rn/Text (str (:primary-sub-node ex-data))]
     ;; (doall (map raw-text-node (:sub-nodes ex-data)))
     (render-node state-conn ex-data 0)]))

(defn root [db-conn state-conn]
  (let [main-nav nil #_(when (not (nil?
  conn)) (navigation/get-main-nav-state conn))]
    (case main-nav
      (r/as-element [:> rn/View {:style {:width "100%" :background :gray}}
                     (example-group-component db-conn state-conn)]))))

(defn ^:dev/after-load render
  [db-conn state-conn]
  (profile "render"
    (expo-root/render-root (r/as-element (root app-db-conn app-state-conn)))))

;; re-render on every DB change
(ds/listen! app-db-conn
            (fn [tx-report]
              ;; (println "app db listen triggered")
              ;; (println tx-report)
              ;; (println app-state-conn)
              (render (r/atom (:db-after tx-report)) app-state-conn)))

(ds/listen! app-state-conn
            (fn [tx-report]
              ;; (println "app state listen triggered")
              ;; (println app-db-conn)
              ;; (println tx-report)
              (render app-db-conn (r/atom (:db-after tx-report)))))

(defn ^:export init []
  (init/initialize-app-db app-db-conn)
  (init/initialize-app-state app-state-conn)
  (render app-db-conn app-state-conn))
