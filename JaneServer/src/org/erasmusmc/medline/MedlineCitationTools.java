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

import java.util.List;

import org.erasmusmc.utilities.StringUtilities;

public class MedlineCitationTools {
	
	public static String extractEMailFromAffiliation(String affiliation) {
		if (affiliation == null)
			return null;
		boolean atSign = false;
		boolean before = false;
		boolean after = false;
		int i = affiliation.length();
		while (i > 0) {
			i--;
			char ch = affiliation.charAt(i);
			if (Character.isLetterOrDigit(ch)) {
				if (atSign)
					before = true;
				else
					after = true;
			} else if (ch == '@') {
				atSign = true;
			} else if (ch != '.' && ch != '-' && ch != '_') {
				i++;
				break;
			}
		}
		if (atSign && before && after) {
			if (affiliation.charAt(affiliation.length() - 1) == '.')
				return affiliation.substring(i, affiliation.length() - 1);
			else
				return affiliation.substring(i, affiliation.length());
		}
		return null;
	}
	
	public static Author findMatchingAuthor(String email, List<Author> authors) {
		if (authors.size() == 1)
			return authors.get(0);
		else {
			String emaillc = email.toLowerCase();
			Author matchedOnInitial = null;
			for (int a = 0; a < authors.size(); a++) {
				Author author = authors.get(a);
				if (author.lastname != null) {
					String authorlc = StringUtilities.replaceInternationalChars(author.lastname).toLowerCase();
					if (emaillc.contains(authorlc.substring(0, Math.min(authorlc.length(), 4))))
						return author;
					if ((a == 0 || a == authors.size() - 1) && author.initials != null) {
						if (emaillc.charAt(0) == author.initials.toLowerCase().charAt(0))
							if (matchedOnInitial == null)
								matchedOnInitial = author;
					}
				}
			}
			return matchedOnInitial;
		}
	}
	
}
