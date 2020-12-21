package io.anserini.ltr.feature.base;

import io.anserini.index.IndexArgs;
import io.anserini.ltr.feature.DocumentContext;
import io.anserini.ltr.feature.FeatureExtractor;
import io.anserini.ltr.feature.FieldContext;
import io.anserini.ltr.feature.QueryContext;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IBMModel1 implements FeatureExtractor {
    private ConcurrentHashMap<Integer, Pair<Integer, String>> sourceVoc;
    private ConcurrentHashMap<String, Integer> sourceLookup;
    private ConcurrentHashMap<Integer, Pair<Integer, String>> targetVoc;
    private ConcurrentHashMap<String, Integer> targetLookup;
    private ConcurrentHashMap<Integer, Map<Integer, Float>> tran;
    private double selfTrans = 0.05;
    private double lambda = 0.1;
    private double minProb = 5e-4;
    private String field;

    public IBMModel1(String dir, String field) throws IOException {
        sourceVoc = this.loadVoc(dir + File.separator + "source.vcb");
        assert !sourceVoc.containsKey("@NULL@");
        sourceVoc.put(0,Pair.of(0,"@NULL@"));
        sourceLookup = this.vocLookup(sourceVoc);
        targetVoc = this.loadVoc(dir + File.separator + "target.vcb");
        targetLookup = this.vocLookup(targetVoc);
        tran = this.loadTran(dir + File.separator + "output.t1.5.bin");
        this.rescale();
        this.field = field;
    }

    public IBMModel1(String field) {
        this.field = field;
    }

    public ConcurrentHashMap<Integer, Pair<Integer, String>> loadVoc(String fileName) throws IOException {
        ConcurrentHashMap<Integer, Pair<Integer, String>> res = new ConcurrentHashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
        String line = reader.readLine();
        while (line != null) {
            String[] parts = line.split("\\s");
            int id = Integer.parseInt(parts[0]);
            String voc = parts[1];
            int freq = Integer.parseInt(parts[2]);
            assert !res.containsKey(id);
            res.put(id, Pair.of(freq, voc));
            line = reader.readLine();
        }
        reader.close();
        return res;
    }

    public ConcurrentHashMap<String, Integer> vocLookup(Map<Integer, Pair<Integer, String>> voc) {
        ConcurrentHashMap<String, Integer> res = new ConcurrentHashMap<>();
        for (Integer key : voc.keySet()) {
            res.put(voc.get(key).getRight(), key);
        }
        return res;
    }

    public ConcurrentHashMap<Integer, Map<Integer, Float>> loadTran(String fileName) throws IOException {
        ConcurrentHashMap<Integer, Map<Integer, Float>> res = new ConcurrentHashMap<>();
        DataInputStream in = new DataInputStream(new FileInputStream(fileName));
        while (in.available() > 0) {
            int sourceID = in.readInt();
            assert sourceID == 0 | sourceVoc.containsKey(sourceID);
            int targetID = in.readInt();
            assert targetVoc.containsKey(targetID);
            float tranProb = in.readFloat();
            assert tranProb >= 1e-3f;
            if (!res.containsKey(sourceID)) {
                Map<Integer, Float> word2prob = new HashMap<>();
                word2prob.put(targetID, tranProb);
                res.put(sourceID, word2prob);
            } else {
                res.get(sourceID).put(targetID, tranProb);
            }
        }
        return res;
    }

    public void rescale() throws IOException {
        Map<Integer, Float> probSum = new HashMap<>();
        for(int sourceID: tran.keySet()){
            Map<Integer, Float> targetProbs = tran.get(sourceID);
            float adjustMult = sourceID > 0 ? (float) (1 - selfTrans) : 1.0f;
            boolean selfTranExist = false;
            for(int targetID: targetProbs.keySet()){
                float tranProb = targetProbs.get(targetID);
                String sourceWord = sourceVoc.get(sourceID).getRight();
                String targetWord = targetVoc.get(targetID).getRight();
                // should use string match, but author use id match, maybe author only use source or target vocabulary
                // to convert string to id?
//                if(sourceWord.equals(targetWord)&&sourceID!=targetID)
//                    System.out.println(sourceWord + ';' + sourceID + ';' + targetID);
                probSum.put(sourceID, probSum.getOrDefault(sourceID, 0f) + tranProb);
                tranProb *= adjustMult;
                if (sourceWord.equals(targetWord)) {
                    tranProb += selfTrans;
                    selfTranExist = true;
                }
                targetProbs.put(targetID, tranProb);
            }
            // in theroy selftrans should be add to every source word except null however when the selftrans is filtered
//            assert sourceID==0|selfTranExist;
        }
        return;
    }

    public float computeQuery(String queryWord, Map<String, Long> docFreq, Long docSize, double colProb) throws IOException {
        double res = 0;
        float totTranProb = 0;
        if (targetLookup.containsKey(queryWord)) {
            int queryWordId = targetLookup.get(queryWord);
            for (String docTerm : docFreq.keySet()) {
                int docWordId = sourceLookup.get(docTerm);
                float tranProb = 0;
                if (queryWord.equals(docTerm)) {
                    tranProb = (float) selfTrans;
                    if (tran.containsKey(docWordId)) {
                        Map<Integer, Float> targetMap = tran.get(docWordId);
                        if (targetMap.containsKey(queryWordId)) {
                            tranProb = Math.max(targetMap.get(queryWordId), tranProb);
                        }
                    }
                } else {
                    if (tran.containsKey(docWordId)) {
                        Map<Integer, Float> targetMap = tran.get(docWordId);
                        if (targetMap.containsKey(queryWordId)) {
                            tranProb = targetMap.get(queryWordId);
                        }
                    }
                }
                if (tranProb >= minProb) {
                    totTranProb += tranProb * (docFreq.get(docTerm) / docSize);
                }
            }
        }
        colProb = Math.max(colProb, 1e-9f);
        res = Math.log((1 - lambda) * totTranProb + lambda * colProb) - Math.log(lambda * colProb);
        return (float) res;
    }

    @Override
    public float extract(DocumentContext documentContext, QueryContext queryContext) throws FileNotFoundException, IOException {
        FieldContext context = documentContext.fieldContexts.get(field);
        long docSize = context.docSize;
        long totalTermFreq = context.totalTermFreq;
        float score = 0;
        if(docSize==0) return 0;
        for (String queryToken : queryContext.queryTokens) {
            double collectProb = (double)context.getCollectionFreq(queryToken)/totalTermFreq;
            score += computeQuery(queryToken, context.termFreqs, context.docSize, collectProb);
        }
        return score;
    }

    @Override
    public float postEdit(DocumentContext context, QueryContext queryContext) {
        return queryContext.getSelfLog(context.docId, getName());
    }

    @Override
    public FeatureExtractor clone() {
        return new IBMModel1(field);
    }

    @Override
    public String getName() {
        return String.format("%s_IBMModel1",field);
    }

    @Override
    public String getField() {
        return field;
    }
}
