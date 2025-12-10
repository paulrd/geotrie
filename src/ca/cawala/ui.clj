(ns ca.cawala.ui
  (:require [org.corfield.logging4j2 :as logger]
            [clojure.java.io :as io]
            [seesaw.core :as s])
  (:import [org.jxmapviewer.examples Examples]
           [org.jxmapviewer.examples.controller MainController]
           [org.jxmapviewer Globals]
           ))

(defn -main [& _]
  (s/native!)
  (let [frame (s/frame :on-close :dispose :title "JXMapViewer2 Examples"
                       :icon (io/resource "images/app.png")
                       :width Globals/WIDTH :height Globals/HEIGHT)]
    (MainController. frame)
    (s/show! frame)))

(comment
  (-main)
  (logger/with-log-context {:id "tony"}
    (logger/info "Hello World Again"))
  (logger/with-log-tag "hungry"
    (logger/info "Hello World Again"))
  "great")
