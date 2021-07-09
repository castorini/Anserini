/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;


public class C4NoCleanCollection extends C4Collection {
  public C4NoCleanCollection(Path path) {
    super(path);
  }

  public static class Segment extends C4Collection.Segment{

    public Segment(Path path) throws IOException {
      super(path);
      String pathS = C4Collection.Segment.getFilePath();
      int fileNumStart = pathS.indexOf("c4-train.") + 9;
      // plus one to remove leading zero
      C4Collection.Segment.setFileName(pathS.substring(fileNumStart + 1, fileNumStart + 14));
    }
  }

  public static class Document extends C4Collection.Document {

    public Document(JsonNode json, String filename, int jsonLoc) {
      super(json, filename, jsonLoc);
      
      try {
        C4Collection.Document.setId(json.get("docno").asText());
      } catch(Exception e) { 
        C4Collection.Document.setId(String.format("en.noclean.c4-train.%s.%d", filename, jsonLoc));
      }
    }
  }
}