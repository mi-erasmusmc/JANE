package org.erasmusmc.jane.indexConstruction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.erasmusmc.medline.Author;
import org.erasmusmc.medline.MedlineCitation;
import org.erasmusmc.medline.MedlineCitationIterator;
import org.erasmusmc.medline.MedlineCitationTools;
import org.erasmusmc.medline.MedlineTools;
import org.erasmusmc.medline.RetrieveSettings;
import org.erasmusmc.utilities.ReadCSVFile;
import org.erasmusmc.utilities.ReadCSVFileWithHeader;
import org.erasmusmc.utilities.ReadTextFile;
import org.erasmusmc.utilities.Row;
import org.erasmusmc.utilities.StringUtilities;
import org.erasmusmc.utilities.WriteTextFile;

public class JournalIndexer {
	public static int						minPMIDsPerJournal	= 20;
	public static int						maxJournalLength	= 80;
	public static Set<String>				ignorePubTypes		= getIgnorePubTypes();
	
	private int								insertCount			= 0;
	private int								medlineCount		= 0;
	private WriteTextFile					outfile;
	private JournalIndexerSettings			settings;
	private GregorianCalendar				calendar			= new GregorianCalendar();
	private EigenFactorData					defaultData;
	private Set<String>						openAccessJournals;
	private Map<String, Integer>			issn2pmcMonths;
	private Map<String, EigenFactorData>	issn2eigenfactordata;
	private IndexWriter						writer;
	private Set<String>						recentJournals		= null;
	private Set<String>                     medlineIndexedNlmIds = null;
	private Set<String>                     medlineIndexedIssns = null;
	private Map<String, String>				issn2title;
	private Map<String, List<String>>		title2issn;
	private Map<String, String>				issn2issn;
	
