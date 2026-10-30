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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.io.Util;
import org.erasmusmc.medline.MedlineTools;
import org.erasmusmc.medline.OnlinePubmed;
import org.erasmusmc.utilities.StringUtilities;

public class JaneMasterIndexingScript {
	public static String    pathToSql       = "E:/Medline/PubMed.sqlite";
	public static String	indexFolder		= "c:/temp/indexNew/";
	public static String	eigenfactorFile	= "E:/Jane/EF_2015.txt";
	public static String	tempFolder		= "E:/Jane/temp/";

	// Did you remember to delete the old index folder?!

	public static void main(String[] args) {
		System.out.println(StringUtilities.now() + "\tFetching list of journals currently indexed in MEDLINE");
		OnlinePubmed.saveIndexedJournalIds(tempFolder + "indexedJournals.txt", "schuemie@ohdsi.org");

		System.out.println(StringUtilities.now() + "\tFetching doaj file");
		downloadFile("https://doaj.org/csv", tempFolder + "doaj.csv");

		System.out.println(StringUtilities.now() + "\tFetching PubMed Central file");
		downloadFile("https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv", tempFolder + "jlist.csv");

		System.out.println(StringUtilities.now() + "\tFetching Medline journals database");
		downloadFileFTP("ftp.ncbi.nih.gov", "/pubmed/.J", "J_Medline.txt", tempFolder + "J_Medline.txt");

		Calendar tenYearsAgo = new GregorianCalendar();
		tenYearsAgo.add(Calendar.YEAR, -10);

		Calendar oneYearAgo = new GregorianCalendar();
		oneYearAgo.add(Calendar.YEAR, -1);

		Calendar future = new GregorianCalendar();
		future.add(Calendar.YEAR, 10);

		System.out.println(StringUtilities.now() + "\tFetching relevant PMIDs");
		MedlineTools medlineTools = new MedlineTools(pathToSql);
		medlineTools.savePMIDsInTimeRange(tempFolder + "Jane.PMIDs", tenYearsAgo.getTime(), future.getTime());

		System.out.println(StringUtilities.now() + "\tFetching PMIDs of recent papers");
		medlineTools.savePMIDsInTimeRange(tempFolder + "Jane_Recent.PMIDs", oneYearAgo.getTime(), future.getTime());

		System.out.println(StringUtilities.now() + "\tFinding journals for all papers");
		PmidsToPmidsInJournal pmidsToPmidsInJournal = new PmidsToPmidsInJournal(pathToSql);
		pmidsToPmidsInJournal.getJournalsToPmids(tempFolder + "Jane.PMIDs", tempFolder + "Jane_Journal2PMID.txt");
		pmidsToPmidsInJournal.getJournalsToPmids(tempFolder + "Jane_Recent.PMIDs", tempFolder + "Jane_Journal2PMID_Recent.txt");

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

	private static void downloadFile(String fileURL, String savePath) {
		try {
			URL url = new URL(fileURL);
			URLConnection connection = url.openConnection();
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
			connection.setConnectTimeout(5000); // 5 seconds
			connection.setReadTimeout(5000); // 5 seconds
			try (InputStream in = new BufferedInputStream(connection.getInputStream());
					FileOutputStream fos = new FileOutputStream(savePath)) {
				byte[] buffer = new byte[1024];
				int bytesRead;
				while ((bytesRead = in.read(buffer)) != -1) {
					fos.write(buffer, 0, bytesRead);
				}
			}
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
