<<<<<<< HEAD
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

=======
>>>>>>> 0b9b484e9f5f9d8568abcb91aa6b305c3bb17b17
package io.anserini.qa.passage;

public class ScoredPassage implements Comparable<ScoredPassage> {
  String sentence;
  double score;

  public ScoredPassage(String sentence, double score) {
    this.sentence = sentence;
    this.score = score;
  }

  public String getSentence() {
    return sentence;
  }

  public double getScore() {
    return score;
  }

  @Override
  public int compareTo(ScoredPassage o) {
    if(score > o.score) {
      return -1;
    } else if(score < o.score) {
      return 1;
    } else {
      return 0;
    }
  }
}
