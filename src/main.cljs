(ns main
  (:require
   [clojure.math :as math]
   [reagent.core :as r]
   ["react-native" :as rn]
   [datascript.core :as ds]
   [expo.root :as expo-root]
   [data.init :as init]
   [data.database :as database]
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

(def ds-conn (ds/create-conn database/schema))

(defn determine-node-type
  [{:keys [text-value start-time task-value tracker-value] :as node-data}]
  (cond
    (some? text-value)    text-node-content
    (some? start-time)    time-node-content
    (some? task-value)    task-node-content
    (some? tracker-value) tracker-node-content
    :else                 #(println "Unknown node:" %2)))

(defn render-node
  [ds-conn {:keys [sub-nodes] :as node-data} nesting-depth]
  (let [render-fn (determine-node-type node-data)
        rendered-sub-nodes
        (when (some? sub-nodes)
          (doall (map #(render-node ds-conn
                                    %
                                    (inc nesting-depth))
                      sub-nodes)))]
    (node/default-node ds-conn node-data render-fn nesting-depth rendered-sub-nodes)))

(defn home-component
  [ds-conn]
  (let [top-level-nodes (map first (queries/find-top-level-nodes ds-conn))]
    [:> rn/View {:style {:height "100vh" :width "100vh"
                         :background :gray :flex 1}}
     [:> rn/ScrollView {:style {:flex 1 :max-height "95vh"}}
      (map #(render-node ds-conn % 0) top-level-nodes)]
     (node/create-node-button ds-conn)]))

(defn root [ds-conn]
  (let [main-nav nil #_(when (not (nil? conn)) (navigation/get-main-nav-state conn))]
    (case main-nav
      (r/as-element (home-component ds-conn)))))

(defn ^:dev/after-load render
  [_]
  (profile "render"
           (expo-root/render-root (r/as-element (root ds-conn)))))

;; re-render on every DB change
(defn start-ds-listen
  []
  (ds/listen! ds-conn
              (fn [tx-report]
                (render (r/atom (:db-after tx-report))))))


(defn ^:export init []
  ;; (init/initialize-ds-from-dt ds-conn)
  (init/initialize-example-ds-conn ds-conn)
  (start-ds-listen)
  (render ds-conn))
