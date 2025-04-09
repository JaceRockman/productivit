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
   [cljs-time.core :as t])
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
  [state-conn entity-id attribute]
  (let [[current-value] (first (ds/q '[:find ?v
                                     :in $ ?e ?a
                                     :where [?e ?a ?v]]
                                     state-conn entity-id attribute))]
    (if (not (nil? current-value))
      [{:db/id entity-id attribute (not current-value)}]
      [{:db/id entity-id attribute true}])))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(defn calculate-height [node-data]
  (letfn [(count-subnodes [sub-nodes]
             (reduce + (map #(if (:sub-nodes %) (+ 1 (count-subnodes (:sub-nodes %))) 1) sub-nodes)))]
    (if (not-empty (:sub-nodes node-data))
      (* 40 (count-subnodes (:sub-nodes node-data)))
      0)))

(def animated-height-atom (r/atom nil))

(defn test-animated-node
  [state-conn node-data nesting-depth]
  [:> rn/View
   [:> rn/Pressable {:on-press #(println "Pressed")}
    [:> rn/Text "Test"]]])

(def component-state (r/atom {:height-val nil
                              :is-expanded nil}))

(defn new-animated-node
  [state-conn render-content node-data nesting-depth]
  (let [;; Create component-local state
        entity-id (:db/id node-data)

        get-height-val (fn []
                         (get-component-state state-conn entity-id :height-val))
        
        update-height-val (fn [new-height]
                            (ds/transact! state-conn [{:db/id entity-id :height-val new-height}]))
        
        update-component-state (fn [state-conn entity-id attribute value]
                                 (ds/transact! state-conn [{:db/id entity-id attribute value}]))

        ;; Initialize once on first render
        _ (when (nil? (get-height-val))
            (let [is-expanded (get-component-state state-conn entity-id :show-children)
                  initial-height (if is-expanded (calculate-height node-data) 0)]

              (update-height-val (new (.-Value rn/Animated) initial-height))))
        
        ;; Toggle function
        toggle (fn []
                (let [new-expanded (not (get-component-state state-conn entity-id :show-children))
                      _ (update-component-state state-conn entity-id :show-children new-expanded)
                      to-value (if new-expanded
                                 (calculate-height node-data)
                                 0)]
                                    
                  (println "Starting animation from" (.-value (get-height-val)) "to" to-value "over 300ms")
                  
                  ;; Direct JavaScript interop approach
                  (let [animation (.timing rn/Animated 
                                          (get-height-val) 
                                          #js {:toValue to-value
                                              :duration 300
                                              :useNativeDriver false})]
                    (.start animation (fn [finished]
                                        (println "Animation finished:" finished))))))]
    
    [:> rn/View
     [:> rn/Pressable {:on-press toggle}
      (render-content state-conn node-data nesting-depth)]
     (divider)
     [:> rn/Animated.View 
      {:style {:overflow "hidden"
               :height (get-height-val)}}
      ;; Always render sub-nodes, but they'll be hidden when height is 0
      (when (not-empty (:sub-nodes node-data))
        (doall (map render-node 
                    (repeat state-conn) 
                    (:sub-nodes node-data) 
                    (repeat (inc nesting-depth)))))]]))

(defn text-node-component
  [state-conn
   {:keys [db/id primary-sub-node sub-nodes text-value] :as node-data}
   nesting-depth]
  (new-animated-node
    state-conn
    (fn [state-conn node-data nesting-depth]
      [:> rn/Text {:key id :style (node-style nesting-depth)} (:text-value node-data)])
    node-data nesting-depth))

(def standard-time-format
  (t-format/formatter "MMM' 'dd', 'YYYY"))

(defn time-node-component
  [state-conn
   {:keys [:db/id primary-sub-node sub-nodes start-time end-time on-time-start on-time-end] :as node-data}
   nesting-depth]
  (new-animated-node
    state-conn
    (fn [state-conn node-data nesting-depth]
      [:> rn/Text {:key id :style (node-style nesting-depth)} (str "Time Value: "
                                                          (t-format/unparse standard-time-format (:start-time node-data)))])
    node-data nesting-depth))

(defn task-node-component
  [state-conn
   {:keys [:db/id primary-sub-node sub-nodes task-value on-task-toggled] :as node-data}
   nesting-depth]
  (new-animated-node
    state-conn
    (fn [state-conn node-data nesting-depth]
      [:> rn/Switch {:key id
                     :style (merge (node-style nesting-depth) 
                                    {:transform [{:scale 0.8}]})
                     :on-value-change #(ds/transact! state-conn [[:db.fn/call toggle-state (:db/id node-data) :task-value]])
                     :value (:task-value (ds/entity @state-conn (:db/id node-data)))
                     :trackColor {:false "#ffcccc"
                                  :true  "#ccffcc"}
                     :thumbColor (if (:task-value (ds/entity @state-conn (:db/id node-data))) "#66ff66" "#ff6666")}])
    node-data nesting-depth))

(defn inc-dec
  [state-conn entity-id attribute function]
  (let [{:keys [tracker-value tracker-max-value tracker-min-value]} (ds/entity state-conn entity-id)]
    (if (not (nil? tracker-value))
      (let [raw-new-value (function tracker-value)
            new-value (cond
                        (and tracker-max-value (< tracker-max-value raw-new-value)) tracker-max-value
                        (and tracker-min-value (> tracker-min-value raw-new-value)) tracker-min-value
                        :else raw-new-value)]
        [{:db/id entity-id attribute new-value}])
      [{:db/id entity-id attribute (function 0)}])))

(defn inc-dec-tracker-component
  [{:keys [state-conn tracker-id plus-or-minus inc-or-dec-amount]}]
  (let [inc-or-dec-fn #(plus-or-minus % inc-or-dec-amount)]
    [:> rn/Pressable {:style {:height 30 :width 30 :background :white :border-radius 2
                              :align-items :center :justify-content :center}
                      :on-press #(ds/transact! state-conn [[:db.fn/call inc-dec
                                                            tracker-id
                                                            :tracker-value
                                                            inc-or-dec-fn]])}
     [:> rn/Text (str (if (= plus-or-minus +) "+" "-") inc-or-dec-amount)]]))

(defn tracker-node-component
  [state-conn
   {:keys [:db/id
           primary-sub-node sub-nodes
           tracker-value tracker-max-value
           on-tracker-increase on-tracker-decrease] :as node-data}
   nesting-depth]
    (new-animated-node
      state-conn
      (fn [state-conn node-data nesting-depth]
        [:> rn/View {:key (:db/id node-data) :style (merge (node-style nesting-depth) {:flex-direction :row :gap 5 :align-items :center})}
        (inc-dec-tracker-component {:state-conn app-db-conn :tracker-id (:db/id node-data) :plus-or-minus - :inc-or-dec-amount 5})
        (inc-dec-tracker-component {:state-conn app-db-conn :tracker-id (:db/id node-data) :plus-or-minus - :inc-or-dec-amount 1})
       [:> rn/Text {:style {:font-size 24}} (:tracker-value (ds/entity @app-db-conn (:db/id node-data)))]
       (inc-dec-tracker-component {:state-conn app-db-conn :tracker-id (:db/id node-data) :plus-or-minus + :inc-or-dec-amount 1})
       (inc-dec-tracker-component {:state-conn app-db-conn :tracker-id (:db/id node-data) :plus-or-minus + :inc-or-dec-amount 5})])
    node-data nesting-depth))

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
