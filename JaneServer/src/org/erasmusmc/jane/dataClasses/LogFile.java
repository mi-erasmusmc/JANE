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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class LogFile {

	private String filename;

	public LogFile(String filename){
		this.filename = filename;
	}


	public void addToLog(String entry) {
		StringBuilder sb = new StringBuilder();
		sb.append(millisecondsToSortableTimeString(System.currentTimeMillis()));
		sb.append("\t");
		sb.append(entry);
		appendToFile(sb.toString());    
	}

	private static String millisecondsToSortableTimeString(long ms) {
		Calendar calendar = new GregorianCalendar();
		DecimalFormat myFormatter = new DecimalFormat("00");
		calendar.setTimeInMillis(ms);
		StringBuilder sb = new StringBuilder();
		sb.append(calendar.get(Calendar.YEAR));
		sb.append(myFormatter.format(calendar.get(Calendar.MONTH)+1));
		sb.append(myFormatter.format(calendar.get(Calendar.DATE)));
		sb.append(myFormatter.format(calendar.get(Calendar.HOUR)));
		sb.append(myFormatter.format(calendar.get(Calendar.MINUTE)));
		sb.append(myFormatter.format(calendar.get(Calendar.SECOND)));
		return sb.toString();
	}

	private void appendToFile(String text) {                 
		try {
			FileOutputStream file = new FileOutputStream(filename,true);
			BufferedWriter bufferedWrite = new BufferedWriter(new OutputStreamWriter(file),1000000);
			try {
				bufferedWrite.write(text);  
				bufferedWrite.newLine();
				bufferedWrite.flush();
				bufferedWrite.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

}