	public void index(JournalIndexerSettings settings) {
		this.settings = settings;
		loadValidPMIDs(settings.journals2PMIDsFile);
		loadMedlineIndexedJournalsFile();
		loadJournalsFile();
		loadOpenAccessJournals();
		loadPMCJournals();
		loadEigenFactorData();
		loadRecentJournals();
		try {
			// Initialize index writer:
			Directory d = FSDirectory.open(new File(settings.indexDirectory));
			Analyzer analyzer = new MedlineAnalyzer(new String[] { "of", "the", "and", "in" });
			writer = new IndexWriter(d, analyzer, true, MaxFieldLength.UNLIMITED);
			writer.setMaxBufferedDocs(1000);
			writer.setMergeFactor(100);
			
			// Initialize Medline iterator:
			RetrieveSettings retrieveSettings = MedlineTools.defaultSettings;
			retrieveSettings.retrieveCitationInformation = true;
			retrieveSettings.retrievePublicatonTypes = true;
			retrieveSettings.retrieveAuthors = true;
			retrieveSettings.retrieveLanguage = true;
			retrieveSettings.retrieveText = true;
			retrieveSettings.pmidsFile = settings.tempFolder + "PMIDsToBeIndexed.txt";
			MedlineCitationIterator iterator = new MedlineCitationIterator(retrieveSettings);
			
			// Iterate over Medline records:
			System.out.println(StringUtilities.now() + "\tCreating index");
			outfile = new WriteTextFile(settings.tempFolder + "log.txt");
			while (iterator.hasNext())
				processMedlineCitation(iterator.next());
			outfile.close();
			writer.optimize();
			writer.close();
			System.out.println("Analysed " + medlineCount + " Medline records, inserted " + insertCount + " records.");
			System.out.println(StringUtilities.now() + "\tDone");
		} catch (CorruptIndexException e) {
			e.printStackTrace();
		} catch (LockObtainFailedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadMedlineIndexedJournalsFile() {
		medlineIndexedNlmIds = new HashSet<String>();
		for (String line : new ReadTextFile(settings.medlineIndexJournalsFile))
			medlineIndexedNlmIds.add(line);
	}
	
	private static Set<String> getIgnorePubTypes() {
		Set<String> types = new HashSet<String>();
		types.add("Comment");
		types.add("Editorial");
		types.add("News");
		types.add("Historical Article");
		types.add("Congresses");
		types.add("Biography");
		types.add("Newspaper Article");
		types.add("Guideline");
		types.add("Practice Guideline");
		types.add("Interview");
		types.add("Bibliography");
		types.add("Legal Cases");
		types.add("Lectures");
		types.add("Consensus Development Conference");
		types.add("Addresses");
		types.add("Clinical Conference");
		types.add("Patient Education Handout");
		types.add("Directory");
		types.add("Technical Reportt");
		types.add("Festschrift");
		types.add("Retraction of Publication");
		types.add("Retracted Publication");
		types.add("Duplicate Publication");
		types.add("Scientific Integrity Review");
		types.add("Published Erratum");
		types.add("Consensus Development Conference, NIH");
		types.add("Periodical Index");
		types.add("Dictionary");
		types.add("Legislation");
		types.add("Government Publications");
		return types;
	}
	
	private void loadJournalsFile() {
		issn2title = new HashMap<String, String>();
		issn2issn = new HashMap<String, String>();
		title2issn = new HashMap<String, List<String>>();
		medlineIndexedIssns = new HashSet<String>();
		ReadTextFile pubmed = new ReadTextFile(settings.journalDatabaseFile);
		String title = "";
		String firstISSN = null;
		String secondISSN = null;
		String fullTitle = "";
		for (String line : pubmed) {
			if (line.startsWith("JournalTitle: ")) {
				fullTitle = line.substring("JournalTitle: ".length());
				title = fullTitle;
				firstISSN = null;
				secondISSN = null;
			} else if (line.startsWith("ISSN") || line.startsWith("ESSN")) {
				int pos = line.indexOf(':');
				if (pos != -1) {
					String issn = line.substring(pos + 2);
					if (!issn.equals("")) {
						issn2title.put(issn, title);
						String normName = normalizeName(fullTitle);
						List<String> issns = title2issn.get(normName);
						if (issns == null) {
							issns = new ArrayList<String>();
							title2issn.put(normName, issns);
						}
						issns.add(issn);
						if (issns.size() > 2)
							System.err.println("Duplicate normalized journal name " + normName + " for " + fullTitle);
						
						if (firstISSN == null)
							firstISSN = issn;
						else {
							secondISSN = issn;
							issn2issn.put(firstISSN, issn);
							issn2issn.put(issn, firstISSN);
						}
					}
				}
			} else if (fullTitle.length() > maxJournalLength && line.startsWith("MedAbbr: ")) {
				title = line.substring("MedAbbr: ".length());
			} else if (line.startsWith("NlmId: ")) {
				String nlmId = line.substring("NlmId: ".length());
				if (medlineIndexedNlmIds.contains(nlmId)) {
					if (firstISSN != null)
						medlineIndexedIssns.add(firstISSN);
					if (secondISSN != null)
						medlineIndexedIssns.add(secondISSN);
				}
			}
		}
	}
	
	private String normalizeName(String name) {
		String temp = StringUtilities.removeParenthesisAndContent(name.toLowerCase()).trim();
		return temp.substring(0, Math.min(70, temp.length()));
	}
	
	private void loadOpenAccessJournals() {
		openAccessJournals = new HashSet<String>();
		ReadCSVFileWithHeader oa = new ReadCSVFileWithHeader(settings.openAccesFile);
		int count = 0;
		for (Row row : oa){
			openAccessJournals.add(row.get("Journal ISSN (print version)"));
			openAccessJournals.add(row.get("Journal EISSN (online version)"));
			count++;		
		}
		System.out.println("Number of open access journals: " + count);
	}
	
	private void loadPMCJournals() {
		issn2pmcMonths = new HashMap<String, Integer>();
		ReadCSVFile pmc = new ReadCSVFile(settings.pmcFile);
		boolean first = true;
		int pISSNcol = -1;
		int eISSNcol = -1;
		int delayCol = -1;
		
		for (List<String> cols : pmc)
			if (cols.size() > 1) {
				if (first) {
					first = false;
					pISSNcol = cols.indexOf("pISSN");
					if (pISSNcol == -1)
						System.err.println("Column not found!");
					eISSNcol = cols.indexOf("eISSN");
					if (eISSNcol == -1)
						System.err.println("Column not found!");
					delayCol = cols.indexOf("Free access");
					if (delayCol == -1)
						System.err.println("Column not found!");
					
				} else { // not first
					String issn = cols.get(pISSNcol);
					String essn = cols.get(eISSNcol);
					
					String text = cols.get(delayCol);
					Integer delay = null;
					if (text.contains("Immediate"))
						delay = 0;
					else if (text.equals("")){
						System.out.println("No duration til open access for journal with ISSN: " + issn);
					} else {
						int io = text.indexOf(" month");
						if (io == -1)
							System.err.println("Illegal duration in PMC file: " + text);
						else {
							String months = text.substring(0, io);// StringUtilities.findBetween(text, "After ", " month");	
							delay = Integer.parseInt(months);
						}
					}
					
					if (delay != null) {
						issn2pmcMonths.put(issn, delay);
						issn2pmcMonths.put(essn, delay);
					}
				}
			}
		System.out.println("Number of pmc journals: " + issn2pmcMonths.size());
	}
	
	private void loadEigenFactorData() {
		issn2eigenfactordata = new HashMap<String, EigenFactorData>();
		List<Float> ais = new ArrayList<Float>();
		ReadTextFile eigenFactor = new ReadTextFile(settings.eigenFactorFile);
		boolean first = true;
		int issn = -1;
//		int fullname = -1;
		int abbr = -1;
		int influence = -1;
		
		for (String line : eigenFactor)
			if (!line.equals("")) {
				List<String> cols = StringUtilities.safeSplit(line, '\t');
				if (first) {
					first = false;
					issn = cols.indexOf("ISSN");
					if (issn == -1)
						System.err.println("Column ISSN not found!");
					abbr = cols.indexOf("ShortName");
					if (abbr == -1)
						System.err.println("Column ShortName not found!");
					influence = cols.indexOf("ArticleInfluence");
					if (influence == -1)
						System.err.println("Column ArticleInfluence not found!");
//					fullname = cols.indexOf("FullName");
//					if (fullname == -1)
//						System.err.println("Column FullName not found!");
				} else { // not first
					String issnString = cols.get(issn);
//					String fullnameString = cols.get(fullname);
					String aiString = cols.get(influence);
					float ai;
					if (aiString.startsWith("<"))
						ai = 0;
					else 
						ai = Float.parseFloat(aiString);
					if (ai != -1) {
						EigenFactorData data = new EigenFactorData();
						data.issn = issnString;
						data.shortName = cols.get(abbr).trim();
						data.influence = ai;
						data.influenceString = aiString;
						ais.add(ai);
						issn2eigenfactordata.put(issnString, data);
						
//						List<String> issns = title2issn.get(normalizeName(fullnameString));
//						if (issns != null && !issns.contains(issnString)) {
//							System.out
//							.println("new ISSN " + issnString + " found for " + fullnameString + "\tKnown ISSNs: " + StringUtilities.join(issns, ","));
//							for (String i : issns)
//								issn2eigenfactordata.put(i, data);
//						}
//						// if (!essnString.equals(""))
						// issn2eigenfactordata.put(essnString, data);
					}
				}
			}
		
		Collections.sort(ais);
		for (EigenFactorData data : issn2eigenfactordata.values()) {
			int rank = Collections.binarySearch(ais, data.influence);
			data.rank = Integer.toString(Math.round(100 * (rank) / (float) ais.size()));
		}
		defaultData = new EigenFactorData();
		defaultData.influence = -1;
		defaultData.influenceString = "-1";
		defaultData.issn = "";
		defaultData.shortName = "";
		defaultData.rank = "";
	}
	
	private class EigenFactorData {
		float	influence;
		String influenceString;
		String	shortName;
		String	issn;
		String	rank;
	}
	
	private void loadRecentJournals() {
		recentJournals = new HashSet<String>();
		ReadTextFile file = new ReadTextFile(settings.recentJournalsFile);
		for (String line : file) {
			recentJournals.add(line.split("\t")[0]);
		}
	}
	
	private void loadValidPMIDs(String journals2PMIDsFile) {
		System.out.println(StringUtilities.now() + "\tLoading PMIDs");
		List<Integer> result = new ArrayList<Integer>();
		
		ReadTextFile trainingfile = new ReadTextFile(journals2PMIDsFile);
		for (String line : trainingfile) {
			String[] cols = line.split("\t");
			String[] pmids = cols[1].split(";");
			if (pmids.length >= minPMIDsPerJournal) {
				// Build list of pmids for this journal:
				List<Integer> pmidsList = new ArrayList<Integer>(pmids.length);
				for (String pmid : pmids)
					if (pmid != "")
						pmidsList.add(Integer.parseInt(pmid));
				result.addAll(pmidsList);
			}
		}
		System.out.println(StringUtilities.now() + "\tloaded " + result.size() + " PMIDs");
		WriteTextFile pmidFile = new WriteTextFile(settings.tempFolder + "PMIDsToBeIndexed.txt");
		for (Integer pmid : result)
			pmidFile.writeln(pmid.toString());
		pmidFile.close();
	}
	
	private Document createDocument(MedlineCitation citation) {
		outfile.writeln("Processing pmid " + citation.pmid);
		outfile.flush();
		
		String issnAlt = issn2issn.get(citation.journal.issn);
		String issnLinkingAlt = issn2issn.get(citation.journal.issnLinking);
		
		Document document = new Document();
		document.add(new Field("title", citation.title, Field.Store.YES, Field.Index.NO));
		document.add(new Field("text", citation.title + "\n" + citation.getConcatenatedAbstract(), Field.Store.NO, Field.Index.ANALYZED));
		String journal = issn2title.get(citation.journal.issn);
		if (journal == null) {
			journal = citation.journal.title;
			if (journal == null)
				return null;
		}
		document.add(new Field("journal", journal, Field.Store.YES, Field.Index.NOT_ANALYZED));
		document.add(new Field("pmid", Integer.toString(citation.pmid), Field.Store.YES, Field.Index.NOT_ANALYZED));
		String ptypString = StringUtilities.join(citation.publicationTypes, "\n");
		document.add(new Field("ptyp", ptypString, Field.Store.NO, Field.Index.ANALYZED));
		// Remove duplicate authors:
		Set<String> authorSet = new HashSet<String>();
		List<String> uniqueAuthors = new ArrayList<String>();
		for (Author author : citation.authors) {
			if (authorSet.add(authorToString(author)))
				uniqueAuthors.add(authorToString(author));
		}
		String authorString = StringUtilities.join(uniqueAuthors, "\n");
		document.add(new Field("authors", authorString, Field.Store.YES, Field.Index.ANALYZED));
		document.add(new Field("language", citation.languages.size() == 0 ? "" : citation.languages.get(0), Field.Store.NO, Field.Index.NOT_ANALYZED));
		
		String recent = recentJournals.contains(citation.journal.medlineTA) ? "true" : "false";
		document.add(new Field("recent", recent, Field.Store.NO, Field.Index.NOT_ANALYZED));
		
		String openaccess = openAccessJournals.contains(citation.journal.issn) || (issnAlt != null && openAccessJournals.contains(issnAlt)) ? "true" : "false";
		document.add(new Field("openaccess", openaccess, Field.Store.YES, Field.Index.NOT_ANALYZED));
		
		String medlineIndexed = medlineIndexedIssns.contains(citation.journal.issn) || (issnAlt != null && medlineIndexedIssns.contains(issnAlt)) ? "true" : "false";
		document.add(new Field("medlineindexed", medlineIndexed, Field.Store.YES, Field.Index.NOT_ANALYZED));
		
		Integer pmcMonths = issn2pmcMonths.get(citation.journal.issn);
		if (pmcMonths == null && issnAlt != null)
			pmcMonths = issn2pmcMonths.get(issnAlt);
		if (pmcMonths == null && citation.journal.issnLinking != null)
			pmcMonths = issn2pmcMonths.get(citation.journal.issnLinking);
		if (pmcMonths == null && issnLinkingAlt != null)
			pmcMonths = issn2pmcMonths.get(issnLinkingAlt);
		if (pmcMonths == null)
			pmcMonths = -1;
		document.add(new Field("pmcmonths", pmcMonths.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		
		calendar.setTime(citation.publicationDate);
		Integer year = calendar.get(Calendar.YEAR);
		document.add(new Field("year", year.toString(), Field.Store.YES, Field.Index.NO));
		String email = "";
		String emailAuthor = "";
		for (Author author : citation.authors) {
			email = MedlineCitationTools.extractEMailFromAffiliation(author.affiliation);
			if (email != null) {
				emailAuthor = authorToString(author);
				break;
			}
		}
		if (email == null)
			email = "";
		document.add(new Field("email", email, Field.Store.YES, Field.Index.NO));
		document.add(new Field("emailauthor", emailAuthor, Field.Store.NO, Field.Index.ANALYZED));
		
		EigenFactorData data = issn2eigenfactordata.get(citation.journal.issn);
		if (data == null && issnAlt != null)
			data = issn2eigenfactordata.get(issnAlt);
		if (data == null && citation.journal.issnLinking != null)
			data = issn2eigenfactordata.get(citation.journal.issnLinking);
		if (data == null && issnLinkingAlt != null)
			data = issn2eigenfactordata.get(issnLinkingAlt);
		
		String storeIssn = citation.journal.issn;
		if (data == null)
			data = defaultData;
		else
			storeIssn = data.issn;
		
		if (storeIssn == null)
			storeIssn = "";
		document.add(new Field("issn", storeIssn, Field.Store.YES, Field.Index.NO));
		document.add(new Field("journalabbr", data.shortName, Field.Store.YES, Field.Index.NO));
		document.add(new Field("airank", data.rank, Field.Store.YES, Field.Index.NO));
		document.add(new Field("ai", data.influenceString, Field.Store.YES, Field.Index.NO));
		return document;
	}
	
	private String authorToString(Author author) {
		if (author.collectiveName != null)
			return author.collectiveName;
		else
			return author.lastname + (author.initials == null ? "" : " " + author.initials);
	}
	
	private void processMedlineCitation(MedlineCitation citation) {
		medlineCount++;
		if (medlineCount % 10000 == 0)
			System.out.println(medlineCount);
		
		if (citation.getConcatenatedAbstract() != null && citation.getConcatenatedAbstract().length() != 0 && legalPubType(citation)) {
			Document document = createDocument(citation);
			try {
				writer.addDocument(document);
				insertCount++;
			} catch (CorruptIndexException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean legalPubType(MedlineCitation citation) {
		for (String pubType : citation.publicationTypes)
			if (ignorePubTypes.contains(pubType))
				return false;
		return true;
	}
}
