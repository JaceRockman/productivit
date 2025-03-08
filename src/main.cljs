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

(def app-conn (ds/create-conn data/schema))

(defn raw-text-node
  [node]
  [:> rn/Text {:key (str (:db/id node))} (str node)])

(declare determine-node-function)
(declare render-node)

(defn text-node-component
  [{:keys [primary-sub-node sub-nodes
           text-value]}
   nesting-depth]
  [:> rn/View {:key   (str (random-uuid))
               :style {:width "100%" :background :gray :margin-left (* 10 nesting-depth)}}
   [:> rn/Text text-value]
   (when sub-nodes
     (map render-node sub-nodes (repeat (inc nesting-depth))))])

(defn time-node-component
  [{:keys [primary-sub-node sub-nodes 
           start-time end-time
           on-time-start on-time-end]}
   nesting-depth]
  [:> rn/View {:key   (str (random-uuid))
               :style {:width "100%" :background :gray :margin-left (* 10 nesting-depth)}}
   [:> rn/Text (str "Time Value: " start-time)]
   (when sub-nodes
     (map render-node sub-nodes (repeat (inc nesting-depth))))])

(defn task-node-component
  [{:keys [primary-sub-node sub-nodes 
           task-value on-task-toggled]}
   nesting-depth]
  [:> rn/View {:key   (str (random-uuid))
               :style {:width "100%" :background :gray :margin-left (* 10 nesting-depth)}}
   [:> rn/Text (str "Task Value: " task-value)]
   (when sub-nodes
     (map render-node sub-nodes (repeat (inc nesting-depth))))])

(defn tracker-node-component
  [{:keys [primary-sub-node sub-nodes
           tracker-value tracker-max-value
           on-tracker-increase on-tracker-decrease]}
   nesting-depth]
  [:> rn/View {:key   (str (random-uuid))
               :style {:width "100%" :background :gray :margin-left (* 10 nesting-depth)}}
   [:> rn/Text (str "Tracker Value: " tracker-value)]
   (when sub-nodes
     (map render-node sub-nodes (repeat (inc nesting-depth))))])

(defn determine-node-function
  [{:keys [text-value start-time task-value tracker-value] :as node}]
  (println node)
  (cond
    (some? text-value)    text-node-component
    (some? start-time)    time-node-component
    (some? task-value)    task-node-component
    (some? tracker-value) tracker-node-component
    :else                 println))

(defn render-node
  [node-data nesting-depth]
  (let [node-fn (determine-node-function node-data)]
    (node-fn node-data nesting-depth)))

(defn example-group-component
  [conn]
  (let [ex-data (ffirst (ds/q '[:find (pull ?e [*])
                                           :where [?e :text-value "ProductiviT"]]
                                         @conn))]
    [:> rn/View
     [:> rn/Text (:text-value ex-data)]
     [:> rn/Text (str (:primary-sub-node ex-data))]
     (doall (map raw-text-node (:sub-nodes ex-data)))
     (render-node ex-data 0)]))

(defn root [conn]
  (let [main-nav nil #_(when (not (nil? conn)) (navigation/get-main-nav-state conn))]
    (case main-nav
      (r/as-element [:> rn/View
                     [:> rn/Text "Nothing is here!"]
                     (example-group-component conn)]))))


(defn render
  [& conn]
  (when (or (empty? conn) (nil? (first conn))) (init/initialize-db app-conn))
  (profile "render"
           (expo-root/render-root (r/as-element [root (or (first conn) app-conn)]))))

;; re-render on every DB change
(ds/listen! app-conn
            (fn [tx-report]
              (render (r/atom (:db-after tx-report)))))

(defn ^:export init []
  (init/initialize-db app-conn)
  (render app-conn))
