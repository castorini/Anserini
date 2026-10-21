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

package io.anserini.fusion;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.anserini.StdOutStdErrRedirectableLuceneTestCase;
import io.anserini.TestUtils;
import io.anserini.search.ScoredDocs;

public class ScoredDocsFuserTest extends StdOutStdErrRedirectableLuceneTestCase {
  @BeforeClass
  public static void setupClass() {
    Configurator.setLevel(FuseRuns.class.getName(), Level.ERROR);
  }

  @Before
  public void setUp() throws Exception {
    // Explictly set locale to US so that decimal points use '.' instead of ','
    Locale.setDefault(Locale.US);

    redirectStdErr();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    restoreStdErr();
    super.tearDown();
  }

  @Test
  public void testInvalidNumRuns() {
    ScoredDocs run = new ScoredDocs();
    Path newPath = Paths.get("runs/new_run");
    IllegalStateException thrown1 = assertThrows(IllegalStateException.class, () -> {
      ScoredDocsFuser.saveToTxt(newPath, "Anserini", run);
    });
    assertEquals("Nothing to save. ScoredDocs is empty".trim(), thrown1.getMessage().trim());

    List<ScoredDocs> runs = new ArrayList<>();
    runs.add(run);
    IllegalArgumentException thrown2 = assertThrows(IllegalArgumentException.class, () -> {
      ScoredDocsFuser.merge(runs, 1000, 1000);
    });
    assertEquals("Merge requires at least 2 runs.".trim(), thrown2.getMessage().trim());
  }

