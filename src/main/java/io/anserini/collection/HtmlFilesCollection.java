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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Using this class we can index a directory consists of HTML files.
 * The file name (excluding the extension) will be the docid and the stripped contents will be the contents.
 * Please note that we intentionally do not apply any restrictions on what the file extension should be --
 * this makes the class a more generic class for indexing other types of the files, e.g. plain text files.
 *
 */
public class HtmlFilesCollection extends DocumentCollection
    implements FileSegmentProvider<HtmlFilesCollection.Document> {

  private static final Logger LOG = LogManager.getLogger(HtmlFilesCollection.class);

  @Override
  public FileSegment createFileSegment(Path p) throws IOException {
    return new FileSegment(p);
  }

  @Override
  public List<Path> getFileSegmentPaths() {
    return discover(path, EMPTY_SET, EMPTY_SET, EMPTY_SET, EMPTY_SET, EMPTY_SET);
  }

  public static class FileSegment extends AbstractFileSegment<Document>  {
    private TarArchiveInputStream inputStream = null;
    private ArchiveEntry nextEntry = null;

    @SuppressWarnings("unchecked")
    public FileSegment(Path path) throws IOException {
      //dType = (T) new Document(path.toString());
      this.path = path;
      this.bufferedReader = null;
      if (path.toString().endsWith(".tgz") || path.toString().endsWith(".tar.gz")) {
        inputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(path.toFile())));
        getNextEntry();
      }
    }

    @Override
    public void close() throws IOException {
      atEOF = true;
      super.close();
    }

    @Override
    public boolean hasNext() {
      return !atEOF;
    }

    @Override
    public Document next() {
      Document doc;

      try {
        if (path.toString().endsWith(".tgz") || path.toString().endsWith(".tar.gz")) {
          bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
          doc = new Document(bufferedReader, Paths.get(nextEntry.getName()).getFileName().toString().replaceAll("\\.html$", ""));
          getNextEntry();
        } else {
          bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()), StandardCharsets.UTF_8));
          doc = new Document(bufferedReader, path.getFileName().toString().replaceAll("\\.html$", ""));
          atEOF = true;
        }
      } catch (IOException e) {
        if (path.toString().endsWith(".html")) {
          atEOF = true;
        }
        return null;
      }

      return doc;
    }

    private void getNextEntry() throws IOException {
      nextEntry = inputStream.getNextEntry();
      if (nextEntry == null) {
        atEOF = true;
        return;
      }
      // an ArchiveEntry may be a directory, so we need to read a next one.
      //   this must be done after the null check.
      if (nextEntry.isDirectory()) {
        getNextEntry();
      }
    }
  }

  public static class Document implements SourceDocument {
    private String id;
    private String contents;

    public Document(BufferedReader bRdr, String fileName) {
      StringBuilder sb = new StringBuilder();
      try {
        String line;
        while ((line = bRdr.readLine()) != null) {
          sb.append(line).append("\n");
        }
        this.contents = sb.toString();
        this.id = fileName;
      } catch (IOException e) {
        LOG.error("Error process file " + fileName);
        LOG.error(e);
      }
    }

    @Override
    public Document readNextRecord(BufferedReader bRdr) {
      // We're slowly refactoring to get rid of this method.
      // See https://github.com/castorini/Anserini/issues/254
      throw new UnsupportedOperationException();
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String content() {
      return contents;
    }

    @Override
    public boolean indexable() {
      return true;
    }
  }
}
