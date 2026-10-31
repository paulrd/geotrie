(ns ca.cawala.geotrie
  (:require
   [ca.cawala.sql :as s]
   [clojure.core.reducers :as r]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.climate.claypoole :as cp]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   (java.awt Rectangle)
   (java.awt.image RenderedImage Raster)
   (org.geotools.coverage.grid GridCoverage2D GridGeometry2D)
   (org.geotools.gce.geotiff GeoTiffReader)
   (org.geotools.geometry Position2D)))

(defn sum-tile
  "Sum of the pixels in the part of region-rect covered by this tile."
  [^Raster raster ^Rectangle region-rect]
  (let [^Rectangle tile-rect (.getBounds raster)
        ^Rectangle rect (.intersection tile-rect region-rect)
        width (int (.getWidth rect))
        height (int (.getHeight rect))
        ^floats buf (make-array Float/TYPE (* width height))
        ^floats a (.getPixels raster (int (.getX rect)) (int (.getY rect))
                              width height buf)]
    (areduce a i s 0.0
             (let [v (aget a i)]
               (if (> v 0) (+ s v) s)))))

(defn to-grid
  "Converts a lat/lon envelope to the absolute pixel rectangle whose pixel
  centres lie inside it. Using pixel centres keeps the pixel sets of sibling
  regions disjoint, so their populations add up instead of double counting
  the boundary pixels."
  [^GridCoverage2D cov {:keys [min-lon max-lon min-lat max-lat]}]
  (let [crs (.getCoordinateReferenceSystem cov)
        ^GridGeometry2D grid-geometry (.getGridGeometry cov)
        ^org.geotools.api.referencing.operation.MathTransform inverse
        (.inverse (.getGridToCRS2D grid-geometry))
        grid-coords (fn [lon lat]
                      (let [dst (Position2D. crs 0.0 0.0)]
                        (.transform inverse (Position2D. crs (double lon) (double lat)) dst)
                        [(.getOrdinate dst 0) (.getOrdinate dst 1)]))
        [x-a y-a] (grid-coords min-lon max-lat)
        [x-b y-b] (grid-coords max-lon min-lat)
        ^Rectangle grid-range (.getGridRange2D grid-geometry)
        lo-x (long (.x grid-range))
        hi-x (long (dec (+ (.x grid-range) (.width grid-range))))
        lo-y (long (.y grid-range))
        hi-y (long (dec (+ (.y grid-range) (.height grid-range))))
        ;; the grid transform maps a world position to the continuous grid
        ;; coordinate of a pixel *centre*, so pixel i's centre is at i.
        min-col (max lo-x (long (Math/ceil (min x-a x-b))))
        max-col (min hi-x (dec (long (Math/ceil (max x-a x-b)))))
        min-row (max lo-y (long (Math/ceil (min y-a y-b))))
        max-row (min hi-y (dec (long (Math/ceil (max y-a y-b)))))]
    (Rectangle. (int min-col) (int min-row)
                (int (max 0 (inc (- max-col min-col))))
                (int (max 0 (inc (- max-row min-row)))))))

(defn- tile-bounds
  "Absolute tile index range [tx0 tx1 ty0 ty1] of the tiles that intersect rect."
  [^RenderedImage img ^Rectangle rect]
  (let [tw (long (.getTileWidth img))
        th (long (.getTileHeight img))
        min-tx (long (.getMinTileX img))
        min-ty (long (.getMinTileY img))
        max-tx (+ min-tx (dec (long (.getNumXTiles img))))
        max-ty (+ min-ty (dec (long (.getNumYTiles img))))
        max-col (+ (long (.x rect)) (dec (long (.width rect))))
        max-row (+ (long (.y rect)) (dec (long (.height rect))))]
    [(max min-tx (Math/floorDiv (long (.x rect)) tw))
     (min max-tx (Math/floorDiv max-col tw))
     (max min-ty (Math/floorDiv (long (.y rect)) th))
     (min max-ty (Math/floorDiv max-row th))]))

