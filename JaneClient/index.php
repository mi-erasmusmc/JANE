<html>
<head>
<title>Journal / Author Name Estimator</title>
<style type="text/css" media="screen">
@import url(home.css);
</style>
<link rel="SHORTCUT ICON" href="jane.ico">
</head>
<body>
<script language="JavaScript" type="text/javascript">
<!--

function doScramble (){
  var array = document.form.text.value.split(/[ .,\n\t:;()]/);
  array.sort();
  document.form.text.value = array.join(' ');
}

function doClear (){
  document.form.text.value = '';
}

function showExtra(){
  if (document.getElementById('extra').style.display == 'none'){
    document.getElementById('extra').style.display = 'block';
    document.form.extraButton.value = 'Hide extra options';
  } else {
    document.getElementById('extra').style.display = 'none';
    document.form.extraButton.value = 'Show extra options';
  }
}

//-->
    </script>
<div id="pagewrapper">
<div id="header">
<h1>Journal / Author Name Estimator</h1>
</div>
<div id="leftcolumn">
<p>&nbsp;</p>
<?php
if (isset($_GET["structured"])) {
  print("Enter your query here: <div id=\"gray\">(or, click <a href=\"index.php\">here</a> to search using title and/or abstract)</div>\n");
} else {
  print("Insert your title and/or abstract here: <div id=\"gray\">(or, click <a href=\"index.php?structured=true\">here</a> to search using keywords)</div>\n");
}
?>
<form name='form' action="suggestions.php" method="post"><?php
if (isset($_GET["structured"])) {
  print("<input type=\"text\" name=\"text\" size=\"80\"/><br>\n");
  print("<input type=\"hidden\" name=\"structured\" value=\"true\">\n");
  print("<div id=\"gray\">For example: (malaria OR tuberculosis) AND \"vaccine development\"</div><br>\n");
} else {
  print("<textarea name=\"text\" rows=\"20\" cols=\"80\">");
  if (isset($_GET["text"]))
    print($_GET["text"]);
  print("</textarea><br>\n");
  print("<input class = \"lowButton\" name=\"scramble\" value=\"Scramble\" type=\"button\" ONCLICK=\"doScramble()\" title=\"Scrambles your text so nobody else can read it\">\n");
}

?> <input class="lowButton" name="clear" value="Clear" type="button"
	ONCLICK="doClear()" title="Clears the text area"> <input
	class="lowButton" name="extraButton" value="Show extra options"
	type="button" ONCLICK="showExtra()"
	title="Shows or hides some extra search options"><br>
