(ns abio.core)

(def ^:dynamic *io-bindings*)

(def ^:dynamic *shell-bindings*)

(defn set-bindings!
  [bindings]
  (set! *io-bindings* (:abio.io/bindings bindings))
  (set! *shell-bindings* (:abio.shell/bindings bindings))
  nil)
