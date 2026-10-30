package org.erasmusmc.jane.indexConstruction;


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
