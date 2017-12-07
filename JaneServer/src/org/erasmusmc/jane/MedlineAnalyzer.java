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

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

public class MedlineAnalyzer extends StandardAnalyzer { 
   
  public MedlineAnalyzer(String[] stopwords){
  	super(Version.LUCENE_30);
  }
  
  public TokenStream tokenStream(String fieldName, Reader reader){
    Reader adaptedReader = new ReaderAdapter(reader);
    return super.tokenStream(fieldName, adaptedReader);
  }
  
  private class ReaderAdapter extends Reader{
    private Reader reader;
    
    public ReaderAdapter(Reader reader){
      this.reader = reader;  
    }
    
    @Override
    public void close() throws IOException {
      reader.close();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      int result = reader.read(cbuf, off, len);
      for (int i = off; i < off+len; i++){
        char ch = cbuf[i]; 
        if (!Character.isLetterOrDigit(ch) && (ch != '.'))
          cbuf[i] = ' ';
      }
      return result;
    }
  }
}
