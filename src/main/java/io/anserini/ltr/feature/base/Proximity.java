package io.anserini.ltr.feature.base;

import io.anserini.index.IndexArgs;
import io.anserini.ltr.feature.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Proximity implements FeatureExtractor {
    private String field;
    private String qfield;

    public Proximity() {
        this.field = IndexArgs.CONTENTS;
        this.qfield = "analyzed";
    }

    public Proximity(String field, String qfield) {
        this.field = field;
        this.qfield = qfield;
    }

    @Override
    public float extract(DocumentContext documentContext, QueryContext queryContext) {
        FieldContext context = documentContext.fieldContexts.get(field);
        QueryFieldContext queryFieldContext = queryContext.fieldContexts.get(qfield);
        float score = 0.0f;
        /* Store condensed direct file */
        List<Pair<String, Integer>> cdf = new ArrayList<>();
        int s = 0;
        for (String queryToken : queryFieldContext.queryTokens) {
            if (context.termFreqs.containsKey(queryToken)) {
                ++s;
                List<Integer> termPos = context.termPositions.get(queryToken);
                for (Integer pos : termPos) {
                    cdf.add(Pair.of(queryToken, pos));
                }
            }
        }
        if (s<2) return score;
        /* sort cdf by positions in the doc*/
        Collections.sort(cdf, new Comparator<>() {
            @Override
            public int compare(final Pair<String, Integer> lhs, final Pair<String, Integer> rhs) {
                return lhs.getValue() - rhs.getValue();
            }
        });

        int window = 8;
        int k1 = 90;
        int b = 40;
        double epsilon = 1e-6;
        /* bigram scan */
        for (int i=0; i< cdf.size()-1; ++i){
            for (int j = i+1; j < cdf.size(); i=j++){
                Pair<String, Integer> lhs = cdf.get(i);
                Pair<String, Integer> rhs = cdf.get(j);
                int dist = rhs.getValue() - lhs.getValue();
                /* skip if same term for bigram */
                if (rhs.getKey() == lhs.getKey()) continue;
                if(dist > 0 && dist <= window){
                    int queryFreq = queryFieldContext.queryFreqs.get(lhs.getKey());
                    float idf = (float) Math.max(epsilon,Math.log(context.numDocs/context.getDocFreq(lhs.getKey()))*queryFreq);
                    float avgDocLen = context.totalTermFreq / context.numDocs;
                    float tf = k1*((1-b) + (b*(context.docSize / avgDocLen)));
                    score += ((k1+1)*context.getTermFreq(lhs.getKey()) / (tf + context.getTermFreq(lhs.getKey()))) * idf;

                    float queryFreqB = queryFieldContext.queryFreqs.get(rhs.getKey());
                    float idfB = (float)Math.max(epsilon, Math.log(context.numDocs/context.getDocFreq(rhs.getKey()))*queryFreqB);
                    score += ((k1+1)*context.getTermFreq(rhs.getKey()) / (tf + context.getTermFreq(rhs.getKey()))) * idfB;
                }
            }
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
        return String.format("%s_%s_Proximity", field, qfield);
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
        return new Proximity(field, qfield);
    }
}