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
package org.erasmusmc.medline;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.medline.MeSHHeader.Qualifier;
import org.erasmusmc.utilities.StringUtilities;

public class RetrieveCitationsThread extends BatchProcessingThread {
	
	public List<Integer>					pmids			= new ArrayList<Integer>();
	public List<MedlineCitation>			medlineCitations;
	
	private RetrieveSettings				settings		= new RetrieveSettings();
	
	private static List<String>				months			= getMonths();
	private Connection						connection;
	private Map<Integer, MedlineCitation>	pmid2Citation	= new HashMap<Integer, MedlineCitation>();
	
	public RetrieveCitationsThread(RetrieveSettings settings) {
		if (settings == null)
			settings = MedlineTools.defaultSettings;
		this.settings = settings;
		connection = DBConnector.connect(settings.server, settings.domain, settings.user, settings.password, settings.dateSourceType);
		connectToSchema(settings.database);
	}
	
	protected void process() {
		String pmidString = "(" + StringUtilities.join(pmids, ",") + ")";
		pmid2Citation.clear();
		if (settings.retrieveText || settings.retrieveCitationInformation) {
			retrieveFromMedCit(pmidString);
		}
		if (settings.retrieveText) {
			retrieveFromMedcit_art_abstract_abstracttext(pmidString);
			retrieveFromMedcit_otherabstract_abstracttext(pmidString);
		}
		
		if (settings.retrieveMeSHHeaders) {
			retrieveFromMedcit_meshheadinglist_meshheading(pmidString);
			retrieveFromMedcit_meshheadinglist_meshheading_qualifiername(pmidString);
		}
		
		if (settings.retrievePublicatonTypes)
			retrieveFromMedcit_art_publicationtypelist_publicationtype(pmidString);
		
		if (settings.retrieveChemicals)
			retrieveFromMedcit_chemicallist_chemical(pmidString);
		
		if (settings.retrieveGeneSymbols)
			retrieveFromMedcit_genesymbollist_genesymbol(pmidString);
		
		if (settings.retrieveAuthors)
			retrieveFromMedcit_art_authorlist_author(pmidString);
		
		if (settings.retrieveLanguage)
			retrieveFromMedcit_art_language(pmidString);
		
		medlineCitations = new ArrayList<MedlineCitation>(pmid2Citation.values());
	}
	
