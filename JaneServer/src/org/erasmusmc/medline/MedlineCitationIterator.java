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
import java.util.Iterator;
import java.util.List;

import org.erasmusmc.utilities.ReadTextFile;

public class MedlineCitationIterator implements Iterator<MedlineCitation>{

	private RetrieveCitationsThread retrieveCitationsThread;
	private Iterator<String> lineIterator;
	private Iterator<MedlineCitation> bufferIterator;
	private boolean isFetching = false;
	public static int batchSize = 1000;

	public MedlineCitationIterator(RetrieveSettings settings){
		retrieveCitationsThread = new RetrieveCitationsThread(settings);
		lineIterator = new ReadTextFile(settings.pmidsFile).iterator();

		startFetch();
		do {
			copyFetchedToBuffer();
			startFetch();
		} while (!bufferIterator.hasNext() && isFetching);
	}

	private void copyFetchedToBuffer() {
		if (isFetching)
			retrieveCitationsThread.waitUntilFinished();
		isFetching = false;
		bufferIterator = retrieveCitationsThread.medlineCitations.iterator();
	}

	private void startFetch() {
		List<Integer> pmids = new ArrayList<Integer>(batchSize);
		while (lineIterator.hasNext() && pmids.size() < batchSize){
			String pmid = lineIterator.next();
			try {
			pmids.add(Integer.parseInt(pmid));
			} catch (NumberFormatException e){
				System.err.println("PMID not a number: " + pmid);
			}
		}
		if (pmids.size() == 0) {
			isFetching = false;
			retrieveCitationsThread.terminate();
		} else {
			retrieveCitationsThread.pmids = pmids;
			retrieveCitationsThread.proceed();
			isFetching = true;
		}
	}

	@Override
	public boolean hasNext() {
		return (bufferIterator.hasNext());
	}

	@Override
	public MedlineCitation next() {
		MedlineCitation next = bufferIterator.next();
		while (!bufferIterator.hasNext() && isFetching){
			copyFetchedToBuffer();
			startFetch();
		}
		return next;
	}

	@Override
	public void remove() {
		throw new RuntimeException("Calling unimplemented method remove in " + this.getClass().getName());
	}
}
