(ns bootleg.enlive
  (:require [bootleg.file :as file]
            [bootleg.context :as context]
            [net.cgrand.enlive-html]
            [clojure.java.io :as io]
            ))

;; implement our own resource getter that abides by path
(defmethod net.cgrand.enlive-html/get-resource String
  [path loader]
  (-> path file/path-relative io/input-stream loader))

(defn at [_ _ node-or-nodes & rules]
  `(let [input-type# (or (bootleg.utils/markup-type ~node-or-nodes) :hickory)
         converted-nodes# (bootleg.utils/convert-to ~node-or-nodes :hickory-seq)]
     (-> converted-nodes# net.cgrand.enlive-html/as-nodes
         ~@(for [[s t] (partition 2 rules)]
             (if (= :lockstep s)
               `(net.cgrand.enlive-html/lockstep-transform
                 ~(into {} (for [[s t] t]
                             [(if (#'net.cgrand.enlive-html/static-selector? s) (net.cgrand.enlive-html/cacheable s) s) t])))
               `(net.cgrand.enlive-html/transform ~(if (#'net.cgrand.enlive-html/static-selector? s) (net.cgrand.enlive-html/cacheable s) s) ~t)))
         bootleg.utils/hickory-seq-add-missing-types
         (bootleg.utils/convert-to input-type#))))

(defn template [_ _ source args & forms]
  (let [[options source & body]
        (#'net.cgrand.enlive-html/pad-unless map? {} (list* source args forms))]
    `(let [opts# (merge (net.cgrand.enlive-html/ns-options (clojure.core/find-ns '~(ns-name *ns*)))
                        ~options)
           source# ~source]
       (net.cgrand.enlive-html/register-resource! source#)
       (comp #(bootleg.utils/convert-to % :hiccup-seq)
             bootleg.utils/hickory-seq-convert-dtd
             (net.cgrand.enlive-html/snippet*
              (net.cgrand.enlive-html/html-resource source# opts#)
              ~@body)))))

(defn deftemplate [_ _ name source args & forms]
  `(def ~name (net.cgrand.enlive-html/template ~source ~args ~@forms)))

(defn snippet* [_ _ nodes & body]
  (let [nodesym (gensym "nodes")]
    `(let [~nodesym (map net.cgrand.enlive-html/annotate ~nodes)]
       (fn ~@(for [[args & forms] (#'net.cgrand.enlive-html/bodies body)]
               `(~args
                 (clojure.core/doall (net.cgrand.enlive-html/flatmap (net.cgrand.enlive-html/transformation ~@forms) ~nodesym))))))))

(defn snippet
 "A snippet is a function that returns a seq of nodes."
 [_ _ source selector args & forms]
  (let [[options source selector args & forms]
         (#'net.cgrand.enlive-html/pad-unless map? {} (list* source selector args forms))]
    `(let [opts# (merge (net.cgrand.enlive-html/ns-options (clojure.core/find-ns '~(ns-name *ns*)))
                   ~options)
           source# ~source]
       (net.cgrand.enlive-html/register-resource! source#)
       (net.cgrand.enlive-html/snippet*
        (net.cgrand.enlive-html/select
         (net.cgrand.enlive-html/html-resource source# opts#)
         ~selector)
        ~args ~@forms))))

(defn defsnippet
 "Define a named snippet -- equivalent to (def name (snippet source selector args ...))."
 [_ _ name source selector args & forms]
 `(def ~name (net.cgrand.enlive-html/snippet ~source ~selector ~args ~@forms)))

(defn transformation
  ([_ _] `identity)
  ([_ _ form] form)
  ([_ _ form & forms] `(fn [node#] (net.cgrand.enlive-html/at node# ~form ~@forms))))

(defn lockstep-transformation
 [_ _ & forms] `(fn [node#] (net.cgrand.enlive-html/at node# :lockstep ~(apply array-map forms))))

(defn clone-for
 [_ _ comprehension & forms]
  `(fn [node#]
     (net.cgrand.enlive-html/flatten-nodes-coll (for ~comprehension ((net.cgrand.enlive-html/transformation ~@forms) node#)))))

(defn defsnippets
 [_ _ source & specs]
 (let [xml-sym (gensym "xml")]
   `(let [~xml-sym (net.cgrand.enlive-html/html-resource ~source)]
      ~@(for [[name selector args & forms] specs]
               `(def ~name (net.cgrand.enlive-html/snippet ~xml-sym ~selector ~args ~@forms))))))

(defn transform-content [_ _ & body]
 `(let [f# (net.cgrand.enlive-html/transformation ~@body)]
    (fn [elt#]
      (assoc elt# :content (net.cgrand.enlive-html/flatmap f# (:content elt#))))))

(defn strict-mode
 "Adds xhtml-transitional DTD to switch browser in 'strict' mode."
 [_ _ & forms]
  `(net.cgrand.enlive-html/do-> (net.cgrand.enlive-html/transformation ~@forms) strict-mode*))

(defn let-select
 "For each node or fragment, performs a subselect and bind it to a local,
  then evaluates body.
  bindings is a vector of binding forms and selectors."
 [_ _ nodes-or-fragments bindings & body]
  (let [node-or-fragment (gensym "node-or-fragment__")
        bindings
          (map (fn [f x] (f x))
            (cycle [identity (fn [spec] `(net.cgrand.enlive-html/select ~node-or-fragment ~spec))])
            bindings)]
    `(map (fn [~node-or-fragment]
            (let [~@bindings]
              ~@body)) ~nodes-or-fragments)))

(defn sniptest
 "A handy macro for experimenting at the repl"
 [_ _ source-string & forms]
  `(net.cgrand.enlive-html/sniptest*
    (net.cgrand.enlive-html/html-snippet ~source-string)
    (net.cgrand.enlive-html/transformation ~@forms)))


;; coercing transformations
(defn content
  "Replaces the content of the element. Values can be nodes or collection of nodes."
  [& values]
  (fn [el]
    (assoc el :content (apply concat (map #(bootleg.utils/convert-to % :hickory-seq) values)))))

(defn append
  "Appends the values to the content of the selected element."
  [& values]
  (fn [el]
    (assoc el :content (concat (:content el) (map #(bootleg.utils/convert-to % :hickory) values)))))

(defn prepend
  "Prepends the values to the content of the selected element."
  [& values]
  (fn [el]
    (assoc el :content (concat (map #(bootleg.utils/convert-to % :hickory) values) (:content el)))))

(defn after
  "Inserts the values after the current selection (node or fragment)."
  [& values]
  (fn [el]
    (net.cgrand.enlive-html/flatten-nodes-coll
     (cons el (map #(bootleg.utils/convert-to % :hickory-seq) values)))))

(defn before
  "Inserts the values before the current selection (node or fragment)."
  [& values]
  (fn [el]
    (net.cgrand.enlive-html/flatten-nodes-coll
     (concat (map #(bootleg.utils/convert-to % :hickory-seq) values) [el]))))

(defn substitute
  "Replaces the current selection (node or fragment)."
  [& values]
  (constantly
   (net.cgrand.enlive-html/flatten-nodes-coll
    (map #(bootleg.utils/convert-to % :hickory-seq) values))))
