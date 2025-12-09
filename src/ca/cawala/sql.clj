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
  (create-region-table)

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

  (+ 1 2))
