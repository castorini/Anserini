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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class HtmlCollectionTest {

  @Test
  public void testCompressedCACM1() {
    HtmlCollection collection = new HtmlCollection(Paths.get("src/main/resources/cacm/"));
    Iterator<FileSegment<HtmlCollection.Document>> segmentIter = collection.iterator();
    Iterator<HtmlCollection.Document> docIter = segmentIter.next().iterator();

    AtomicInteger cnt = new AtomicInteger();
    docIter.forEachRemaining(d -> cnt.getAndIncrement());
    assertEquals(3204, cnt.get());

    assertFalse(segmentIter.hasNext());
  }

  @Test
  public void testCompressedCACM2() throws IOException {
    HtmlCollection collection = new HtmlCollection(Paths.get("src/main/resources/cacm/"));
    List<Path> paths = collection.getSegmentPaths();

    assertEquals(1, paths.size());
    AtomicInteger cnt = new AtomicInteger();
    try (HtmlCollection.Segment segment = new HtmlCollection.Segment(paths.get(0))) {
      Iterator<HtmlCollection.Document> docIter = segment.iterator();
      docIter.forEachRemaining(d -> cnt.getAndIncrement());
    }
    assertEquals(3204, cnt.get());
  }

  @Test
  public void testReadCompressedSegmentOverHttp() throws IOException, InterruptedException {
    URI uri = URI.create("https://rgw.cs.uwaterloo.ca/pyserini/collections/cacm/cacm.tar.gz");

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    assertEquals(200, response.statusCode());

    long bytes = 0L;
    byte[] buffer = new byte[1024 * 1024];
    try (InputStream in = response.body()) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        bytes += read;
      }
    }

    assertEquals(808000, bytes);
  }

}
