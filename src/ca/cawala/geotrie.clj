(ns ca.cawala.geotrie
  (:import (java.io File)
           (java.lang.management ManagementFactory)
           (org.locationtech.jts.geom GeometryFactory)
           (org.geotools.referencing.operation.projection Mollweide)
           (org.geotools.referencing CRS)
           (org.opengis.referencing.crs CoordinateReferenceSystem)
           (org.geotools.api.geometry Position)
           (org.geotools.gce.geotiff GeoTiffReader)
           (org.geotools.geometry Position2D)
           (org.geotools.geometry.jts ReferencedEnvelope)
           (org.geotools.referencing.crs DefaultGeographicCRS)))

(defn foo
  "I don't do a whole lot."
  [x]
  (prn x "Hello, World!"))

(defn main []
;; load tiff file to memory

  (let [file (File. "/home/paul/Downloads/ppp_2020_1km_Aggregated.tif")
        reader (GeoTiffReader. file)
        cov (.read reader nil)
        image (.getRenderedImage cov)
        ;; calling getData required 74% of my system memory and required 6G allocated the jvm max
        ;; heap size - in any case, this is probably not required.

        ;; setting the max heap size to 4G sometimes results in the application
        ;; killing all processes on my machine; I will leave max heap size to
        ;; the default of 2G and try to avoid putting the entire raster image in
        ;; memory.

        ;; raster (.getData image)

        max-heap-size (-> (ManagementFactory/getMemoryMXBean) .getHeapMemoryUsage .getMax)

        lat 37.75497
        lon -122.44580
        wgs84 DefaultGeographicCRS/WGS84
        gg (.getGridGeometry cov)
        posWorld (Position2D. wgs84 lon lat)
        posGrid (.worldToGrid gg posWorld)]
    (println "max heap size: " max-heap-size)
    (println "image is of type:" (type image)))

;; File tiffFile = new File("/development/workspace/USGS_13_n38w123_uncomp.tif");
;; GeoTiffReader reader = new GeoTiffReader(tiffFile);
;; GridCoverage2D cov = reader.read(null);
;; Raster tiffRaster = cov.getRenderedImage().getData();
  )

(comment
  (foo "Paul")
  (main)
  (def file (File. "/home/paul/Downloads/ppp_2020_1km_Aggregated.tif"))
  (def reader (GeoTiffReader. file))
  (def cov (.read reader nil))
  (def image (.getRenderedImage cov))

  (def lat 37.75497)
  (def lon -122.44580)
  (def min-lon -123.0)
  (def max-lon -122.0)
  (def min-lat 37.0)
  (def max-lat 38.0)
  (def wgs84 DefaultGeographicCRS/WGS84)
  (def gg (.getGridGeometry cov))
  (def posWorld (Position2D. wgs84 lon lat))
  (def posGrid (.worldToGrid gg posWorld))

  (.getNumSampleDimensions cov)
  (.getSampleDimension cov 0)
  (type (first (.evaluate cov posWorld)))
  (def crs (.getCoordinateReferenceSystem cov))
  (def roi-envelope (ReferencedEnvelope. min-lon max-lon min-lat max-lat crs))
  (def pixel-envelope (.worldToGrid gg roi-envelope))

  (.getArea roi-envelope)

  (def roi-geometry (-> (GeometryFactory.) (.toGeometry roi-envelope)))
  (def transform )

;; note from the api: getters and setters are such that the end points are
  ;; inclusive - ie getHigh returns the valid maximum inclusive grid coordinates
  (.getLow pixel-envelope)
  (.getHigh pixel-envelope) bb

  (def grid-range (.getGridRange gg))
  (def envelope (.getEnvelope cov))

  (.getSpan grid-range 0)
  (.getSpan grid-range 1)
  (vec (.getOptimalDataBlockSizes cov)))
