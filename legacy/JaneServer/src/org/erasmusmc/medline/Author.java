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

import java.text.Normalizer;

public class Author {
	public String	collectiveName;
	public String	forename;
	public String	initials;
	public String	lastname;
	public String	suffix;
	public String	affiliation;
	
	public String toString() {
		if (collectiveName != null)
			return collectiveName;
		else
			return lastname + (initials == null ? "" : " " + initials) + (suffix == null ? "" : " " + suffix);
	}
	
	public String normName() {
		String normName;
		
		if (collectiveName != null)
			normName = collectiveName;
		else
			normName = lastname + (initials == null ? "" : " " + initials.substring(0, 1));
		normName.replace("\n", "");
		normName = normName.toLowerCase().trim();
		normName = Normalizer.normalize(normName, Normalizer.Form.NFD);
		normName = normName.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		return normName;
	}
}
