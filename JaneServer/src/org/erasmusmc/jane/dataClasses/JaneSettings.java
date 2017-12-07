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

import java.util.HashMap;
import java.util.Map;

import org.erasmusmc.utilities.ReadTextFile;


/**
 * Structure for storing all settings used by Jane
 * @author schuemie
 *
 */
public class JaneSettings {
	
	public static String settingsFileLocation = "/var/jane/settings.txt";
	public static String DEFAULT = "DEFAULT"; //Default subset name
	private Map<String, Settings> subset2Settings;
  private String logFile;
  
	public void setLogFile(String logFile) {
		this.logFile = logFile;
	}

	private Settings getSettingsCreateIfUnknown(String subset) {
		Settings settings = subset2Settings.get(subset);
		if (settings == null){
			settings = new Settings();
			subset2Settings.put(subset, settings);
		}
		return settings;
	}
	
	private Settings getSettingsDefaultIfUnknown(String subset) {
		if (subset == null)
			return subset2Settings.get(DEFAULT);
		Settings settings = subset2Settings.get(subset);
		if (settings == null)
			settings = subset2Settings.get(DEFAULT);
		return settings;
	}

	public String getLogFile() {
		return logFile;
	}
	public void setIndexFolder(String subset, String indexFolder) {
		Settings settings = getSettingsCreateIfUnknown(subset);
		settings.indexFolder = indexFolder;
	}
	public String getIndexFolder(String subset) {
		return getSettingsDefaultIfUnknown(subset).indexFolder;
	}
	public void setK(String subset, int k) {
		Settings settings = getSettingsCreateIfUnknown(subset);
		settings.k = k;
	}
	public int getK(String subset) {
		return getSettingsDefaultIfUnknown(subset).k;
	}
	public static JaneSettings load() {
		JaneSettings settings = new JaneSettings();
		settings.subset2Settings = new HashMap<String, JaneSettings.Settings>();
		String subset = DEFAULT;
		for (String line : new ReadTextFile(settingsFileLocation)){
			if (line.startsWith("[")){
				String trimLine = line.trim();
				subset = trimLine.substring(1,trimLine.length()-1);
			} else {
			String[] parts = line.split("=");
			if (parts[0].trim().equals("logFile"))
				settings.setLogFile(parts[1].trim());
			if (parts[0].trim().equals("indexFolder"))
				settings.setIndexFolder(subset, parts[1].trim());
			if (parts[0].trim().equals("k"))
				settings.setK(subset, Integer.parseInt(parts[1].trim()));
			if (parts[0].trim().equals("requireDisambiguationForEMail"))
				settings.setRequireDisambiguationForEMail(subset, Boolean.parseBoolean(parts[1].trim()));
			}
		}
		return settings;
	}
	public void setMaxSimilarDocuments(String subset, int maxSimilarDocuments) {
		Settings settings = getSettingsCreateIfUnknown(subset);
		settings.maxSimilarDocuments = maxSimilarDocuments;
	}
	public int getMaxSimilarDocuments(String subset) {
		return getSettingsDefaultIfUnknown(subset).maxSimilarDocuments;
	}
	public void setRequireDisambiguationForEMail(String subset, boolean requireDisambiguationForEMail) {
		Settings settings = getSettingsCreateIfUnknown(subset);
		settings.requireDisambiguationForEMail = requireDisambiguationForEMail;
	}
	public boolean isRequireDisambiguationForEMail(String subset) {
		return getSettingsDefaultIfUnknown(subset).requireDisambiguationForEMail;
	}
	
	private class Settings {
	  private String indexFolder;
	  private int k = 50;
		private int maxSimilarDocuments = 20;
		private boolean requireDisambiguationForEMail = true;
	}
}
