(ns ui.node
  (:require ["react-native" :as rn]
            [datascript.core :as ds]
            ["@expo/vector-icons" :refer [FontAwesome5]]
            [data.queries :as queries]
            [init :as init]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(defn divider
  []
  [:> rn/View {:style {:background :black :width "100%" :height 2}}])

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

(defn template->db-data
  [template-data]
  (let [new-entity-id (ds/tempid :db/id)
        new-sub-nodes (mapv template->db-data (:sub-nodes template-data))]
    (assoc template-data
           :db/id new-entity-id
           :entity-type "node"
           :sub-nodes new-sub-nodes)))

(defn instantiate-template
  [ds-conn template-data]
  (let [db-data (template->db-data template-data)
        db-txn-result (ds/transact! ds-conn [db-data])
        db-node-id (get (:tempids db-txn-result) (:db/id db-data))
        transacted-db-data (ds/pull @ds-conn '[*] db-node-id)
        new-state-data (init/node->init-state [transacted-db-data])
        result (ds/transact! ds-conn [new-state-data])]
    {:txn-result result
     :db-node-id (get (:tempids result) (:db/id new-state-data))}))

(defn db-entity->template
  [node-data]
  (let [new-entity-id (ds/tempid :db/id)
        new-sub-nodes (mapv db-entity->template (:sub-nodes node-data))]
    (assoc node-data
           :db/id new-entity-id
           :entity-type "template"
           :sub-nodes new-sub-nodes)))

(defn save-as-template
  [ds-conn node-data]
  (let [template-data (db-entity->template node-data)
        result (ds/transact! ds-conn [template-data])]
    {:txn-result result
     :template-id (get (:tempids result) (:db/id template-data))}))

(defn delete-template
  [ds-conn template-id]
  (ds/transact! ds-conn [[:db.fn/retractEntity template-id]]))

(defn duplicate-node
  [ds-conn {:keys [db/id] :as node-data}]
  (let [{:keys [txn-result template-id] :as template-result}
        (save-as-template ds-conn node-data)
        ;; _ (println "template-result" template-result)

        template-data (ds/pull @ds-conn '[*] template-id)
        ;; _ (println "template-data" template-data)

        result (instantiate-template ds-conn template-data)
        ;; _ (println "result" result)

        _ (delete-template ds-conn template-id)]
    result))

;; I don't quite have this working yet so I get some weird behavior when deleting nodes.
(defn branch-node
  [ds-conn {:keys [db/id sub-nodes] :as node-data}]
  (let [sub-node-ids (mapv :db-ref sub-nodes)
        new-db-ref (ds/tempid :db/id)
        new-db-data-txn (ds/transact! ds-conn [{:db/id new-db-ref :entity-type "node" :sub-nodes sub-node-ids}])
        new-db-data-id (get-in new-db-data-txn [:tempids new-db-ref])
        new-db-data (ds/pull @ds-conn '[*] new-db-data-id)
        new-state-data (assoc (init/node->init-state [new-db-data]) :db/id id)]

    (ds/transact! ds-conn [new-state-data])))

;; The default behavior of the delete button is to remove the node from the parent node.
;; This also creates a new instance of the parent node to differentiate it from the original.
(defn delete-node
  [ds-conn {:keys [db/id] :as node-data}]
  (let [parent-node (queries/get-state-node-parent ds-conn id)]
    (queries/remove-from-parent-node ds-conn id)
    (ds/transact! ds-conn [[:db.fn/retractEntity (:db/id node-data)]])))

(defn ActionMenu
  [ds-conn {:keys [db/id] :as node-data}]
  [:> rn/View
   [:> rn/View {:style {:background-color :white :flex-direction :row :height 40 :gap 20 :justify-content :center :align-items :center}}
    [:> rn/Pressable {:on-press #(duplicate-node ds-conn node-data)}
     [:> FontAwesome5 {:name "copy" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(save-as-template ds-conn node-data)}
     [:> FontAwesome5 {:name "stream" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(println "Edited")}
     [:> FontAwesome5 {:name "edit" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(delete-node ds-conn node-data)}
     [:> FontAwesome5 {:name "trash" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(println "Created Subnode")}
     [:> FontAwesome5 {:name "plus" :size 20 :color :black}]]]
   (divider)])

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