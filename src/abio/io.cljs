(ns abio.io
  (:refer-clojure :exclude [-write])
  (:require-macros
   [abio.io :refer [with-open]])
  (:require
   [abio.core :refer [*io-bindings*]]
   [clojure.string :as string])
  (:import
   (goog.string StringBuffer)
   (goog Uri)))

(defprotocol IBindings
  "This is the top level protocol that defines how hosts handle high-level IO and
  reader/writer/stream creation."
  (-path-sep [this] "Return the path separator for the current host.")
  (-directory? [this f] "Is `f` a directory?")
  (-list-files
    [this d] [this d callback] "List the files in directory `d`. Pass a callback for asynchronicity.")
  (-delete-file [this f] "Delete the file at path `f`")
  (-file-writer-open [this path options] "Create a file writer.")
  (-file-reader-open [this path options] "Create a file reader."))

(def
  ^{:doc "An abio.io/IReader representing standard input for read operations."
    :dynamic true}
  *in*)

(defprotocol IClosable
  (-close [this]))

;; XXX: I had to change this from IWriter to IAbioWriter because the compiled js
;;      code would reference cljs.core/IWriter's `-write` method instead of the
;;      one defined here. I'm not sure why that is, though...
(defprotocol IAbioWriter
  "Protocol for writing."
  (-write [this output] [this output callback] "Writes output to a resource."))

(defprotocol IReader
  "Protocol for reading."
  (-read [this] [this callback] "Returns available characters as a string or nil if EOF."))

(defprotocol IBufferedReader
  "Protocol for reading line-based content."
  (-read-line [this] [this callback] "Reads the next line."))

(defprotocol IInputStream
  "Protocol for reading binary data."
  (-read-bytes [this] "Returns available bytes as an array of unsigned numbers or nil if EOF."))

(defprotocol IOutputStream
  "Protocol for writing binary data."
  (-write-bytes [this byte-array] "Writes byte array.")

  (-flush-bytes [this] "Flushes output."))

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
  (make-writer [x opts] "Creates an IAbioWriter. See also IOFactory docs.")
  (make-input-stream [x opts] "Creates an IInputStream. See also IOFactory docs.")
  (make-output-stream [x opts] "Creates an IOutputStream. See also IOFactory docs."))

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

(extend-protocol IOFactory
  string
  (make-reader [s opts]
    (make-reader (as-url-or-file s) opts))
  (make-writer [s opts]
    (make-writer (as-url-or-file s) opts))
  (make-input-stream [s opts]
    (make-input-stream (as-file s) opts))
  (make-output-stream [s opts]
    (make-output-stream (as-file s) opts))

  File
  (make-reader [file opts]
    (-file-reader-open *io-bindings* (:path file) opts))
  (make-writer [file opts]
    (-file-writer-open *io-bindings* (:path file) opts))
  (make-input-stream [file opts]) ;; TODO
  (make-output-stream [file opts]) ; TODO

  default
  (make-reader [x _]
    (if (satisfies? IReader x)
      x
      (throw (ex-info (str "Can't make a reader from " x) {}))))
  (make-writer [x _] nil
    (if (satisfies? IAbioWriter x)
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

(defn reader
  "Attempts to coerce its argument into an open IReader."
  [x & opts]
  (make-reader x (when opts (apply hash-map opts))))

(defn writer
  "Attempts to coerce its argument into an open IAbioWriter."
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
  "Returns the path separator for the host system."
  []
  (-path-sep *io-bindings*))

(defn file
  "Returns a File for given path.

  Multiple-arg versions treat the first argument as parent and subsequent args
  as children relative to the parent."
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

;; XXX Passing this a string path and opts fails, but passing an existing
;;  BufferedReader works.
;;
;; (defn waittest [ms]
;;   (let [br (io/reader testpath :encoding "UTF8") ; you have to define testpath first
;;         wait-fn #(do (println (io/-read-line br))(io/-close br))]
;;     (js/setTimeout wait-fn ms)))
;;
;; If you wait 3 milliseconds, then this works properly. Since `readable.read`
;; -- see https://nodejs.org/api/stream.html#stream_readable_read_size --
;; will return `null`/`nil` if the buffer is empty when you call `read`, I'm
;; assuming this has to do with the time it takes to set up the stream and get
;; everything primed.
;;
;; This test was done with Node `ReadableStream`s. If we want to use streams in
;; Lumo/Node, we need to attach to the events emitted by the streams. As such,
;; it's not feasible to make synchronous streams (though maybe with
;; async/await...) on a Node host.
;;
;; Also, this will loop forever with the current `abio.node` reader
;; implementation, since the `fs.readSync` function will return the contents of
;; the file each time its called, never returning `nil`.
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
