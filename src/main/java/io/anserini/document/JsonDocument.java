/**
 * Anserini: An information retrieval toolkit built on Lucene
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

package io.anserini.document;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 * Json adaptor
 * Inside each file is either a JSON Object (one document) or a JSON Array (multiple documents)
 * Example of JSON Object:
 * {
 *   "id": "doc1",
 *   "contents": "this is the contents."
 * }
 * Example of JSON Array:
 * [
 *   {
 *     "id": "doc1",
 *     "contents": "this is the contents 1."
 *   },
 *   {
 *     "id": "doc2",
 *     "contents": "this is the contents 2."
 *   }
 * ]
 *
 */
public class JsonDocument implements SourceDocument {
  protected String id;
  protected String contents;
  private JsonElement raw;
  private int i;

  public JsonDocument(String path) throws FileNotFoundException {
    JsonParser parser = new JsonParser();
    try {
      raw = parser.parse(new BufferedReader(new FileReader(path))).getAsJsonArray();
    } catch (IllegalStateException e1) {
      try {
        raw = parser.parse(new BufferedReader(new FileReader(path))).getAsJsonObject();
      } catch (IllegalStateException e2) {
        raw = parser.parse(new BufferedReader(new FileReader(path))).getAsJsonNull();
      }
    }
    i = 0;
  }

  @Override
  public JsonDocument readNextRecord(BufferedReader bRdr) {
    if (raw.isJsonArray() && i < raw.getAsJsonArray().size()) {
      JsonObject o = raw.getAsJsonArray().get(i).getAsJsonObject();
      id = o.get("id").getAsString();
      contents = o.get("contents").getAsString();
      i++;
      return this;
    } else if (raw.isJsonObject()) {
      id = raw.getAsJsonObject().get("id").getAsString();
      contents = raw.getAsJsonObject().get("contents").getAsString();
      return this;
    }
    return null;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String content() {
    return contents;
  }

  @Override
  public boolean indexable() {
    return true;
  }
}
