(ns nhl-stat-scraper.common.ranged)

(defn in-range?
  [value test-range]
  (and (<= (first test-range) value)
       (<= value (last test-range))))

(defn contains-range?
  [contained-range container-range]
  (and (<= (first container-range) (first contained-range))
       (<= (last contained-range) (last container-range))))

(defn contiguous-to-int-range?
  [value test-range]
  (and (not (in-range? value test-range))
       (or (= value (dec (first test-range)))
           (= value (inc (last test-range))))))

(defn add-to-int-range
  [value test-range]
  (if (= (+ value 1) (first test-range))
    [value (last test-range)]
    (if (= (- value 1) (last test-range))
      [(first test-range) value]
      test-range)))
