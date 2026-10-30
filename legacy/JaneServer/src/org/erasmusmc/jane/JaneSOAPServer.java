/*******************************************************************************
 * Copyright 2017 Erasmus University Medical Center
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.erasmusmc.jane;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similar.MoreLikeThis;
import org.apache.lucene.util.Version;
import org.erasmusmc.jane.dataClasses.Author;
import org.erasmusmc.jane.dataClasses.EMail;
import org.erasmusmc.jane.dataClasses.JaneSettings;
import org.erasmusmc.jane.dataClasses.Journal;
import org.erasmusmc.jane.dataClasses.LogFile;
import org.erasmusmc.jane.dataClasses.Paper;
import org.erasmusmc.jane.dataClasses.ScoredItem;

/**
 * Main class that handles all requests to Jane.
 * 
 * @author schuemie
 *
 */
public class JaneSOAPServer {
	
	public static int					minTermFreq				= 1;
	public static String				FIELD_TEXT				= "text";
	public static String				FIELD_PMID				= "pmid";
	public static String				FIELD_EMAIL_AUTHOR		= "emailauthor";
	public static String				FIELD_PUBLICATION_TYPE	= "ptyp";
	public static String				FIELD_LANGUAGE			= "language";
	public static String				FIELD_AUTHORS			= "authors";
	public static String				FIELD_JOURNAL			= "journal";
	public static String				FIELD_RECENT			= "recent";
	public static String				FIELD_OPEN_ACCESS		= "openaccess";
	public static String				FIELD_MEDLINE_INDEXED	= "medlineindexed";
	public static String				FIELD_NIHPA				= "nihpa";
	public static String				FIELD_PMC_MONTHS		= "pmcmonths";
	
	public static int					maxResultSize			= 100;
	
	private static boolean				initialized				= false;
	private static JaneSettings			settings;
	private static Analyzer				analyzer;
	private static Lock					lock					= new ReentrantLock();
	private static LogFile				logFile;
	private static IndexSearcherPool	indexSearcherPool		= new IndexSearcherPool();
	
	public JaneSOAPServer() {
		if (!initialized) {
			initialized = true;
			settings = JaneSettings.load();
			analyzer = new MedlineAnalyzer(new String[] { "of", "the", "and", "in" });
			logFile = new LogFile(settings.getLogFile());
		}
	}
	
	public Journal[] getJournals(String text, String filterString) {
		if (text == null || text.equals(""))
			return new Journal[0];
		String subset = extractSubset(filterString);
		List<Journal> journals;
		lock.lock();
		try {
			logFile.addToLog("Journals\t" + text.length() + "\t" + filterString);
			ScoreDoc[] topDocs = searchIndex(settings.getIndexFolder(subset), text, filterString + "|recent", 0, settings.getK(subset));
			journals = computeJournalScores(settings.getIndexFolder(subset), topDocs);
		} finally {
			lock.unlock();
		}
		return (Journal[]) journals.toArray(new Journal[journals.size()]);
	}
	
	/*
	 * private String apiString() { MessageContext messageContext = MessageContext.getCurrentMessageContext();
	 * 
	 * String ipAddress = (String)messageContext.getProperty(MessageContext.REMOTE_ADDR); return "|api="+ipAddress; }
	 */
	
	public Author[] getAuthors(String text, String filterString) {
		if (text == null || text.equals(""))
			return new Author[0];
		if (filterString == null)
			filterString = "";
		String subset = extractSubset(filterString);
		List<Author> authors;
		lock.lock();
		try {
			logFile.addToLog("Authors\t" + text.length() + "\t" + filterString);
			ScoreDoc[] topDocs = searchIndex(settings.getIndexFolder(subset), text, filterString, 0, settings.getK(subset));
			authors = computeAuthorScores(settings.getIndexFolder(subset), topDocs);
		} finally {
			lock.unlock();
		}
		return (Author[]) authors.toArray(new Author[authors.size()]);
	}
	
	public Paper[] getPapers(String text, String filterString, int count, int offset) {
		if (text.equals(""))
			return new Paper[0];
		String subset = extractSubset(filterString);
		List<Paper> papers;
		lock.lock();
		try {
			logFile.addToLog("Articles\t" + text.length() + "\t" + filterString);
			ScoreDoc[] topDocs = searchIndex(settings.getIndexFolder(subset), text, filterString, offset, count);
			papers = docsToPapers(settings.getIndexFolder(subset), topDocs);
		} finally {
			lock.unlock();
		}
		return (Paper[]) papers.toArray(new Paper[papers.size()]);
	}
	
