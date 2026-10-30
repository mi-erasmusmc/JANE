package org.erasmusmc.jane.indexConstruction;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.medline.MedlineCitation;
import org.erasmusmc.medline.MedlineCitationIterator;
import org.erasmusmc.medline.RetrieveSettings;

public class PmidsToPmidsInJournal {

	private Map<String, List<Integer>> titles;
	private String pathToSqlite;

	public PmidsToPmidsInJournal(String pathToSqlite) {
		this.pathToSqlite = pathToSqlite;
	}

	public void getJournalsToPmids(String pmidsFile, String outputFile) {
		RetrieveSettings retrieveSettings = new RetrieveSettings();
		retrieveSettings.pathToSqlite = pathToSqlite;
		retrieveSettings.pmidsFile = pmidsFile;
		MedlineCitationIterator iterator = new MedlineCitationIterator(retrieveSettings);
		titles = new HashMap<String, List<Integer>>();
		while (iterator.hasNext())
			processMedlineRecords(iterator.next());
		saveTitles(outputFile);
		titles = null;
	}

	private void saveTitles(String filename) {
		try {
			FileOutputStream PSFFile = new FileOutputStream(filename);
			BufferedWriter bufferedWrite = new BufferedWriter(new OutputStreamWriter(PSFFile), 1000000);
			try {
				for (Map.Entry<String, List<Integer>> entry : titles.entrySet()) {
					StringBuffer line = new StringBuffer();
					line.append(entry.getKey());
					line.append("\t");
					for (Integer pmid : entry.getValue()) {
						line.append(pmid);
						line.append(";");
					}
					bufferedWrite.write(line.toString());
					bufferedWrite.newLine();
				}

				bufferedWrite.flush();
				bufferedWrite.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void processMedlineRecords(MedlineCitation citation) {
		String title = citation.journal.medlineTA;
		List<Integer> pmids = titles.get(title);
		if (pmids == null) {
			pmids = new ArrayList<Integer>();
			titles.put(title, pmids);
		}
		pmids.add(citation.pmid);
	}
}
