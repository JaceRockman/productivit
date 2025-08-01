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


(defn update-component-state
  [ds-conn entity-id attribute value]
  (ds/transact! ds-conn [{:db/id entity-id attribute value}]))

(defn update-parent-height
  [ds-conn {:keys [db/id group-height] :as parent-node-data}]
  (let [to-value (queries/get-node-group-height ds-conn (:db/id parent-node-data))
        animation (.timing rn/Animated
                           group-height
                           #js {:toValue to-value
                                :duration 300
                                :useNativeDriver true})
        next-parent-node (queries/get-node-parent ds-conn id)]

    (when next-parent-node
      (update-parent-height ds-conn next-parent-node))

    (.start animation (fn [finished]
                        (println "Animation finished:" finished)))))

(defn toggle-expand
  [ds-conn {:keys [db/id show-children group-height] :as node-data}]
  (fn []
    (let [new-expanded (not show-children)
          _ (update-component-state ds-conn id :show-children new-expanded)
          to-value (queries/get-node-group-height ds-conn id)
          animation (.timing rn/Animated
                             group-height
                             #js {:toValue to-value
                                  :duration 300
                                  :useNativeDriver true})
          parent-node (queries/get-node-parent ds-conn id)]

      (when parent-node
        (update-parent-height ds-conn parent-node))

      (.start animation (fn [finished]
                          (println "Animation finished:" finished))))))

(defn toggle-menu
  [ds-conn {:keys [db/id group-height show-menu] :as node-data}]
  (fn []
    (let [new-show-menu (not show-menu)
          _ (update-component-state ds-conn id :show-menu new-show-menu)
          _ (if new-show-menu (do (update-component-state ds-conn id :show-children true)
                                  (update-component-state ds-conn id :item-height 84))
                (update-component-state ds-conn id :item-height 42))
          to-value (queries/get-node-group-height ds-conn id)
          animation (.timing rn/Animated
                             group-height
                             #js {:toValue to-value
                                  :duration 300
                                  :useNativeDriver true})
          parent-node (queries/get-node-parent ds-conn id)]

      (when parent-node
        (update-parent-height ds-conn parent-node))

      (.start animation (fn [finished]
                          (println "Animation finished:" finished))))))

(defn create-node-button
  [ds-conn]
  [:> rn/Pressable {:style {:align-items :center
                            :justify-content :center
                            :height "5vh"
                            :width "100vw"}
                    :on-press #(queries/open-node-creation-modal ds-conn nil)}
   [:> FontAwesome5 {:name "plus" :size 20 :color :black}]])

(defn initialize-node-states!
  [ds-conn {:keys [show-children item-height group-height] :as node-data}]
  (when (nil? show-children)
    (init/initialize-show-children! ds-conn node-data))
  #_(when (or (nil? item-height) (nil? group-height))
      (init/initialize-heights! ds-conn node-data)))

(defn expand-collapse-button
  [ds-conn {:keys [db/id show-children] :as node-data}]
  [:> rn/Pressable {:style {:padding-right 10}
                    :on-press (toggle-expand ds-conn node-data)}
   (if show-children
     [:> FontAwesome5 {:name "chevron-up" :size 16 :color :black}]
     [:> FontAwesome5 {:name "chevron-down" :size 16 :color :black}])])

(defn sub-node
  [ds-conn {:keys [db/id sub-nodes show-children group-height] :as node-data} render-content nesting-depth rendered-sub-nodes]
  (initialize-node-states! ds-conn node-data)
  [:> rn/Animated.View
   {:style {:overflow "hidden"
            :height group-height}}
   [:> rn/Pressable {:style {:flex-direction :row :align-items :center}
                     :on-press #(queries/select-node ds-conn id)
                     :on-long-press #(queries/select-node ds-conn id)}
    (render-content ds-conn node-data nesting-depth)
    (when (not-empty sub-nodes)
      [expand-collapse-button ds-conn node-data])]
   (divider)
   rendered-sub-nodes])

(defn selected-node
  [ds-conn {:keys [db/id sub-nodes show-children group-height] :as node-data} render-content]
  (let [parent-id (:db/id (queries/get-node-parent ds-conn id))]
    [:> rn/Pressable {:style {:overflow "hidden"
                              :height "5vh"
                              :background-color :lightgray}
                      :on-press #(queries/select-node ds-conn parent-id)}
     (render-content ds-conn node-data)]))

(defn selection-node
  [ds-conn node-data render-content]
  [:> rn/Pressable {:on-press #(queries/select-node ds-conn (:db/id node-data))
                    :on-long-press (toggle-menu ds-conn node-data)}
   (render-content ds-conn node-data 0 {:style-override {:justify-content :center :text-align :center}})
   (divider)])