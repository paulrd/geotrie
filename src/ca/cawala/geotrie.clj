(ns ca.cawala.geotrie
  (:import
   (org.geotools.coverage.grid GridCoordinates2D GridEnvelope2D GridGeometry2D)
   (org.geotools.coverage.grid.io AbstractGridFormat)
   (org.geotools.coverage.processing CoverageProcessor)
   (org.geotools.gce.geotiff GeoTiffReader)
   (org.geotools.geometry Position2D)
   (org.geotools.geometry.jts ReferencedEnvelope)))

(defn sum-area [region min-col min-row max-col max-row]
  (apply +
         (for [col (range min-col (inc max-col))
               row (range min-row (inc max-row))
               :let [val (first (.evaluate region (GridCoordinates2D. col row) nil))]
               :when (> val 0)]
           val)))

(defn sum-in-area [file min-lon max-lon min-lat max-lat]
  (let [reader (GeoTiffReader. file)
        cov (.read reader nil)
        meters-per-degree 111320
        crs (.getCoordinateReferenceSystem cov)
        roi-envelope (ReferencedEnvelope. min-lon max-lon min-lat max-lat crs)
        processor (CoverageProcessor/getInstance nil)
        param (.getParameters (.getOperation processor "CoverageCrop"))
        _ (.setValue (.parameter param "Source") cov)
        _ (.setValue (.parameter param "Envelope") roi-envelope)
        cropped (.doOperation processor param)
        grid-geometry (.getGridGeometry cropped)
        lower-left (Position2D. crs min-lon min-lat)
        upper-right (Position2D. crs max-lon max-lat)
        grid-lower-left (.worldToGrid grid-geometry lower-left)
        grid-upper-right (.worldToGrid grid-geometry upper-right)
        grid-range (.getGridRange2D grid-geometry)
        min-col (Math/max (.x grid-lower-left) (.x grid-range))
        min-row (Math/max (.y grid-upper-right) (.y grid-range))
        max-col (Math/min (.x grid-upper-right) (+ (.x grid-range) (.width grid-range) -1))
        max-row (Math/min (.y grid-lower-left) (+ (.y grid-range) (.height grid-range) -1))
        gg (.createValue AbstractGridFormat/READ_GRIDGEOMETRY2D)
        grid-envelope-2D (GridEnvelope2D. min-col min-row (- max-col min-col -1) (- max-row min-row -1))
        _ (.setValue gg (GridGeometry2D. grid-envelope-2D (.getGridToCRS grid-geometry) crs))
        region (.read reader (into-array [gg]))
        pop (sum-area region min-col min-row max-col max-row)]
    (println "region in pixels: " [min-col min-row max-col max-row])
    (println "population of Newfoundland: " pop)
    pop))

(def file "global_pop_2026_CN_1km_R2025A_UA_v1.tif")

(defn main
  "this region should cover the main island of newfoundland it should be around
  530,000"
  []
  (let [path (str "/home/paul/Downloads/" file)]
    (sum-in-area path -59.963389 -50.976356 46.408949 52.014232)))

;; Data source: worldpop.org.
;; old file: ppp_2020_1km_Aggregated.tif

(comment
  (main)
  )
