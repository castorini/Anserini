/**
 * Anserini: A toolkit for reproducible information retrieval research built on Lucene
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.rerank.lib;

import io.anserini.index.generator.LuceneDocumentGenerator;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.search.query.FilterQueryBuilder;
import io.anserini.util.FeatureVector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static io.anserini.search.SearchCollection.BREAK_SCORE_TIES_BY_DOCID;
import static io.anserini.search.SearchCollection.BREAK_SCORE_TIES_BY_TWEETID;


public class CondensedListRelevanceReranker extends Rm3Reranker {
  private static final Logger LOG = LogManager.getLogger(CondensedListRelevanceReranker.class);

  private String condenseListGenerator;
  public static final String QUERY_FILTER_RESCORER = "queryFilter";
  public static final String DOC_FILTER_RESCORER = "docFilter";
  public static final String HIT_RESCORER = "hitRescorer";

  public CondensedListRelevanceReranker(Analyzer analyzer, String field, int fbTerms, int fbDocs, float originalQueryWeight, boolean outputQuery, String clGenerator) {
    super(analyzer, field, fbTerms, fbDocs, originalQueryWeight, outputQuery);
    this.condenseListGenerator = clGenerator;
  }

  @Override
  public ScoredDocuments rerank(ScoredDocuments docs, RerankerContext context) {

    assert (docs.documents.length == docs.scores.length);

    IndexSearcher searcher = context.getIndexSearcher();
    Query finalQuery = reformulateQuery(docs, context);
    Query filterQuery = null;
    TopDocs rs = null;
    if (this.condenseListGenerator
        .equals(QUERY_FILTER_RESCORER) || this.condenseListGenerator.equals(DOC_FILTER_RESCORER)) {
      filterQuery = this.condenseListGenerator.equals(QUERY_FILTER_RESCORER)? context.getQuery(): FilterQueryBuilder.buildSetQuery(LuceneDocumentGenerator.FIELD_ID,
          getFieldValues(docs, LuceneDocumentGenerator.FIELD_ID, searcher));
      rs = rescoreWithTieBreaker(finalQuery,filterQuery,context,searcher);
    }else if (this.condenseListGenerator.equals(HIT_RESCORER)){
      QueryRescorer rm3Rescoer = new RM3QueryRescorer(finalQuery);
      try {
         rs = rm3Rescoer.rescore(searcher,docs.topDocs,context.getSearchArgs().hits);
      } catch (IOException e) {
         e.printStackTrace();
      }
    }

    return rs==null? docs:ScoredDocuments.fromTopDocs(rs, searcher);

  }

  private TopDocs rescoreWithTieBreaker(Query finalQuery, Query filterQuery, RerankerContext context, IndexSearcher searcher){

    TopDocs rs = null;
    finalQuery = FilterQueryBuilder.addFilterQuery(finalQuery,filterQuery);
    try {
      // Figure out how to break the scoring ties.
      if (context.getSearchArgs().arbitraryScoreTieBreak) {
        rs = searcher.search(finalQuery, context.getSearchArgs().hits);
      } else if (context.getSearchArgs().searchtweets) {
        rs = searcher.search(finalQuery, context.getSearchArgs().hits, BREAK_SCORE_TIES_BY_TWEETID, true);
      } else {
        rs = searcher.search(finalQuery, context.getSearchArgs().hits, BREAK_SCORE_TIES_BY_DOCID, true);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return rs;
  }

  private Set<String> getFieldValues(ScoredDocuments docs, String fld, IndexSearcher searcher) {
    Set<String> values = new HashSet<>();

    for (int id : docs.ids) {
      try {
        values.add(searcher.doc(id).getField(fld).stringValue());
      } catch (IOException e) {
        LOG.warn(String.format("Failed to extract %s from document with lucene id %s", fld, id));
        e.printStackTrace();
      }
    }
    return values;
  }

  public Query reformulateQuery(ScoredDocuments docs, RerankerContext context) {
    FeatureVector rm3 = estimateRM3Model(docs, context);

    return buildFeedbackQuery(context, rm3);
  }


  @Override
  public String tag() {
    return "CLRm3(fbDocs=" + getFbDocs() + ",fbTerms=" + getFbTerms() + ",originalQueryWeight:" + getOriginalQueryWeight() + ")";
  }


}

class RM3QueryRescorer extends QueryRescorer {
  public RM3QueryRescorer(Query query) {
    super(query);
  }

  @Override
  protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
    return secondPassScore;
  }
}

