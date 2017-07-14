(ns abio.io
  (:require-macros
    [abio.io :refer [with-open]])
  (:require
    [abio.core :refer [*io-bindings*]]
    [clojure.string :as string])
  (:import
    (goog.string StringBuffer)
    (goog Uri)))

;; XXX: Why does this exist?
(defprotocol IBindings
  (-path-sep [this])
  (-directory? [this f])
  (-list-files [this d])
  (-async-list-files [this d])
  (-delete-file [this f])
  (-file-reader-open [this path encoding])
  (-async-file-reader-open [this path encoding])
  (-file-reader-read [this reader])
  (-file-reader-close [this reader]))

(def
  ^{:doc "An abio.io/IReader representing standard input for read operations."
    :dynamic true}
  *in*)


; Protocols (a.k.a if something implements a protocol, it can do the following)
;; How to close a thing
(defprotocol IClosable
  (-close [this]))

;; Sync/Async Writer
(defprotocol IWriter
  "Protocol for writing."
  (-write [this] "Writes output to a file."))

(defprotocol IAsyncWriter
  "Protocol for asynchronous writing."
  (-write [this] "Returns a channel that eventually may contain an error, or nothing, depending on the outcome of the write."))

;; Sync/Async Reader
(defprotocol IReader
  "Protocol for reading."
  (-read [this] "Returns available characters as a string or nil if EOF."))

(defprotocol IAsyncReader
  "Protocol for asynchronous reading."
  (-read [this]
    "Returns a channel that will eventually contain the available characters as a string or nil if EOF."))

;; Sync/Async Buffered Reader
(defprotocol IBufferedReader
  "Protocol for reading line-based content."
  (-read-line [this] "Reads the next line."))

(defprotocol IAsyncBufferedReader
  "Protocol for asynchronously reading line-based content."
  (-read-line [this]
    "Returns a channel that will eventually contain the available characters as a string or nil if EOF."))

;; Streams
;; TODO: implement async version?
(defprotocol IInputStream
  "Protocol for reading binary data."
  (-read-bytes [this] "Returns available bytes as an array of unsigned numbers or nil if EOF."))

(defprotocol IOutputStream
  "Protocol for writing binary data."
  (-write-bytes [this byte-array] "Writes byte array.")

  (-flush-bytes [this] "Flushes output."))

;; Coerce between a url and a file
(defprotocol Coercions
  "Coerce between various 'resource-namish' things."
  (as-file [x] "Coerce argument to a File.")
  (as-url [x] "Coerce argument to a goog.Uri."))

(defprotocol IOFactory
  "Factory functions that create ready-to-use versions of
  the various stream types, on top of anything that can
  be unequivocally converted to the requested kind of stream.

  Common options include

    :append   true to open stream in append mode
    :encoding  string name of encoding to use, e.g. \"UTF-8\".

    Callers should generally prefer the higher level API provided by
    reader, writer, input-stream, and output-stream."
  (make-reader [x opts] "Creates an IReader. See also IOFactory docs.")
  (make-writer [x opts] "Creates an IWriter. See also IOFactory docs.")
  (make-async-reader [x opts] "Creates an IAsyncReader. See also IOFactory docs.")
  (make-async-writer [x opts] "Creates an IAsyncWriter. See also IOFactory docs.")
  (make-input-stream [x opts] "Creates an IInputStream. See also IOFactory docs.")
  (make-output-stream [x opts] "Creates an IOutputStream. See also IOFactory docs."))

;; Concrete implementation of a File with Protocols defined
;; Here we abstract on the base class that there's a toString function
(defrecord File [path]
  Object
  (toString [_] path))

(defn- build-uri
  "Builds a URI"
  [scheme server-name server-port uri query-string]
  (doto (Uri.)
    (.setScheme (name (or scheme "http")))
    (.setDomain server-name)
    (.setPort server-port)
    (.setPath uri)
    (.setQuery query-string true)))

;; Given some protocol Foo, you can deftype or defrecord a class and have it implement concrete
;; versions of Foo, OR you can extend-protocol Foo for those specific classes
;; Any nil, string, or File now supports as-file/as-url
(extend-protocol Coercions
  nil
  (as-file [_] nil)
  (as-url [_] nil)

  string
  (as-file [s] (->File s))
  (as-url [s] (Uri. s))

  File
  (as-file [f] f)
  (as-url [f] (build-uri :file nil nil (:path f) nil)))

