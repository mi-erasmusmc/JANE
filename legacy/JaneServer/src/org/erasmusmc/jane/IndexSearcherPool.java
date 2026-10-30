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
package org.erasmusmc.jane;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Keeps a pool of indexsearchers open. Speeds up searches when compared to reinstantiating every time.
 * Indexer is reinstantiated if open for more than an hour.
 * @author schuemie
 *
 */
public class IndexSearcherPool {
	public static final long MAX_REFRESH_TIME = 60*60*1000; //=1 hour
	private Map<String, SearcherWithTime> folder2searcher = new HashMap<String, SearcherWithTime>();
	private ReentrantLock lock = new ReentrantLock();
	
	public IndexSearcher get(String indexFolder) throws CorruptIndexException, IOException {
		lock.lock();
		SearcherWithTime searcherWithTime = folder2searcher.get(indexFolder);
		if (searcherWithTime == null || (System.currentTimeMillis()-searcherWithTime.refreshTime) > MAX_REFRESH_TIME){
			if (searcherWithTime != null)
			  searcherWithTime.indexSearcher.close();
			searcherWithTime = new SearcherWithTime();
			Directory d = FSDirectory.open(new File(indexFolder));
			searcherWithTime.indexSearcher = new IndexSearcher(d);
			searcherWithTime.refreshTime = System.currentTimeMillis();
			folder2searcher.put(indexFolder, searcherWithTime);
		}
		lock.unlock();
		return searcherWithTime.indexSearcher;		
	}

	private class SearcherWithTime {
		IndexSearcher indexSearcher;
		long refreshTime;
	}
}
