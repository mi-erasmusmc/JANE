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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.erasmusmc.utilities.StringUtilities;
import org.erasmusmc.utilities.WriteTextFile;

public class OnlinePubmed {
	
	public static int		maxAttempts		= 10;
	public static String	response;
	private static long		lastQueryTime	= 0;
	private static long		extraDelay		= 0;
	private static int		minWaitTime		= 500;				// Wait for 0.5 second to do next query
	public static int		batchSize		= 100000;
	public static int		maxReturn		= Integer.MAX_VALUE;
	private static int		OK				= 0;
	private static int		ERROR			= 1;
		
	public static List<String> getIndexedJournalIds(String email) {
		QueryParameters queryParameters = new QueryParameters("currentlyindexed", batchSize, 0, email, "nlmcatalog");
		boolean done = false;
		List<Integer> uids = new ArrayList<Integer>();
		System.out.println("Retrieving currently indexed journal UIDs");
		while (!done) {
			queryParameters.setRetStart(uids.size());
			String response = postQuery(queryParameters);
			done = parseResponse(response, uids);
		}
		System.out.println("Converting UIDs to NLM IDs");
		List<String> nlmIds = new ArrayList<String>();
		int start = 0;
		while (start < uids.size()) {
			List<Integer> subset = uids.subList(start, Math.min(start + batchSize, uids.size()));
			String response = postUidToNlmIdQuery(subset);
			parseResponseNlmIds(response, nlmIds);
			start += batchSize;
		}
		return nlmIds;
	}
	
	public static int getPMIDCount(String query, String email) {
		QueryParameters queryParameters = new QueryParameters(query, 0, 0, email);
		System.out.println("Sending query to Pubmed: " + query);
		String response = postQuery(queryParameters);
		String count = StringUtilities.findBetween(response, "<Count>", "</Count>");
		if (count != null && StringUtilities.isNumber(count))
			return Integer.parseInt(count);
		else
			return -1;
	}
	
	private static class QueryParameters extends HashMap<String, String> {
		private static final long serialVersionUID = 6938200802340903045L;
		
		public QueryParameters(String query, int retMax, int retStart, String email) {
			super(4);
			put("term", query);
			put("email", email);
			put("retmax", Integer.toString(retMax));
			put("retstart", Integer.toString(retStart));
		}
		
		public QueryParameters(String query, int retMax, int retStart, String email, String db) {
			super(5);
			put("term", query);
			put("email", email);
			put("retmax", Integer.toString(retMax));
			put("retstart", Integer.toString(retStart));
			put("db", db);
		}
		
		public void setRetStart(int retStart) {
			put("retstart", Integer.toString(retStart));
		}
	}
	
	/**
	 * Queries the online Pubmed database, and writes the PMIDs to the specified file
	 * 
	 * @param query
	 *            The Pubmed query (e.g. 'Schuemie MJ[Author]')
	 * @param filename
	 *            Name of the text file where the PMIDs will be saved
	 * @param email
	 *            An e-mail address NLM can use to contact you about the query if need be
	 */
	public static void savePMIDs(String query, String filename, String email) {
		List<Integer> pmids = getPMIDs(query, email);
		WriteTextFile out = new WriteTextFile(filename);
		for (Integer pmid : pmids)
			out.writeln(pmid);
		System.out.println("Saved " + pmids.size() + " pmids to " + filename);
		out.close();
	}
	
	public static void saveIndexedJournalIds(String filename, String email) {
		List<String> nlmIds = getIndexedJournalIds(email);
		WriteTextFile out = new WriteTextFile(filename);
		for (String nlmId : nlmIds)
			out.writeln(nlmId);
		System.out.println("Saved " + nlmIds.size() + " nlm IDs to " + filename);
		out.close();
	}
	
	/**
	 * 
	 * @param query
	 *            The Pubmed query (e.g. 'Schuemie MJ[Author]')
	 * @param email
	 *            An e-mail address NLM can use to contact you about the query if need be
	 * @return A list of PMIDs matching your searching criteria
	 */
	public static List<Integer> getPMIDs(String query, String email) {
		QueryParameters queryParameters = new QueryParameters(query, batchSize, 0, email);
		boolean done = false;
		List<Integer> pmids = new ArrayList<Integer>();
		while (!done) {
			queryParameters.setRetStart(pmids.size());
			System.out.println("Sending query to Pubmed: " + query);
			String response = postQuery(queryParameters);
			done = parseResponse(response, pmids);
		}
		return pmids;
	}
	
