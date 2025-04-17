(ns ui.tracker-node
    (:require [ui.node :as node]
              [datascript.core :as ds]
              ["react-native" :as rn]))

(defn inc-dec
  [db-conn entity-id attribute inc-or-dec-fn]
  (let [{:keys [tracker-value tracker-max-value tracker-min-value]} (ds/entity db-conn entity-id)]
    (if (not (nil? tracker-value))
      (let [raw-new-value (inc-or-dec-fn tracker-value)
            new-value (cond
                        (and tracker-max-value (< tracker-max-value raw-new-value)) tracker-max-value
                        (and tracker-min-value (> tracker-min-value raw-new-value)) tracker-min-value
                        :else raw-new-value)]
        [{:db/id entity-id attribute new-value}])
      [{:db/id entity-id attribute 0}])))

(defn inc-dec-tracker-component
  [{:keys [db-conn tracker-id plus-or-minus inc-or-dec-amount]}]
  (let [inc-or-dec-fn #(plus-or-minus % inc-or-dec-amount)]
    [:> rn/Pressable {:style {:height 30 :width 30 :background :white :border-radius 2
                              :align-items :center :justify-content :center}
                      :on-press #(ds/transact! db-conn [[:db.fn/call inc-dec
                                                            tracker-id
                                                            :tracker-value
                                                            inc-or-dec-fn]])}
     [:> rn/Text (str (if (= plus-or-minus +) "+" "-") inc-or-dec-amount)]]))

(defn tracker-node-content
  [db-conn _ {:keys [:db/id primary-sub-node sub-nodes tracker-value] :as node-data} nesting-depth]
    [:> rn/View {:key id :style (merge (node/node-style nesting-depth) {:flex-direction :row :gap 5 :align-items :center})}
      (inc-dec-tracker-component {:db-conn db-conn :tracker-id id :plus-or-minus - :inc-or-dec-amount 5})
      (inc-dec-tracker-component {:db-conn db-conn :tracker-id id :plus-or-minus - :inc-or-dec-amount 1})
      [:> rn/Text {:style {:font-size 24}} (:tracker-value (ds/entity @db-conn id))]
      (inc-dec-tracker-component {:db-conn db-conn :tracker-id id :plus-or-minus + :inc-or-dec-amount 1})
      (inc-dec-tracker-component {:db-conn db-conn :tracker-id id :plus-or-minus + :inc-or-dec-amount 5})])