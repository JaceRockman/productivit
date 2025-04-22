(ns ui.node
    (:require ["react-native" :as rn]
              [datascript.core :as ds]
              ["@expo/vector-icons" :refer [FontAwesome5]]
              [data.queries :as queries]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

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

(defn calculate-height
  [node-data state-conn]
  (letfn [(count-subnodes [sub-nodes]
            (reduce + (map #(if (and (:sub-nodes %) (get-component-state state-conn (:db/id %) :show-children))
                                (inc (count-subnodes (:sub-nodes %)))
                                1)
                            sub-nodes)))]
    (println "count-subnodes" (count-subnodes (:sub-nodes node-data)))
    (if (not-empty (:sub-nodes node-data))
      (+ 42 (* 42 (count-subnodes (:sub-nodes node-data))))
      42)))

(defn get-height-atom
  [state-conn entity-id]
  (get-component-state state-conn entity-id :height-val))

(defn update-height-atom
  [state-conn entity-id new-height]
  (ds/transact! state-conn [{:db/id entity-id :height-val new-height}]))

(defn update-component-state
  [state-conn entity-id attribute value]
  (ds/transact! state-conn [{:db/id entity-id attribute value}]))

(defn update-parent-height-atom
  [db-conn state-conn entity-id]
  (let [to-value (* 42 (count (queries/recursively-get-displayed-sub-node-ids db-conn state-conn entity-id)))
        animation (.timing rn/Animated
                    (get-height-atom state-conn entity-id) 
                    #js {:toValue to-value
                         :duration 300
                         :useNativeDriver true})]
        (.start animation (fn [finished]
                              (println "Animation finished:" finished)))))

(defn toggle 
    [db-conn state-conn node-data]
    (fn []
        (let [entity-id (:db/id node-data)
              new-expanded (not (get-component-state state-conn entity-id :show-children))
              _ (update-component-state state-conn entity-id :show-children new-expanded)
              to-value (if new-expanded
                        (calculate-height node-data state-conn)
                        42)
              animation (.timing rn/Animated 
                          (get-height-atom state-conn entity-id) 
                          #js {:toValue to-value
                               :duration 300
                               :useNativeDriver true})]

             (doall (for [parent-node (queries/get-parent-nodes db-conn entity-id)]
                         (update-parent-height-atom db-conn state-conn parent-node)))
             
             (.start animation (fn [finished]
                                   (println "Animation finished:" finished))))))

(defn duplicate-node 
  [db-conn node-data]
  (let [entity-id (:db/id node-data)
        new-entity-id (ds/tempid :db/id)
        new-entity-data (assoc node-data :db/id new-entity-id)
        parent-nodes (queries/get-parent-nodes db-conn entity-id)]
    (ds/transact! db-conn [new-entity-data])))

(defn ActionMenu
  [db-conn node-data]
  [:> rn/View 
    [:> rn/View {:style {:background-color :white :flex-direction :row :height 40 :gap 20 :justify-content :center :align-items :center}}
        [:> rn/Pressable {:on-press #(duplicate-node db-conn node-data)}
            [:> FontAwesome5 {:name "copy" :size 20 :color :black}]]
        [:> rn/Pressable {:on-press #(println "Edited")}
            [:> FontAwesome5 {:name "edit" :size 20 :color :black}]]
        [:> rn/Pressable {:on-press #(println "Deleted")}
            [:> FontAwesome5 {:name "trash" :size 20 :color :black}]]
        [:> rn/Pressable {:on-press #(println "Created Subnode")}
            [:> FontAwesome5 {:name "plus" :size 20 :color :black}]]]
   (divider)])

(defn toggle-menu
  [state-conn db-conn node-data]
  (fn []
    (let [entity-id (:db/id node-data)
          new-show-menu (not (get-component-state state-conn entity-id :show-menu))]
      (ds/transact! state-conn [{:db/id entity-id :show-menu new-show-menu}]))))

(defn create-node-button
  [state-conn db-conn]
  [:> rn/Pressable {:style {:align-items :center
                            :justify-content :center
                            :height "5vh"
                            :width "100%"}
                    :on-press #(println "Create New Top Level Node")}
   [:> FontAwesome5 {:name "plus" :size 20 :color :black}]])

(defn initialize-show-children
  [state-conn entity-id]
  (println (get-component-state state-conn entity-id :show-children))
  (when (nil? (get-component-state state-conn entity-id :show-children))
    (update-component-state state-conn entity-id :show-children true)))

(defn initialize-height-atom
  [node-data state-conn entity-id]
  (when (nil? (get-height-atom state-conn entity-id))
    (let [is-expanded (get-component-state state-conn entity-id :show-children)
          initial-height (if is-expanded
                          (calculate-height node-data state-conn)
                          0)]
      (update-height-atom state-conn entity-id (new (.-Value rn/Animated) initial-height)))))

(defn initialize-node-states
  [state-conn node-data]
  (let [entity-ids (first (ds/q '[:find ?e
                                  :in $ ?db-id
                                  :where [?e :id-ref ?db-id]]
                              @state-conn (:db/id node-data)))]

    (doall (map #(initialize-show-children state-conn %) entity-ids))
    (doall (map #(initialize-height-atom node-data state-conn %) entity-ids))))

(defn default-node
  [state-conn db-conn render-content node-data nesting-depth child-nodes]
  (let [entity-id (:db/id node-data)
        _ (initialize-node-states state-conn node-data)]
    
    [:> rn/Animated.View
     {:style {:overflow "hidden"
              :height (get-height-atom state-conn entity-id)}}
     [:> rn/Pressable {:on-press (toggle db-conn state-conn node-data)
                       :on-long-press (toggle-menu state-conn db-conn node-data)}
      (render-content db-conn state-conn node-data nesting-depth)]
     (divider)
     (when (get-component-state state-conn entity-id :show-menu)
       (ActionMenu db-conn node-data))
     child-nodes]))