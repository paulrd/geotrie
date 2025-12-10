(ns ca.cawala.ui
  (:require
   [clojure.java.io :as io]
   [org.corfield.logging4j2 :as logger]
   [seesaw.core :as s])
  (:import
   [org.jxmapviewer Globals]
   [org.jxmapviewer.examples.controller MainController]))

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