(defn- as-url-or-file [f]
  (if (string/starts-with? f "http")
    (as-url f)
    (as-file f)))

;; Now string and File have implementations of IOFactory, 
(extend-protocol IOFactory
  string
  (make-reader [s opts]
    (make-reader (as-url-or-file s) opts))
  (make-async-reader [s opts]
    (make-async-reader (as-url-or-file s) opts))
  (make-writer [s opts]
    (make-writer (as-url-or-file s) opts))
  (make-async-writer [s opts]
    (make-async-writer (as-url-or-file s) opts))
  (make-input-stream [s opts]
    (make-input-stream (as-file s) opts))
  (make-output-stream [s opts]
    (make-output-stream (as-file s) opts))

  File
  (make-reader [file opts]
    (-file-reader-open *io-bindings* (:path file) (:encoding opts)))
  (make-async-reader [file opts]
    (-async-file-reader-open *io-bindings* (:path file) (:encoding opts)))
  (make-writer [file opts]
    ; TODO
    )
  (make-input-stream [file opts]
    ; TODO
    )
  (make-output-stream [file opts]
    ; TODO
    )

  ;; XXX: does this need to be "Object"?
  default
  (make-reader [x _]
    (if (satisfies? IReader x)
      x
      (throw (ex-info (str "Can't make a reader from " x) {}))))
  (make-writer [x _] nil
    (if (satisfies? IWriter x)
      x
      (throw (ex-info (str "Can't make a writer from " x) {}))))
  (make-input-stream [x _]
    (if (satisfies? IInputStream x)
      x
      (throw (ex-info (str "Can't make an input stream from " x) {}))))
  (make-output-stream [x _]
    (if (satisfies? IOutputStream x)
      x
      (throw (ex-info (str "Can't make an output stream from " x) {})))))

;; Helper/Constructor functions
(defn reader
  "Attempts to coerce its argument into an open IBufferedReader."
  [x & opts]
  (make-reader x (when opts (apply hash-map opts))))

(defn async-reader
  "Attempts to coerce its argument into an open IAsyncReader."
  [x & opts]
  (make-async-reader x (when opts (apply hash-map opts))))

(defn writer
  "Attempts to coerce its argument into an open IWriter."
  [x & opts]
  (make-writer x (when opts (apply hash-map opts))))

(defn input-stream
  "Attempts to coerce its argument into an open IInputStream."
  [x & opts]
  (make-input-stream x (when opts (apply hash-map opts))))

(defn output-stream
  "Attempts to coerce its argument into an open IOutputStream."
  [x & opts]
  (make-output-stream x (when opts (apply hash-map opts))))

(defn path-separator
  "Returns the path separator for the system."
  []
  (-path-sep *io-bindings*))

(defn file
  "Returns a File for given path.  Multiple-arg
   versions treat the first argument as parent and subsequent args as
   children relative to the parent."
  ([path]
   (->File path))
  ([parent & more]
   (->File (apply str parent (interleave (repeat (path-separator)) more)))))

(defn delete-file
  "Delete file f."
  [f]
  (-delete-file *io-bindings* (:path (as-file f))))

(defn ^boolean directory?
  "Checks if dir is a directory."
  [dir]
  (-directory? *io-bindings* (:path (as-file dir))))

(defn read-line
  "Reads the next line from the current value of abio.io/*in*"
  []
  (-read-line *in*))

(defn line-seq
  "Returns the lines of text from rdr as a lazy sequence of strings.
  rdr must implement IBufferedReader."
  [rdr]
  (when-let [line (-read-line rdr)]
    (cons line (lazy-seq (line-seq rdr)))))

(defn file-seq
  "A tree seq on files"
  [dir]
  (tree-seq
    (fn [f] (-directory? *io-bindings* (:path f)))
    (fn [d] (map as-file
              (-list-files *io-bindings* (:path d))))
    (as-file dir)))

(defn slurp
  "Opens a reader on f and reads all its contents, returning a string.
  See abio.io/reader for a complete list of supported arguments."
  [f & opts]
  (with-open [r (apply reader f opts)]
    (let [sb (StringBuffer.)]
      (loop [s (-read r)]
        (if (nil? s)
          (.toString sb)
          (do
            (.append sb s)
            (recur (-read r))))))))

(defn spit
  "Opposite of slurp.  Opens f with writer, writes content, then
  closes f. Options passed to abio.io/writer."
  [f content & opts]
  (with-open [w (apply writer f opts)]
    (-write w (str content))))
