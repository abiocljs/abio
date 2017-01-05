# Abio

A set of host abstractions mimicking similar facilities available in Clojure for I/O, shell access, _etc_., along with mechanisms to bind the abstractions to host-specific implementations.

## Usage

```
$ lumo -qc abio.jar:abio-node.jar
cljs.user=> (require 'abio.core '[abio.io :as io] 'abio.node)
nil
cljs.user=> (abio.core/set-bindings! (abio.node/bindings))
nil
cljs.user=> (def users-dir (io/as-file "/Users"))
#'cljs.user/users-dir
cljs.user=> users-dir
#abio.io.File{:path "/Users"}
cljs.user=> (io/directory? users-dir)
true
cljs.user=> (io/directory? "/etc/hosts")
false
cljs.user=> (def r (io/reader "/usr/share/dict/words" :encoding "UTF8"))
#'cljs.user/r
cljs.user=> (take 8 (io/line-seq r))
("A" "a" "aa" "aal" "aalii" "aam" "Aani" "aardvark")
```

## License

Copyright © 2017 abiocljs and Contributors

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.