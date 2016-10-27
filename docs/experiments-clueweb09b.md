# Anserini Experiments on ClueWeb09 (Category B)

Indexing:

```
nohup sh target/appassembler/bin/IndexWebCollection -collection CW09 -input /path/to/cw09/ClueWeb09_English_1/ \
 -index lucene-index.cw09b.pos -threads 32 -positions -optimize \
 2> log.cw09b.pos.emptyDocids.txt 1> log.cw09b.pos.recordCounts.txt &
```

The directory `/path/to/cw09/ClueWeb09_English_1` should be the root directory of ClueWeb09B collection, i.e., `ls /path/to/cw09/ClueWeb09_English_1` should bring up a bunch of subdirectories, `en0000` to `enwp03`.

After indexing is done, you should be able to perform a retrieval run (the options are exactly the same as with Gov2 above):

```
sh target/appassembler/bin/SearchWebCollection -collection CW09 -index lucene-index.cw09b.pos -bm25 \
  -topics src/main/resources/topics-and-qrels/topics.web.51-100.txt -output run.web.51-100.bm25.txt
```

You should then be able to evaluate using `trec_eval`, as with Gov2 above. With the topics and qrels in `src/main/resources/topics-and-qrels/`, you should be able to replicate the following results:

MAP                                                                           | BM25   |BM25+RM3| QL     | QL+RM3
------------------------------------------------------------------------------|--------|--------|--------|--------
[TREC 2010 Web Track: Topics 51-100](http://trec.nist.gov/data/web10.html)    | 0.1091 | 0.1065 | 0.1026 | 0.1055
[TREC 2011 Web Track: Topics 101-150](http://trec.nist.gov/data/web2011.html) | 0.1095 | 0.1140 | 0.0972 | 0.1021
[TREC 2012 Web Track: Topics 151-200](http://trec.nist.gov/data/web2012.html) | 0.1072 | 0.1336 | 0.1035 | 0.1120


P30                                                                           | BM25   |BM25+RM3| QL     | QL+RM3
------------------------------------------------------------------------------|--------|--------|--------|--------
[TREC 2010 Web Track: Topics 51-100](http://trec.nist.gov/data/web10.html)    | 0.2667 | 0.2583 | 0.2403 | 0.2521
[TREC 2011 Web Track: Topics 101-150](http://trec.nist.gov/data/web2011.html) | 0.2540 | 0.2627 | 0.2220 | 0.2267
[TREC 2012 Web Track: Topics 151-200](http://trec.nist.gov/data/web2012.html) | 0.2187 | 0.2313 | 0.2027 | 0.2007
