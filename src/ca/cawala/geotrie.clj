(ns ca.cawala.geotrie
  (:import (java.io File)
           (java.lang.management ManagementFactory)
           (org.locationtech.jts.geom GeometryFactory)
           (org.geotools.coverage.grid.io AbstractGridFormat)
           (org.geotools.coverage.grid GridEnvelope2D GridGeometry2D GridCoordinates2D)
           (org.geotools.coverage.processing CoverageProcessor Operations)
           (org.geotools.referencing.operation.projection Mollweide)
           (org.geotools.referencing CRS)
           (org.opengis.referencing.crs CoordinateReferenceSystem)
           (org.geotools.api.geometry Position)
           (org.geotools.api.parameter GeneralParameterValue)
           (org.geotools.gce.geotiff GeoTiffReader)
           (org.geotools.geometry Position2D)
           (org.geotools.geometry.jts ReferencedEnvelope)
           (org.geotools.parameter Parameter DefaultParameterDescriptor)
           (org.geotools.referencing.crs DefaultGeographicCRS)))

(defn foo
  "I don't do a whole lot."
  [x]
  (prn x "Hello, World!"))

(defn main []
  (let [file (File. "/home/paul/Downloads/ppp_2020_1km_Aggregated.tif")
        reader (GeoTiffReader. file)
        cov (.read reader nil)
        meters-per-degree 111320
        ;; calling getData required 74% of my system memory and required 6G allocated the jvm max
        ;; heap size - in any case, this is probably not required.

        ;; setting the max heap size to 4G sometimes results in the application
        ;; killing all processes on my machine; I will leave max heap size to
        ;; the default of 2G and try to avoid putting the entire raster image in
        ;; memory.

        ;; raster (.getData image)

        max-heap-size (-> (ManagementFactory/getMemoryMXBean) .getHeapMemoryUsage .getMax)
        min-lon -59.963389
        max-lon -50.976356
        min-lat 46.408949
        max-lat 52.014232
        crs (.getCoordinateReferenceSystem cov)
        roi-envelope (ReferencedEnvelope. min-lon max-lon min-lat max-lat crs)
        gg (.getGridGeometry cov)
        desc (DefaultParameterDescriptor/create "ReadGridGeometry2D" nil (class roi-envelope) roi-envelope false)
        bbox-param (Parameter. desc roi-envelope)
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
        region (.read reader (into-array GeneralParameterValue [gg]))
        hi "hi"]
    (println "region in pixels: " [min-col min-row max-col max-row])
    (println "a pixel: " (vec (.evaluate region (GridCoordinates2D. min-col max-row) nil)))
    (println "max heap size: " max-heap-size)))

(comment
  (main)


;; // 4. Sum pixel values in the region
        ;; double sum = 0;
        ;; int[] pixel = new int[1]; // For single-band images
        ;; float[] floatPixel = new float[1]; // For floating-point images
        ;; for (int row = minRow; row <= maxRow; row++) {
        ;;     for (int col = minCol; col <= maxCol; col++) {
        ;;         // Handle different data types
        ;;         Object data = region.evaluate(new GridCoordinates2D(col, row));
        ;;         if (data instanceof int[]) {
        ;;             sum += ((int[]) data)[0];
        ;;         } else if (data instanceof float[]) {
        ;;             sum += ((float[]) data)[0];
        ;;         } else if (data instanceof double[]) {
        ;;             sum += ((double[]) data)[0];
        ;;         } else if (data instanceof Number) {
        ;;             sum += ((Number) data).doubleValue();
        ;;         }
        ;;     }
        ;; }
        ;; reader.dispose();
        ;; return sum;

  (.getNumSampleDimensions cov)
  (.getSampleDimension cov 0)
  (type (first (.evaluate cov posWorld)))
  (def pixel-envelope (.worldToGrid gg roi-envelope))

  (.getArea roi-envelope)

  (def roi-geometry (-> (GeometryFactory.) (.toGeometry roi-envelope)))

  ;; note from the api: getters and setters are such that the end points are
  ;; inclusive - ie getHigh returns the valid maximum inclusive grid coordinates
  (.getLow pixel-envelope)
  (.getHigh pixel-envelope) bb

  (def grid-range (.getGridRange gg))
  (def envelope (.getEnvelope cov))

  (.getSpan grid-range 0)
  (.getSpan grid-range 1)
  (vec (.getOptimalDataBlockSizes cov)))
