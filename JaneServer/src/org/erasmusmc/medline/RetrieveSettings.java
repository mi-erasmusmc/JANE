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

import org.erasmusmc.utilities.IniFile;

public class RetrieveSettings {
	public boolean	retrieveText				= false;
	public boolean	retrieveMeSHHeaders			= false;
	public boolean	retrieveGeneSymbols			= false;
	public boolean	retrieveCitationInformation	= false;
	public boolean	retrieveChemicals			= false;
	public boolean	retrievePublicatonTypes		= false;
	public boolean	retrieveAuthors				= false;
	public boolean	retrieveLanguage			= false;
	
	public String	server;
	public String	database;
	public String	domain;
	public String	user;
	public String	password;
	public DbType	dateSourceType;
	
	public String	pmidsFile;
	
	public static RetrieveSettings loadDatabaseSettingsFromIniFile(String filename) {
		RetrieveSettings settings = new RetrieveSettings();
		IniFile iniFile = new IniFile(filename);
		settings.server = iniFile.get("SERVER");
		settings.database = iniFile.get("SCHEMA");
		settings.domain = iniFile.get("DOMAIN");
		settings.user = iniFile.get("USER");
		settings.password = iniFile.get("PASSWORD");
		settings.dateSourceType = new DbType(iniFile.get("DATA_SOURCE_TYPE"));
		return settings;
	}
}