	public EMail[] getEMail(String author, String pmids, String filterString) {
		List<EMail> eMails;
		lock.lock();
		String subset = extractSubset(filterString);
		try {
			logFile.addToLog("EMail\t" + author.length() + "\t");
			if (settings.isRequireDisambiguationForEMail(subset)) {
				Set<String> coauthors = getCoauthors(settings.getIndexFolder(subset), author, pmids);
				eMails = getEMails(settings.getIndexFolder(subset), author, coauthors);
			} else
				eMails = getEMails(settings.getIndexFolder(subset), author, null);
			
		} finally {
			lock.unlock();
		}
		return (EMail[]) eMails.toArray(new EMail[eMails.size()]);
	}
	
	public String[] getSimilarDocumentsOfAuthor(String text, String author, String filterString) {
		String subset = extractSubset(filterString);
		List<String> pmids = getSimilarDocuments(settings.getIndexFolder(subset), text, author, "authors", settings.getMaxSimilarDocuments(subset));
		return (String[]) pmids.toArray(new String[pmids.size()]);
	}
	
	public String[] getSimilarDocumentsInJournal(String text, String journal, String filterString) {
		String subset = extractSubset(filterString);
		List<String> pmids = getSimilarDocuments(settings.getIndexFolder(subset), text, journal, "journal", settings.getMaxSimilarDocuments(subset));
		return (String[]) pmids.toArray(new String[pmids.size()]);
	}
	
