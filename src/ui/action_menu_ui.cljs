(ns ui.action-menu-ui
  (:require
   ["react-native" :as rn]
   [reagent.core :as r]
   ["@expo/vector-icons" :refer [FontAwesome5]]
   [data.database :as database]
   [data.init :as init]
   [data.queries :as queries]
   [datascript.core :as ds]
   [ui.shared :refer [divider]]
   [ui.date-time-picker :refer [DateTimePicker]]
   [camel-snake-kebab.core :as csk]))


(defn template->db-data
  [template-data]
  (let [new-entity-id (ds/tempid :db/id)
        new-sub-nodes (mapv template->db-data (:sub-nodes template-data))]
    (assoc template-data
           :db/id new-entity-id
           :entity-type "node"
           :sub-nodes new-sub-nodes)))

(defn instantiate-template
  [ds-conn template-data & {:keys [parent-id]}]
  (println "parent-id" parent-id)
  (let [db-data (template->db-data template-data)
        db-txn-result (ds/transact! ds-conn [db-data])
        db-node-id (get (:tempids db-txn-result) (:db/id db-data))
        transacted-db-data (ds/pull @ds-conn '[*] db-node-id)
        new-state-data (init/node->init-state transacted-db-data)
        result (ds/transact! ds-conn [new-state-data])]
    (when parent-id (ds/transact! ds-conn [{:db/id parent-id :sub-nodes [new-state-data]}]))
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
  (let [parent-id (:db/id (queries/get-node-parent ds-conn id))

        {:keys [txn-result template-id] :as template-result}
        (save-as-template ds-conn node-data)
        ;; _ (println "template-result" template-result)

        template-data (ds/pull @ds-conn '[*] template-id)
        ;; _ (println "template-data" template-data)

        result (instantiate-template ds-conn template-data {:parent-id parent-id})
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
  (let [parent-node (queries/get-node-parent ds-conn id)]
    (ds/transact! ds-conn [[:db.fn/retractEntity (:db/id node-data)]])
    (queries/select-node ds-conn (:db/id parent-node))))



(defn add-node-rule
  [ds-conn {:keys [db/id] :as node-data}])

(def modal-style {:width "80vw" :height "80vh"
                  :background-color :white :opacity 1 :color :black
                  :text-align :center :font-size 20 :font-weight :bold})

(defn edit-text-node
  [ds-conn {:keys [text-value] :as node-data}]
  [:> rn/View {:style modal-style}
   [:> rn/Text "Text node"]
   [:> rn/Text (str "Text value: " text-value)]])


(defn edit-time-node
  [ds-conn {:keys [start-time] :as node-data}]
  [:> rn/View {:style modal-style}
   [:> rn/Text "Time node"]
   [:> rn/Text (str "Start time: " start-time)]
   [:> DateTimePicker {:date-value start-time
                       :on-change (fn [new-date]
                                    (ds/transact! ds-conn [{:db/id (:db/id node-data) :start-time new-date}])
                                    (queries/toggle-modal ds-conn))}]])

(defn edit-task-node
  [ds-conn {:keys [task-value] :as node-data}]
  [:> rn/View {:style modal-style}
   [:> rn/Text "Task node"]
   [:> rn/Text (str "Task value: " task-value)]])


(defn numeric-input-state-change
  [ds-conn {:keys [attribute new-value]}]
  (let [{:keys [tracker-min-value tracker-value tracker-max-value]}
        (queries/get-modal-state ds-conn)]
    (queries/update-modal-state ds-conn attribute new-value)
    (queries/update-modal-state ds-conn
                                :valid-input?
                                (<= tracker-min-value
                                    tracker-value
                                    tracker-max-value))))

(defn clojurize-keys
  [props]
  (let [clj-props (js->clj props)]
    (into {} (map (fn [[k v]] [(csk/->kebab-case-keyword k) (if (map? v) (clojurize-keys v) v)]) clj-props))))

(def NumericInput
  (r/create-class
   {:reagent-render
    (fn [props]
      (let [{:keys [ds-conn label modal-state attribute]} (clojurize-keys props)]
        [:> rn/View {:style {:flex-direction :row :gap 10 :align-items :center}}
         [:> rn/Text label]
         [:> rn/TextInput {:placeholder (get (js->clj modal-state) (keyword attribute))
                           :keyboard-type "numeric"
                           :style {:border-width 1 :border-color :black :padding 5 :border-radius 5 :width 100}
                           :on-change-text (fn [text]
                                             (let [new-value (js/parseInt text)]
                                               (numeric-input-state-change ds-conn {:attribute attribute
                                                                                    :new-value new-value})))}]]))}))

(defn initialize-modal-state
  [ds-conn modal-state-data]
  (let [{:keys [:db/id]} (queries/get-modal-state ds-conn)]
    (ds/transact! ds-conn
                  (map (fn [[k v]] [:db/add id k v]) modal-state-data))))

(defn tracker-node-edit-modal
  [ds-conn node-data]
  (let [txn-values (select-keys node-data [:tracker-min-value :tracker-value :tracker-max-value])
        _ (when (not (or (contains? (queries/get-modal-state ds-conn) :tracker-min-value)
                         (contains? (queries/get-modal-state ds-conn) :tracker-max-value)
                         (contains? (queries/get-modal-state ds-conn) :tracker-value)))
            (initialize-modal-state ds-conn txn-values))
        {:keys [valid-input? tracker-min-value tracker-value tracker-max-value] :as modal-state}
        (queries/get-modal-state ds-conn)]
    [:> rn/View {:style modal-style}
     [:> rn/Text "Tracker node"]
     [:> rn/Pressable {:style {:position :absolute :top 0 :right 0}
                       :disabled (not valid-input?)
                       :on-press #(if valid-input?
                                    (do (ds/transact! ds-conn [{:db/id (:db/id node-data)
                                                                :tracker-min-value tracker-min-value
                                                                :tracker-value tracker-value
                                                                :tracker-max-value tracker-max-value}])
                                        (queries/toggle-modal ds-conn))

                                    (println "Invalid Transaction"
                                             (queries/get-modal-state ds-conn)))}
      [:> rn/Text {:style {:color (if valid-input? :black :red)}} "Save"]]
     [:> NumericInput {:label "Minimum value:"
                       :dsConn ds-conn
                       :modalState modal-state
                       :attribute :tracker-min-value}]
     [:> NumericInput {:label "Tracker value:"
                       :dsConn ds-conn
                       :modalState modal-state
                       :attribute :tracker-value}]
     [:> NumericInput {:label "Maximum value:"
                       :dsConn ds-conn
                       :modalState modal-state
                       :attribute :tracker-max-value}]]))

(defn edit-tracker-node
  [ds-conn node-data]
  (tracker-node-edit-modal ds-conn node-data))

(defn node-edit-window
  [ds-conn {:keys [text-value start-time task-value tracker-value] :as node-data}]
  (cond
    (some? text-value)    (edit-text-node ds-conn node-data)
    (some? start-time)    (edit-time-node ds-conn node-data)
    (some? task-value)    (edit-task-node ds-conn node-data)
    (some? tracker-value) (edit-tracker-node ds-conn node-data)
    :else                 #(println "Unknown node type:" %2)))

(defn ActionMenu
  [ds-conn {:keys [db/id] :as node-data}]
  [:> rn/View
   [:> rn/View {:style {:background-color :white :flex-direction :row :height 40 :gap 20 :justify-content :space-evenly
                        :align-items :center}}
    [:> rn/Pressable {:on-press #(duplicate-node ds-conn node-data)}
     [:> FontAwesome5 {:name "copy" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(save-as-template ds-conn node-data)}
     [:> FontAwesome5 {:name "stream" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(queries/open-node-edit-modal ds-conn node-data)}
     [:> FontAwesome5 {:name "edit" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(delete-node ds-conn node-data)}
     [:> FontAwesome5 {:name "trash" :size 20 :color :black}]]
    [:> rn/Pressable {:on-press #(println "Created Subnode")}
     [:> FontAwesome5 {:name "plus" :size 20 :color :black}]]]])