  @Test
  public void testResort() {
    try {
      Path path = Paths.get("src/test/resources/sample_runs/run3");
      ScoredDocs run = ScoredDocsFuser.readRun(path, true);
      Path newPath = Paths.get("runs/sorted_run");
      ScoredDocsFuser.saveToTxt(newPath, "Anserini", run);
      TestUtils.checkFile("runs/sorted_run", new String[]{
        "query1 Q0 doc1 1 7.000000 Anserini",
        "query1 Q0 doc2 2 6.000000 Anserini",
        "query1 Q0 doc3 3 5.000000 Anserini",
        "query2 Q0 doc1 1 14.000000 Anserini",
        "query2 Q0 doc2 2 13.000000 Anserini",
        "query2 Q0 doc3 3 12.000000 Anserini"
      });
      assertTrue(new File("runs/sorted_run").delete());
    }
    catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  @Test
  public void testNormalizeScores() throws Exception {
    Path path = Paths.get("src/test/resources/sample_runs/run1");
    ScoredDocs run = ScoredDocsFuser.readRun(path, false);
    
    // Normalize the scores
    ScoredDocsFuser.normalizeScores(run);
    
    // After normalization, scores should be in range [0, 1] per topic
    // Verify exact normalized scores
    // run1 has: query1 [doc1=7.0, doc2=6.0, doc3=5.0], query2 [doc1=14.0, doc2=13.0, doc3=12.0]
    // Normalization per topic: (score - min) / (max - min)
    // query1: min=5.0, max=7.0 -> doc1=1.0, doc2=0.5, doc3=0.0
    // query2: min=12.0, max=14.0 -> doc1=1.0, doc2=0.5, doc3=0.0
    
    assertEquals("query1 doc1 should be normalized to 1.0", 1.0f, run.scores[0], 0.0001f);
    assertEquals("query1 doc2 should be normalized to 0.5", 0.5f, run.scores[1], 0.0001f);
    assertEquals("query1 doc3 should be normalized to 0.0", 0.0f, run.scores[2], 0.0001f);
    assertEquals("query2 doc1 should be normalized to 1.0", 1.0f, run.scores[3], 0.0001f);
    assertEquals("query2 doc2 should be normalized to 0.5", 0.5f, run.scores[4], 0.0001f);
    assertEquals("query2 doc3 should be normalized to 0.0", 0.0f, run.scores[5], 0.0001f);
  }

  @Test
  public void testNormalizeScoresWithSameScores() throws Exception {
    // Test normalization when all scores for a topic are the same
    // This tests the edge case where max == min
    // Expected behavior: when all scores are equal, assign 1.0 to all (highest normalized value)
    Path path = Paths.get("src/test/resources/sample_runs/run_same_scores");
    ScoredDocs run = ScoredDocsFuser.readRun(path, false);
    
    // Normalize the scores
    ScoredDocsFuser.normalizeScores(run);
    
    // When all scores are the same for a topic, assign 1.0 to all
    // query1: all scores are 5.0 -> min=5.0, max=5.0 -> assign 1.0 to all
    // query2: all scores are 10.0 -> min=10.0, max=10.0 -> assign 1.0 to all
    assertEquals("query1 doc1 should be 1.0 when all scores are equal", 1.0f, run.scores[0], 0.0001f);
    assertEquals("query1 doc2 should be 1.0 when all scores are equal", 1.0f, run.scores[1], 0.0001f);
    assertEquals("query1 doc3 should be 1.0 when all scores are equal", 1.0f, run.scores[2], 0.0001f);
    assertEquals("query2 doc1 should be 1.0 when all scores are equal", 1.0f, run.scores[3], 0.0001f);
    assertEquals("query2 doc2 should be 1.0 when all scores are equal", 1.0f, run.scores[4], 0.0001f);
  }

  @Test
  public void testNormalizeScoresMixed() throws Exception {
    // Test normalization with mixed case: one topic has same scores, another has different scores
    Path path = Paths.get("src/test/resources/sample_runs/run_mixed_scores");
    ScoredDocs run = ScoredDocsFuser.readRun(path, false);
    
    // Normalize the scores
    ScoredDocsFuser.normalizeScores(run);
    
    // query1: all scores are 5.0 -> min=5.0, max=5.0 -> assign 1.0 to all
    // query2: scores [7.0, 6.0, 5.0] -> min=5.0, max=7.0 -> doc1=1.0, doc2=0.5, doc3=0.0
    assertEquals("query1 doc1 should be 1.0 when all scores are equal", 1.0f, run.scores[0], 0.0001f);
    assertEquals("query1 doc2 should be 1.0 when all scores are equal", 1.0f, run.scores[1], 0.0001f);
    assertEquals("query1 doc3 should be 1.0 when all scores are equal", 1.0f, run.scores[2], 0.0001f);
    assertEquals("query2 doc1 should be normalized to 1.0", 1.0f, run.scores[3], 0.0001f);
    assertEquals("query2 doc2 should be normalized to 0.5", 0.5f, run.scores[4], 0.0001f);
    assertEquals("query2 doc3 should be normalized to 0.0", 0.0f, run.scores[5], 0.0001f);
  }

  @Test
  public void testRescoreMethods() throws Exception {
    Path path = Paths.get("src/test/resources/sample_runs/run1");
    ScoredDocs run = ScoredDocsFuser.readRun(path, false);
    
    // Test SCALE rescore with factor 2.0
    // Original scores: query1 [7.0, 6.0, 5.0], query2 [14.0, 13.0, 12.0]
    // After scaling: query1 [14.0, 12.0, 10.0], query2 [28.0, 26.0, 24.0]
    ScoredDocsFuser.rescore(ScoredDocsFuser.RescoreMethod.SCALE, 0, 2.0, run);
    assertEquals("query1 doc1 scaled to 14.0", 14.0f, run.scores[0], 0.0001f);
    assertEquals("query1 doc2 scaled to 12.0", 12.0f, run.scores[1], 0.0001f);
    assertEquals("query1 doc3 scaled to 10.0", 10.0f, run.scores[2], 0.0001f);
    assertEquals("query2 doc1 scaled to 28.0", 28.0f, run.scores[3], 0.0001f);
    assertEquals("query2 doc2 scaled to 26.0", 26.0f, run.scores[4], 0.0001f);
    assertEquals("query2 doc3 scaled to 24.0", 24.0f, run.scores[5], 0.0001f);
    
    // Reset and test RRF rescore with k=60
    // RRF formula: 1.0 / (k + rank)
    // query1: ranks [1, 2, 3] -> scores [1/61, 1/62, 1/63]
    // query2: ranks [1, 2, 3] -> scores [1/61, 1/62, 1/63]
    run = ScoredDocsFuser.readRun(path, false);
    ScoredDocsFuser.rescore(ScoredDocsFuser.RescoreMethod.RRF, 60, 0, run);
    assertEquals("query1 doc1 RRF score (rank 1)", 1.0f / 61.0f, run.scores[0], 0.0001f);
    assertEquals("query1 doc2 RRF score (rank 2)", 1.0f / 62.0f, run.scores[1], 0.0001f);
    assertEquals("query1 doc3 RRF score (rank 3)", 1.0f / 63.0f, run.scores[2], 0.0001f);
    assertEquals("query2 doc1 RRF score (rank 1)", 1.0f / 61.0f, run.scores[3], 0.0001f);
    assertEquals("query2 doc2 RRF score (rank 2)", 1.0f / 62.0f, run.scores[4], 0.0001f);
    assertEquals("query2 doc3 RRF score (rank 3)", 1.0f / 63.0f, run.scores[5], 0.0001f);
  }

  @Test
  public void testResortReassignsRanks() throws Exception {
    // Ranks in the file (1, 2, 3) disagree with the score order (doc1 7.0, doc2 6.0, doc3 5.0).
    Path path = Paths.get("src/test/resources/sample_runs/run_unsorted");

    ScoredDocs run = ScoredDocsFuser.readRun(path, true);
    assertArrayEquals(new String[]{"doc1", "doc2", "doc3"}, run.docids);
    assertArrayEquals(new int[]{1, 2, 3}, run.lucene_docids);

    // RRF should therefore follow the score order, not the original ranks.
    ScoredDocsFuser.rescore(ScoredDocsFuser.RescoreMethod.RRF, 60, 0, run);
    assertEquals(1.0f / 61.0f, run.scores[0], 0.0001f);
    assertEquals(1.0f / 63.0f, run.scores[2], 0.0001f);

    // Without resorting, the original order and ranks are kept.
    run = ScoredDocsFuser.readRun(path, false);
    assertArrayEquals(new String[]{"doc3", "doc1", "doc2"}, run.docids);
    assertArrayEquals(new int[]{1, 2, 3}, run.lucene_docids);
  }

  @Test
  public void testMergeDepth() throws Exception {
    List<ScoredDocs> runs = new ArrayList<>();
    runs.add(ScoredDocsFuser.readRun(Paths.get("src/test/resources/sample_runs/run1"), false));
    runs.add(ScoredDocsFuser.readRun(Paths.get("src/test/resources/sample_runs/run2"), false));

    // With depth 1, only the top result of each run per topic is considered.
    ScoredDocs merged = ScoredDocsFuser.merge(runs, 1, 1000);
    Path newPath = Paths.get("runs/merged_depth_run");
    ScoredDocsFuser.saveToTxt(newPath, "Anserini", merged);
    TestUtils.checkFile("runs/merged_depth_run", new String[]{
      "query1 Q0 doc1 1 7.000000 Anserini",
      "query1 Q0 doc2 2 5.000000 Anserini",
      "query2 Q0 doc1 1 14.000000 Anserini",
      "query2 Q0 doc3 2 9.000000 Anserini"
    });
    assertTrue(new File("runs/merged_depth_run").delete());

    // A null depth considers all results.
    merged = ScoredDocsFuser.merge(runs, null, null);
    assertEquals(9, merged.docids.length);
  }

  @Test
  public void testMergeBreaksTiesByDocid() throws Exception {
    List<ScoredDocs> runs = new ArrayList<>();
    runs.add(ScoredDocsFuser.readRun(Paths.get("src/test/resources/sample_runs/run1"), false));
    runs.add(ScoredDocsFuser.readRun(Paths.get("src/test/resources/sample_runs/run2"), false));
    for (ScoredDocs run : runs) {
      ScoredDocsFuser.rescore(ScoredDocsFuser.RescoreMethod.RRF, 60, 0, run);
    }

    // query1: doc1 and doc2 tie (1/61 + 1/62), as do doc3 and doc4 (1/63).
    ScoredDocs merged = ScoredDocsFuser.merge(runs, 1000, 1000);
    ScoredDocsFuser.sortScoredDocs(merged);
    assertArrayEquals(new String[]{"doc1", "doc2", "doc3", "doc4"},
        Arrays.copyOfRange(merged.docids, 0, 4));
  }
}
