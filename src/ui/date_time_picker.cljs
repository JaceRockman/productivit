(ns ui.date-time-picker
  (:require [reagent.core :as r]
            [react-native :as rn]
            [cljs-time.core :as t]
            [cljs-time.coerce :as t-coerce]
            [cljs-time.format :as t-format]))

(def standard-date-format
  (t-format/formatter "MMM' 'dd', 'YYYY"))

(def standard-time-format
  (t-format/formatter "HH:mm:ss"))

(def DatePicker (.-default (js/require "react-native-ui-datepicker")))
;; (def TimePicker (.-default (js/require "react-native-timer-picker")))

(def TimePicker
  (r/create-class
   {:reagent-render
    (fn [{:keys [dateAtom onChange style]}]
      (let [current-date (or @dateAtom (js/Date.))
            update-time (fn [new-time]
                          (reset! dateAtom new-time)
                          (when onChange
                            (onChange new-time)))]
        [:> rn/View {:style (merge {:padding 10} style)}
         [:> rn/Text {:style {:font-weight "bold" :margin-bottom 5}}
          "Select Time:"]
         [:> rn/View {:style {:flex-direction "row" :align-items "center" :justify-content "center"}}
          [:> rn/Text
           (t-format/unparse standard-time-format current-date)]
          [:> rn/Pressable
           {:style {:margin-left 10
                    :padding 8
                    :background-color "#007AFF"
                    :border-radius 5}
            :on-press #(-> (js/require "react-native-timer-picker")
                           (.-default)
                           (.show
                            (clj->js {:value (js/Date. current-date)
                                      :onConfirm (fn [selected-time]
                                                   (update-time selected-time))
                                      :onCancel (fn [] (println "Time picker cancelled"))
                                      :mode "time"
                                      :is24Hour true})))}
           [:> rn/Text {:style {:color "white"}}
            "Change"]]]]))

    :component-did-mount (fn [this]
                           (let [props (r/props this)
                                 date-atom (:date-atom props)]
                             (when (and date-atom (nil? @date-atom))
                               (reset! date-atom (js/Date.)))
                             (.log js/console "DateTimePicker mounted!")))
    :component-will-unmount (fn [this]
                              (.log js/console "DateTimePicker will unmount!"))}))

(def DateTimePicker
  (r/create-class
   {:reagent-render (fn [{:keys [dateAtom onChange] :as props}]
                      (let [date-value @dateAtom
                            update-date (fn [new-date]
                                          (reset! dateAtom new-date)
                                          (when onChange
                                            (onChange new-date)))]
                        [:> rn/ScrollView {:style {:flex 1 :overflow "hidden" :height "100%"}}
                         [:> rn/View {:style {:margin 30}}
                          [:> rn/Text {:style {:font-weight "bold" :margin-bottom 10 :text-align "center"}} "Date:"]
                          [:> DatePicker
                           {:styles {:today {:borderColor "#007AFF" :borderWidth 1}
                                     :selected {:backgroundColor "#007AFF"}
                                     :selected_label {:color "white"}}
                            :date date-value
                            :mode "single"
                            :onChange (fn [date]
                                        (println "Selected date:" date)
                                        ;; Extract the date part from the selected date
                                        ;; and combine it with the time part from current-date
                                        (let [selected-date (-> date
                                                                js->clj
                                                                (get "date")
                                                                t-coerce/from-date
                                                                (t/floor t/day))
                                              hour-value (t/hours (t/hour date-value))
                                              minute-value (t/minutes (t/minute date-value))
                                              second-value (t/seconds (t/second date-value))
                                              millis-value (t/millis (t/milli date-value))
                                              combined-date (t/plus selected-date
                                                                    hour-value
                                                                    minute-value
                                                                    second-value
                                                                    millis-value)]
                                          (update-date combined-date)))}]]

                         [:> rn/View {:style {:margin 30}}
                          [:> rn/Text {:style {:font-weight "bold" :margin-bottom 10 :text-align "center"}} "Time:"]
                          [:> TimePicker
                           {:date-atom dateAtom
                            :on-change (fn [date] (update-date date))}]]]))
    :component-did-mount (fn [this]
                           (let [props (r/props this)
                                 date-atom (:date-atom props)]
                             (when (and date-atom (nil? @date-atom))
                               (reset! date-atom (js/Date.)))
                             (.log js/console "DateTimePicker mounted!")))
    :component-will-unmount (fn [this]
                              (.log js/console "DateTimePicker will unmount!"))}))
