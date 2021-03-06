(ns clime.cota
  (:require [clojure.test :refer :all]))

(def VAR_FRAGMENT 0)
(def OPEN_BLOCK_FRAGMENT 1)
(def CLOSE_BLOCK_FRAGMENT 2)
(def TEXT_FRAGMENT 3)

(def VAR_TOKEN_START "{{")
(def VAR_TOKEN_END "}}")
(def BLOCK_TOKEN_START "{%")
(def BLOCK_TOKEN_END "%}")

(def VAR_TOKEN_START_RE "\\{\\{")
(def VAR_TOKEN_END_RE "\\}\\}")
(def BLOCK_TOKEN_START_RE "\\{\\%")
(def BLOCK_TOKEN_END_RE "\\%\\}")

(def TOK_REGEX (re-pattern (format "%s.*?%s|%s.*?%s" VAR_TOKEN_START_RE VAR_TOKEN_END_RE BLOCK_TOKEN_START_RE BLOCK_TOKEN_END_RE)))
(def WHITESPACE #"\s+")

(defn truth?
  [val]
  (and ((complement nil?) val)
       (cond
         (number? val) ((complement zero?) val)
         :else ((complement empty?) val))))

(defn drop-last-v
  [coll]
  (vec (drop-last coll)))

(defn drop-v
  [count coll]
  (vec (drop count coll)))

(defn take-str
  [str' count]
  (apply str (take count str')))

(defn drop-str
  [str' count]
  (apply str (drop count str')))

(defn drop-last-str
  [str' count]
  (apply str (drop-last count str')))

(defn take-last-str
  [str' count]
  (apply str (take-last count str')))

(defn tokenizer
  [template-str]
  (let [special (re-seq TOK_REGEX template-str)
        normal (clojure.string/split template-str TOK_REGEX)]
    (if (empty? normal)
      special
      (->> (map (fn [s n]
                  [n s]) special normal)
           (apply concat)
           (filter (complement empty?))))))

(defn deref-nested-atoms
  [atoms nested-key]
  (let [val @atoms
        nested (nested-key val)]
    (assoc val nested-key (mapv #(deref-nested-atoms % nested-key) nested))))

(def operator_lookup_table
  {"<"  <,
   ">"  >,
   "==" =,
   "!=" not=,
   "<=" <=,
   ">=" >=})

(defmacro create-kw-map
  "(let [a 1 b 2 c 3] (create-kw-map a b c)) => {:a 1 :b 2 :c 3}"
  [& syms]
  `(zipmap (map keyword '~syms) (list ~@syms)))

(defn pow
  ([] (pow 2 2))
  ([m] (pow m 2))
  ([m e] (Math/pow m e)))

;;for test

(defmacro is= [& body]
  `(is (= ~@body)))

(defmacro isnot [& body]
  `(is (not ~@body)))