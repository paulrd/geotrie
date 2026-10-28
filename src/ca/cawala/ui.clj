(ns ca.cawala.ui
  (:require
   [org.corfield.logging4j2 :as logger]
   [seesaw.core :as s])
  (:import
   ))

(comment
  (show!)
  (JFXPanel.)
  (Platform/setImplicitExit false)
  (declare stage)
  (fx-run
   (let [stage (javafx.stage.Stage.)]
     (def stage stage)
     (.show stage)))

  (WebView.)
  (fx-run (MainApp/main (into-array String [])))
  (fx-run (MainApp2/main (into-array String [])))
  (MainApp/main (into-array String []))
  (MainApp2/main (into-array String []))
  (ClusteredMainApp/main (into-array String []))
  (invoke-private-method my-instance "privateMethod" arg1 arg2)
  (call-method MapLayer "getMapPoint" [Double/TYPE Double/TYPE] (MapLayer.) 0.0 0.0))

(comment
  (-main)
  (logger/with-log-context {:id "tony"}
    (logger/info "Hello World Again"))
  (logger/with-log-tag "hungry"
    (logger/info "Hello World Again"))
  "great")
