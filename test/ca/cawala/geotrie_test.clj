(ns ca.cawala.geotrie-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [ca.cawala.geotrie :refer :all]
            [clojure.core.reducers :as r])
  (:import (java.awt Rectangle)
           (org.geotools.gce.geotiff GeoTiffReader)
           (org.geotools.coverage.grid GridCoordinates2D GridEnvelope2D GridGeometry2D)))

(def one-place {:val 3.88987e-14 :col 14384 :row 4527
                :upper-left {:lat 46.274420 :lon -60.132772}
                :lower-right {:lat 46.267677 :lon -60.126012}})
(def min-lon (get-in one-place [:upper-left :lon]))
(def max-lon (get-in one-place [:lower-right :lon]))
(def min-lat (get-in one-place [:lower-right :lat]))
(def max-lat (get-in one-place [:upper-left :lat]))
(def file-name "global_pop_2026_CN_1km_R2025A_UA_v1.tif")
(def cov (.read (GeoTiffReader. (clojure.java.io/resource file-name)) nil))

(deftest to-grid-test
  (testing "Test conversion from lat/lon area to grid area."
    (is (= (Rectangle. 0 0 43200 17280)
           (to-grid cov -180 180 -90 90)))
    (is (= (Rectangle. (:col one-place) (:row one-place) 1 1)
           (to-grid cov min-lon max-lon min-lat max-lat)))))

(deftest y-grid-is-north
  (testing "Test to see that if we go up in y, we go north."
    (let [x1 (:col one-place)
          x2 (inc (:col one-place))
          y1 (:row one-place)
          y2 (inc (:row one-place))]
      (is (and (< (first (.evaluate cov (GridCoordinates2D. x1 y1) (make-array Float/TYPE 1)))
                  3.8898729E-14)
               (> (first (.evaluate cov (GridCoordinates2D. x2 y2) (make-array Float/TYPE 1)))
                  3.8898727E-14))))))

(deftest get-tiles-test
  (testing "get tile rasters for grid range"
    (let [grid-rect (to-grid cov min-lon max-lon min-lat max-lat)
          tile (first (get-tiles cov grid-rect))]
      (is (= (count (get-tiles cov grid-rect)) 1))
      (is (<= (.getMinX tile) (.getX grid-rect))
          (<= (.getMinY tile) (.getY grid-rect)))
      (is (= (count (get-tiles cov (Rectangle. (.getX grid-rect)
                                               (.getY grid-rect)
                                               (+ (.getWidth grid-rect) 512)
                                               (+ (.getHeight grid-rect) 512))))
             4)))))

(deftest sum-tiles-test
  (testing "sum tiles in region quickly"
    (let [grid-rect (to-grid cov 0 10 0 10)]
      (is (>= (time (sum-tiles cov grid-rect)) 0)))))

(comment
  (do
    (set! *warn-on-reflection* false)
    (println "doing:")
    #_(def grid-rect (to-grid cov min-lon max-lon min-lat max-lat))
    (def grid-rect (to-grid cov -180 180 -90 90))
    (time (sum-tiles cov grid-rect))
    "kiss me")
  "hug me")
