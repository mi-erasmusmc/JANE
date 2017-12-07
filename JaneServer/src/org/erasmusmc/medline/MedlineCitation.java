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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MedlineCitation {
	public int					pmid;
	public int					pmid_version		= 1;
	public String				title;
	public List<AbstractText>	abstractTexts		= new ArrayList<AbstractText>(0);
//	public String				otherAbstract;
	public List<MeSHHeader>		meshHeaders			= new ArrayList<MeSHHeader>(0);
	public List<Chemical>		chemicals			= new ArrayList<Chemical>(0);
	public List<String>			geneSymbols			= new ArrayList<String>(0);
	public List<Author>			authors				= new ArrayList<Author>(0);
	public List<String>			publicationTypes	= new ArrayList<String>(0);
	public Journal				journal;
	public String				volume;
	public String				issue;
	public String				pages;
	public Date					publicationDate;
	public List<String>			languages			= new ArrayList<String>(0);
	
	public String getConcatenatedAbstract() {
		StringBuilder text = new StringBuilder();
		for (AbstractText abstractText : abstractTexts) {
			if (text.length() != 0)
				text.append('\n');
			if (abstractText.label != null) {
				text.append(abstractText.label);
				text.append('\n');
			} 
//			else if (abstractText.nlmCategory != null) {
//				text.append(abstractText.nlmCategory);
//				text.append('\n');
//			}
			if (abstractText.text != null) {
				text.append(abstractText.text);
				text.append('\n');
			}
		}
//		if (otherAbstract != null) {
//			if (text.length() != 0)
//				text.append('\n');
//			text.append(otherAbstract);
//			text.append('\n');
//		}
		
		return text.toString();
	}
	
	public String getTitleAndAbstract() {
		return title + "\n" + getConcatenatedAbstract();
	}
}
