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
import org.erasmusmc.medline.MedlineTools;
import org.erasmusmc.medline.RetrieveSettings;

public class PMIDs2PMIDsPerJournal {
	private static int							type;
	private static int							JOURNALNAME	= 0;
	private static int							ISSN		= 1;
	private static int							JOURNALABBR	= 2;
	
	public static Map<String, List<Integer>>	titles;
	
	/**
	 * First parameter: name of PMID file Second parameter: name of output file Third parameter: "issn", "journaltitle", "medlineabbr"
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args[2].equals("issn"))
			type = ISSN;
		else if (args[2].equals("medlineabbr"))
			type = JOURNALABBR;
		else
			type = JOURNALNAME;
		RetrieveSettings fetchSettings = MedlineTools.defaultSettings;
		fetchSettings.retrieveCitationInformation = true;
		fetchSettings.pmidsFile = args[0];
		MedlineCitationIterator iterator = new MedlineCitationIterator(fetchSettings);
		titles = new HashMap<String, List<Integer>>();
		while (iterator.hasNext())
			processMedlineRecords(iterator.next());
		saveTitles(args[1]);
		titles = null;
	}
	
	private static void saveTitles(String filename) {
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
	
	public static void processMedlineRecords(MedlineCitation citation) {
		String title;
		if (type == ISSN)
			title = citation.journal.issn;
		else if (type == JOURNALABBR)
			title = citation.journal.medlineTA;
		else
			title = citation.journal.title.trim();
		List<Integer> pmids = titles.get(title);
		if (pmids == null) {
			pmids = new ArrayList<Integer>();
			titles.put(title, pmids);
		}
		pmids.add(citation.pmid);
	}
}
