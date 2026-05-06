(ns word-frequencies-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [word-frequencies :as wf])
  (:import
   (java.util.zip ZipEntry ZipOutputStream)))


(defn- write-zip-entry!
  [^ZipOutputStream zip name content]
  (.putNextEntry zip (ZipEntry. name))
  (.write zip (.getBytes content "UTF-8"))
  (.closeEntry zip))


(defn- minimal-subtlex-xlsx
  "Build a tiny XLSX fixture mirroring the real SUBTLEX-DE export shape:
   one xl/sharedStrings.xml plus one xl/worksheets/sheet1.xml.
   Headers are Word + SUBTLEX (other real columns omitted as irrelevant)."
  []
  (let [file (java.io.File/createTempFile "subtlex" ".xlsx")
        shared-strings "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">
  <si><t>Word</t></si>
  <si><t>SUBTLEX</t></si>
  <si><t>für</t></si>
  <si><t>Mädchen</t></si>
</sst>"
        sheet "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">
  <sheetData>
    <row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>
    <row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>4179.78</v></c></row>
    <row r=\"3\"><c r=\"A3\" t=\"s\"><v>3</v></c><c r=\"B3\"><v>468.32</v></c></row>
  </sheetData>
</worksheet>"]
    (.deleteOnExit file)
    (with-open [zip (ZipOutputStream. (io/output-stream file))]
      (write-zip-entry! zip "xl/sharedStrings.xml" shared-strings)
      (write-zip-entry! zip "xl/worksheets/sheet1.xml" sheet))
    file))


(deftest normalize-xlsx-reads-word-and-subtlex
  (testing "reads SUBTLEX-DE cleaned xlsx export with per-million normalized counts"
    (let [rows (wf/normalize-xlsx (.getAbsolutePath (minimal-subtlex-xlsx)))]
      (is (= [{:word "für" :count 4179.78 :source "subtlex-de"}
              {:word "Mädchen" :count 468.32 :source "subtlex-de"}]
             rows)))))


(deftest main-writes-tsv-from-xlsx
  (testing "-main reads --input xlsx and writes the TSV pipeline output"
    (let [input  (minimal-subtlex-xlsx)
          output (java.io.File/createTempFile "frequency-xlsx" ".tsv")]
      (.deleteOnExit output)
      (wf/-main "--input" (.getAbsolutePath input)
                "--output" (.getAbsolutePath output))
      (is (= ["word\tcount\tsource"
              "für\t4179.78\tsubtlex-de"
              "Mädchen\t468.32\tsubtlex-de"]
             (vec (line-seq (io/reader output))))))))
