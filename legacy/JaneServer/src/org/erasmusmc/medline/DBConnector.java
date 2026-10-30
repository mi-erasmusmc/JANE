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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnector {
	
	public static void main(String[] args) {
	}
	
	public static Connection connect(String connectionString, String user, String password, DbType dbType) {
		loadDriver(dbType);
		try {
			return DriverManager.getConnection(connectionString, user, password);
		} catch (SQLException e1) {
			throw new RuntimeException("Cannot connect to DB server: " + e1.getMessage());
		}
	}
	
	private static void loadDriver(DbType dbType) {
		String className;
		if (dbType.equals(DbType.MYSQL))
			className = "com.mysql.jdbc.Driver";
		else if (dbType.equals(DbType.MSSQL))
			className = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		else if (dbType.equals(DbType.ORACLE))
			className = "oracle.jdbc.driver.OracleDriver";
		else if (dbType.equals(DbType.POSTGRESQL))
			className = "org.postgresql.Driver";
		else if (dbType.equals(DbType.REDSHIFT))
			className = "com.amazon.redshift.jdbc4.Driver";
		else
			className = null;
		try {
			Class.forName(className);
		} catch (ClassNotFoundException e1) {
			throw new RuntimeException("Cannot find JDBC driver. Make sure the file is in the path");
		}
	}
	
	public static Connection connect(String server, String domain, String user, String password, DbType dbType) {
		String connectionString;
		if (dbType.equals(DbType.MYSQL))
			connectionString = "jdbc:mysql://" + server + ":3306/?useCursorFetch=true";
		else if (dbType.equals(DbType.MSSQL))
			connectionString = "jdbc:sqlserver://" + server + ((user == null) ? ";integratedSecurity=true" : "");
		else if (dbType.equals(DbType.ORACLE))
			throw new RuntimeException("Oracle driver can only be used by specifying full connection string");
		else if (dbType.equals(DbType.POSTGRESQL))
			connectionString = "jdbc:postgresql://" + server;
		else if (dbType.equals(DbType.REDSHIFT))
			connectionString = "jdbc:redshift://" + server;
		else
			return null;
		return connect(connectionString, user, password, dbType);
	}
}
