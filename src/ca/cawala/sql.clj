(ns ca.cawala.sql
  (:require
   [clojure.string :as str]
   [honey.sql :as s]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.result-set :as rs])
  (:import
   [com.zaxxer.hikari HikariDataSource]))

(def db {:dbtype "sqlite" :dbname "trie.db"})

(def ds
  ;; WAL lets readers and the single writer coexist. synchronous=NORMAL skips
  ;; an fsync per commit (crash-safe, may lose the last transactions on power
  ;; loss) because this db can always be rebuilt. The longer busy timeout makes
  ;; writers wait for the write lock instead of failing with SQLITE_BUSY after
  ;; the driver's 3s default, which is too short when the build is also
  ;; hammering the disk reading the GeoTIFF.
  (connection/->pool HikariDataSource
                     {:jdbcUrl (connection/jdbc-url
                                (assoc db
                                       :busy_timeout 30000
                                       :journal_mode "WAL"
                                       :synchronous "NORMAL"))}))

(defn create-tables
  "Drops any existing tables and recreates them. Destroys all existing data."
  []
  (jdbc/execute! ds ["drop table if exists geo_index"])
  (jdbc/execute! ds ["drop table if exists region"])
  (jdbc/execute! ds ["create virtual table geo_index using
       rtree(id, min_lon, max_lon, min_lat, max_lat)"])
  (jdbc/execute! ds ["create table region(id integer primary key,
population, binary_path, materialized_path,
min_lon, max_lon, min_lat, max_lat)"])
  ;; materialized_path is the natural key of a region: it uniquely identifies
  ;; the region within the trie, and the unique index lets insert-regions!
  ;; upsert instead of duplicating rows when the same data is inserted again.
  (jdbc/execute! ds ["create unique index idx_region_path on region(materialized_path)"]))

(defn- sqlite-locked?
  "True when the exception says SQLite could not get a lock. Error codes 5 and
  6 are SQLITE_BUSY and SQLITE_LOCKED."
  [e]
  (and (instance? java.sql.SQLException e)
       (contains? #{5 6} (.getErrorCode ^java.sql.SQLException e))))

(defn- with-busy-retry
  "Calls f, retrying with backoff if SQLite reports the database is locked.
  f must be a whole transaction so a retry cannot leave partial writes."
  [f]
  (loop [attempt 1]
    (let [outcome (try
                    {:value (f)}
                    (catch java.sql.SQLException e
                      (if (and (sqlite-locked? e) (< attempt 4))
                        {:retry (* 500 attempt)}
                        (throw e))))]
      (if-some [delay-ms (:retry outcome)]
        (do (Thread/sleep (long delay-ms)) (recur (inc attempt)))
        (:value outcome)))))

(def ^:private max-rows-per-statement
  "Stay well below SQLite's bound-variable limit when building multi-row SQL."
  1000)

(defn- placeholders
  "A comma-separated list of n groups of placeholders, e.g. (?,?,?),(?,?,?)."
  [n per-row]
  (str/join "," (repeat n (str "(" (str/join "," (repeat per-row "?")) ")"))))

(defn- in-placeholders [n]
  (str "(" (str/join "," (repeat n "?")) ")"))

(defn- insert-batch!
  "Insert up to max-rows-per-statement regions using set-based statements:
  one select for what already exists, one multi-row upsert, one select for the
  ids, and one multi-row insert into geo_index for the rows that are new.
  Returns a map of materialized-path -> id."
  [tx rows]
  (let [n (count rows)
        paths (mapv :materialized-path rows)
        in (in-placeholders n)
        existing (into #{}
                       (map :materialized-path)
                       (jdbc/execute! tx
                                      (into [(str "select materialized_path from region
                                                   where materialized_path in " in)] paths)
                                      {:builder-fn rs/as-unqualified-kebab-maps}))]
    (jdbc/execute! tx
                   (into [(str "insert into region (population, binary_path, materialized_path,
                                                      min_lon, max_lon, min_lat, max_lat) values "
                               (placeholders n 7)
                               " on conflict(materialized_path) do update
                                 set population = excluded.population,
                                     binary_path = excluded.binary_path,
                                     min_lon = excluded.min_lon,
                                     max_lon = excluded.max_lon,
                                     min_lat = excluded.min_lat,
                                     max_lat = excluded.max_lat")]
                         (mapcat (juxt :population :binary-path :materialized-path
                                       :min-lon :max-lon :min-lat :max-lat) rows)))
    (let [by-path (into {}
                        (map (juxt :materialized-path :id))
                        (jdbc/execute! tx
                                       (into [(str "select id, materialized_path from region
                                                    where materialized_path in " in)] paths)
                                       {:builder-fn rs/as-unqualified-kebab-maps}))
          ;; one geo_index row per new path (a batch may repeat a path)
          new-rows (vals (reduce (fn [m row]
                                   (if (contains? existing (:materialized-path row))
                                     m
                                     (assoc m (:materialized-path row) row)))
                                 {}
                                 rows))]
      (when (seq new-rows)
        (jdbc/execute! tx
                       (into [(str "insert into geo_index (id, min_lon, max_lon, min_lat, max_lat) values "
                                   (placeholders (count new-rows) 5))]
                             (mapcat (fn [{:keys [materialized-path min-lon max-lon min-lat max-lat]}]
                                       [(by-path materialized-path) min-lon max-lon min-lat max-lat])
                                     new-rows))))
      by-path)))

(defn insert-regions!
  "Inserts each region into the region and geo_index tables, using the same
  generated id for both rows. Each region is a map containing :population,
  :binary-path, :materialized-path, :min-lon, :max-lon, :min-lat and :max-lat.
  Returns the ids of the regions.

  Idempotent: materialized-path identifies a region. An already-present region
  keeps its id and its geo_index row; only its population and binary-path are
  refreshed. A region's bounds are determined by its materialized-path, so they
  never need updating for a path that already exists. Rebuild with
  create-tables if the subdivision itself changes.

  Uses set-based statements, so pass regions in large batches (hundreds or
  thousands) rather than one row at a time."
  [regions]
  (if (empty? regions)
    []
    (with-busy-retry
      (fn []
        (jdbc/with-transaction [tx ds]
          (let [by-path (reduce (fn [m batch]
                                  (merge m (insert-batch! tx batch)))
                                {}
                                (partition-all max-rows-per-statement regions))]
            (mapv (comp by-path :materialized-path) regions)))))))

(defn get-layer
  "Rows of the given layer, including bounds (region carries them, so this does
  not need the spatial index)."
  [layer-number]
  (jdbc/execute! ds ["select * from region where length(materialized_path) = ?" layer-number]
                 {:builder-fn rs/as-unqualified-kebab-maps}))

(defn count-regions-in-layer
  "Returns the number of regions in the given layer, i.e. regions whose
  materialized-path is exactly layer-number characters long."
  [layer-number]
  (:n (jdbc/execute-one!
       ds
       ["select count(*) as n from region where length(materialized_path) = ?" layer-number]
       {:builder-fn rs/as-unqualified-lower-maps})))

#_(defn get-layer [layer-number]
  (jdbc/execute! ds (s/format {:select [:*] :from [:region]
                               :where [:= [:length :materialized-path] layer-number]})
                 {:builder-fn rs/as-unqualified-kebab-maps}))
(comment
  (create-tables)

  ;; Encode splits as nsew - north, south, east or west. Each represents a split
  ;; in the region either horizontally or vertically in half, then choosing one
  ;; of these regions. We will split the region with the largest population in
  ;; half recursively until we get 8 regions. It will be split so that the
  ;; length and height of the sub-regions remain as square as possible. That is,
  ;; always slitting along the axis that that halves the longest edge of the
  ;; rectangle. Then we choose the region that has the largest population of
  ;; among the regions so far to split again. Each step adds 2 new regions of
  ;; similar size but removes the region that was split.

  ;; Regions may not be split evenly, so 3 to 7 characters will be needed to
  ;; describe each of the 8 regions if we want to create an octal trie. We can
  ;; label each of the regions lexically, then we give the label 'a' to the
  ;; first region all the way to the 'h' for the eighth region. This means that
  ;; eastern regions come before northern before southern before western.

  (get-layer 1)
  (count-regions-in-layer 8)
  )
