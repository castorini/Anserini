package io.anserini.util;

import io.anserini.collection.FileSegment;
import io.anserini.collection.HtmlCollection;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ReadHtmlCollectionSegmentOverHttp {
  public static void main(String[] args) throws Exception {
    String s3Path = "s3://pyserini/collections/cacm/cacm.tar.gz";
    URI s3Uri = URI.create(s3Path);
    String bucket = s3Uri.getHost();
    String objectKey = s3Uri.getPath().replaceFirst("^/", "");
    URI uri = URI.create("https://rgw.cs.uwaterloo.ca/" + bucket + "/" + objectKey);

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new RuntimeException("Download failed with HTTP status " + response.statusCode());
    }

    HtmlCollection collection = new HtmlCollection();
    try (InputStream responseBody = response.body();
         FileSegment<HtmlCollection.Document> segment = collection.createFileSegment(responseBody, objectKey)) {
      for (HtmlCollection.Document doc : segment) {
        String contents = doc.contents();
        int end = Math.min(100, contents.length());
        String preview = contents.substring(0, end).replace('\n', ' ').replace('\r', ' ');
        System.out.println(doc.id() + "\t" + preview);
      }
    }
  }
}
