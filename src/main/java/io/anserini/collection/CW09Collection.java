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

package io.anserini.collection;

import io.anserini.document.ClueWeb09WarcRecord;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Class representing an instance of the ClueWeb09 collection.
*/
public class CW09Collection extends WarcCollection<ClueWeb09WarcRecord> {
  public class File extends WarcCollection.File {
    public File(Path curInputFile) throws IOException {
      super(curInputFile);
    }

    @Override
    public ClueWeb09WarcRecord next() {
      ClueWeb09WarcRecord doc = new ClueWeb09WarcRecord();
      try {
        doc = doc.readNextWarcRecord(inStream, ClueWeb09WarcRecord.WARC_VERSION);
        if (doc == null) {
          atEOF = true;
          doc = null;
        }
      } catch (IOException e) {
        doc = null;
      }
      return doc;
    }
  }

  public CW09Collection() throws IOException {
    super();
  }

  @Override
  public CollectionFile createCollectionFile(Path p) throws IOException {
    return new File(p);
  }
}
