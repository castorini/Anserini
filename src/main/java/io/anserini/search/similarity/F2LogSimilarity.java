/**
 * Anserini: An information retrieval toolkit built on Lucene
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

package io.anserini.search.similarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

/**
 * Hui Fang and ChengXiang Zhai. 2005. An exploration of axiomatic approaches to information retrieval.
 * In Proceedings of the 28th annual international ACM SIGIR conference on Research and development in
 * information retrieval (SIGIR '05). ACM, New York, NY, USA, 480-487.
 */
public class F2LogSimilarity extends Similarity {
  private final float s;

  /**
   * F2Log with the supplied parameter values.
   * @param s Controls to what degree document length normalizes tf values.
   * @throws IllegalArgumentException if {@code s} is infinite or if {@code s} is
   *         not within the range {@code [0..1]}
   */
  public F2LogSimilarity(float s) {
    if (Float.isNaN(s) || s < 0 || s > 1) {
      throw new IllegalArgumentException("illegal s value: " + s + ", must be between 0 and 1");
    }
    this.s = s;
  }

  /** F2Log with these default values:
   * <ul>
   *   <li>{@code s = 0.5}</li>
   * </ul>
   */
  public F2LogSimilarity() {
    this(0.5f);
  }

  /** Implemented as <code>log(1 + (docCount - docFreq + 0.5)/(docFreq + 0.5))</code>.
   *
   * @param docFreq terms's document frequency
   * @param docCount total document count in the index
   * @return inverted document frequency
   * */
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log((1.0f + docCount) / docFreq);
  }

  /** Implemented as <code>1 / (distance + 1)</code>.
   *
   * @param distance distance
   * @return sloppy frequency
   * */
  protected float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
  }

  /** The default implementation returns <code>1</code>
   *
   * @param doc doc
   * @param start start
   * @param end end
   * @param payload payload
   * @return 1
   * */
  protected float scorePayload(int doc, int start, int end, BytesRef payload) {
    return 1;
  }

  /** The default implementation computes the average as <code>sumTotalTermFreq / docCount</code>,
   * or returns <code>1</code> if the index does not store sumTotalTermFreq:
   * any field that omits frequency information).
   *
   * @param collectionStats collection-wide statistics
   * @return average document length of FIELD_BODY
   * */
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    final long sumTotalTermFreq = collectionStats.sumTotalTermFreq();
    if (sumTotalTermFreq <= 0) {
      return 1f;       // field does not exist, or stat is unsupported
    } else {
      final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
      return (float) (sumTotalTermFreq / (double) docCount);
    }
  }

  /** The default implementation encodes <code>boost / sqrt(length)</code>
   * with {@link SmallFloat#floatToByte315(float)}.  This is compatible with
   * Lucene's default implementation.  If you change this, then you should
   * change {@link #decodeNormValue(byte)} to match.
   *
   * @param boost boost
   * @param fieldLength fieldLength
   * @return encoded document lengths
   * */
  protected byte encodeNormValue(float boost, int fieldLength) {
    return SmallFloat.floatToByte315(boost / (float) Math.sqrt(fieldLength));
  }

  /** The default implementation returns <code>1 / f<sup>2</sup></code>
   * where <code>f</code> is {@link SmallFloat#byte315ToFloat(byte)}.
   *
   * @param b encoded document length
   * @return decoded document length
   * */
  protected float decodeNormValue(byte b) {
    return NORM_TABLE[b & 0xFF];
  }

  /**
   * True if overlap tokens (tokens with a position of increment of zero) are
   * discounted from the document's length.
   */
  protected boolean discountOverlaps = true;

  /** Sets whether overlap tokens (Tokens with 0 position increment) are
   *  ignored when computing norm.  By default this is true, meaning overlap
   *  tokens do not count when computing norms.
   *
   * @param v v
   *  */
  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  /**
   * Returns true if overlap tokens are discounted from the document's length.
   * @see #setDiscountOverlaps
   *
   * @return discountOverlaps
   */
  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }

  /** Cache of decoded bytes. */
  private static final float[] NORM_TABLE = new float[256];

  static {
    for (int i = 1; i < 256; i++) {
      float f = SmallFloat.byte315ToFloat((byte)i);
      NORM_TABLE[i] = 1.0f / (f*f);
    }
    NORM_TABLE[0] = 1.0f / NORM_TABLE[255]; // otherwise inf
  }


  @Override
  public long computeNorm(FieldInvertState state) {
    final int numTerms = discountOverlaps ? state.getLength() - state.getNumOverlap() : state.getLength();
    return encodeNormValue(state.getBoost(), numTerms);
  }

  /**
   * Computes a score factor for a simple term and returns an explanation
   * for that score factor.
   *
   * <p>
   * The default implementation uses:
   *
   * <pre class="prettyprint">
   * idf(docFreq, docCount);
   * </pre>
   *
   * Note that {@link CollectionStatistics#docCount()} is used instead of
   * {@link org.apache.lucene.index.IndexReader#numDocs() IndexReader#numDocs()} because also
   * {@link TermStatistics#docFreq()} is used, and when the latter
   * is inaccurate, so is {@link CollectionStatistics#docCount()}, and in the same direction.
   * In addition, {@link CollectionStatistics#docCount()} does not skew when fields are sparse.
   *
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explain object that includes both an idf score factor
  and an explanation for the term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(idf, "idf(docFreq=" + df + ", docCount=" + docCount + ")");
  }

  /**
   * Computes a score factor for a phrase.
   *
   * <p>
   * The default implementation sums the idf factor for
   * each term in the phrase.
   *
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the terms in the phrase
   * @return an Explain object that includes both an idf
   *         score factor for the phrase and an explanation
   *         for each term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    final long docCount = collectionStats.docCount() == -1 ? collectionStats.maxDoc() : collectionStats.docCount();
    float idf = 0.0f;
    List<Explanation> details = new ArrayList<>();
    for (final TermStatistics stat : termStats ) {
      final long df = stat.docFreq();
      final float termIdf = idf(df, docCount);
      details.add(Explanation.match(termIdf, "idf(docFreq=" + df + ", docCount=" + docCount + ")"));
      idf += termIdf;
    }
    return Explanation.match(idf, "idf(), sum of:", details);
  }

  @Override
  public SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf = termStats.length == 1 ? idfExplain(collectionStats, termStats[0]) : idfExplain(collectionStats, termStats);

    float avgdl = avgFieldLength(collectionStats);

    // compute freq-independent part of f2log equation across all norm values
    float cache[] = new float[256];
    for (int i = 0; i < cache.length; i++) {
      cache[i] = s + s * decodeNormValue((byte)i) / avgdl;
    }
    return new F2LogStats(collectionStats.field(), idf, avgdl, cache);
  }

  @Override
  public SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException {
    F2LogStats f2logStats = (F2LogStats) stats;
    return new F2LogDocScorer(f2logStats, context.reader().getNormValues(f2logStats.field));
  }

  private class F2LogDocScorer extends SimScorer {
    private final F2LogStats stats;
    private final float weightValue; // boost * idf * (k1 + 1)
    private final NumericDocValues norms;
    private final float[] cache;

    F2LogDocScorer(F2LogStats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.weight;
      this.cache = stats.cache;
      this.norms = norms;
    }

    @Override
    public float score(int doc, float freq) {
      // if there are no norms, we act as if b=0
      float norm = norms == null ? 1.0f : cache[(byte)norms.get(doc) & 0xFF];
      return weightValue * freq / (freq + norm);
    }

    @Override
    public Explanation explain(int doc, Explanation freq) {
      return explainScore(doc, freq, stats, norms);
    }

    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }
  }

  /** DocumentCollection statistics for the F2Log model. */
  private static class F2LogStats extends SimWeight {
    /** F2Log's idf */
    private final Explanation idf;
    /** The average document length. */
    private final float avgdl;
    /** query boost */
    private float boost;
    /** weight (idf * boost) */
    private float weight;
    /** field name, for pulling norms */
    private final String field;
    /** precomputed norm[256] with k1 * ((1 - b) + b * dl / avgdl) */
    private final float cache[];

    F2LogStats(String field, Explanation idf, float avgdl, float cache[]) {
      this.field = field;
      this.idf = idf;
      this.avgdl = avgdl;
      this.cache = cache;
      normalize(1f, 1f);
    }

    @Override
    public float getValueForNormalization() {
      // we return a TF-IDF like normalization to be nice, but we don't actually normalize ourselves.
      return weight * weight;
    }

    @Override
    public void normalize(float queryNorm, float boost) {
      // we don't normalize with queryNorm at all, we just capture the top-level boost
      this.boost = boost;
      this.weight = idf.getValue() * boost;
    }
  }

  private Explanation explainTFNorm(int doc, Explanation freq, F2LogStats stats, NumericDocValues norms) {
    List<Explanation> subs = new ArrayList<>();
    subs.add(freq);
    subs.add(Explanation.match(s, "parameter s"));
    if (norms == null) {
      subs.add(Explanation.match(0, "parameter s (norms omitted for field)"));
      return Explanation.match(
        freq.getValue() / freq.getValue(),
        "tfNorm, computed from:", subs);
    } else {
      float doclen = decodeNormValue((byte)norms.get(doc));
      subs.add(Explanation.match(stats.avgdl, "avgFieldLength"));
      subs.add(Explanation.match(doclen, "fieldLength"));
      return Explanation.match(
        freq.getValue() / (freq.getValue() + s + s * doclen/stats.avgdl),
        "tfNorm, computed from:", subs);
    }
  }

  private Explanation explainScore(int doc, Explanation freq, F2LogStats stats, NumericDocValues norms) {
    Explanation boostExpl = Explanation.match(stats.boost, "boost");
    List<Explanation> subs = new ArrayList<>();
    if (boostExpl.getValue() != 1.0f)
      subs.add(boostExpl);
    subs.add(stats.idf);
    Explanation tfNormExpl = explainTFNorm(doc, freq, stats, norms);
    subs.add(tfNormExpl);
    return Explanation.match(
      boostExpl.getValue() * stats.idf.getValue() * tfNormExpl.getValue(),
      "score(doc="+doc+",freq="+freq+"), product of:", subs);
  }

  @Override
  public String toString() {
    return "F2Logs=" + s +")";
  }

  /**
   * Returns the <code>b</code> parameter
   * @see #F2LogSimilarity(float)
   *
   * @return s
   */
  public float getS() {
    return s;
  }
}
