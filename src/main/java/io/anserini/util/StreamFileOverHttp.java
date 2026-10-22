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

package io.anserini.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;

public class StreamFileOverHttp {
  public static void main(String[] args) throws Exception {
    String baseUrl = "https://rgw.cs.uwaterloo.ca/pyserini";
    String s3Path = "s3://pyserini/collections/cacm/cacm.tar.gz";
    String objectKey = s3Path.replaceFirst("^s3://pyserini/", "");
    URI uri = URI.create(baseUrl + "/" + objectKey);

    System.out.println("Requesting URL: " + uri);
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Download failed with HTTP status " + response.statusCode());
    }

    long bytes = 0L;
    byte[] buffer = new byte[1024 * 1024];
    try (InputStream in = response.body()) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        bytes += read;
      }
    }

    System.out.println("Streamed " + bytes + " bytes from " + s3Path);
  }
}
