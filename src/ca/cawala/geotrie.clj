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
;; load tiff file to memory

  (let [file (File. "/home/paul/Downloads/ppp_2020_1km_Aggregated.tif")
        reader (GeoTiffReader. file)
        cov (.read reader nil)
        image (.getRenderedImage cov)
        meters-per-degree 111320
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
  (type image)

  (def lat 37.75497)
  (def lon -122.44580)
  (def min-lon -123.0)
  (def max-lon -122.0)
  (def min-lat 37.0)
  (def max-lat 38.0)
  ;;(def wgs84 DefaultGeographicCRS/WGS84)
  ;;(def gg (.getGridGeometry cov))
  ;;(def posWorld (Position2D. wgs84 lon lat))
  ;;(def posGrid (.worldToGrid gg posWorld))
  (def crs (.getCoordinateReferenceSystem cov))
  (def roi-envelope (ReferencedEnvelope. min-lon max-lon min-lat max-lat crs))
  ;;(def bbox-param (.createValue AbstractGridFormat/READ_GRIDGEOMETRY2D))
  (def desc (DefaultParameterDescriptor/create "ReadGridGeometry2D" nil (class roi-envelope) roi-envelope false))
  (def bbox-param (Parameter. desc roi-envelope))
  (.getValue bbox-param)
  (.read reader (into-array GeneralParameterValue [bbox-param]))
  ;;(Parameter/create "ReadGridGeometry2D" (class roi-envelope) ^ReferencedEnvelope roi-envelope)

  (def processor (CoverageProcessor/getInstance nil))
  (def param (.getParameters (.getOperation processor "CoverageCrop")))
  (.setValue (.parameter param "Source") cov)
  (.setValue (.parameter param "Envelope") roi-envelope)
  (def cropped (.doOperation processor param))

;; // 2. Convert geographic coordinates to pixel coordinates
;;         GridGeometry2D gridGeometry = coverage.getGridGeometry();
  (def grid-geometry (.getGridGeometry cropped))
;;         CoordinateReferenceSystem crs = gridGeometry.getCoordinateReferenceSystem();
  (def lower-left (Position2D. crs min-lon min-lat))
  (def upper-right (Position2D. crs max-lon max-lat))
;;         DirectPosition lowerLeft = new DirectPosition2D(crs, minX, minY);
;;         DirectPosition upperRight = new DirectPosition2D(crs, maxX, maxY);
  (def grid-lower-left (.worldToGrid grid-geometry lower-left))
  (def grid-upper-right (.worldToGrid grid-geometry upper-right))
;;         GridCoordinates2D gridLowerLeft = gridGeometry.worldToGrid(lowerLeft);
;;         GridCoordinates2D gridUpperRight = gridGeometry.worldToGrid(upperRight);
  (def grid-range (.getGridRange2D grid-geometry))
;;Ensure coordinates are within bounds. Note that grid coordinates have opposite x/col
  ;; directions than the world coordinates (latitude)
  (def min-col (Math/max (.x grid-lower-left) (.x grid-range)))
  (def min-row (Math/max (.y grid-upper-right) (.y grid-range)))
  (def max-col (Math/min (.x grid-upper-right) (+ (.x grid-range) (.width grid-range) -1)))
  (def max-row (Math/min (.y grid-lower-left) (+ (.y grid-range) (.height grid-range) -1)))
;;         int minCol = Math.max(gridLowerLeft.x, gridRange.x);
;;         int minRow = Math.max(gridLowerLeft.y, gridRange.y);
;;         int maxCol = Math.min(gridUpperRight.x, gridRange.x + gridRange.width - 1);
;;         int maxRow = Math.min(gridUpperRight.y, gridRange.y + gridRange.height - 1);

  (def gg (.createValue AbstractGridFormat/READ_GRIDGEOMETRY2D))
  (def grid-envelope-2D (GridEnvelope2D. min-col min-row (- max-col min-col -1) (- max-row min-row -1)))
  (.setValue gg (GridGeometry2D. grid-envelope-2D (.getGridToCRS grid-geometry) crs))
  (def region (.read reader (into-array GeneralParameterValue [gg])))

        ;; // 3. Extract the region of interest
        ;; ParameterValue<GridGeometry2D> gg = AbstractGridFormat.READ_GRIDGEOMETRY2D.createValue();
        ;; gg.setValue(new GridGeometry2D(
        ;;     new GridEnvelope2D(minCol, minRow, maxCol - minCol + 1, maxRow - minRow + 1),
        ;;     gridGeometry.getGridToCRS(),
        ;;     crs
        ;; ));

        ;; GridCoverage2D region = reader.read(new GeneralParameterValue[]{gg});

  (vec (.evaluate region (GridCoordinates2D. min-col min-row) nil))

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
