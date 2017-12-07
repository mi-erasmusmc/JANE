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
package org.erasmusmc.jane.dataClasses;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;

public class Journal extends ScoredItem {
	private String name;
	private String openAccess;
	private String medlineIndexed;
	private String pmcMonths;
	private String ai;
	private String airank;
	private String issn;
	private String journalAbbr;
	private List<Paper> papers = new ArrayList<Paper>();
	
	public Journal(Document document) {
		setName(document.get("journal"));
		setOpenAccess(document.get("openaccess"));
		setMedlineIndexed(document.get("medlineindexed"));
		setPmcMonths(document.get("pmcmonths"));
		setAi(document.get("ai"));
		setAirank(document.get("airank"));
		setIssn(document.get("issn"));
		setJournalAbbr(document.get("journalabbr"));
	}
	
	public void addPaper(Paper paper){
		papers.add(paper);
	}
	
	public Paper[] getPapers(){
		return (Paper[])papers.toArray(new Paper[papers.size()]);
	}
	
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}

	public void setOpenAccess(String openAccess) {
		this.openAccess = openAccess;
	}
	
	public void setMedlineIndexed(String medlineIndexed) {
		this.medlineIndexed = medlineIndexed;
	}

	public String getOpenAccess() {
		return openAccess;
	}
	
	public String getMedlineIndexed() {
		return medlineIndexed;
	}

	public void setPmcMonths(String pmcMonths) {
		this.pmcMonths = pmcMonths;
	}

	public String getPmcMonths() {
		return pmcMonths;
	}

	public void setAi(String ai) {
		this.ai = ai;
	}

	public String getAi() {
		return ai;
	}

	public void setAirank(String airank) {
		this.airank = airank;
	}

	public String getAirank() {
		return airank;
	}

	public void setIssn(String issn) {
		this.issn = issn;
	}

	public String getIssn() {
		return issn;
	}

	public void setJournalAbbr(String journalAbbr) {
		this.journalAbbr = journalAbbr;
	}

	public String getJournalAbbr() {
		return journalAbbr;
	}
}