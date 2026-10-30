/*******************************************************************************
 * Copyright 2025 Erasmus University Medical Center
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

import org.erasmusmc.utilities.WriteTextFile;

public class MedlineTools {

	public static GregorianCalendar calendar = new GregorianCalendar();

	private Connection connection;

	public MedlineTools(String pathToSqlite) {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		try {
			connection = DriverManager.getConnection("jdbc:sqlite:" + pathToSqlite);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	protected void finalize() {
		try {
			connection.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		Calendar oneYearAgo = new GregorianCalendar();
		oneYearAgo.add(Calendar.YEAR, -40);
		
		Calendar future = new GregorianCalendar();
		future.add(Calendar.YEAR, 10);
		
		MedlineTools medlineTools = new MedlineTools("E:/Medline/PubMed.sqlite");
		medlineTools.savePMIDsInTimeRange("E:/Medline/test.txt", oneYearAgo.getTime(), future.getTime());
	}
		
//	public static void main(String[] args) throws Exception {
//		Class.forName("org.sqlite.JDBC");
//		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + "E:/Medline/PubMed.sqlite");
//		String sql = "SELECT * FROM pubmed_articles WHERE pmid = 32132887;";
//		Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
//		ResultSet resultSet = statement.executeQuery(sql);
//		while (resultSet.next()) {
//			System.out.println(resultSet.getString("file_number"));
//		}
//		connection.close();
//	}

	public void savePMIDsInTimeRange(String filename, Date start, Date end) {
		try {
			assurePublicationDataIsIndexed();
			
			String sql = "SELECT pmid FROM pubmed_articles WHERE publication_date >= " + format(start)
					+ " AND publication_date <= " + format(end) + " ORDER BY pmid";
			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet resultSet = statement.executeQuery(sql);
			WriteTextFile out = new WriteTextFile(filename);
			int count = 0;
			while (resultSet.next()) {
				out.writeln(resultSet.getString("pmid"));
				count++;
			}
			SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy");
			System.out.println("Found " + count + " PMIDs published between " + format.format(start) + " and "
					+ format.format(end));
			out.close();
			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void assurePublicationDataIsIndexed() throws SQLException {
		String sql = "SELECT * FROM sqlite_master WHERE type= 'index' and tbl_name = 'pubmed_articles' and name = 'pub_date_idx';";
		Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet resultSet = statement.executeQuery(sql);
		boolean hasIndex = resultSet.next();
		statement.close();
		if (!hasIndex) {
			System.out.println("No index on publication date found. Creating new index");
			sql = "CREATE INDEX pub_date_idx ON pubmed_articles (publication_date);";
			statement = connection.createStatement();
			statement.execute(sql);
			System.out.println("Finished creating new index");
		}
	}

	private static String format(Date date) {
        Calendar year1Epoch = new GregorianCalendar(1, Calendar.JANUARY, 1);
        year1Epoch.set(Calendar.HOUR_OF_DAY, 0);
        year1Epoch.set(Calendar.MINUTE, 0);
        year1Epoch.set(Calendar.SECOND, 0);
        year1Epoch.set(Calendar.MILLISECOND, 0);

        Calendar targetDateCal = new GregorianCalendar();
        targetDateCal.setTime(date);
        targetDateCal.set(Calendar.HOUR_OF_DAY, 0);
        targetDateCal.set(Calendar.MINUTE, 0);
        targetDateCal.set(Calendar.SECOND, 0);
        targetDateCal.set(Calendar.MILLISECOND, 0);
        targetDateCal.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        year1Epoch.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

        long diffMillis = targetDateCal.getTimeInMillis() - year1Epoch.getTimeInMillis();
        long millisecondsInADay = TimeUnit.DAYS.toMillis(1);
        long daysBetween = diffMillis / millisecondsInADay;
        long pythonLikeOrdinal = daysBetween + 1;
		return Long.toString(pythonLikeOrdinal);
	}
}
