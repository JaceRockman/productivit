(ns ui.date-time-picker
  (:require [clojure.math :as math]
            [reagent.core :as r]
            [react-native :as rn]
            [cljs-time.core :as t]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.format :as t-format]))

(def standard-date-format
  (t-format/formatter "MMM' 'dd', 'YYYY"))

(def standard-time-format
  (t-format/formatter "HH:mm:ss"))

(def DefaultDatePicker (.-default (js/require "react-native-ui-datepicker")))

(defn combine-date-and-time [{:keys [date-to-keep time-to-add]}]
  (let [date-value (t/floor date-to-keep t/day)
        hour-value (t/hours (t/hour time-to-add))
        minute-value (t/minutes (t/minute time-to-add))
        second-value (t/seconds (t/second time-to-add))
        millis-value (t/millis (t/milli time-to-add))
        combined-date (t/plus date-value
                              hour-value
                              minute-value
                              second-value
                              millis-value)]
    combined-date))

(def ScrollSelector
  (r/create-class
   {:reagent-render
    (fn [{:keys [updateTime dateTimeAtom currentValue minValue maxValue interval unit]}]
      (let [values (range minValue maxValue interval)
            selected-value (if (some #(= % @currentValue) values) @currentValue (first values))
            divider [:> rn/View {:style {:background-color "black" :height 2}}]
            options (map-indexed (fn [index value]
                                   [:> rn/View
                                    [:> rn/Text {:style {:height 40 :color (if (= selected-value value) "blue" "black")
                                                         :font-size 20 :font-weight "bold" :text-align "center"}} value]
                                    (when (< index (dec (count values))) divider)])
                                 values)]
        [:> rn/ScrollView {:on-scroll
                           (fn [scroll-data]
                             (let [selected-position (-> scroll-data
                                                         js->clj
                                                         (get-in ["nativeEvent" "contentOffset" "y"])
                                                         (/ 42)
                                                         math/round)
                                   selected-value (nth values selected-position)
                                   new-time (t/plus (t/floor @dateTimeAtom t/day)
                                                    (if (= unit "hour") (t/hours selected-value) (t/hours (t/hour @dateTimeAtom)))
                                                    (if (= unit "minute") (t/minutes selected-value) (t/minutes (t/minute @dateTimeAtom)))
                                                    (if (= unit "second") (t/seconds selected-value) (t/seconds (t/second @dateTimeAtom)))
                                                    (if (= unit "millisecond") (t/millis selected-value) (t/millis (t/milli @dateTimeAtom))))]
                               (updateTime new-time)))
                           :paging-enabled true
                           :shows-horizontal-scroll-indicator false
                           :shows-vertical-scroll-indicator false
                           :style {:background "rgba(0,0,0,0.2)" :flex 1 :overflow "scroll" :height 124 :width 100 :text-align "center" :align-content "center"}}
         [:> rn/View {:style {:height 42}}]
         options
         [:> rn/View {:style {:height 42}}]]))}))

(def TimePicker
  (r/create-class
   {:reagent-render
    (fn [{:keys [dateTimeAtom style]}]
      (let [date-time-atom dateTimeAtom
            update-time (fn [new-time]
                          (reset! date-time-atom new-time))]
        [:> rn/View {:style {:align-items "center" :justify-content "center"}}
         [:> rn/Text
          (t-format/unparse standard-time-format @date-time-atom)]
         [:> rn/View {:style {:flex-direction "row" :gap 10 :align-items "center" :justify-content "center"}}
          [:> ScrollSelector {:updateTime update-time
                              :dateTimeAtom date-time-atom
                              :currentValue (r/atom (t/hour @date-time-atom))
                              :unit "hour"
                              :minValue 0
                              :maxValue 24
                              :interval 1}]
          [:> ScrollSelector {:updateTime update-time
                              :dateTimeAtom date-time-atom
                              :currentValue (r/atom (t/minute @date-time-atom))
                              :unit "minute"
                              :minValue 0
                              :maxValue 60
                              :interval 10}]
          [:> ScrollSelector {:updateTime update-time
                              :dateTimeAtom date-time-atom
                              :currentValue (r/atom (t/second @date-time-atom))
                              :unit "second"
                              :minValue 0
                              :maxValue 60
                              :interval 15}]]]))

    :component-did-mount (fn [this]
                           (let [props (r/props this)
                                 date-atom (:date-atom props)]
                             (when (and date-atom (nil? @date-atom))
                               (reset! date-atom (js/Date.)))
                             (.log js/console "DateTimePicker mounted!")))
    :component-will-unmount (fn [this]
                              (.log js/console "DateTimePicker will unmount!"))}))

(def DatePicker
  (r/create-class
   {:reagent-render
    (fn [{:keys [dateTimeAtom style]}]
      (let [date-time-atom dateTimeAtom
            update-date (fn [new-date]
                          (reset! date-time-atom new-date))]
        [:> DefaultDatePicker
         {:styles {:today {:borderColor "#007AFF" :borderWidth 1}
                   :selected {:backgroundColor "#007AFF"}
                   :selected_label {:color "white"}}
          :date @date-time-atom
          :mode "single"
          :onChange (fn [date]
                      (let [updated-date (combine-date-and-time
                                          {:date-to-keep (t-coerce/from-date (get (js->clj date) "date"))
                                           :time-to-add @date-time-atom})]
                        (update-date updated-date)))}]))}))

(def DateTimePicker
  (r/create-class
   {:reagent-render (fn [{:keys [dateValue onChange]}]
                      (let [datetime-atom (r/atom dateValue)
                            submit-changes (fn [new-date]
                                             (when onChange
                                               (onChange new-date)))]
                        [:> rn/ScrollView {:style {:flex 1 :overflow "hidden" :height "100%"}}
                         [:> rn/Pressable {:on-press #(submit-changes @datetime-atom)}
                          [:> rn/Text "Submit"]]

                         [:> rn/View {:style {:margin 30}}
                          [:> DatePicker {:date-time-atom datetime-atom}]]

                         [:> rn/View {:style {:margin 30}}
                          [:> TimePicker {:date-time-atom datetime-atom}]]]))

    :component-did-mount (fn [this]
                           (let [props (r/props this)
                                 _ (println props)
                                 date-atom (:date-atom props)]
                             (when (and date-atom (nil? @date-atom))
                               (reset! date-atom (js/Date.)))
                             (.log js/console "DateTimePicker mounted!")))

    :component-will-unmount (fn [this]
                              (.log js/console "DateTimePicker will unmount!"))}))
