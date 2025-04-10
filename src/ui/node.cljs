(ns ui.node
    (:require ["react-native" :as rn]
              [datascript.core :as ds]
              ["@expo/vector-icons" :refer [FontAwesome5]]))

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

(defn calculate-height [node-data]
  (letfn [(count-subnodes [sub-nodes]
             (reduce + (map #(if (:sub-nodes %) (+ 1 (count-subnodes (:sub-nodes %))) 1) sub-nodes)))]
    (if (not-empty (:sub-nodes node-data))
      (* 42 (count-subnodes (:sub-nodes node-data)))
      0)))

(defn get-height-val
  [state-conn entity-id]
  (get-component-state state-conn entity-id :height-val))

(defn update-height-val
  [state-conn entity-id new-height]
  (ds/transact! state-conn [{:db/id entity-id :height-val new-height}]))

(defn update-component-state
  [state-conn entity-id attribute value]
  (ds/transact! state-conn [{:db/id entity-id attribute value}]))

(defn toggle 
    [state-conn node-data]
    (fn []
        (let [entity-id (:db/id node-data)
              new-expanded (not (get-component-state state-conn entity-id :show-children))
              _ (update-component-state state-conn entity-id :show-children new-expanded)
              to-value (if new-expanded
                        (calculate-height node-data)
                        0)]
                  
                  (let [animation (.timing rn/Animated 
                                          (get-height-val state-conn entity-id) 
                                          #js {:toValue to-value
                                              :duration 300
                                              :useNativeDriver false})]
                    (.start animation (fn [finished]
                                        (println "Animation finished:" finished)))))))

(defn ActionMenu [node-data]
  [:> rn/View 
    [:> rn/View {:style {:background-color :white :flex-direction :row :height 40 :gap 20 :justify-content :center :align-items :center}}
        [:> rn/Pressable {:on-press #(println "Duplicated")}
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

(defn initialize-node-state
  [state-conn node-data]
  (let [entity-id (:db/id node-data)
        is-expanded (get-component-state state-conn entity-id :show-children)
        initial-height (if is-expanded (calculate-height node-data) 0)]
    (when (nil? (get-component-state state-conn entity-id :show-children))
        (update-component-state state-conn entity-id :show-children true))

    (when (nil? (get-height-val state-conn entity-id))
            (let [is-expanded (get-component-state state-conn entity-id :show-children)
                  initial-height (if is-expanded (calculate-height node-data) 0)]

              (update-height-val state-conn entity-id (new (.-Value rn/Animated) initial-height))))))

(defn animated-node
  [state-conn db-conn render-content node-data nesting-depth child-nodes]
  (let [entity-id (:db/id node-data)
        _ (initialize-node-state state-conn node-data)]
    
    [:> rn/View
     [:> rn/Pressable {:on-press (toggle state-conn node-data)
                      :on-long-press (toggle-menu state-conn db-conn node-data)}
      (render-content state-conn node-data nesting-depth)]
     (divider)
     (when (get-component-state state-conn entity-id :show-menu)
       (ActionMenu node-data))
     [:> rn/Animated.View 
      {:style {:overflow "hidden"
               :height (get-height-val state-conn entity-id)}}
      ;; Always render sub-nodes, but they'll be hidden when height is 0
      child-nodes]]))