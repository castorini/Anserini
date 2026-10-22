/**
 * Anserini: A Lucene toolkit for replicable information retrieval research
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

package io.anserini.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.ParserProperties;

import io.anserini.collection.DocumentCollection;
import io.anserini.collection.FileSegment;
import io.anserini.collection.SourceDocument;

// Simple program to benchmark IO performance, reading collections from disk.
public final class BenchmarkCollectionReader {
  private static final Logger LOG = LogManager.getLogger(BenchmarkCollectionReader.class);

  public static final class Args {
    @Option(name = "-input", metaVar = "[Directory]", required = true, usage = "collection directory")
    public String input;

    @Option(name = "-threads", metaVar = "[Num]", required = true, usage = "Number of Threads")
    public int threads;

    @Option(name = "-collection", required = true, usage = "collection class in io.anserini.collection")
    public String collectionClass;
  }

  private final class ReaderThread extends Thread {
    final private Path inputFile;
    final private DocumentCollection<?> collection;
    private int recordsProcessed = 0;

    private ReaderThread(DocumentCollection<?> collection, Path inputFile) {
      this.collection = collection;
      this.inputFile = inputFile;

      setName(inputFile.getFileName().toString());
    }

    @Override
    public void run() {
      try {
        @SuppressWarnings("unchecked")
        FileSegment<SourceDocument> segment = (FileSegment<SourceDocument>) collection.createFileSegment(inputFile);

        // We're calling these records because the documents may not an indexable.
        int records = 0;
        for (@SuppressWarnings("unused") SourceDocument ignored : segment) {
          records++;
        }
        recordsProcessed = records;

        segment.close();
        LOG.info(String.format("%s%s%s: %d records processed.",
            inputFile.getParent().getFileName().toString(),
            File.separator,
            inputFile.getFileName().toString(),
            records));
      } catch (Exception e) {
        LOG.error(Thread.currentThread().getName() + ": Unexpected Exception:", e);
      }
    }

    public int getRecordsProcessed() {
      return recordsProcessed;
    }
  }

  private final Args args;
  private final Path collectionPath;
  private final Class<?> collectionClass;
  private final DocumentCollection<?> collection;
  private final LongAdder totalRecordCount = new LongAdder();
  private final AtomicInteger completedTaskCount = new AtomicInteger();

  public BenchmarkCollectionReader(Args args) throws Exception {
    this.args = args;

    LOG.info("DocumentCollection path: " + args.input);
    LOG.info("CollectionClass: " + args.collectionClass);
    LOG.info("Threads: " + args.threads);

    collectionPath = Paths.get(args.input);
    if (!Files.exists(collectionPath) || !Files.isReadable(collectionPath) || !Files.isDirectory(collectionPath)) {
      throw new RuntimeException(String.format("Document directory %s does not exist or is not readable, please check the path.", collectionPath));
    }

    this.collectionClass = Class.forName("io.anserini.collection." + args.collectionClass);

    // Initialize the collection.
    collection = (DocumentCollection<?>) this.collectionClass.getConstructor(Path.class).newInstance(collectionPath);
  }

  public void run() {
    final long start = System.nanoTime();
    LOG.info("Starting MapCollections...");
    totalRecordCount.reset();
    completedTaskCount.set(0);

    final List<?> segmentPaths = collection.getSegmentPaths();
    final int segmentCnt = segmentPaths.size();

    LOG.info(String.format("%d files found in %s", segmentCnt, collectionPath));

    List<Callable<Void>> tasks = new ArrayList<>(segmentCnt);
    for (int i = 0; i < segmentCnt; i++) {
      int finalI = i;
      tasks.add(() -> {
        ReaderThread reader = new ReaderThread(collection, (Path) segmentPaths.get(finalI));
        reader.run();
        totalRecordCount.add(reader.getRecordsProcessed());
        completedTaskCount.incrementAndGet();
        return null;
      });
    }

    // Work-stealing executor + progress logger
    try (ExecutorService executor = Executors.newWorkStealingPool(args.threads);
         ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor()) {
      // log progress every minute
      monitor.scheduleAtFixedRate(() -> {
        double pct = (double) completedTaskCount.get() / segmentCnt * 100.0d;
        LOG.info(String.format("%.2f percent completed", pct));
      }, 1, 1, TimeUnit.MINUTES);

      executor.invokeAll(tasks);      // blocks until every task is done
      monitor.shutdown();             // stop the progress logger
      monitor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    LOG.info("Total tasks completed: {}", completedTaskCount.get());
    LOG.info("Total records processed: {}", String.format(Locale.US, "%,d", totalRecordCount.sum()));

    if (segmentCnt != completedTaskCount.get()) {
      throw new RuntimeException(String.format("totalFiles = %d is not equal to completedTaskCount = %d", segmentCnt, completedTaskCount.get()));
    }

    final long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    LOG.info("Total running time: {}ms", durationMillis);
  }

  public long getTotalRecordCount() {
    return totalRecordCount.sum();
  }

  public int getCompletedTaskCount() {
    return completedTaskCount.get();
  }

  public static void main(String[] args) throws Exception {
    Args mapCollectionArgs = new Args();
    CmdLineParser parser = new CmdLineParser(mapCollectionArgs, ParserProperties.defaults().withUsageWidth(90));

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.err.println(String.format("Example: %s%s", BenchmarkCollectionReader.class.getSimpleName(), parser.printExample(OptionHandlerFilter.REQUIRED)));
      return;
    }

    new BenchmarkCollectionReader(mapCollectionArgs).run();
  }
}
