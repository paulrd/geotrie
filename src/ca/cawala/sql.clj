(ns ca.cawala.sql
  (:require [sqlite4clj.core :as d]
            [next.jdbc :as jdbc]
            [honey.sql :as s]))

#_(defonce db (d/init-db! "trie.db"))
(def db {:dbtype "sqlite" :dbname "trie.db"})
(def ds (jdbc/get-datasource db))

(defn create-region-table []
  (jdbc/execute! ds ["create virtual table if not exists region using
       rtree(id, min_lon, max_lon, min_lat, max_lat, +population, +binary_path,
       +materialized_path)"]))

(defn insert-regions! [regions]
  (jdbc/execute! ds (s/format {:insert-into [:region] :values regions}))
  #_(d/q (:writer db) (s/format {:insert-into [:region] :values regions})))

(comment
  (def db {:dbtype "sqlite" :dbname "trie.db"})

  (jdbc/execute! ds ["create table address (id, name, email)"])
  (jdbc/execute! ds (s/format {:insert-into [:region] :values mr}))

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
