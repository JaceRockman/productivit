(ns ui.tracker-node
  (:require [ui.node :as node]
            [datascript.core :as ds]
            ["react-native" :as rn]))

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
                 :style (merge (node/node-style nesting-depth)
                               {:flex-direction :row :gap 5 :align-items :center}
                               style-override)}
     (map inc-dec-component-builder decrement-vals)
     [:> rn/Text {:style {:font-size 24}} tracker-value-display]
     (map inc-dec-component-builder increment-vals)]))