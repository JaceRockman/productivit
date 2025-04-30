(ns ui.node
  (:require ["react-native" :as rn]
            [datascript.core :as ds]
            ["@expo/vector-icons" :refer [FontAwesome5]]
            [data.queries :as queries]
            [data.init :as init]
            [ui.action-menu-ui :refer [ActionMenu]]
            [ui.shared :refer [divider]]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(defn get-component-state
  ([ds-conn entity-id]
   (ds/pull ds-conn '[*] entity-id))
  ([ds-conn entity-id attribute]
   (ffirst
    (ds/q '[:find ?v
            :in $ ?e ?a
            :where [?e ?a ?v]]
          @ds-conn entity-id attribute))))

(defn toggle-state
  [ds-conn entity-id attribute]
  (let [[current-value] (first (ds/q '[:find ?v
                                       :in $ ?e ?a
                                       :where [?e ?a ?v]]
                                     ds-conn entity-id attribute))]
    (if (not (nil? current-value))
      [{:db/id entity-id attribute (not current-value)}]
      [{:db/id entity-id attribute true}])))

(defn get-height-atom
  [ds-conn entity-id]
  (get-component-state ds-conn entity-id :height-val))

(defn update-height-atom
  [ds-conn entity-id new-height]
  (ds/transact! ds-conn [{:db/id entity-id :height-val new-height}]))

(defn update-component-state
  [ds-conn entity-id attribute value]
  (ds/transact! ds-conn [{:db/id entity-id attribute value}]))

(defn initialize-show-children!
  [ds-conn {:keys [db/id] :as node-data}]
  (update-component-state ds-conn id :show-children true))

(defn initialize-height-atom!
  [ds-conn {:keys [db/id] :as node-data}]
  (let [initial-height (queries/derive-node-height node-data)]
    (update-height-atom ds-conn id (new (.-Value rn/Animated) initial-height))))

(defn update-parent-height
  [ds-conn {:keys [db/id height-val] :as parent-node-data}]
  (let [to-value (queries/derive-node-height parent-node-data)
        animation (.timing rn/Animated
                           height-val
                           #js {:toValue to-value
                                :duration 300
                                :useNativeDriver true})
        next-parent-node (queries/get-state-node-parent ds-conn id)]

    (when next-parent-node
      (update-parent-height ds-conn next-parent-node))

    (.start animation (fn [finished]
                        (println "Animation finished:" finished)))))

(defn toggle
  [ds-conn {:keys [db/id show-children height-val] :as node-data}]
  (fn []
    (let [new-expanded (not show-children)
          _ (update-component-state ds-conn id :show-children new-expanded)
          new-node-data (get-component-state @ds-conn id)
          to-value (queries/derive-node-height new-node-data)
          animation (.timing rn/Animated
                             height-val
                             #js {:toValue to-value
                                  :duration 300
                                  :useNativeDriver true})
          parent-node (queries/get-state-node-parent ds-conn id)]

      (when parent-node
        (update-parent-height ds-conn parent-node))

      (.start animation (fn [finished]
                          (println "Animation finished:" finished))))))

(defn toggle-menu
  [ds-conn {:keys [db/id height-val show-menu] :as node-data}]
  (fn []
    (let [new-show-menu (not show-menu)
          _ (update-component-state ds-conn id :show-menu new-show-menu)
          _ (if new-show-menu (update-component-state ds-conn id :show-children true))
          new-node-data (get-component-state @ds-conn id)
          to-value (queries/derive-node-height new-node-data)
          animation (.timing rn/Animated
                             height-val
                             #js {:toValue to-value
                                  :duration 300
                                  :useNativeDriver true})
          parent-node (queries/get-state-node-parent ds-conn id)]

      (when parent-node
        (update-parent-height ds-conn parent-node))

      (.start animation (fn [finished]
                          (println "Animation finished:" finished))))))

(defn create-node-button
  [ds-conn]
  [:> rn/Pressable {:style {:align-items :center
                            :justify-content :center
                            :height "5vh"
                            :width "100%"}
                    :on-press #(println "Create New Top Level Node")}
   [:> FontAwesome5 {:name "plus" :size 20 :color :black}]])

(defn initialize-node-states!
  [ds-conn {:keys [show-children height-val] :as node-data}]
  (when (nil? show-children)
    (initialize-show-children! ds-conn node-data))
  (when (nil? height-val)
    (initialize-height-atom! ds-conn node-data)))

(defn default-node
  [ds-conn {:keys [db/id sub-nodes show-children height-val] :as node-data} render-content nesting-depth rendered-sub-nodes]
  (initialize-node-states! ds-conn node-data)
  [:> rn/Animated.View
   {:style {:overflow "hidden"
            :height height-val}}
   [:> rn/Pressable {:on-press (toggle ds-conn node-data)
                     :on-long-press (toggle-menu ds-conn node-data)}
    (render-content ds-conn node-data nesting-depth)]
   [:> rn/Text {:style {:position :absolute :top 0 :right 0}} (str "state-id: " (:db/id node-data))]
   (divider)
   (when (get-component-state ds-conn (:db/id node-data) :show-menu)
     (ActionMenu ds-conn node-data))
   rendered-sub-nodes])