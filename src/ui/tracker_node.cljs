(ns ui.tracker-node
  (:require [ui.node :as node]
            [datascript.core :as ds]
            ["react-native" :as rn]))

(defn inc-dec
  [_ {:keys [id] :as tracker-node-data} attribute inc-or-dec-fn]
  (let [{:keys [tracker-value tracker-max-value tracker-min-value]} tracker-node-data]
    (if (not (nil? tracker-value))
      (let [raw-new-value (inc-or-dec-fn tracker-value)
            new-value (cond
                        (and tracker-max-value (< tracker-max-value raw-new-value)) tracker-max-value
                        (and tracker-min-value (> tracker-min-value raw-new-value)) tracker-min-value
                        :else raw-new-value)]
        [{:db/id id attribute new-value}])
      [{:db/id id attribute 0}])))

(defn inc-dec-tracker-component
  [{:keys [ds-conn tracker-node-data plus-or-minus inc-or-dec-amount]}]
  (let [inc-or-dec-fn #(plus-or-minus % inc-or-dec-amount)]
    [:> rn/Pressable {:style {:height 30 :width 30 :background :white :border-radius 2
                              :align-items :center :justify-content :center}
                      :on-press #(ds/transact! ds-conn [[:db.fn/call inc-dec
                                                         tracker-node-data
                                                         :tracker-value
                                                         inc-or-dec-fn]])}
     [:> rn/Text (str (if (= plus-or-minus +) "+" "-") inc-or-dec-amount)]]))

(defn tracker-node-content
  [ds-conn {:keys [:db/id primary-sub-node sub-nodes tracker-value] :as node-data} nesting-depth]
  [:> rn/View {:key id :style (merge (node/node-style nesting-depth) {:flex-direction :row :gap 5 :align-items :center})}
   (inc-dec-tracker-component {:ds-conn ds-conn :tracker-node-data node-data :plus-or-minus - :inc-or-dec-amount 5})
   (inc-dec-tracker-component {:ds-conn ds-conn :tracker-node-data node-data :plus-or-minus - :inc-or-dec-amount 1})
   [:> rn/Text {:style {:font-size 24}} (:tracker-value node-data)]
   (inc-dec-tracker-component {:ds-conn ds-conn :tracker-node-data node-data :plus-or-minus + :inc-or-dec-amount 1})
   (inc-dec-tracker-component {:ds-conn ds-conn :tracker-node-data node-data :plus-or-minus + :inc-or-dec-amount 5})])