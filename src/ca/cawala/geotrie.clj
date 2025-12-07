(ns ca.cawala.geotrie
  (:require
   [clojure.core.reducers :as r])
  (:import
   (java.awt Rectangle)
   (org.geotools.coverage.processing CoverageProcessor)
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
        mid-lat (/ (+ min-lat max-lat) 2)
        reduction-factor-at-mid-lat (Math/cos (/ (* Math/PI mid-lat) 180))]
    (if (> (* reduction-factor-at-mid-lat x) y) :vertical :horizontal)))

(defn cannot-be-divided [grid cut-axis]
  (case cut-axis
    :vertical (= 1 (.getHeight grid))
    :horizontal (= 1 (.getWidth grid))))

(defn make-eight-regions
  "cut this region into 8, may return fewer regions if a region cannot be further
  subdivided because a region has too small of population"
  [coverage parent-region]
  (let [{:keys [min-lon max-lon min-lat max-lat]} parent-region]
    #_(loop [cut-axis (determine-cut-axis parent-region)
           grid (to-grid coverage parent-region)
           regions []
           ]
      (if (cannot-be-divided grid cut-axis)
        regions
        (recur [cut-axis (determine-cut-axis )])
        )
      )))

(comment
  (def x (- 180 -180))
  (def y (- 90 -90))
  (def min-lat -90)
  (def max-lat 90)
  (def mid-lat (/ (+ min-lat max-lat) 2))
  (def reduction (Math/cos (/ (* Math/PI mid-lat) 180)))
  (if (> (* reduction x) y) :vertical :horizontal)
  "hi")

(defn make-eight-regions
  ""
  [min-lon, max-lon, min-lat, max-lat]

  )

(comment
;; Data source: worldpop.org.
;; old file: ppp_2020_1km_Aggregated.tif
;; Bounds: -180.0, -59.999999423999995, 179.99999856000005, 84.0
;; [-180.0 179.99999856000005 -59.999999423999995 84.0]
  (do
    (def meters-per-degree 111320)) ; to use to get approximate dimensions of a 'square' lat/lon
  :dbg)
