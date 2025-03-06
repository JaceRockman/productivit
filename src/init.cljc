(ns init
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            ["react-native" :as rn]
            [datascript.core :as ds]
            [datascript.impl.entity :as de]
            [data :as data]))

(defn initialize-db
  [conn]
  (ds/transact! conn data/example-group)
  :success)
