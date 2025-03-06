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

(defn example-group-component
  [conn]
  (let [ex-data (ffirst (ds/q '[:find (pull ?e [*])
                                           :where [?e :text/value "ProductiviT"]]
                                         @conn))]
    (println ex-data)
    [:> rn/View
     [:> rn/Text (:text/value ex-data)]
     [:> rn/Text (str (:primary-sub-node ex-data))]
     (doall (map raw-text-node (:sub-nodes ex-data)))]))

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
