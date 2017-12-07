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
import java.util.List;

public class MeSHHeader {
	  public String descriptor;
	  public List<Qualifier> qualifiers = new ArrayList<Qualifier>(0);
	  public boolean major = false;
	  public String type;
	  
	  public String toString(){
		  if (qualifiers.size() == 0)
			  return descriptor + (major?"*":"");
		  else {
			  StringBuilder text = new StringBuilder();
			  for (Qualifier qualifier : qualifiers){
				  if (text.length() != 0)
					  text.append('\n');
				  text.append(descriptor + (major?"*":"") + "/" + qualifier.descriptor + (qualifier.major?"*":""));
			  }
			  return text.toString();
		  }
	  }
	  
	  public Qualifier createQualifier(){
		  return new Qualifier();
	  }
	  
	  public class Qualifier {
		  public String descriptor;
		  public boolean major = false;
	  }
}
