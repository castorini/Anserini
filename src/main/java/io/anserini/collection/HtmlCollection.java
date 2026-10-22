/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
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
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

/**
 * A collection of HTML documents.
 * The file name (excluding the extension) will be the docid and the stripped contents will be the contents.
 * Please note that we intentionally do not apply any restrictions on what the file extension should be --
 * this makes the class a more generic class for indexing other types of the files, e.g. plain text files.
 */
public class HtmlCollection extends DocumentCollection<HtmlCollection.Document> {
  private static final Logger LOG = LogManager.getLogger(HtmlCollection.class);

  public HtmlCollection(Path path) {
    this.path = path;
  }

  public HtmlCollection() {
  }

  @Override
  public FileSegment<HtmlCollection.Document> createFileSegment(Path p) throws IOException {
    return new Segment(p);
  }

  @Override
  public FileSegment<HtmlCollection.Document> createFileSegment(BufferedReader bufferedReader) throws IOException {
    return new Segment(bufferedReader);
  }

  public FileSegment<HtmlCollection.Document> createFileSegment(InputStream inputStream, String segmentName) throws IOException {
    return new Segment(inputStream, segmentName);
  }

  /**
   * An individual file in {@code HtmlCollection}.
   */
  public static class Segment extends FileSegment<HtmlCollection.Document> {
    private static final String HTML_EXTENSION = ".html";
    private static final String TGZ_EXTENSION = ".tgz";
    private static final String TAR_GZ_EXTENSION = ".tar.gz";
    private static final String STREAM_SEGMENT_NAME = "s3-object";

    private TarArchiveInputStream inputStream = null;
    private final boolean archiveSegment;
    private final String segmentName;
    private ArchiveEntry nextEntry = null;

    public Segment(Path path) throws IOException {
      super(path);
      this.bufferedReader = null;
      String pathString = path.toString();
      this.archiveSegment = pathString.endsWith(TGZ_EXTENSION) || pathString.endsWith(TAR_GZ_EXTENSION);
      this.segmentName = path.getFileName() == null ? pathString : path.getFileName().toString();
      if (archiveSegment) {
        inputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(path.toFile())));
      }
    }

    public Segment(BufferedReader bufferedReader) throws IOException {
      super(bufferedReader);
      this.archiveSegment = true;
      this.segmentName = STREAM_SEGMENT_NAME;
      inputStream = new TarArchiveInputStream(
        ReaderInputStream.builder()
          .setReader(bufferedReader)
          .setCharset(StandardCharsets.UTF_8)
          .get()
      );
    }

    public Segment(InputStream inputStream, String segmentName) throws IOException {
      super((BufferedReader) null);
      this.bufferedReader = null;
      this.segmentName = segmentName == null ? STREAM_SEGMENT_NAME : segmentName;
      this.archiveSegment = this.segmentName.endsWith(TGZ_EXTENSION) || this.segmentName.endsWith(TAR_GZ_EXTENSION);
      if (archiveSegment) {
        this.inputStream = new TarArchiveInputStream(new GzipCompressorInputStream(inputStream));
      } else {
        this.bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      }
    }

    @Override
    public void readNext() throws IOException {
      try {
        if (archiveSegment) {
          getNextEntry();
          bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
          bufferedRecord = new Document(bufferedReader, stripHtmlExtension(Paths.get(nextEntry.getName()).getFileName().toString()));
        } else {
          if (bufferedReader == null) {
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()), StandardCharsets.UTF_8));
          }
          bufferedRecord = new Document(bufferedReader, stripHtmlExtension(segmentName));
          atEOF = true;
        }
      } catch (IOException e1) {
        if (!archiveSegment && segmentName.endsWith(HTML_EXTENSION)) {
          atEOF = true;
        }
        throw e1;
      }
    }

    private static String stripHtmlExtension(String fileName) {
      if (fileName.endsWith(HTML_EXTENSION)) {
        return fileName.substring(0, fileName.length() - HTML_EXTENSION.length());
      }
      return fileName;
    }

    private void getNextEntry() throws IOException {
      nextEntry = inputStream.getNextEntry();
      if (nextEntry == null) {
        throw new NoSuchElementException();
      }
      // an ArchiveEntry may be a directory, so we need to read a next one.
      //   this must be done after the null check.
      if (nextEntry.isDirectory()) {
        getNextEntry();
      }
    }
  }

  /**
   * A generic document in {@code HtmlCollection}.
   */
  public static class Document implements SourceDocument {
    private String id;
    private String raw;

    public Document(BufferedReader bRdr, String fileName) {
      StringBuilder sb = new StringBuilder();
      try {
        String line;
        while ((line = bRdr.readLine()) != null) {
          sb.append(line).append("\n");
        }
        this.raw = sb.toString();
        this.id = fileName;
      } catch (IOException e) {
        LOG.error("Error process file " + fileName);
        LOG.error(e);
      }
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String contents() {
      try {
        return JsoupStringTransform.SINGLETON.apply(raw).trim();
      } catch (Exception e) {
        // If there's an exception, just eat it and return empty contents.
        return "";
      }
    }

    @Override
    public String raw() {
      return raw;
    }

    @Override
    public boolean indexable() {
      return true;
    }
  }
}