	private void retrieveFromMedCit(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT pmid");
			if (settings.retrieveText)
				sql.append("," + "art_arttitle");
			if (settings.retrieveCitationInformation)
				sql.append("," + "art_journal_title," + "art_journal_isoabbreviation," + "medlinejournalinfo_medlineta," + "art_journal_issn,"
						+ "medlinejournalinfo_issnlinking," + "art_journal_journalissue_volume," + "art_journal_journalissue_issue,"
						+ "art_pagination_medlinepgn," + "art_journal_journalissue_pubdate_year," + "art_journal_journalissue_pubdate_season,"
						+ "art_journal_journalissue_pubdate_month," + "art_journal_journalissue_pubdate_day," + "art_journal_journalissue_pubdate_medlinedate");
			
			sql.append(" FROM medcit WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				if (settings.retrieveText) {
					medlineCitation.title = resultSet.getString("art_arttitle");
					if (medlineCitation.title == null)
						medlineCitation.title = "";
				}
				
				if (settings.retrieveCitationInformation) {
					medlineCitation.journal = new Journal();
					medlineCitation.journal.title = resultSet.getString("art_journal_title");
					medlineCitation.journal.isoAbbreviation = resultSet.getString("art_journal_isoabbreviation");
					medlineCitation.journal.medlineTA = resultSet.getString("medlinejournalinfo_medlineta");
					medlineCitation.journal.issn = resultSet.getString("art_journal_issn");
					medlineCitation.journal.issnLinking = resultSet.getString("medlinejournalinfo_issnlinking");
					medlineCitation.volume = resultSet.getString("art_journal_journalissue_volume");
					medlineCitation.issue = resultSet.getString("art_journal_journalissue_issue");
					medlineCitation.pages = resultSet.getString("art_pagination_medlinepgn");
					medlineCitation.publicationDate = parseDate(resultSet.getString("art_journal_journalissue_pubdate_year"),
							resultSet.getString("art_journal_journalissue_pubdate_season"), resultSet.getString("art_journal_journalissue_pubdate_month"),
							resultSet.getString("art_journal_journalissue_pubdate_day"), resultSet.getString("art_journal_journalissue_pubdate_medlinedate"));
				}
			}
			statement.close();
			
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_art_abstract_abstracttext(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT pmid, value, label, medcit_art_abstract_abstracttext_order");
			sql.append(" FROM medcit_art_abstract_abstracttext WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			sql.append(" ORDER BY pmid, medcit_art_abstract_abstracttext_order");
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			
			ResultSet resultSet = statement.executeQuery(sql.toString());
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				AbstractText abstractText = new AbstractText();
				abstractText.text = resultSet.getString("value");
				abstractText.label = resultSet.getString("label");
				
				medlineCitation.abstractTexts.add(abstractText);
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_otherabstract_abstracttext(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT pmid, value, label, medcit_otherabstract_order, medcit_otherabstract_abstracttext_order");
			sql.append(" FROM medcit_otherabstract_abstracttext WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			sql.append(" ORDER BY pmid, medcit_otherabstract_order, medcit_otherabstract_abstracttext_order");
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			
			ResultSet resultSet = statement.executeQuery(sql.toString());
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				AbstractText abstractText = new AbstractText();
				abstractText.text = resultSet.getString("value");
				abstractText.label = resultSet.getString("label");
				medlineCitation.abstractTexts.add(abstractText);
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	// private void retrieveFromMedcit_otherabstract(String pmidString) {
	// StringBuilder sql = new StringBuilder();
	// try {
	// sql.append("SELECT pmid,abstracttext,language,medcit_otherabstract_order");
	// sql.append(" FROM medcit_otherabstract WHERE pmid_version = 1 AND pmid IN ");
	// sql.append(pmidString);
	// sql.append(" ORDER BY " + "pmid," + "medcit_otherabstract_order");
	// Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
	//
	// ResultSet resultSet = statement.executeQuery(sql.toString());
	// while (resultSet.next()) {
	// if (resultSet.getString("language").equals("eng")) {
	// int pmid = resultSet.getInt("pmid");
	// MedlineCitation medlineCitation = getCitation(pmid);
	// if (medlineCitation.otherAbstract == null)
	// medlineCitation.otherAbstract = resultSet.getString("abstracttext");
	// else
	// medlineCitation.otherAbstract += "\n" + resultSet.getString("abstracttext");
	// }
	// }
	// statement.close();
	// } catch (SQLException e) {
	// System.err.println(sql.toString());
	// e.printStackTrace();
	// }
	// }
	
	private void retrieveFromMedcit_meshheadinglist_meshheading(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT " + "pmid," + "descriptorname," + "descriptorname_majortopicyn," + "descriptorname_type,"
					+ "medcit_meshheadinglist_meshheading_order");
			sql.append(" FROM medcit_meshheadinglist_meshheading WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			sql.append(" ORDER BY " + "pmid," + "medcit_meshheadinglist_meshheading_order");
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			
			ResultSet resultSet = statement.executeQuery(sql.toString());
			// resultSet.beforeFirst();
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				MeSHHeader meSHHeader = new MeSHHeader();
				meSHHeader.descriptor = resultSet.getString("descriptorname");
				meSHHeader.major = resultSet.getString("descriptorname_majortopicyn").equals("Y");
				meSHHeader.type = resultSet.getString("descriptorname_type");
				medlineCitation.meshHeaders.add(meSHHeader);
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_meshheadinglist_meshheading_qualifiername(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT " + "pmid," + "value," + "majortopicyn," + "medcit_meshheadinglist_meshheading_order,"
					+ "medcit_meshheadinglist_meshheading_qualifiername_order");
			sql.append(" FROM medcit_meshheadinglist_meshheading_qualifiername WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			sql.append(" ORDER BY " + "pmid," + "medcit_meshheadinglist_meshheading_order," + "medcit_meshheadinglist_meshheading_qualifiername_order");
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			// resultSet.beforeFirst();
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				int meshHeaderOrder = resultSet.getInt("medcit_meshheadinglist_meshheading_order");
				MeSHHeader meshHeader = medlineCitation.meshHeaders.get(meshHeaderOrder - 1);
				
				Qualifier qualifier = meshHeader.createQualifier();
				qualifier.descriptor = resultSet.getString("value");
				qualifier.major = resultSet.getString("majortopicyn").equals("Y");
				meshHeader.qualifiers.add(qualifier);
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_art_publicationtypelist_publicationtype(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT " + "pmid," + "value");
			sql.append(" FROM medcit_art_publicationtypelist_publicationtype WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			// resultSet.beforeFirst();
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				medlineCitation.publicationTypes.add(resultSet.getString("value"));
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_chemicallist_chemical(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT " + "pmid," + "nameofsubstance," + "registrynumber");
			sql.append(" FROM medcit_chemicallist_chemical WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			// resultSet.beforeFirst();
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				Chemical chemical = new Chemical();
				chemical.name = resultSet.getString("nameofsubstance");
				chemical.registryNumber = resultSet.getString("registrynumber");
				
				medlineCitation.chemicals.add(chemical);
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_genesymbollist_genesymbol(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT " + "pmid," + "value");
			sql.append(" FROM medcit_genesymbollist_genesymbol WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			// resultSet.beforeFirst();
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				medlineCitation.geneSymbols.add(resultSet.getString("value"));
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_art_authorlist_author(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT au.pmid, affiliation, collectivename, forename, initials, lastname, suffix, au.medcit_art_authorlist_author_order");
			sql.append(" FROM medcit_art_authorlist_author au");
			sql.append(" LEFT JOIN (");
			sql.append("   SELECT * FROM  medcit_art_authorlist_author_affiliationinfo WHERE medcit_art_authorlist_author_affiliationinfo_order = 1");
			sql.append(" ) ai");
			sql.append(" ON au.pmid = ai.pmid");
			sql.append(" AND au.pmid_version = ai.pmid_version");
			sql.append(" AND au.medcit_art_authorlist_author_order = ai.medcit_art_authorlist_author_order");
			sql.append(" WHERE au.pmid_version = 1 ");
			sql.append(" AND au.pmid IN ");
			sql.append(pmidString);
			sql.append(" ORDER BY " + "pmid," + "medcit_art_authorlist_author_order");
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			// resultSet.beforeFirst();
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				
				Author author = new Author();
				author.collectiveName = resultSet.getString("collectivename");
				author.forename = resultSet.getString("forename");
				author.initials = resultSet.getString("initials");
				author.lastname = resultSet.getString("lastname");
				author.suffix = resultSet.getString("suffix");
				author.affiliation = resultSet.getString("affiliation");
				
				medlineCitation.authors.add(author);
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private void retrieveFromMedcit_art_language(String pmidString) {
		StringBuilder sql = new StringBuilder();
		try {
			sql.append("SELECT " + "pmid," + "medcit_art_language_order," + "value");
			sql.append(" FROM medcit_art_language WHERE pmid_version = 1 AND pmid IN ");
			sql.append(pmidString);
			sql.append(" ORDER BY " + "pmid," + "medcit_art_language_order");
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql.toString());
			while (resultSet.next()) {
				int pmid = resultSet.getInt("pmid");
				MedlineCitation medlineCitation = getCitation(pmid);
				medlineCitation.languages.add(resultSet.getString("value"));
			}
			statement.close();
		} catch (SQLException e) {
			System.err.println(sql.toString());
			e.printStackTrace();
		}
	}
	
	private static List<String> getMonths() {
		List<String> result = new ArrayList<String>(12);
		result.add("Jan");
		result.add("Feb");
		result.add("Mar");
		result.add("Apr");
		result.add("May");
		result.add("Jun");
		result.add("Jul");
		result.add("Aug");
		result.add("Sep");
		result.add("Oct");
		result.add("Nov");
		result.add("Dec");
		return result;
	}
	
	private Date parseDate(String yearString, String seasonString, String monthString, String dayString, String medlineString) {
		int year = 0;
		if (yearString == null) {
			if (medlineString == null)
				return null;
			
			for (Integer i = 1950; i < 2100; i++)
				if (medlineString.contains(i.toString())) {
					year = i;
					break;
				}
			if (year == 0)
				return null;
		} else {
			year = Integer.parseInt(yearString);
		}
		int month = 0;
		if (monthString == null) {
			if (medlineString != null) {
				for (int i = 0; i < months.size(); i++) {
					if (medlineString.contains(months.get(i))) {
						month = i;
						break;
					}
				}
			}
			if (month == 0 && seasonString != null) {
				if (seasonString.equals("Spring"))
					month = 2;
				if (seasonString.equals("Summer"))
					month = 5;
				if (seasonString.equals("Fall"))
					month = 8;
				if (seasonString.equals("Winter"))
					month = 11;
			}
		} else {
			month = months.indexOf(monthString);
		}
		int day = dayString == null ? 1 : Integer.parseInt(dayString);
		return new GregorianCalendar(year, month, day).getTime();
	}
	
	private MedlineCitation getCitation(int pmid) {
		MedlineCitation citation = pmid2Citation.get(pmid);
		if (citation == null) {
			citation = new MedlineCitation();
			citation.pmid = pmid;
			pmid2Citation.put(pmid, citation);
		}
		return citation;
	}
	
	protected void finalize() {
		try {
			connection.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void connectToSchema(String schema) {
		StringBuilder sql = new StringBuilder();
		try {
			// Connect to correct database;
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
			sql.append("USE " + schema + ";");
			statement.execute(sql.toString());
			statement.close();
		} catch (SQLException e) {
			System.err.println("SQL: " + sql.toString());
			throw new RuntimeException(e);
		}
	}
}