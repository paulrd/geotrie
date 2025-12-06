(ns ca.cawala.sql
  (:require [sqlite4clj.core :as d]))

(defonce db (d/init-db! "trie.db"))

(defn create-region-table []
  (d/q (:writer db)
       ["create virtual table if not exists region using rtree(id, min_x, max_x,
       min_y, max_y, +population, +binary_path)"]))

(defn insert-region [min-x max-x min-y max-y population path]
  (d/q (:writer db)
       [(str "INSERT INTO region (min_x, max_x, min_y, max_y, population, "
             "binary_path) values (?, ?, ?, ?, ?, ?)")
        min-x max-x min-y max-y population path]))

(comment
  (create-region-table)
  (insert-region -180 180 -90 90 88 "nsew")

  (d/q (:reader db)
       ["select * from region"])

  (type 8)
  (Math/cos (/ (* Math/PI 84) 90 2))
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
