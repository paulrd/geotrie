(ns ca.cawala.sql
  (:require
   [honey.sql :as s]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(def db {:dbtype "sqlite" :dbname "trie.db"})
(def ds (jdbc/get-datasource db))

(defn create-region-table []
  (jdbc/execute! ds ["create virtual table if not exists region using
       rtree(id, min_lon, max_lon, min_lat, max_lat, +population, +binary_path,
       +materialized_path)"]))

(defn insert-regions! [regions]
  (jdbc/execute! ds (s/format {:insert-into [:region] :values regions})))

(defn get-layer [layer-number]
  (jdbc/execute! ds (s/format {:select [:*] :from [:region]
                               :where [:= [:length :materialized-path] layer-number]})
                 {:builder-fn rs/as-unqualified-kebab-maps}))

(comment
  (jdbc/execute! ds (s/format {:select [:*] :from [:region]
                               :where [:= [:length :materialized-path] 7]})
                 {:builder-fn rs/as-unqualified-kebab-maps})

  (jdbc/execute! ds (s/format {:insert-into [:region] :values mr}))
  (get-layer 1)

  (create-region-table)
  (s/format {:select [:*] :from [:region] :where [:= :id 1]})
  (s/format '{select [*] from [region] where [= id 1]})
  (insert-regions! [{:min_x -180 :max_x 180 :min_y -90 :max_y 90
                     :population 44 :binary_path "abc"}])
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
  ;; label each of the regions lexically such that shorter strings come first
  ;; and characters have this ordering: w < e < s < n. Then we give the label
  ;; 'a' to the first region all the way to the 'h' for the eighth region. This
  ;; would mean larger regions would come first in the order followed by those
  ;; that have lower longitude then lower latitude.

  (+ 1 2))
