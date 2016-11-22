package io.anserini.nrts;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.anserini.nrts.TweetStreamIndexer.StatusField;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TweetServlet extends HttpServlet {

  // TODO Auto-generated serialVersionUID
  private static final long serialVersionUID = 1L;
  String MustacheTemplatePath="src/main/java/io/anserini/nrts/ServletResponseTemplate.mustache";
  private IndexReader reader;
  
  static class TweetHits {
    
    TweetHits(String query, int hitLength){
      this.query=query;
      this.hitLength=hitLength;      
    }
       
    String query;
    int hitLength;   
    List<Hit> hits=new ArrayList<Hit>();
    
    static class Hit{
      int hitIndex;
      String tweetInfo;
      Hit(int hitIndex, String tweetInfo) {
        this.hitIndex = hitIndex;
        this.tweetInfo = tweetInfo;
      } 
    }
    
    public void addHit(int hitIndex, String tweetInfo) {
      hits.add(new Hit(hitIndex,tweetInfo));
    }
    
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    if (request.getRequestURI().equals("/search")) {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("text/html");
      request.setCharacterEncoding("UTF-8");
      Query q;
      try {
        q = new QueryParser(StatusField.TEXT.name, TweetSearcher.ANALYZER).parse(request.getParameter("query"));
        try {
          reader = DirectoryReader.open(TweetSearcher.indexWriter, true, true);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        IndexReader newReader = DirectoryReader.openIfChanged((DirectoryReader) reader, TweetSearcher.indexWriter,
            true);
        if (newReader != null) {
          reader.close();
          reader = newReader;
        }
        IndexSearcher searcher = new IndexSearcher(reader);
        
        int topN;
        if (request.getParameter("top") != null) {
          topN = Integer.parseInt(request.getParameter("top"));
        } else {
          // TODO configurable, default(parameter unspecified in url) topN = 20
          topN = 20;
        }
        TopScoreDocCollector collector = TopScoreDocCollector.create(topN);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;        
        TweetHits tweetHits=new TweetHits(request.getParameter("query"),hits.length);
        
        for (int i = 0; i < hits.length; ++i) {
          int docId = hits[i].doc;
          Document d = searcher.doc(docId);         
          tweetHits.addHit(i,String.valueOf(d.get(StatusField.ID.name)));          
        }
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(MustacheTemplatePath);
        mustache.execute(response.getWriter(), tweetHits).flush();
      } catch (ParseException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
