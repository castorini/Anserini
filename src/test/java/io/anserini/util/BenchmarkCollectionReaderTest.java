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

import static org.junit.Assert.assertEquals;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.BeforeClass;
import org.junit.Test;

public class BenchmarkCollectionReaderTest {
  @BeforeClass
  public static void setupClass() {
    Configurator.setLevel(BenchmarkCollectionReader.class.getName(), Level.ERROR);
  }

  @Test
  public void testBenchmarkCollectionReaderOnSampleTrecCollection() throws Exception {
    BenchmarkCollectionReader.Args args = new BenchmarkCollectionReader.Args();
    args.input = "src/test/resources/sample_docs/trec/collection1";
    args.threads = 1;
    args.collectionClass = "TrecCollection";

    BenchmarkCollectionReader reader = new BenchmarkCollectionReader(args);

    reader.run();
    assertEquals(2, reader.getTotalRecordCount());
    assertEquals(1, reader.getCompletedTaskCount());
  }

  @Test
  public void testBenchmarkCollectionReaderOnCacm() throws Exception {
    BenchmarkCollectionReader.Args args = new BenchmarkCollectionReader.Args();
    args.input = "src/main/resources/cacm/";
    args.threads = 1;
    args.collectionClass = "HtmlCollection";

    BenchmarkCollectionReader reader = new BenchmarkCollectionReader(args);

    reader.run();
    assertEquals(3204, reader.getTotalRecordCount());
    assertEquals(1, reader.getCompletedTaskCount());
  }
}
