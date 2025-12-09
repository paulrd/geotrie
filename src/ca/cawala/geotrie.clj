(ns ca.cawala.geotrie
  (:require
   [ca.cawala.sql :as s]
   [clojure.core.reducers :as r]
   [clojure.java.io :as io]
   [com.climate.claypoole :as cp])
  (:import
   (java.awt Rectangle)
   (org.geotools.coverage.processing CoverageProcessor)
   (org.geotools.gce.geotiff GeoTiffReader)
   (org.geotools.geometry Position2D)
   (org.geotools.geometry.jts ReferencedEnvelope)))

(defn sum-tile [raster region-rect]
  (let [tile-rect (.getBounds raster)
        rect (.intersection tile-rect region-rect)
        width (int (.getWidth rect))
        height (int (.getHeight rect))
        a (.getPixels raster (int (.getX rect)) (int (.getY rect)) width height
                      (make-array Float/TYPE (* width height)))]
    (apply + ^floats (filter #(> % 0) a))))

(defn to-grid
  "Converts from lat/lon to grid coordinates."
  [cov {:keys [min-lon max-lon min-lat max-lat]}]
  (let [crs (.getCoordinateReferenceSystem cov)
        processor (CoverageProcessor/getInstance nil)
        param (.getParameters (.getOperation processor "CoverageCrop"))
        _ (.setValue (.parameter param "Source") cov)
        _ (.setValue (.parameter param "Envelope") (ReferencedEnvelope. min-lon max-lon min-lat max-lat crs))
        grid-geometry (.getGridGeometry (.doOperation processor param))
        grid-lower-left (.worldToGrid grid-geometry (Position2D. crs min-lon min-lat))
        grid-upper-right (.worldToGrid grid-geometry (Position2D. crs max-lon max-lat))
        grid-range (.getGridRange2D grid-geometry)
        min-col (Math/max (.x grid-lower-left) (.x grid-range))
        min-row (Math/max (.y grid-upper-right) (.y grid-range))
        max-col (Math/min (.x grid-upper-right) (+ (.x grid-range) (.width grid-range) -1))
        max-row (Math/min (.y grid-lower-left) (+ (.y grid-range) (.height grid-range) -1))]
    (Rectangle. min-col min-row (inc (- max-col min-col)) (inc (- max-row min-row)))))

(defn get-tiles
  "Takes a coverage object and a rectangle and returns a set of tiles containing these points."
  [cov rect]
  (let [img (.getRenderedImage cov)
        min-x (.getMinTileX img)
        min-y (.getMinTileY img)
        tiles (vec (for [x (range (.getNumXTiles img))
                         y (range (.getNumYTiles img))]
                     [x y]))
        min-col (.getX rect)
        max-col (+ min-col (dec (.getWidth rect)))
        min-row (.getY rect)
        max-row (+ min-row (dec (.getHeight rect)))
        tileWidth (.getTileWidth img)
        tileHeight (.getTileHeight img)]
    (->> tiles
         (r/map (fn [[x y]] [(+ min-x x) (+ min-y y)]))
         (r/filter #(not (or (<= (* (inc (first %)) tileWidth) min-col) ; tile is too far left
                             (> (* (first %) tileWidth) max-col)        ; tile is too far right
                             (<= (* (inc (last %)) tileHeight) min-row) ; tile is too far above
                             (> (* (last %) tileHeight) max-row)        ; tile is too far below
                             )))
         (r/map #(.getTile img (first %) (last %)))
         (r/foldcat))))

(defn sum-tiles
  "Takes a coverage object and a rectangle and returns sum of tile values within the rectangle.
  Note that pixels on boundaries will get counted twice when summing separate
  regions that share a border."
  [cov rect]
  (let [img (.getRenderedImage cov)
        min-x (.getMinTileX img)
        min-y (.getMinTileY img)
        tiles (vec (for [x (range (.getNumXTiles img))
                         y (range (.getNumYTiles img))]
                     [x y]))
        min-col (.getX rect)
        max-col (+ min-col (dec (.getWidth rect)))
        min-row (.getY rect)
        max-row (+ min-row (dec (.getHeight rect)))
        tileWidth (.getTileWidth img)
        tileHeight (.getTileHeight img)]
    (->> tiles
         (r/map (fn [[x y]] [(+ min-x x) (+ min-y y)]))
         (r/filter #(not (or (<= (* (inc (first %)) tileWidth) min-col) ; tile is too far left
                             (> (* (first %) tileWidth) max-col)        ; tile is too far right
                             (<= (* (inc (last %)) tileHeight) min-row) ; tile is too far above
                             (> (* (last %) tileHeight) max-row)        ; tile is too far below
                             )))
         (r/map #(.getTile img (first %) (last %)))
         (r/map #(sum-tile % rect))
         (r/fold +))))

(defn determine-cut-axis
  "returns :vertical if the real-word dimension is longer in the north-south
  direction. Else it returns :horizontal"
  [{:keys [min-lon max-lon min-lat max-lat]}]
  (let [x (- max-lon min-lon)
        y (- max-lat min-lat)
        mid-lat (/ (+ min-lat max-lat) 2.0)
        reduction-factor-at-mid-lat (Math/cos (/ (* Math/PI mid-lat) 180))]
    (if (> (* reduction-factor-at-mid-lat x) y) :horizontal :vertical)))

(defn cannot-be-divided [grid cut-axis {:keys [min-lon max-lon min-lat max-lat population]}]
  (let [g-min-lon -180 g-max-lon 179.9999986
        g-min-lat -59.9999994 g-max-lat 84
        mid-lon (/ (+ min-lon max-lon) 2)
        mid-lat (/ (+ min-lat max-lat) 2)]
    (if (< population 1) true
        (case cut-axis
          :vertical (or (> mid-lat g-max-lat) (< mid-lat g-min-lat)
                        (= 1.0 (.getHeight grid)))
          :horizontal (or (> mid-lon g-max-lon) (< mid-lon g-min-lon)
                          (= 1.0 (.getWidth grid)))))))

(defn split-region [coverage region cut-axis]
  (case cut-axis
    :vertical
    (let [mid-lat (/ (+ (:min-lat region) (:max-lat region)) 2.0)
          region1 (assoc region :max-lat mid-lat)
          region2 (assoc region :min-lat mid-lat)]
      [(assoc region1 :population (sum-tiles coverage (to-grid coverage region1))
              :binary-path (str (:binary-path region) "s"))
       (assoc region2 :population (sum-tiles coverage (to-grid coverage region2))
              :binary-path (str (:binary-path region) "n"))])
    :horizontal
    (let [mid-lon (/ (+ (:min-lon region) (:max-lon region)) 2.0)
          region1 (assoc region :max-lon mid-lon)
          region2 (assoc region :min-lon mid-lon)]
      [(assoc region1 :population (sum-tiles coverage (to-grid coverage region1))
              :binary-path (str (:binary-path region) "w"))
       (assoc region2 :population (sum-tiles coverage (to-grid coverage region2))
              :binary-path (str (:binary-path region) "e"))])))

(defn make-eight-regions
  "cut this region into 8, may return fewer regions if a region cannot be further
  subdivided because a region has too small of population"
  [coverage regions]
  (let [sorted (sort-by :population regions)
        but-last (drop-last sorted)
        cut-axis (determine-cut-axis (last sorted))
        grid (to-grid coverage (last sorted))]
    (if (or (>= (count regions) 8) (cannot-be-divided grid cut-axis (last sorted)))
      (map #(assoc %1 :materialized-path (str (:materialized-path %1) %2))
           (sort-by :binary-path regions) "abcdefgh")
      (recur coverage
             (concat but-last (split-region coverage (last sorted) cut-axis))))))

(defn write-layer! [layer-number]
  (let [file-name "global_pop_2026_CN_1km_R2025A_UA_v1.tif"
        coverage (.read (GeoTiffReader. (io/resource file-name)) nil)
        start-regions (map #(dissoc % :id) (s/get-layer (dec layer-number)))]
    (cp/pdoseq 8 [region start-regions]
               (let [r (make-eight-regions coverage [region])]
                 (when (> (count r) 1)
                   (s/insert-regions! r))))))

(defn write-first-layer! []
  (let [file-name "global_pop_2026_CN_1km_R2025A_UA_v1.tif"
        coverage (.read (GeoTiffReader. (io/resource file-name)) nil)
        world {:min-lon -180 :max-lon 180 :min-lat -90 :max-lat 90
               :population 8.266e9 :binary-path "" :materialized-path ""}]
    (s/insert-regions! (make-eight-regions coverage [world]))))

(defn -main [& args]
  (let [layer (first args)]
    (if (= layer 1)
      (write-first-layer!)
      (write-layer! layer))))

(comment
  ;; layer 1: 164 seconds, 8 regions
  ;; layer 1: 174 seconds, 8 regions - threadpool 8
  ;; layer 1: 176 seconds, 8 regions - threadpool 8

  ;; layer 2: 262 seconds, 64 regions, 72 total
  ;; layer 2: 144 seconds, 64 regions, 72 total - threadpool 8
  ;; layer 2: 133 seconds, 64 regions, 72 total - threadpool 4
  ;; layer 2: 126 seconds, 64 regions, 72 total - threadpool 8

  ;; layer 3 248 seconds, 476 regions, 548 total
  ;; layer 3 99 seconds, 476 regions, 548 total - threadpool 4
  ;; layer 3 97 seconds, 476 regions, 548 total - threadpool 8
  ;; layer 3 100 seconds, 480 regions, 552 total - threadpool 8

  ;; layer 4 153 seconds, 3396 regions, 3944 total
  ;; layer 4 83 seconds, 3368 regions, 3916 total
  ;; layer 4 78 seconds, 3400 regions, 3952 total
  ;; layer 4 90 seconds, 3616 regions, 4168 total

  ;; layer 5 154 seconds, 22272 regions, 26216 total
  ;; layer 5 52 seconds, 21719 regions, 25635 total
  ;; layer 5 64 seconds, 22080 regions, 26032 total
  ;; layer 5 99 seconds, 26592 regions, 30760 total

  ;; layer 6 408 seconds, 122577 regions, 148793 total
  ;; layer 6 414 seconds, 121000 regions, 14xxxx total - no population splitting < 1
  ;; layer 6 221 seconds, 121000 regions, 14xxxx total - no population splitting < 1; threadpool 4
  ;; layer 6 171 seconds, 116232 regions, 141867 total - no population splitting < 1; threadpool 8
  ;; layer 6 168 seconds, 117089 regions, 143121 total - no population splitting < 1; threadpool 8
  ;; layer 6 389 seconds, 195803 regions, 226563 total - no population splitting < 1; threadpool 8

  ;; layer 7 19 minutes, 372931 regions, 521724 total
  ;; layer 7 7 minutes, 314272 regions, 456139 total
  ;; layer 7 40 minutes, 1451807 regions, 1678370 total
  (time (-main 7))

;; Data source​ worldpop.org.
;; old file​ ppp_2020_1km_Aggregated.tif
;; Bounds​ -180.0, -59.999999423999995, 179.99999856000005, 84.0
;; [-180.0 179.99999856000005 -59.999999423999995 84.0]
  :dbg)
