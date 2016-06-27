package io.anserini.search;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.collect.Sets;
import io.anserini.rerank.RerankerCascade;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.rerank.rm3.Rm3Reranker;
import io.anserini.util.AnalyzerUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.ParserProperties;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static io.anserini.index.IndexWebCollection.FIELD_BODY;
import static io.anserini.index.IndexWebCollection.FIELD_ID;

/**
 * Searcher for Wt10g, Gov2, ClueWeb09, and ClueWeb12 corpra.
 * TREC Web Tracks from 2009 to 2014
 * TREC Terabyte Tracks from 2004 to 2006
 */
public final class SearchWebCollection implements Closeable {

  private static final Logger LOG = LogManager.getLogger(SearchWebCollection.class);

  private final IndexReader reader;

  public SearchWebCollection(String indexDir) throws IOException {

    Path indexPath = Paths.get(indexDir);

    if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
      throw new IllegalArgumentException(indexDir + " does not exist or is not a directory.");
    }

    this.reader = DirectoryReader.open(FSDirectory.open(indexPath));
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  private static String extract(String line, String tag) {

    int i = line.indexOf(tag);

    if (i == -1) throw new IllegalArgumentException("line does not contain the tag : " + tag);

    int j = line.indexOf("\"", i + tag.length() + 2);
    if (j == -1) throw new IllegalArgumentException("line does not contain quotation");

    return line.substring(i + tag.length() + 2, j);
  }

  /**
   * Read topics of TREC Web Tracks from 2009 to 2014
   *
   * @param topicsFile One of: topics.web.1-50.txt topics.web.51-100.txt topics.web.101-150.txt topics.web.151-200.txt topics.web.201-250.txt topics.web.251-300.txt
   * @return SortedMap where keys are query/topic IDs and values are title portions of the topics
   * @throws IOException
   */
  public static SortedMap<Integer, String> readWebTrackQueries(Path topicsFile) throws IOException {

    SortedMap<Integer, String> map = new TreeMap<>();
    List<String> lines = Files.readAllLines(topicsFile, StandardCharsets.UTF_8);

    String number = "";
    String query = "";

    for (String line : lines) {

      line = line.trim();

      if (line.startsWith("<topic"))
        number = extract(line, "number");

      if (line.startsWith("<query>") && line.endsWith("</query>"))
        query = line.substring(7, line.length() - 8).trim();

      if (line.startsWith("</topic>"))
        map.put(Integer.parseInt(number), query);

    }

    lines.clear();
    return map;
  }

  /**
   * Read topics of TREC Terabyte Tracks from 2004 to 2006
   *
   * @param topicsFile One of: topics.701-750.txt topics.751-800.txt topics.801-850.txt
   * @return SortedMap where keys are query/topic IDs and values are title portions of the topics
   * @throws IOException
   */
  public static SortedMap<Integer, String> readTeraByteTackQueries(Path topicsFile) throws IOException {

    SortedMap<Integer, String> map = new TreeMap<>();
    List<String> lines = Files.readAllLines(topicsFile, StandardCharsets.UTF_8);

    String number = "";
    String query = "";

    boolean found = false;
    boolean badTitleFound = false;

    for (String line : lines) {

      line = line.trim();	

      if (!found && "<top>".equals(line)) {
        found = true;
        continue;
      }

      if (found && line.startsWith("<title>"))
        query = line.substring(7).trim();

      if (badTitleFound) {
	query = line.trim();
	badTitleFound = false;
      }

      if (query.length() == 0) {
	badTitleFound = true;
	continue;
      }

      if (found && line.startsWith("<num>")) {
        int i = line.lastIndexOf(" ");
        if (-1 == i) throw new RuntimeException("cannot find space in : " + line);
        number = line.substring(i).trim();
      }

      if (found && "</top>".equals(line)) {
        found = false;
        int qID = Integer.parseInt(number);

        map.put(qID, query);

      }
    }
    lines.clear();
    return map;
  }

  /**
   * Searches queries and saves results to the supplied TREC submission file
   *
   * @param topics         queries
   * @param submissionFile TREC submission file
   * @param similarity     similarity
   * @param numHits        number of documents to retrieve
   * @param runTag         run tag to include in submission file
   * @throws IOException
   * @throws ParseException
   */

