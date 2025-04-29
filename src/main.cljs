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
(def app-state-conn (ds/create-conn data/schema))

(defn determine-node-type
  [db-conn db-ref]
  (let [{:keys [text-value start-time task-value tracker-value] :as node}
        (ds/pull @db-conn
                 '[:db/id :text-value :start-time :task-value :tracker-value] db-ref)]
    (cond
      (some? text-value)    text-node-content
      (some? start-time)    time-node-content
      (some? task-value)    task-node-content
      (some? tracker-value) tracker-node-content
      :else                 #(println "Unknown node:" %2))))

(defn render-node
  [db-conn state-conn state-node-id nesting-depth]
  (let [{:keys [db-ref sub-nodes] :as state-data}
        (ds/pull @state-conn '[*] state-node-id)

        render-fn (determine-node-type db-conn db-ref)
        sub-node-ids (map :db/id sub-nodes)
        sub-nodes
        (when (some? sub-node-ids)
          (doall (map #(render-node db-conn
                                    state-conn
                                    %
                                    (inc nesting-depth))
                      sub-node-ids)))]
    (node/default-node db-conn state-conn render-fn state-data nesting-depth sub-nodes)))

(defn home-component
  [db-conn state-conn]
  (let [top-level-state-ids (queries/find-top-level-node-ids state-conn)

        ;; Might use this in the future to determine the view height so the + button stays with the content
        ;; displayed-nodes (queries/recursively-get-displayed-sub-node-ids
        ;;                   app-db-conn
        ;;                   app-state-conn
        ;;                   (queries/find-top-level-node-ids db-conn))
        ]
    [:> rn/View {:style {:height "100vh" :width "100vh"
                         :background :gray :flex 1}}
     [:> rn/ScrollView {:style {:flex 1 :max-height "95vh"}}
      (map #(render-node db-conn state-conn % 0) top-level-state-ids)]
     (node/create-node-button state-conn db-conn)]))

(defn root [db-conn state-conn]
  (let [main-nav nil #_(when (not (nil? conn)) (navigation/get-main-nav-state conn))]
    (case main-nav
      (r/as-element (home-component db-conn state-conn)))))

(defn ^:dev/after-load render
  [db-conn state-conn]
  (profile "render"
           (expo-root/render-root (r/as-element (root app-db-conn app-state-conn)))))

;; re-render on every DB change
(defn start-db-listen
  []
  (ds/listen! app-db-conn
              (fn [tx-report]
                (render (r/atom (:db-after tx-report)) app-state-conn))))

;; re-render on every state change
(defn start-state-listen
  []
  (ds/listen! app-state-conn
              (fn [tx-report]
                (render app-db-conn (r/atom (:db-after tx-report))))))

(defn ^:export init []
  (init/initialize-db-and-state app-db-conn app-state-conn)
  (start-db-listen)
  (start-state-listen)
  (render app-db-conn app-state-conn))