(defn get-tiles
  "Takes a coverage object and a rectangle and returns a set of tiles containing these points."
  [^GridCoverage2D cov ^Rectangle rect]
  (let [^RenderedImage img (.getRenderedImage cov)
        [tx0 tx1 ty0 ty1] (tile-bounds img rect)]
    (vec (for [tx (range tx0 (inc tx1))
               ty (range ty0 (inc ty1))]
           (.getTile img (int tx) (int ty))))))


(defn- null-tile-error?
  "The JAI tile scheduler occasionally hands a waiting thread a null tile when
  several threads read the same RenderedOp at once. Retrying the read fixes it."
  [e]
  (and (instance? RuntimeException e)
       (some-> (ex-message e) (str/includes? "null tile"))))

(defn sum-tiles
  "Takes a coverage object and a rectangle and returns the sum of the pixel
  values in it. Pixels are assigned by their centres, so regions that share a
  border never count the same pixel twice."
  [^GridCoverage2D cov ^Rectangle rect]
  (if (or (<= (.width rect) 0) (<= (.height rect) 0))
    0.0
    (loop [attempt 1]
      (let [outcome (try
                      {:value
                       (let [^RenderedImage img (.getRenderedImage cov)
                             [tx0 tx1 ty0 ty1] (tile-bounds img rect)
                             tiles (vec (for [tx (range tx0 (inc tx1))
                                              ty (range ty0 (inc ty1))]
                                          [tx ty]))]
                         (r/fold + (r/map (fn [[tx ty]]
                                            (sum-tile (.getTile img (int tx) (int ty)) rect))
                                          tiles)))}
                      (catch RuntimeException e
                        (if (and (null-tile-error? e) (< attempt 4))
                          {:retry true}
                          (throw e))))]
        (if (:retry outcome)
          (do (Thread/sleep 20) (recur (inc attempt)))
          (:value outcome))))))

(defn determine-cut-axis
  "returns :vertical if the real-word dimension is longer in the north-south
  direction. Else it returns :horizontal"
  [{:keys [min-lon max-lon min-lat max-lat]}]
  (let [x (- max-lon min-lon)
        y (- max-lat min-lat)
        mid-lat (/ (+ min-lat max-lat) 2.0)
        reduction-factor-at-mid-lat (Math/cos (/ (* Math/PI mid-lat) 180))]
    (if (> (* reduction-factor-at-mid-lat x) y) :horizontal :vertical)))

(defn cannot-be-divided
  "True when the region cannot be split any further: too small a population, a
  midpoint outside the raster, or fewer than two pixels in the split axis. The
  last case is what stops the tree at pixel scale: a region whose pixel set has
  one row or column cannot produce two non-empty halves."
  [^Rectangle grid cut-axis {:keys [min-lon max-lon min-lat max-lat population]}]
  (let [g-min-lon -180 g-max-lon 179.9999986
        g-min-lat -59.9999994 g-max-lat 84
        mid-lon (/ (+ min-lon max-lon) 2)
        mid-lat (/ (+ min-lat max-lat) 2)]
    (if (< population 1) true
        (case cut-axis
          :vertical (or (> mid-lat g-max-lat) (< mid-lat g-min-lat)
                        (<= (.getHeight grid) 1))
          :horizontal (or (> mid-lon g-max-lon) (< mid-lon g-min-lon)
                          (<= (.getWidth grid) 1))))))

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
  subdivided because a region has too small of population or is at pixel scale"
  [coverage regions]
  (let [sorted (sort-by :population regions)
        region (last sorted)
        cut-axis (determine-cut-axis region)]
    (if (or (>= (count regions) 8)
            ;; check population before paying for to-grid
            (< (:population region) 1)
            (cannot-be-divided (to-grid coverage region) cut-axis region))
      (map #(assoc %1 :materialized-path (str (:materialized-path %1) %2))
           (sort-by :binary-path regions) "abcdefgh")
      (recur coverage
             (concat (drop-last sorted) (split-region coverage region cut-axis))))))

