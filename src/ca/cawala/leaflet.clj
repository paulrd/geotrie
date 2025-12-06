(ns ca.cawala.leaflet
  (:require
   [clojure.java.io :as io])
  (:import
   (javax.swing ImageIcon JFrame UIManager)
   (org.apache.logging.log4j LogManager)
   (org.jxmapviewer Globals JXMapViewerLogger)
   (org.jxmapviewer.examples HelperFunctions)
   (org.jxmapviewer.examples.controller MainController)))

(defmacro fx-run
  "With this macro what you run is run in the JavaFX Application thread.
  Is needed for all calls related with JavaFx"
  [& code]
  `(javafx.application.Platform/runLater (fn [] ~@code)))

(defn jxmap []
  (let [log (LogManager/getLogger MainController)
        _ (JXMapViewerLogger/initLogger Globals/PATH_LOG4J2)
        _ (try (UIManager/setLookAndFeel (UIManager/getSystemLookAndFeelClassName))
               (catch Exception ex (.error log (.getLocalizedMessage ex))))
        frame (JFrame.)]
    (doto frame (.setTitle "JXMapViewer2 Examples")
          (.setIconImage (.getImage (ImageIcon. (io/resource "app.png"))))
          (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
          (.setSize Globals/WIDTH Globals/HEIGHT)
          (.setExtendedState Globals/FRAME_STATE))
    (MainController. frame)
    (HelperFunctions/centerWindow frame)
    (.setVisible frame true)))

(comment
  (jxmap)
  )
