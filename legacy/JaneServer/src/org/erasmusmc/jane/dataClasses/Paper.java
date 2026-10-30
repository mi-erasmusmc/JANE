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

import org.apache.lucene.document.Document;

public class Paper extends ScoredItem {
	private String pmid;
	private String title;
	private String[] authors;
	private String journal;
	private String year;
	
	public Paper(Document document, double score) {
		setPmid(document.get("pmid"));
		setAuthors(document.get("authors").split("\n"));
		setTitle(document.get("title"));
		setJournal(document.get("journal"));
		setYear(document.get("year"));
		setScore(score);
	}
	public void setPmid(String pmid) {
		this.pmid = pmid;
	}
	public String getPmid() {
		return pmid;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getTitle() {
		return title;
	}
	public void setAuthors(String[] authors) {
		this.authors = authors;
	}
	public String[] getAuthors() {
		return authors;
	}
	public void setJournal(String journal) {
		this.journal = journal;
	}
	public String getJournal() {
		return journal;
	}
	public void setYear(String year) {
		this.year = year;
	}
	public String getYear() {
		return year;
	}
	
}