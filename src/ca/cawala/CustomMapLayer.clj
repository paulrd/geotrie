(ns ca.cawala.CustomMapLayer
  (:import [com.gluonhq.maps MapLayer])
  (:gen-class
   :implements [clojure.lang.IDeref]))
#_(:gen-class
 :implements [clojure.lang.IDeref])

(defn -deref
  [this]
  "Hello, World!")

(comment
  @(ca.cawala.CustomMapLayer.)
  (-deref (ca.cawala.CustomMapLayer.))
  (:import [com.gluonhq.maps MapView MapLayer MapPoint]))
