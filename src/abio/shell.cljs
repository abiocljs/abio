(ns abio.shell
  (:require-macros 
    [abio.shell])
  (:require 
    [abio.core :refer [*shell-bindings*]]))

(defprotocol IBindings
  (-sh [this args]))

(def ^:dynamic *sh-dir* nil)

(def ^:dynamic *sh-env* nil)

(defn sh
  "Launches a sub-process with the supplied arguments.
  Parameters: cmd, <options>
  cmd      the command(s) (Strings) to execute. will be concatenated together.
  options  optional keyword arguments-- see below.
  Options are:
  :in      may be given followed by a string of one of the following formats:
           String conforming to URL Syntax: 'file:///tmp/test.txt'
           String pointing at an *existing* 'file: '/tmp/test.txt'
           String with string input: 'Printing input from stdin with funny chars like $@ &'
           to be fed to the sub-process's stdin.
  :in-enc  option may be given followed by a String, used as a character
           encoding name (for example \"UTF-8\" or \"ISO-8859-1\") to
           convert the input string specified by the :in option to the
           sub-process's stdin.  Defaults to UTF-8.
  :out-enc option may be given followed by a String. If a
           String is given, it will be used as a character encoding
           name (for example \"UTF-8\" or \"ISO-8859-1\") to convert
           the sub-process's stdout to a String which is returned.
  :env     override the process env with a map of String: String.
  :dir     override the process dir with a String or planck.io/File.
  if the command can be launched, sh returns a map of
    :exit => sub-process's exit code
    :out  => sub-process's stdout (as String)
    :err  => sub-process's stderr (String via platform default encoding),
  otherwise it throws an exception"
  [& args]
  (-sh *shell-bindings* args))
