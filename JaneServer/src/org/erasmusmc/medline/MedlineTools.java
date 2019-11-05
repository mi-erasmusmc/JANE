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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;

import org.erasmusmc.medline.RichConnection.QueryResult;
import org.erasmusmc.utilities.Row;
import org.erasmusmc.utilities.WriteCSVFileWithHeader;
import org.erasmusmc.utilities.WriteTextFile;

public class MedlineTools {
	
	public static RetrieveSettings	defaultSettings	= RetrieveSettings.loadDatabaseSettingsFromIniFile("S:\\Data\\MEDLINE\\Unprocessed\\MedlineParser.ini");
	public static GregorianCalendar	calendar		= new GregorianCalendar();
	
	public static void saveAllPMIDsInDatabase(String filename) {
		saveAllPMIDsInDatabase(filename, defaultSettings.server, defaultSettings.database, null, defaultSettings.user, defaultSettings.password,
				defaultSettings.dateSourceType);
	}
	
	public static void saveAllPMIDsInDatabase(String filename, String server, String database, String domain, String user, String password, DbType sourceType) {
		RichConnection connection = new RichConnection(server, domain, user, password, sourceType);
		connection.use(database);
		Iterator<Row> iterator = connection.query("SELECT DISTINCT pmid FROM medcit ORDER BY pmid").iterator();
		WriteTextFile out = new WriteTextFile(filename);
		while (iterator.hasNext()) {
			out.writeln(iterator.next().get("pmid"));
		}
		out.close();
	}
	
	public static void savePMIDsInTimeRange(String filename, Date start, Date end) {
		savePMIDsInTimeRange(filename, start, end, defaultSettings.server, defaultSettings.database, null, defaultSettings.user, defaultSettings.password,
				defaultSettings.dateSourceType);
	}
	
	public static void savePMIDsInTimeRange(String filename, Date start, Date end, String server, String database, String domain, String user, String password,
			DbType sourceType) {
		
		RichConnection connection = new RichConnection(server, domain, user, password, sourceType);
		connection.use(database);
		String sql;
		if (sourceType.equals(DbType.MSSQL)) {
			connection.execute("SET DateFormat MDY;");
			sql = "SELECT pmid FROM pmid_to_date WHERE pmid_version = 1 AND date >= '" + format(start) + "' AND date <= '" + format(end) + "' ORDER BY pmid";
		} else {
			sql = "SELECT pmid FROM pmid_to_date WHERE pmid_version = 1 AND date >= '" + format(start) + "' AND date <= '" + format(end) + "' ORDER BY pmid";
		}
		QueryResult queryResult = connection.query(sql);
		WriteCSVFileWithHeader out = new WriteCSVFileWithHeader(filename);
		int count = 0;
		for (Row row : queryResult) {
			out.write(row);
			count++;
		}
		SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy");
		System.out.println("Found " + count + " PMIDs published between " + format.format(start) + " and " + format.format(end));
		out.close();
	}
	
	private static String format(Date date) {
		calendar.setTime(date);
		return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DATE);
	}
}
