(ns ca.cawala.geotrie
  (:require [clojure.core.reducers :as r]
            [clojure.java.io :as io])
  (:import
   (java.awt Rectangle)
   (java.awt.image Raster)
   (org.geotools.coverage.grid GridCoordinates2D GridEnvelope2D GridGeometry2D)
   (org.geotools.coverage.grid.io AbstractGridFormat)
   (org.geotools.coverage.processing CoverageProcessor)
   (org.geotools.gce.geotiff GeoTiffReader)
   (org.geotools.geometry Position2D)
   (org.geotools.geometry.jts ReferencedEnvelope)))

(defn sum-tile [raster min-col min-row max-col max-row]
  (let [tile-rect (.getBounds raster)
        region-rect (Rectangle. min-col min-row (- max-col min-col) (- max-row min-row))
        rect (.intersection tile-rect region-rect)]
    (apply + (for [x (range (.getWidth rect))
                   y (range (.getHeight rect))]
               (.getSampleFloat raster (+ x (.getX rect)) (+ y (.getY rect)) 0)))))

(defn sum-area [region min-col min-row max-col max-row]
  (let [img (.getRenderedImage region)
        minX (.getMinTileX img)
        minY (.getMinTileY img)
        tiles (vec (for [x (range minX (+ minX (.getNumXTiles img)))
                         y (range minY (+ minY (.getNumYTiles img)))]
                     [x y]))
        tileWidth (.getTileWidth img)
        tileHeight (.getTileHeight img)]
    (->> tiles
         (r/filter #(not (or (<= (* (inc (first %)) tileWidth) min-col) ; tile is too far left
                             (> (* (first %) tileWidth) max-col)        ; tile is too far right
                             (<= (* (inc (last %)) tileHeight) min-row) ; tile is too far above
                             (> (* (last %) tileHeight) max-row)        ; tile is too far below
                             )))
         (r/map #(.getTile img (first %) (last %)))
         (r/map #(sum-tile % min-col min-row max-col max-row))
         (r/filter #(< 0 %))
         (r/fold +))))

(defn to-grid
  "Converts from lat/lon to grid coordinates."
  [cov min-lon max-lon min-lat max-lat]
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

(comment
;; Data source: worldpop.org.
;; old file: ppp_2020_1km_Aggregated.tif
;; Bounds: -180.0, -59.999999423999995, 179.99999856000005, 84.0
;; [-180.0 179.99999856000005 -59.999999423999995 84.0]
  (do
    (def file-name "global_pop_2026_CN_1km_R2025A_UA_v1.tif")
    (def meters-per-degree 111320)) ; to use to get approximate dimensions of a 'square' lat/lon
  (io/resource file-name)
  (* 85 34)
  (* 85 512)
  (* 34 512)
  :dbg)
