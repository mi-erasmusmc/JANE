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

public class ScoredItem implements Comparable<ScoredItem>{
	private double score;

	public void setScore(double score) {
		this.score = score;
	}

	public double getScore() {
		return score;
	}
	
	@Override
	public int compareTo(ScoredItem arg0) {
		return Double.compare(arg0.score, this.score);
	}
}