(def ^:private parent-batch-size
  "Parents per insert batch. Small enough to keep memory flat, large enough that
  each batch becomes one reasonably big insert transaction."
  256)

(def ^:private parent-chunk-size
  "Parents per parallel task inside a batch."
  32)

(def ^:private read-batch-size
  "Parents read per query. Each query is its own read transaction, so the WAL
  can checkpoint in between and memory stays flat however deep the layer is."
  20000)

(defn- parent-batches
  "Reads the parent layer lazily, batch by batch, in id order."
  [layer-number]
  (let [len (dec layer-number)]
    ((fn step [after-id]
       (lazy-seq
        (let [rows (jdbc/execute! s/ds
                                  ["select id, materialized_path, population, binary_path,
                                           min_lon, max_lon, min_lat, max_lat
                                    from region
                                    where id > ? and length(materialized_path) = ?
                                    order by id limit ?"
                                   after-id len read-batch-size]
                                  {:builder-fn rs/as-unqualified-kebab-maps})]
          (when (seq rows)
            (cons rows (step (:id (peek rows))))))))
     0)))

(defn- write-batch!
  "Splits one batch of parents in parallel and inserts the resulting regions
  in a single transaction."
  [coverage batch]
  (let [regions (->> (partition-all parent-chunk-size batch)
                     (cp/pmap 8 (fn [chunk]
                                  (mapcat (fn [region]
                                            (let [r (make-eight-regions coverage [region])]
                                              (when (> (count r) 1) r)))
                                          chunk)))
                     (mapcat identity)
                     (doall))]
    (when (seq regions)
      (s/insert-regions! regions))))

(defn write-layer! [layer-number]
  (let [file-name "global_pop_2027_CN_1km_R2025A_UA_v1.tif"
        coverage (.read (GeoTiffReader. (io/resource file-name)) nil)]
    ;; Compute in parallel but insert from this thread in large batches: SQLite
    ;; has a single writer, and many small concurrent transactions spend most of
    ;; their time on fsync. Parents are read a batch at a time so memory does
    ;; not grow with the layer size.
    (doseq [rows (parent-batches layer-number)
            batch (partition-all parent-batch-size rows)]
      (write-batch! coverage batch))))

(defn write-first-layer! []
  (let [file-name "global_pop_2027_CN_1km_R2025A_UA_v1.tif"
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
  ;; layer 1 27 seconds, 8 regions - threadpool 8
  ;; layer 2 45 seconds, 64 regions - threadpool 8
  ;; layer 3 33 seconds, 480 regions - threadpool 8
  ;; layer 4 22 seconds, 3616 regions - threadpool 8
  ;; layer 5 16 seconds, 26567 regions - threadpool 8
  ;; layer 6 15 seconds, 191925 regions - threadpool 8
  ;; layer 7 33 seconds, 1.26M regions - threadpool 8
  ;; layer 8 124 seconds, 5.98M regions - threadpool 8
  ;; layer 9 335 seconds, 16.2M regions
  ;; layer 10 554 seconds, 22.6M regions
  ;; layer 11 528 seconds, 17.5M regions
  ;; layer 12 308 seconds, 8M regions
  ;; layer 13 116 seconds, 2.04M regions
  ;; layer 14 31 seconds, 248687 regions
  ;; layer 15 9 seconds, 13578 regions
  ;; layer 16 6 seconds, 372 regions
  ;; layer 17 5 seconds, 2 regions
  ;; layer 18 5 seconds, 0 regions
  (time (-main 18))

;; Data source​ worldpop.org.
;; old file​ ppp_2020_1km_Aggregated.tif
;; Bounds​ -180.0, -59.999999423999995, 179.99999856000005, 84.0
;; [-180.0 179.99999856000005 -59.999999423999995 84.0]
  :dbg)
