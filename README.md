Journal / Author Name Estimator (JANE)
======================================

This repository contains the source code for [JANE](http://jane.biosemantics.org/).

JANE consists of two parts: 

- **JaneServer** is Java code for creating the search index behind JANE, and providing search functions using the index.
- **JaneClient** is a PHP web interface which uses the search functions.

How to deploy
=============

1. Download [MEDLINE / PubMed](https://www.nlm.nih.gov/databases/download/pubmed_medline.html) from NLM.
2. Use [MedlineXmlToDatabase](https://github.com/OHDSI/MedlineXmlToDatabase) to load MEDLINE / PubMed in a database.
3. Use the `org.erasmusmc.jane.indexConstruction.JaneMasterIndexingScript` class in JaneServer to build a search index.
4. On the server, create a folder `/var/jane', and copy the `JaneServer/settings.txt` file to this folder.
5. Copy the search index to the folder specified in the `settings.txt` file.
6. Build a .war file of JaneServer, and deploy on a Tomcat server. When deployed, it will look for the `settings.txt` file and open the search index. It will also act as a SOAP server, exposing the search functions.
7. Copy the code in JaneClient to a web document folder. Make sure the web server is set up to support PHP, and PHP is set up to support SOAP calls.

License
=======
JANE is licensed under Apache License 2.0


