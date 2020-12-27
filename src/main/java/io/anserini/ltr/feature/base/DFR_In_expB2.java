package io.anserini.ltr.feature.base;

import io.anserini.index.IndexArgs;
import io.anserini.ltr.feature.*;

public class DFR_In_expB2 implements FeatureExtractor {

  private String field;
  private String qfield;

  public DFR_In_expB2() {
    this.field = IndexArgs.CONTENTS;
    this.qfield = "analyzed";
  }

  public DFR_In_expB2(String field, String qfield) {
    this.field = field;
    this.qfield = qfield;
  }

  double log2(double x){
    return Math.log(x)/Math.log(2);
  }

  @Override
  public float extract(DocumentContext documentContext, QueryContext queryContext) {
    FieldContext context = documentContext.fieldContexts.get(field);
    QueryFieldContext queryFieldContext = queryContext.fieldContexts.get(qfield);
    long numDocs = context.numDocs;
    long docSize = context.docSize;
    long totalTermFreq = context.totalTermFreq;
    double avgFL = (double)totalTermFreq/numDocs;
    float score = 0;

    for (String queryToken : queryFieldContext.queryTokens) {
      double tfn = context.getTermFreq(queryToken)*log2(1+avgFL/docSize);
      if(tfn==0) continue;
      double cf = context.getCollectionFreq(queryToken);
      double ne = numDocs*(1-Math.pow((double)(numDocs-1)/numDocs, cf));
      double ine = log2(((double)numDocs+1)/(ne+0.5));
      score += tfn*ine*((cf+1)/((double)context.getDocFreq(queryToken)*(tfn+1)));
    }
    return score;
  }

  @Override
  public float postEdit(DocumentContext context, QueryContext queryContext) {
    QueryFieldContext queryFieldContext = queryContext.fieldContexts.get(qfield);
    return queryFieldContext.getSelfLog(context.docId, getName());
  }

  @Override
  public String getName() {
    return String.format("%s_%s_DFR_In_expB2", field, qfield);
  }

  @Override
  public String getField() {
    return field;
  }

  @Override
  public String getQField() {
    return qfield;
  }

  @Override
  public FeatureExtractor clone() {
    return new DFR_In_expB2(field, qfield);
  }
}
