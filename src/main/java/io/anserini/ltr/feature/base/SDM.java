package io.anserini.ltr.feature.base;

import io.anserini.index.IndexArgs;
import io.anserini.ltr.feature.DocumentContext;
import io.anserini.ltr.feature.FieldContext;
import io.anserini.ltr.feature.FeatureExtractor;
import io.anserini.ltr.feature.QueryContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class SDM implements FeatureExtractor {
  private String field;
  /*
  * fxt,indri use window = 8 -> window = 7 because fxt,indri's both word while we only include second
  * fxt,indri do not allow overlapped window, we choose to allow it
  * todo need discussion about overlap
  * */
  int window;
  double mu;
  double mu_phrase;
  double term_weight;
  double ordered_weight;
  double unordered_weight;

  public SDM() {
    this.window = 7;
    this.mu = 2500;
    this.mu_phrase = 2500;
    this.term_weight = 0.8;
    this.ordered_weight = 0.15;
    this.unordered_weight = 0.05;
    this.field = IndexArgs.CONTENTS;
  }

  public SDM(String field) {
    this.window = 7;
    this.mu = 2500;
    this.mu_phrase = 2500;
    this.term_weight = 0.8;
    this.ordered_weight = 0.15;
    this.unordered_weight = 0.05;
    this.field = field;
  }

  @Override
  public float extract(DocumentContext documentContext, QueryContext queryContext) {
    FieldContext context = documentContext.fieldContexts.get(field);
    double per_term = term_weight / queryContext.querySize;
    List<Pair<String, String>> bigrams = queryContext.genQueryBigram();
    double per_bigram_ordered = ordered_weight / bigrams.size();
    double per_bigram_unordered = unordered_weight / bigrams.size();

    long docSize = context.docSize;
    long totalTermFreq = context.totalTermFreq;
    float score = 0;

    for (String queryToken : queryContext.queryTokens) {
      long termFreq = context.getTermFreq(queryToken);
      double collectProb = (double)context.getCollectionFreq(queryToken)/totalTermFreq;
      //todo need discuss this
      if(collectProb==0) continue;
      score += per_term*Math.log((termFreq+mu*collectProb)/(mu+docSize));
    }

    for (Pair<String, String> bigram : bigrams) {
      long termFreq = context.countBigram(bigram.getLeft(), bigram.getRight(),1);
      double collectProb = (double)context.getBigramCollectionFreqs(bigram.getLeft(), bigram.getRight(), 1)/totalTermFreq;
      //todo need discuss this
      if(collectProb==0) continue;
      score += per_bigram_ordered*Math.log((termFreq+mu*collectProb)/(mu+docSize));
    }

    for (Pair<String, String> bigram : bigrams) {
      long termFreq = context.countBigram(bigram.getLeft(), bigram.getRight(),this.window);
      double collectProb = (double)context.getBigramCollectionFreqs(bigram.getLeft(), bigram.getRight(), this.window)/totalTermFreq;
      //todo need discuss this
      if(collectProb==0) continue;
      score += per_bigram_unordered*Math.log((termFreq+mu*collectProb)/(mu+docSize));
    }

    return score;
  }

  @Override
  public String getName() {
    return String.format("%s_SDM", field);
  }

  @Override
  public String getField() {
    return field;
  }

  @Override
  public FeatureExtractor clone() {
    return new SDM(field);
  }
}
