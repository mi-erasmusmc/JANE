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
package org.erasmusmc.jane.indexConstruction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.io.Util;
import org.erasmusmc.medline.MedlineTools;
import org.erasmusmc.medline.OnlinePubmed;
import org.erasmusmc.utilities.StringUtilities;

public class JaneMasterIndexingScript {
	public static String	indexFolder		= "S:/Data/JANE/indexNew/";
	public static String	eigenfactorFile	= "S:/Data/JANE/EF_2015.txt";
	public static String	tempFolder		= "S:/Data/JANE/temp/";
	
	// Did you remember to delete the old index folder?!
	// Manually download the two files below
	
	public static void main(String[] args) {
		Calendar tenYearsAgo = new GregorianCalendar();
		tenYearsAgo.add(Calendar.YEAR, -10);
		
		Calendar oneYearAgo = new GregorianCalendar();
		oneYearAgo.add(Calendar.YEAR, -1);
		
		Calendar future = new GregorianCalendar();
		future.add(Calendar.YEAR, 10);
		
//		System.out.println(StringUtilities.now() + "\tFetching relevant PMIDs");
//		MedlineTools.savePMIDsInTimeRange(tempFolder + "Jane.PMIDs", tenYearsAgo.getTime(), future.getTime());
//		
//		System.out.println(StringUtilities.now() + "\tFetching PMIDs of recent papers");
//		MedlineTools.savePMIDsInTimeRange(tempFolder + "Jane_Recent.PMIDs", oneYearAgo.getTime(), future.getTime());
		
		System.out.println(StringUtilities.now() + "\tFinding journals for all papers");
		PMIDs2PMIDsPerJournal.main(new String[] { tempFolder + "Jane.PMIDs", tempFolder + "Jane_Journal2PMID.txt", "medlineabbr" });
		PMIDs2PMIDsPerJournal.main(new String[] { tempFolder + "Jane_Recent.PMIDs", tempFolder + "Jane_Journal2PMID_Recent.txt", "medlineabbr" });
		
		// No longer works automatically. Download manually using Filezilla
//		System.out.println(StringUtilities.now() + "\tFetching Medline journals database");
//		downloadFileFTP("ftp.ncbi.nih.gov", "/pubmed/.J", "J_Medline.txt", tempFolder + "J_Medline.txt");
		
		System.out.println(StringUtilities.now() + "\tFetching list of journals currently indexed in MEDLINE");
		OnlinePubmed.saveIndexedJournalIds(tempFolder + "indexedJournals.txt", "schuemie@ohdsi.org");
		
		// No longer works automatically. Download manually from https://doaj.org/csv
//		System.out.println(StringUtilities.now() + "\tFetching doaj file");
//		downloadFile("https://doaj.org", tempFolder + "csv");
		
		System.out.println(StringUtilities.now() + "\tFetching PubMed Central file");
		downloadFile("https://www.ncbi.nlm.nih.gov/pmc/front-page/jlist.csv", tempFolder + "jlist.csv");
		
		System.out.println(StringUtilities.now() + "\tIndexing articles");
		JournalIndexerSettings settings = new JournalIndexerSettings();
		settings.recentJournalsFile = tempFolder + "Jane_Journal2PMID_Recent.txt";
		settings.journalDatabaseFile = tempFolder + "J_Medline.txt";
		settings.medlineIndexJournalsFile = tempFolder  + "indexedJournals.txt";
		settings.openAccesFile = tempFolder + "doaj.csv";
		settings.pmcFile = tempFolder + "/jlist.csv";
		settings.eigenFactorFile = eigenfactorFile;
		settings.journals2PMIDsFile = tempFolder + "Jane_Journal2PMID.txt";
		settings.indexDirectory = indexFolder;
		settings.tempFolder = tempFolder;
		JournalIndexer indexer = new JournalIndexer();
		indexer.index(settings);
	}
	
	private static void downloadFile(String url, String filename) {
		try {
			// Workaround to fetch data from https URL without having security certificate
			// Copied from http://stackoverflow.com/a/24501156
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
	            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
	            public void checkClientTrusted(X509Certificate[] certs, String authType) { }
	            public void checkServerTrusted(X509Certificate[] certs, String authType) { }

	        } };

	        SSLContext sc = SSLContext.getInstance("SSL");
	        sc.init(null, trustAllCerts, new java.security.SecureRandom());
	        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

	        // Create all-trusting host name verifier
	        HostnameVerifier allHostsValid = new HostnameVerifier() {
	            public boolean verify(String hostname, SSLSession session) { return true; }
	        };
	        // Install the all-trusting host verifier
	        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
			// End of workaround
	        
			URL website = new URL(url);
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			FileOutputStream fos = new FileOutputStream(filename);
			fos.getChannel().transferFrom(rbc, 0, 1 << 24);
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void downloadFileFTP(String host, String dir, String remoteFile, String localFilename) {
		FTPClient client = new FTPClient(); 
		try {
			client.connect(host);
			client.login("anonymous", "schuemie@ohdsi.org");
			client.changeWorkingDirectory(dir);
			OutputStream os = new FileOutputStream(new File(localFilename));
			InputStream is = client.retrieveFileStream(remoteFile);
			Util.copyStream(is, os);
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
