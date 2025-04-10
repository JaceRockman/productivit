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
   [ui.text-node :refer [text-node-component]]
   [ui.time-node :refer [time-node-component]]
   [ui.task-node :refer [task-node-component]]
   [ui.tracker-node :refer [tracker-node-component]]
   [data.queries :as queries]
   ["@expo/vector-icons" :refer [FontAwesome5]])
  (:require-macros
   [macros :refer [profile]]))

(def app-db-conn (ds/create-conn data/schema))
(def app-state-conn (ds/create-conn {}))

(defn determine-node-function
  [{:keys [text-value start-time task-value tracker-value] :as node}]
  (cond
    (some? text-value)    text-node-component
    (some? start-time)    time-node-component
    (some? task-value)    task-node-component
    (some? tracker-value) tracker-node-component
    :else                 #(println "Unknown node:" %2)))

(defn render-node
  [state-conn db-conn node-data nesting-depth]
  (let [node-fn (determine-node-function node-data)
        sub-node-data (:sub-nodes node-data)
        child-nodes (when (some? sub-node-data)
                      (doall (map #(render-node state-conn db-conn % (inc nesting-depth)) sub-node-data)))]
    (node-fn state-conn db-conn node-data nesting-depth child-nodes)))

(defn example-group-component
  [db-conn state-conn]
  (let [ex-data (ffirst (ds/q '[:find (pull ?e [*])
                                :where [?e :text-value "ProductiviT"]]
                              @db-conn))
        ;; Might use this in the future to determine the view height so the + button stays with the content
        displayed-nodes (queries/calculate-displayed-nodes app-db-conn app-state-conn)]
    [:> rn/View {:style {:height "100vh" :width "100vh" :background :gray :flex 1}}
     [:> rn/ScrollView {:style {:flex 1 :max-height "95vh"}}
      (render-node state-conn db-conn ex-data 0)]
     (node/create-node-button state-conn db-conn)]))

(defn root [db-conn state-conn]
  (let [main-nav nil #_(when (not (nil?
  conn)) (navigation/get-main-nav-state conn))]
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