	private List<Paper> docsToPapers(String indexFolder, ScoreDoc[] topDocs) {
		List<Paper> papers = new ArrayList<Paper>();
		for (int i = 0; i < topDocs.length; i++) {
			try {
				IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
				ScoreDoc scoreDoc = topDocs[i];
				Document document = indexSearcher.doc(scoreDoc.doc);
				papers.add(new Paper(document, scoreDoc.score));
			} catch (CorruptIndexException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return papers;
	}
	
	private List<String> getSimilarDocuments(String indexFolder, String text, String filterTerm, String filterField, int maxDocuments) {
		List<String> pmids = new ArrayList<String>();
		try {
			lock.lock();
			IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
			TopScoreDocCollector collector = TopScoreDocCollector.create(maxDocuments, true);
			Query query = initMLT(indexSearcher).like(new StringReader(text));
			if (filterTerm.equals("")) {
				indexSearcher.search(query, collector);
			} else {
				if (filterField.equals(FIELD_JOURNAL)) {
					TermsFilter filter = new TermsFilter();
					filter.addTerm(new Term(FIELD_JOURNAL, filterTerm));
					indexSearcher.search(query, filter, collector);
				} else {
					Query filterQuery = new QueryParser(Version.LUCENE_30, FIELD_AUTHORS, analyzer).parse("\"" + filterTerm + "\"");
					QueryWrapperFilter filter = new QueryWrapperFilter(filterQuery);
					indexSearcher.search(query, filter, collector);
				}
			}
			ScoreDoc[] scoreDocs = collector.topDocs().scoreDocs;
			for (int i = 0; i < scoreDocs.length; i++) {
				int docID = scoreDocs[i].doc;
				Document doc = indexSearcher.doc(docID);
				pmids.add(doc.get("pmid"));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return pmids;
	}
	
	private List<EMail> getEMails(String indexFolder, String author, Set<String> coauthors) {
		List<EMail> eMails = new ArrayList<EMail>();
		StringBuilder filterString = new StringBuilder();
		if (coauthors != null) {
			Iterator<String> iterator = coauthors.iterator();
			while (iterator.hasNext()) {
				filterString.append("\"" + iterator.next() + "\"");
				if (iterator.hasNext())
					filterString.append(" OR ");
			}
		}
		try {
			IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
			Query query = new QueryParser(Version.LUCENE_30, FIELD_EMAIL_AUTHOR, analyzer).parse("\"" + author + "\"");
			
			TopScoreDocCollector collector = TopScoreDocCollector.create(100, true);
			if (coauthors == null)
				indexSearcher.search(query, collector);
			else {
				Filter filter = new QueryWrapperFilter(new QueryParser(Version.LUCENE_30, FIELD_AUTHORS, analyzer).parse(filterString.toString()));
				indexSearcher.search(query, filter, collector);
			}
			ScoreDoc[] scoreDocs = collector.topDocs().scoreDocs;
			for (int i = 0; i < scoreDocs.length; i++)
				if (scoreDocs[i].score > 0) {
					int docID = scoreDocs[i].doc;
					Document doc = indexSearcher.doc(docID);
					eMails.add(new EMail(doc));
				}
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Collections.sort(eMails);
		return eMails;
	}
	
	private Set<String> getCoauthors(String indexFolder, String author, String pmids) {
		Set<String> coauthors = new HashSet<String>();
		String authorlc = author.toLowerCase();
		try {
			IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
			Query query = new QueryParser(Version.LUCENE_30, FIELD_PMID, analyzer).parse(pmids.replace(";", " OR "));
			TopScoreDocCollector collector = TopScoreDocCollector.create(pmids.split(";").length, true);
			indexSearcher.search(query, collector);
			ScoreDoc[] scoreDocs = collector.topDocs().scoreDocs;
			for (int i = 0; i < scoreDocs.length; i++) {
				int docID = scoreDocs[i].doc;
				Document doc = indexSearcher.doc(docID);
				String authors = doc.get(FIELD_AUTHORS);
				for (String coauthor : authors.split("\n"))
					if (!coauthor.toLowerCase().equals(authorlc))
						coauthors.add(coauthor);
			}
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return coauthors;
	}
	
	private ScoreDoc[] searchIndex(String indexFolder, String text, String filterString, int offset, int size) {
		TopScoreDocCollector collector = TopScoreDocCollector.create(offset + size, true);
		try {
			IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
			Query query;
			if (filterString.contains("structured query"))
				query = new QueryParser(Version.LUCENE_30, FIELD_TEXT, analyzer).parse(text);
			else
				query = initMLT(indexSearcher).like(new StringReader(text));
			
			BooleanQuery filterQuery = parseFilter(filterString);
			if (filterQuery.clauses().size() == 0) {
				indexSearcher.search(query, collector);
			} else {
				Filter filter = new QueryWrapperFilter(filterQuery);
				indexSearcher.search(query, filter, collector);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		TopDocs docs = collector.topDocs();
		if (offset == 0)
			return normalize(docs.scoreDocs, docs.getMaxScore());
		else {
			int length = docs.scoreDocs.length - offset;
			ScoreDoc[] subSet = new ScoreDoc[length];
			System.arraycopy(docs.scoreDocs, offset, subSet, 0, length);
			return normalize(subSet, docs.getMaxScore());
		}
	}
	
	private MoreLikeThis initMLT(IndexSearcher indexSearcher) {
		MoreLikeThis mlt = new MoreLikeThis(indexSearcher.getIndexReader());
		mlt.setFieldNames(new String[] { FIELD_TEXT });
		mlt.setMinTermFreq(minTermFreq);
		mlt.setAnalyzer(analyzer);
		return mlt;
	}
	
	private ScoreDoc[] normalize(ScoreDoc[] scoreDocs, float maxScore) {
		// Reproduces the score normalization in Lucene <3, limiting the scores to a max of 1
		if (maxScore > 1)
			for (int i = 0; i < scoreDocs.length; i++)
				scoreDocs[i].score /= maxScore;
		return scoreDocs;
	}
	
	private BooleanQuery parseFilter(String filterString) throws ParseException {
		StringBuilder typeFilterString = new StringBuilder();
		StringBuilder languageFilterString = new StringBuilder();
		String[] parts = filterString.split("\\|");
		BooleanQuery query = new BooleanQuery();
		
		for (String part : parts) {
			String[] subs = part.split("=");
			if (subs[0].equals("language")) {
				if (languageFilterString.length() != 0)
					languageFilterString.append(" OR ");
				languageFilterString.append(subs[1]);
			} else if (subs[0].equals("type")) {
				if (typeFilterString.length() != 0)
					typeFilterString.append(" OR ");
				typeFilterString.append(subs[1]);
			} else if (subs[0].equals("openaccess")) {
				query.add(new QueryParser(Version.LUCENE_30, FIELD_OPEN_ACCESS, analyzer).parse(subs[1]), BooleanClause.Occur.MUST);
			} else if (subs[0].equals("medlineindexed")) {
				query.add(new QueryParser(Version.LUCENE_30, FIELD_MEDLINE_INDEXED, analyzer).parse(subs[1]), BooleanClause.Occur.MUST);
			} else if (subs[0].equals("nihpa")) {
				query.add(new QueryParser(Version.LUCENE_30, FIELD_NIHPA, analyzer).parse(subs[1]), BooleanClause.Occur.MUST);
			} else if (subs[0].equals("pmcmonths")) {
				if (subs[1].equals("immediate"))
					query.add(new QueryParser(Version.LUCENE_30, FIELD_PMC_MONTHS, analyzer).parse("0"), BooleanClause.Occur.MUST);
				else if (subs[1].equals("inoneyear"))
					query.add(NumericRangeQuery.newIntRange(FIELD_PMC_MONTHS, 0, 12, true, true), BooleanClause.Occur.MUST);
				else if (subs[1].equals("any"))
					query.add(NumericRangeQuery.newIntRange(FIELD_PMC_MONTHS, 0, 999, true, true), BooleanClause.Occur.MUST);
			} else if (part.equals("recent")) {
				query.add(new QueryParser(Version.LUCENE_30, FIELD_RECENT, analyzer).parse("true"), BooleanClause.Occur.MUST);
			}
		}
		
		if (typeFilterString.length() != 0)
			query.add(new QueryParser(Version.LUCENE_30, FIELD_PUBLICATION_TYPE, analyzer).parse(typeFilterString.toString()), BooleanClause.Occur.MUST);
		if (languageFilterString.length() != 0)
			query.add(new QueryParser(Version.LUCENE_30, FIELD_LANGUAGE, analyzer).parse(languageFilterString.toString()), BooleanClause.Occur.MUST);
		
		return query;
	}
	
	private List<Journal> computeJournalScores(String indexFolder, ScoreDoc[] topDocs) {
		Map<String, Journal> journalName2score = new HashMap<String, Journal>();
		for (int i = 0; i < topDocs.length; i++) {
			try {
				IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
				ScoreDoc scoreDoc = topDocs[i];
				Document document = indexSearcher.doc(scoreDoc.doc);
				Journal journal = journalName2score.get(document.get("journal"));
				if (journal == null) {
					journal = new Journal(document);
					journalName2score.put(document.get("journal"), journal);
				}
				journal.addPaper(new Paper(document, scoreDoc.score));
				journal.setScore(journal.getScore() + scoreDoc.score);
			} catch (CorruptIndexException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		List<Journal> sortedJournals = new ArrayList<Journal>(journalName2score.values());
		Collections.sort(sortedJournals);
		normalizeScores(sortedJournals);
		if (sortedJournals.size() > maxResultSize)
			sortedJournals = sortedJournals.subList(0, maxResultSize);
		return sortedJournals;
	}
	
	private void normalizeScores(List<? extends ScoredItem> scoredItems) {
		double sum = 0;
		for (ScoredItem item : scoredItems)
			sum += item.getScore();
		for (ScoredItem score : scoredItems)
			score.setScore(score.getScore() / sum);
	}
	
	private List<Author> computeAuthorScores(String indexFolder, ScoreDoc[] topDocs) {
		Map<String, Author> authorName2score = new HashMap<String, Author>();
		for (int i = 0; i < topDocs.length; i++) {
			try {
				IndexSearcher indexSearcher = indexSearcherPool.get(indexFolder);
				ScoreDoc scoreDoc = topDocs[i];
				Document document = indexSearcher.doc(scoreDoc.doc);
				Paper paper = new Paper(document, scoreDoc.score);
				for (String authorName : document.get("authors").split("\n")) {
					Author author = authorName2score.get(authorName);
					if (author == null) {
						author = new Author(authorName);
						authorName2score.put(authorName, author);
					}
					author.addPaper(paper);
					author.setScore(author.getScore() + scoreDoc.score);
				}
			} catch (CorruptIndexException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		List<Author> sortedAuthors = new ArrayList<Author>(authorName2score.values());
		Collections.sort(sortedAuthors);
		normalizeScores(sortedAuthors);
		if (sortedAuthors.size() > maxResultSize)
			sortedAuthors = sortedAuthors.subList(0, maxResultSize);
		return sortedAuthors;
	}
	
	private String extractSubset(String filterString) {
		String subset = JaneSettings.DEFAULT;
		if (filterString != null)
			for (String part : filterString.split("\\|")) {
				String[] subs = part.split("=");
				if (subs[0].equals("subset"))
					subset = subs[1];
			}
		return subset;
	}
}