<div id="extra" style="display: none;">
<table width="100%">
	<tr>
		<td class="options">Choose the <strong>language</strong>(s) you want
		to publish in:
		<table>
			<tr>
				<td><?php
				$languages = file('Languages.txt');
				for ($i = 0; $i < round(sizeof($languages) / 2); $i++) {
				  $language = explode('=',$languages[$i]);
				  print("                        <input type=\"checkbox\" name=\"language$i\" value=\"$language[1]\">&nbsp;$language[0]<br>\n");
				}
				print("                      </td>\n");
				print("                      <td valign=\"top\">\n");
				for ($i = round(sizeof($languages) / 2); $i < sizeof($languages); $i++) {
				  $language = explode('=',$languages[$i]);
				  print("                        <input type=\"checkbox\" name=\"language$i\" value=\"$language[1]\">&nbsp;$language[0]<br>\n");
				}
				print("                        <input type=\"hidden\" name=\"languageCount\" value=\"".sizeof($languages)."\">\n");
				?></td>
			</tr>
		</table>
		</td>
		<td width="1px"></td>
		<td class="options">Select the <strong>publication type</strong>(s)
		best describing your manuscript:
		<table width="100%">
			<tr>
				<td><?php
				$types = file('AllowedPublicationTypes.txt');
				for ($i = 0; $i < round(sizeof($types) / 2); $i++) {
				  print("                        <input type=\"checkbox\" name=\"type$i\" value=\"$types[$i]\">&nbsp;$types[$i]<br>\n");
				}
				print("                      </td>\n");
				print("                      <td valign=\"top\">\n");
				for ($i = round(sizeof($types) / 2); $i < sizeof($types); $i++) {
				  print("                        <input type=\"checkbox\" name=\"type$i\" value=\"$types[$i]\">&nbsp;$types[$i]<br>\n");
				}
				print("                        <input type=\"hidden\" name=\"typeCount\" value=\"".sizeof($types)."\">\n");
				?></td>
			</tr>
		</table>
		</td>
	</tr>
	<tr height="7px">
		<td colspan="3"></td>
	</tr>
	<tr>
		<td class="options">Choose your <strong>open access</strong> options:*
		<p><input name="openaccess" value="no preference" type="radio" checked/>No
		preference<br />
		<input name="openaccess" value="true" type="radio"/>Search only open
		access journals<br />
		<input name="openaccess" value="false" type="radio"/>Exclude open
		access journals<br />
		</p>
		</td>
		<td></td>
		<td class="options">Included only journals in <strong>PubMed Central</strong>?:*
		<p><input name="pubmedcentral" value="no preference" type="radio"
			checked/>No preference<br />
		<input name="pubmedcentral" value="immediate" type="radio"/>Only
		journals with immediate access<br />
		<input name="pubmedcentral" value="inoneyear" type="radio"/>Only
		journals with a maximum access delay of 12 months<br />
		<input name="pubmedcentral" value="any" type="radio"/>Only journals in
		Pubmed Central<br />
		&nbsp;<br />
		<!--<input name="nihpa" value="nihpa" type="checkbox">Exclude journals requiring NIHPA submission.-->
		</p>
		</td>
	</tr>
</table>
* these options only work when searching for journals</div>
<br>
<input class="highButton" name="findJournals" value="Find journals"
	type="submit" title="Find journals with similar articles"> <input
	class="highButton" name="findAuthors" value="Find authors"
	type="submit" title="Find authors that have published similar articles">
<input class="highButton" name="findPapers" value="Find articles"
	type="submit" title="Find similar articles"></form>
</div>
<div id="rightcolumn">
<h2>Welcome to Jane</h2>
<p>Have you recently written a paper, but you're not sure to which
journal you should submit it? Or maybe you want to find relevant
articles to cite in your paper? Or are you an editor, and do you need to
find reviewers for a particular paper? Jane can help!</p>
<p>Just enter the title and/or abstract of the paper in the box, and
click on 'Find journals', 'Find authors' or 'Find Articles'. Jane will
then compare your document to millions of documents in <em>PubMed</em>
to find the best matching journals, authors or articles.</p>
<h3>Keyword search</h3>
<p>Instead of using a title or abstract, you can also search using a
keyword search, similar to popular web search engines. Click <a
	href="index.php?structured=true">here</a> to search using keywords.</p>
<h3>Beware of predatory journals</h3>
<p>JANE relies on the data in PubMed, which can contain papers from predatory journals, and therefore these journals can appear in JANE's results. To help identify high-quality journals, JANE now tags journals that are currently indexed in MEDLINE, and open access journals approved by the Directory of Open Access Journals (DOAJ).</p>
<p align="right"><a href="faq.php">Additional information about Jane</a></p>
</div>
<div id="footer">Copyright 2007, <a href="http://biosemantics.org/index.php">The
Biosemantics Group</a>. Research funded by <a href="http://www.nbic.nl">NBIC</a>.
Created and maintained by <script language=javascript>
	  <!--
	    var username = "schuemie";
	    var hostname = "ohdsi.org";
	    document.write("<a href=" + "mail" + "to:" + username + "@" + hostname + ">Martijn Schuemie</a>.")
	  //-->
	</script>
<noscript>Martijn Schuemie.</noscript> <br/>Hosting provided by the <a href="http://ohdsi.org">Observational Health Data Science and Informatics</a>
</div>
</div>
</body>
</html>