	private static boolean parseResponse(String response, List<Integer> pmids) {
		String[] lines = response.split("\n");
		boolean ids = false;
		boolean haveCount = false;
		int count = 0;
		for (String line : lines) {
			
			if (line.contains("<OutputMessage>")) {
				System.err.println(line.trim());
				return true;
			} else if (!haveCount && line.contains("<Count>")) {
				count = Integer.parseInt(StringUtilities.findBetween(line, "<Count>", "</Count>"));
				haveCount = true;
			}
			if (ids) {
				if (line.contains("</IdList>"))
					ids = false;
				else {
					String pmid = StringUtilities.findBetween(line, "<Id>", "</Id>");
					try {
						pmids.add(Integer.parseInt(pmid));
					} catch (NumberFormatException e) {
						System.err.println(e.getMessage() + ", Problem parsing ID: " + line);
						return true;
					}
				}
			}
			if (line.contains("<IdList>"))
				ids = true;
		}
		return (pmids.size() == count || pmids.size() >= maxReturn);
	}
	
	private static void parseResponseNlmIds(String response, List<String> nmlIds) {
		String[] lines = response.split("\n");
		for (String line : lines) {
			if (line.contains("<OutputMessage>")) {
				System.err.println(line.trim());
				return;
			} else if (line.contains("<Item Name=\"NLMUniqueID\" Type=\"String\">")) {
				String nlmId = StringUtilities.findBetween(line, "<Item Name=\"NLMUniqueID\" Type=\"String\">", "</Item>");
				nmlIds.add(nlmId);
			}
		}
	}
	
	private static String postQuery(QueryParameters queryParameters) {
		response = "";
		int status;
		int attempts = 0;
		do {
			checkWaitTime();
			status = OK;
			try {
				StringBuilder postData = new StringBuilder();
				for (Map.Entry<String, String> param : queryParameters.entrySet()) {
					if (postData.length() != 0)
						postData.append('&');
					postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
					postData.append('=');
					postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
				}
				// System.out.println(postData.toString());
				byte[] postDataBytes = postData.toString().getBytes("UTF-8");
				
				URL url = new URL("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
				conn.setDoOutput(true);
				conn.getOutputStream().write(postDataBytes);
				response = convertStreamToString(conn.getInputStream());
			} catch (IOException e) {
				System.err.println("Error retrieving url: " + e.getMessage());
				status = ERROR;
			}
			if (response.contains("<ERROR>")) {
				System.err.println(response);
				status = ERROR;
				extraDelay = 60000; // wait minute extra
			}
			resetWaitTime();
			attempts++;
		} while (status == ERROR && attempts <= maxAttempts);
		if (attempts > maxAttempts)
			System.err.println("Failed after " + attempts + " attempts on URL: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi");
		
		return response;
	}
	
	private static String postUidToNlmIdQuery(List<Integer> uids) {
		response = "";
		int status;
		int attempts = 0;
		do {
			checkWaitTime();
			status = OK;
			try {
				String postData = "db=nlmcatalog&id=" + StringUtilities.join(uids, ",");
				byte[] postDataBytes = postData.toString().getBytes("UTF-8");
				URL url = new URL("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
				conn.setDoOutput(true);
				conn.getOutputStream().write(postDataBytes);
				response = convertStreamToString(conn.getInputStream());
			} catch (IOException e) {
				System.err.println("Error retrieving url: " + e.getMessage());
				status = ERROR;
			}
			if (response.contains("<ERROR>")) {
				System.err.println(response);
				status = ERROR;
				extraDelay = 60000; // wait minute extra
			}
			resetWaitTime();
			attempts++;
		} while (status == ERROR && attempts <= maxAttempts);
		if (attempts > maxAttempts)
			System.err.println("Failed after " + attempts + " attempts on URL: https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi");
		
		return response;
	}
	
	private static void resetWaitTime() {
		lastQueryTime = System.currentTimeMillis();
	}
	
	private static void checkWaitTime() {
		long timePassed = System.currentTimeMillis() - lastQueryTime;
		if (timePassed < minWaitTime + extraDelay) {
			try {
				System.out.println("Waiting to send next query");
				Thread.sleep(minWaitTime + extraDelay - timePassed);
				extraDelay = 0;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static String convertStreamToString(InputStream inputStream) {
		Scanner scanner = extract(inputStream).useDelimiter("\\A");
		return scanner.hasNext() ? scanner.next() : "";
	}
	
	private static Scanner extract(InputStream inputStream) {
		return new Scanner(inputStream);
	}
}