  public void search(SortedMap<Integer, String> topics, String submissionFile, Similarity similarity, int numHits, final String runTag) throws IOException, ParseException {


    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(submissionFile), StandardCharsets.US_ASCII));


    QueryParser queryParser = new QueryParser(FIELD_BODY, new EnglishAnalyzer());
    queryParser.setDefaultOperator(QueryParser.Operator.OR);

    for (Map.Entry<Integer, String> entry : topics.entrySet()) {

      int qID = entry.getKey();
      String queryString = entry.getValue().replaceAll("[^\\p{L}\\p{Z}]", "");
      Query query = queryParser.parse(queryString);

      ScoreDoc[] hits = searcher.search(query, numHits).scoreDocs;

      /**
       * the first column is the topic number.
       * the second column is currently unused and should always be "Q0".
       * the third column is the official document identifier of the retrieved document.
       * the fourth column is the rank the document is retrieved.
       * the fifth column shows the score (integer or floating point) that generated the ranking.
       * the sixth column is called the "run tag" and should be a unique identifier for your
       */
      for (int i = 0; i < hits.length; i++) {
        int docId = hits[i].doc;
        Document doc = searcher.doc(docId);
        out.print(qID);
        out.print("\tQ0\t");
        out.print(doc.get(FIELD_ID));
        out.print("\t");
        out.print(i + 1);
        out.print("\t");
        out.print(hits[i].score);
        out.print("\t");
        out.print(runTag);
        out.println();
      }
    }
    out.flush();
    out.close();
  }

  public void search(RerankerCascade cascade, SortedMap<Integer, String> topics, String submissionFile, Similarity similarity, int numHits, final String runTag) throws IOException {

    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(submissionFile), StandardCharsets.US_ASCII));

    for (Map.Entry<Integer, String> entry : topics.entrySet()) {

      int qID = entry.getKey();
      String queryText = entry.getValue();


      Query query = AnalyzerUtils.buildBagOfWordsQuery(FIELD_BODY, new EnglishAnalyzer(), queryText);
      TopDocs rs = searcher.search(query, numHits);

      RerankerContext context = new RerankerContext(searcher, query, Integer.toString(qID), queryText,
              Sets.newHashSet(AnalyzerUtils.tokenize(new EnglishAnalyzer(), queryText)), null);
      ScoredDocuments docs = cascade.run(ScoredDocuments.fromTopDocs(rs, searcher), context);

      for (int i = 0; i < docs.documents.length; i++) {
        out.println(String.format("%d Q0 %s %d %f %s", qID,
                docs.documents[i].getField(FIELD_ID).stringValue(), (i + 1), docs.scores[i], runTag));
      }
    }
    out.flush();
    out.close();
  }


  public static void main(String[] args) throws IOException, ParseException {

    SearchArgs searchArgs = new SearchArgs();
    CmdLineParser parser = new CmdLineParser(searchArgs, ParserProperties.defaults().withUsageWidth(90));

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.err.println("Example: SearchWebCollection" + parser.printExample(OptionHandlerFilter.REQUIRED));
      return;
  }

    Similarity similarity = null;

    if (searchArgs.ql) {
      LOG.info("Using QL scoring model with mu=" + searchArgs.mu);
      similarity = new LMDirichletSimilarity(searchArgs.mu);
    } else if (searchArgs.bm25) {
      LOG.info("Using BM25 scoring model with k1=" + searchArgs.k1 + " and b=" + searchArgs.b);
      similarity = new BM25Similarity(searchArgs.k1, searchArgs.b);
    } else {
      LOG.error("Error: Must specify scoring model!");
      System.exit(-1);
    }

    Path topicsFile = Paths.get(searchArgs.topics);

    if (!Files.exists(topicsFile) || !Files.isRegularFile(topicsFile) || !Files.isReadable(topicsFile)) {
      throw new IllegalArgumentException("Topics file : " + topicsFile + " does not exist or is not a (readable) file.");
    }

    final long start = System.nanoTime();
    SortedMap<Integer, String> topics = (io.anserini.document.Collection.GOV2.equals(searchArgs.collection) || io.anserini.document.Collection.WT10G.equals(searchArgs.collection)) ? readTeraByteTackQueries(topicsFile) : readWebTrackQueries(topicsFile);

    try (SearchWebCollection searcher = new SearchWebCollection(searchArgs.index)) {

      if (searchArgs.rm3) {
        RerankerCascade cascade = new RerankerCascade();
        cascade.add(new Rm3Reranker(new EnglishAnalyzer(), FIELD_BODY, "src/main/resources/io/anserini/rerank/rm3/rm3-stoplist.gov2.txt"));
        searcher.search(cascade, topics, searchArgs.output, similarity, searchArgs.hits, searchArgs.runtag);
      } else
        searcher.search(topics, searchArgs.output, similarity, searchArgs.hits, searchArgs.runtag);
    }

    final long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    LOG.info("Total " + topics.size() + " topics searched in " + DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss"));
  } 
}
