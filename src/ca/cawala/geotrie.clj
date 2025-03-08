(ns ca.cawala.geotrie
  (:import (java.io File)
           (org.geotools.gce.geotiff GeoTiffReader)
           (org.geotools.geometry Position2D)
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
        raster (.getData image)

        lat 37.75497
        lon -122.44580
        wgs84 DefaultGeographicCRS/WGS84
        gg (.getGridGeometry cov)
        posWorld (Position2D. wgs84 lon lat)
        posGrid (.worldToGrid gg posWorld)]
    (println "image is of type:" (type image)))

;; File tiffFile = new File("/development/workspace/USGS_13_n38w123_uncomp.tif");
;; GeoTiffReader reader = new GeoTiffReader(tiffFile);
;; GridCoverage2D cov = reader.read(null);
;; Raster tiffRaster = cov.getRenderedImage().getData();
  )

(comment
  (foo "Paul")
  (main)

  )
