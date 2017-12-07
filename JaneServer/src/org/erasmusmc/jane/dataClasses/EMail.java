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

public class EMail implements Comparable<EMail> {
	private String eMail;
	private String year;
	private String pmid;
	
	public EMail(Document doc) {
		seteMail(doc.get("email"));
		setYear(doc.get("year"));
		setPmid(doc.get("pmid"));
	}

	public void seteMail(String eMail) {
		this.eMail = eMail;
	}

	public String geteMail() {
		return eMail;
	}

	public void setYear(String year) {
		this.year = year;
	}

	public String getYear() {
		return year;
	}

	public void setPmid(String pmid) {
		this.pmid = pmid;
	}

	public String getPmid() {
		return pmid;
	}
	
	@Override
	public int compareTo(EMail arg0) {
		int result = arg0.year.compareTo(this.year);
		if (result == 0)
			return arg0.pmid.compareTo(this.pmid);
		else
		  return result;
	}
}