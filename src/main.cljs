(ns main
  (:require
   [clojure.math :as math]
   [reagent.core :as r]
   ["react-native" :as rn]
   [datascript.core :as ds]
   [expo.root :as expo-root]
   [init :as init]
   [data :as data]
   [cljs-time.format :as t-format]
   [cljs-time.core :as t]
   [ui.node :as node]
   [ui.text-node :refer [text-node-content]]
   [ui.time-node :refer [time-node-content]]
   [ui.task-node :refer [task-node-content]]
   [ui.tracker-node :refer [tracker-node-content]]
   [data.queries :as queries]
   ["@expo/vector-icons" :refer [FontAwesome5]])
  (:require-macros
   [macros :refer [profile]]))

(def app-db-conn (ds/create-conn data/schema))
(def app-state-conn (ds/create-conn {}))

(defn determine-node-content-function
  [{:keys [text-value start-time task-value tracker-value] :as node}]
  (cond
    (some? text-value)    text-node-content
    (some? start-time)    time-node-content
    (some? task-value)    task-node-content
    (some? tracker-value) tracker-node-content
    :else                 #(println "Unknown node:" %2)))

(defn render-node
  [state-conn db-conn node-data nesting-depth]
  (let [node-content-fn (determine-node-content-function node-data)
        sub-node-data (:sub-nodes node-data)
        child-nodes (when (some? sub-node-data)
                      (doall (map #(render-node state-conn db-conn % (inc nesting-depth)) sub-node-data)))]
    (node/default-node state-conn db-conn node-content-fn node-data nesting-depth child-nodes)))

(defn example-group-component
  [db-conn state-conn]
  (let [data (ds/pull-many @db-conn '[*] (queries/find-top-level-node-ids db-conn))
        ;; Might use this in the future to determine the view height so the + button stays with the content
        displayed-nodes (queries/calculate-displayed-nodes app-db-conn app-state-conn)]
    [:> rn/View {:style {:height "100vh" :width "100vh" :background :gray :flex 1}}
     [:> rn/ScrollView {:style {:flex 1 :max-height "95vh"}}
      (map #(render-node state-conn db-conn % 0) data)]
     (node/create-node-button state-conn db-conn)]))

(defn root [db-conn state-conn]
  (let [main-nav nil #_(when (not (nil? conn)) (navigation/get-main-nav-state conn))]
    (case main-nav
      (r/as-element (example-group-component db-conn state-conn)))))

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
