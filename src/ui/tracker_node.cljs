(ns ui.tracker-node
  (:require [datascript.core :as ds]
            ["react-native" :as rn]
            [data.queries :as queries]
            [reagent.core :as r]
            [camel-snake-kebab.core :as csk]))

(defn apply-inc-dec
  [_ {:keys [db/id] :as tracker-node-data} attribute inc-or-dec-amount]
  (let [{:keys [tracker-value tracker-max-value tracker-min-value]} tracker-node-data
        raw-new-value (+ (or tracker-value 0) inc-or-dec-amount)
        validated-new-value (cond
                              (when tracker-max-value (< tracker-max-value raw-new-value)) tracker-max-value
                              (when tracker-min-value (> tracker-min-value raw-new-value)) tracker-min-value
                              :else raw-new-value)]
    [{:db/id id attribute validated-new-value}]))

(defn inc-dec-component
  [{:keys [ds-conn tracker-node-data inc-or-dec-amount]}]
  (let [disabled? (let [calculated-value (+ (or (:tracker-value tracker-node-data) 0) inc-or-dec-amount)]
                    (not (apply <= (remove nil? [(:tracker-min-value tracker-node-data)
                                                 calculated-value
                                                 (:tracker-max-value tracker-node-data)]))))]
    [:> rn/Pressable {:style {:height 30 :width 30 :background (if disabled? :lightgray :white) :border-radius 2
                              :align-items :center :justify-content :center}
                      :disabled disabled?
                      :on-press #(ds/transact! ds-conn [[:db.fn/call apply-inc-dec
                                                         tracker-node-data
                                                         :tracker-value
                                                         inc-or-dec-amount]])}
     [:> rn/Text {:style {:color (if disabled? :gray :black)}}
      (str (when (< 0 inc-or-dec-amount) "+") inc-or-dec-amount)]]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(defn tracker-node-content
  [ds-conn
   {:keys [:db/id primary-sub-node sub-nodes
           tracker-value tracker-min-value tracker-max-value increments] :as node-data}
   nesting-depth
   & {:keys [style-override]}]
  (let [increment-vals (filter pos? increments)
        decrement-vals (filter neg? increments)
        inc-dec-component-builder (fn [inc-or-dec-amount]
                                    (inc-dec-component {:ds-conn ds-conn
                                                        :tracker-node-data node-data
                                                        :inc-or-dec-amount inc-or-dec-amount}))
        tracker-value-display (if tracker-max-value (str tracker-value "/" tracker-max-value) tracker-value)]
    [:> rn/View {:key id
                 :style (merge (node-style nesting-depth)
                               {:flex-direction :row :gap 5 :align-items :center}
                               style-override)}
     (map inc-dec-component-builder decrement-vals)
     [:> rn/Text {:style {:font-size 24}} tracker-value-display]
     (map inc-dec-component-builder increment-vals)]))

(defn initialize-modal-state
  [ds-conn modal-state-data]
  (let [{:keys [:db/id]} (queries/get-modal-state ds-conn)]
    (ds/transact! ds-conn
                  (map (fn [[k v]] [:db/add id k v]) modal-state-data))))

(def modal-style {:width "80vw" :height "80vh"
                  :background-color :white :opacity 1 :color :black
                  :text-align :center :font-size 20 :font-weight :bold})

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

(defn tracker-node-modal
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