
(ns sagittariidae.fe.checksum
  "In which are defined the mechanisms that the frontend employs to produce
  checksums for the files (and parts thereof) that are uploaded to the web
  service."
  (:require [goog.crypt :as gcrypt]))

(defn array-buffer->utf8-array
  "Convert an `ArrayBuffer` (such as is returned by the Web File/Blob `slice`
  method) into a Uint8 array suitable for digesting."
  [buf]
  (try
    (js/Uint8Array. buf)
    (catch :default e
      (.error js/console "Error creating typed array" buf e))))

(defn utf8-array->hex-string
  "Convert a Uint8Array of bytes into a hex string."
  [a]
  (gcrypt/byteArrayToHex a))

(defn string->utf8-array
  [s]
  "Convert a string into a Uint8 array suitable for digesting"
  (js/Uint8Array. (gcrypt/stringToUtf8ByteArray s)))
