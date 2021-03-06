(ns clime.node
  (:require [clojure.string :as cs])
  (:require [clime.cota :refer :all]
            [clime.exception :refer :all])
  (:refer-clojure :exclude [resolve compile type]))

(declare render-children)

(defn- resolve-tokens
  [tokens context]
  (if (empty? tokens)
    context
    (let [token (first tokens)]
      (resolve-tokens (rest tokens) (context token)))))

(defn resolve
  [name context]
  (let [resolve' #(resolve-tokens (cs/split %1 #"\.") %2)]
    (if (.startsWith name "@")
      (let [context (context "@" {})
            name (subs name 1)]
        (resolve' name context))
      (resolve' name context))))

(defprotocol Node
  (creates-scope [_])
  (enter-scope [_])
  (exit-scope [_])
  (process-fragment [self])
  (render [self context]))

(defrecord Root [children]
  Node
  (render [self context]
    (render-children self context)))

(defrecord Variable [children name]
  Node
  (creates-scope [_] false)
  (process-fragment [self] self)
  (render [self context]
    (resolve (:name self) context)))

(defn render-children
  ([node context]
   (render-children node context nil))
  ([node context children]
   (let [render-child (fn [child]
                        (let [child-html (render child context)]
                          (if child-html
                            (str child-html) "")))]
     (if (nil? children)
       (let [children (:children node)]
         (cs/join "" (map render-child children)))
       (cs/join "" (map render-child children))))))

(defn eval-expression
  [node exp key]
  (let [ast-eval (read-string exp)]
    (assoc node key [(if (symbol? ast-eval) "name" "literal") ast-eval])))

(defrecord Text [children text]
  Node
  (creates-scope [_] false)
  (process-fragment [self] self)
  (render [self _] (:text self)))

(defrecord Each [children fragment]
  Node
  (creates-scope [_] true)
  (enter-scope [self] (do (prn "enter each scope") self))
  (exit-scope [self] (do (prn "exit each scope") self))
  (process-fragment [self]
    (let [it (apply str (-> (cs/split (:fragment self) WHITESPACE) rest))]
      (eval-expression self it :it)))
  (render [self context]
    (let [it (:it self)
          items (if (= (first it) "literal")
                  (second it)
                  (resolve (str (second it)) context))
          render-item (fn [item]
                        (render-children self {"@"    context
                                               "item" item}))]
      (cs/join "" (map render-item items)))))

(defrecord Else [children fragment]
  Node
  (creates-scope [_] false)
  (process-fragment [self] self)
  (render [_ _]))

(defn resolve-side
  [side context]
  (if (= (first side) "literal")
    (second side)
    (resolve (str (second side)) context)))

(defn split-children
  [node]
  (loop [children (:children node)
         curr :if-branch
         branchs {:if-branch   []
                  :else-branch []}]
    (let [child (first children)]
      (if ((complement empty?) children)
        (if (instance? Else @child)
          (recur (rest children) :else-branch branchs)
          (recur (rest children) curr (update-in branchs [curr] conj child)))
        (let [{:keys [if-branch else-branch]} branchs]
          {:if-branch   (mapv #(deref-nested-atoms % :children) if-branch)
           :else-branch (mapv #(deref-nested-atoms % :children) else-branch)})))))

(defrecord If [children fragment]
  Node
  (creates-scope [_] true)
  (enter-scope [self] (do (prn "enter if scope") self))
  (exit-scope [self] (do (prn "exit if scope")
                         (let [branches (split-children self)]
                           (merge self branches))))
  (process-fragment [self]
    (let [fragment (:fragment self)
          bits (drop-v 1 (cs/split fragment WHITESPACE))]
      (if (not (#{1 3} (count bits)))
        (throw-template-syntax-error fragment)
        (let [self-with-lhs (eval-expression self (nth bits 0) :lhs)]
          (if (= (count bits) 3)
            (eval-expression
              (assoc
                self-with-lhs :op (nth bits 1))
              (nth bits 2) :rhs)
            self-with-lhs)))))
  (render [self context]
    (let [lhs (resolve-side (:lhs self) context)
          exec-if-branch (if-let [op-name (:op self)]
                           (if-let [op (operator_lookup_table op-name)]
                             (let [rhs (resolve-side (:rhs self) context)]
                               (op lhs rhs))
                             (throw-template-syntax-error op-name))
                           (truth? lhs))]
      (render-children self context (if exec-if-branch
                                      (:if-branch self)
                                      (:else-branch self))))))

(defrecord Call [children fragment]
  Node
  (creates-scope [_] false)
  (process-fragment [self]
    (let [bits (cs/split fragment WHITESPACE)
          callable (second bits)
          args (map read-string (drop-v 2 bits))]
      (merge self (create-kw-map callable args))))
  (render [self context]
    (let [callable (resolve (:callable self) context)
          args (map (fn [arg]
                      (if (symbol? arg)
                        (resolve (str arg) context) arg))
                    (:args self))]
      (apply callable args))))

