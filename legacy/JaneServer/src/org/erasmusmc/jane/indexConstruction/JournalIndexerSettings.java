package org.erasmusmc.jane.indexConstruction;

public class JournalIndexerSettings {
	 /**
   * file with a list of journals that have pubished in the last 12 months 
   */  
  public String recentJournalsFile;
  
  /**
   * file containing information on journals
   * Download this file from http://www.ncbi.nlm.nih.gov/entrez/citmatch_help.html#JournalLists
   */
  public String journalDatabaseFile;
  
  /**
   * File containing a list of all open access journals
   * Download this file from https://doaj.org/csv 
   */
  public String openAccesFile;
  
  /**
   * File containg information on Pubmed Central
   * Download from http://www.pubmedcentral.nih.gov/fprender.fcgi?cmd=full_view
   */
  public String pmcFile;
  
  public String medlineIndexJournalsFile;
  
  public String eigenFactorFile;
  
  public String journals2PMIDsFile;
  
  public String indexDirectory;
  
  public String tempFolder;
}
