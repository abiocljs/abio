(ns abio.core)

(def ^:dynamic *bindings*)

(defn set-bindings!
  [bindings]
  (set! *bindings* bindings)
  nil)
