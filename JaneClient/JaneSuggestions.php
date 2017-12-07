<?php
include 'Utilities.php';

class JaneSuggestions {
  private $client;
  private $filter = "";
  private $types = "";
  private $languages = "";
  private $openaccessString = "";
  private $medlineindexedString = "";
  private $pubmedcentralString = "";
  private $text = "";
  public $paperURLTemplate = "http://www.ncbi.nlm.nih.gov/sites/entrez?cmd=Retrieve&db=PubMed&list_uids=%pmid";

  public function __construct(){
    $this->client = new SoapClient("http://localhost:8080/JaneServer/services/JaneSOAPServer?wsdl");
    $this->parseParams();
  }

  private function printCitations($papers) {
    if (!is_array($papers))
    $papers = array($papers);
    print("<table>\n");
    print("<th>Similarity</th><th></th><th>Citation</th>\n");
    for ($p = 0; $p < sizeof($papers); $p++) {
      $paper = $papers[$p];
      $score = round($paper->score * 100);
      print("<tr>\n");
      print("<td>\n");
      print("<div id=\"bluebar\" title=\"Similarity to your query is $score%\">\n");
      print("<div id=\"bluebarfill\" style=\"width:".$score."px; left:".(100-$score)."px;\"></div>\n");
      print("<div>\n");
      print("</td>\n");
      print("<td>\n");
      print("<span name=\"Wrap_PMID_$paper->pmid\" class=\"styledCheckboxWrap\"><input type=\"checkbox\" name=\"PMID_$paper->pmid\" id=\"PMID_$paper->pmid\" value=\"$paper->pmid\" onclick=\"setCheckboxDisplay(this)\" class=\"styledCheckbox\" /></span>");
      print("</td>\n");
      print("<td>\n");
      $authorString = "";
      $authors = $paper->authors;
      if (!is_array($authors))
      $authors = array($authors);
      for ($a = 0; $a < sizeof($authors); $a++) {
        if ($a != 0){
          $authorString = $authorString . ", ";
        }
        $authorString = $authorString . $authors[$a];
      }
      $paperURL = str_replace("%pmid", $paper->pmid, $this->paperURLTemplate);
      print("                      <a href=\"$paperURL\" target=\"_blank\"/>$authorString</a><br>\n");
      print("                      <strong>$paper->title</strong><br>\n");
      print("                      $paper->journal. $paper->year<br>\n");
      print("                      <br>\n");
      print("                    </td>\n");
      print("                  </tr>\n");
    }
    print("                </table>\n");
  }

  private function concatenatePMIDs($papers) {
    if (!is_array($papers))
    $papers = array($papers);
    $result = "";
    for ($p = 0; $p < sizeof($papers); $p++) {
      $paper = $papers[$p];
      $result = $result.$paper->pmid;
      if ($p < sizeof($papers)-1)
      $result = $result.";";
    }
    return $result;
  }

  private function parseParams(){
    $this->text = unicode2ascii(getParam("text"));
    $this->text = escape($this->text);

    if (isParamSet("filterString")){
      $this->filter = getParam("filterString");
      $this->types = getParam("types");
      $this->languages = getParam("languages");
    } else {
      $this->filter = "IP=".$_SERVER['REMOTE_ADDR'];

      if (isParamSet("structured"))
        $this->filter = $this->filter."|structured query";

      if (isParamSet("typeCount")){
        $count = getParam("typeCount");
        for ($i = 0; $i < $count; $i++) {
          if (isParamSet("type$i")){
            if ($this->filter != "")
            $this->filter = $this->filter."|";
            $this->filter = $this->filter."type=".getParam("type$i");

            if ($this->types != "")
            $this->types = $this->types.", ";
            $this->types = $this->types.getParam("type$i");
          }
        }
      }

      if (isParamSet("languageCount")){
        $count = getParam("languageCount");
        for ($i = 0; $i < $count; $i++) {
          if (isParamSet("language$i")){
            if ($this->filter != "")
            $this->filter = $this->filter."|";
            $this->filter = $this->filter."language=".getParam("language$i");

            if ($this->languages != "")
            $this->languages = $this->languages.", ";
            $this->languages = $this->languages.getParam("language$i");
          }
        }
      }

      $this->openaccessString = "";
      $this->pubmedCentralString = "";
      if (isParamSet("findJournals")){
        if (isParamSet("openaccess")){
          $openaccess = getParam("openaccess");
          if ($openaccess != "no preference"){
            if ($this->filter != "")
            $this->filter = $this->filter."|";
            $this->filter = $this->filter."openaccess=".$openaccess;
            if ($openaccess == "true"){
              $this->openaccessString = "open access journals";
            } else {
              $this->openaccessString = "non-open access journals";
            }
          }
        }

        if (isParamSet("pubmedcentral")){
          $pubmedcentral = getParam("pubmedcentral");
          if ($pubmedcentral != "no preference"){
            if ($this->filter != "")
            $this->filter = $this->filter."|";
            $this->filter = $this->filter."pmcmonths=".$pubmedcentral;
            if ($pubmedcentral == "immediate"){
              $this->pubmedcentralString = "journals with immediate open access in PubMed Central";
            } else if ($pubmedcentral == "inoneyear"){
              $this->pubmedcentralString = "journals with open access in PubMed Central in 12 months or less";
            } else if ($pubmedcentral == "any"){
              $this->pubmedcentralString = "journals with open access in PubMed Central";
            }
          }
        }
      }
    }

  }
  public function printUserQuery(){
    print("      <p>\n");
    if (isParamSet("findJournals")){
      print("        These journals have articles most similar to your input:<br/>\n");
    } else if (isParamSet("findAuthors")){
      print("        These authors have written articles most similar to your input:<br/>\n");
    } else if (isParamSet("findPapers")){
      print("        These articles are most similar to your input:<br/>\n");
    }
    print("        &quot;<em>".substr($this->text,0,100));
    if (strlen($this->text) > 100) print("...");
    print("</em>&quot;<br/>\n");
    if ($this->languages != "")
    print("        Limited to the languages: <em>".$this->languages."</em><br/>\n");
    if ($this->types != "")
    print("        Limited to the publication types: <em>".$this->types."</em><br/>\n");
    if ($this->openaccessString != "")
    print("        Limited to <em>".$this->openaccessString."</em><br/>\n");
    if ($this->pubmedcentralString != "")
    print("        Limited to <em>".$this->pubmedcentralString."</em><br/>\n");
    print("      </p>\n");

  }

  public function printSuggestions(){
    if (isParamSet("findPapers")){
      $this->printPapers();
    } else {
      $this->printJournalsOrAuthors();
    }
  }

  private function printPapers(){
    $size = 20;
    if (getParam("findPapers") == "Next page")
    $offset = getParam("offset") + $size;
    else if (getParam("findPapers") == "Previous page")
    $offset = getParam("offset") - $size;
    else $offset = 0;
    $results = $this->client->getPapers(array('text' => unescape($this->text),  'filterString' => $this->filter,'count' => $size, 'offset' => $offset))->return;
    if (sizeof($results) == 0){
      print("<p>No items found matching your query.</p> ");
    } else {
      print("<p>Showing results ".($offset+1)." - ".($offset+sizeof($results))."</p>\n");
      print("<form name=\"suggestionsform\" action=\"suggestions.php\" method=\"post\">");
      $this->printCitations($results);

      $filterString = $this->filter;
      if (strstr($filterString,"repeat")===FALSE){
        if ($filterString != "")
        $filterString = $filterString."|";
        $filterString = $filterString."repeat"; // Repeat option is not used by server, but stored in log
      }
      print("<input type=\"hidden\" name=\"filterString\" value=\"$filterString\">\n");
      print("<input type=\"hidden\" name=\"languages\" value=\"$this->languages\">\n");
      print("<input type=\"hidden\" name=\"types\" value=\"$this->types\">\n");
      print("<input type=\"hidden\" name=\"offset\" value=\"$offset\">\n");
      print("<p>");
      print("<input class = \"lowButton\" name=\"selectAll\" value=\"Select all\" type=\"button\" onclick=\"checkAll()\" title=\"Select all documents\">\n");
      print("<input class = \"lowButton\" name=\"deselectAll\" value=\"Deselect all\" type=\"button\" onclick=\"uncheckAll()\" title=\"Deselect all documents\">\n");
      print("<input class = \"lowButton\" name=\"export\" value=\"Export citations\" type=\"button\" onclick=\"exportCitations()\" title=\"Export citations in MEDLINE format\">\n");
      print("</p>");
      if ($offset != 0)
      print("<input class = \"highButton\" name=\"findPapers\" value=\"Previous page\" type=\"submit\" title=\"Previous page\">\n");
      if (sizeof($results) == $size)
      print("<input class = \"highButton\" name=\"findPapers\" value=\"Next page\" type=\"submit\" title=\"Next page\">\n");
    }
    print("        <input type=\"hidden\" name=\"text\" value=\"".escape($this->text)."\">\n");
    print("</form>\n");
    print("<p>\n");
    if (strpos($this->filter, "structured query") === false) {
      print("Click <a href=\"index.php\">here</a> to perform another search.\n");
    } else {
      print("Click <a href=\"index.php?structured=true\">here</a> to perform another search.\n");
    }
    print("</p>\n");

  }

  private function printJournalsOrAuthors() {
    print("<form name=\"suggestionsform\" action=\"similar.php\" method=\"post\" target=\"_blank\">\n");
    if (isParamSet("findJournals")){
      $results = $this->client->getJournals(array('text' => unescape($this->text),  'filterString' => $this->filter));
      print("<input type=\"hidden\" name=\"type\" value=\"journal\">\n");
    } else {
      $results = $this->client->getAuthors(array('text' => unescape($this->text),  'filterString' => $this->filter));
      print("<input type=\"hidden\" name=\"type\" value=\"authors\">\n");
    }
    if (property_exists($results,"return"))
      $results = $results->return;
    else
      $results = array(  );
    if (sizeof($results) == 0){
      print("        <p>No items found matching your query.</p> ");
      if ($this->filter != "")
      print("Perhaps you could remove some of the publication type limits?");
    } else if (isParamSet("findJournals")){
      print("<table width=\"100%\">\n");
      print("<th>Confidence</th><th>Journal</th><th>Article Influence&nbsp;<a href=\"articleinfluence.html\" class=\"jTip\" id=\"ai\" name=\"Article Influence\"><img  style=\"position: relative; top: 2px; border: 0px;\" src=\"images/questionmark.gif\"/></a></th><th>Articles</th>\n");
    } else {
      print("<table width=\"100%\">\n");
      print("<th>Confidence</th><th>Author</th><th>E-mail</th><th>Articles</th>\n");
    }

    for ($i = 0; $i < sizeof($results); $i++) {
      $item = $results[$i];
      $score = round($item->score * 100);
      if ($i % 2 == 0){
        print("<tr>\n");
      } else {
        print("<tr class=\"line\">\n");
      }
      print("<td>\n");
      print("<div id=\"bluebar\" title=\"Confidence is $score%\">\n");
      print("<div id=\"bluebarfill\" style=\"width:".$score."px; left:".(100-$score)."px;\"></div>\n");
      print("<div>\n");
      print("</td>\n");
      print("<td width=\"100%\">\n");

      if (isParamSet("findAuthors")){
        $author = $item->name;
        $name = $author;
        $originalName = $item->name;
        $similarHint = "Show the articles by $author";
        $emailHint = "Search for the e-mail address of $author";
        $similarPubMedHint = "Show these and other similar articles by $author in PubMed";
        $colspan = 4;
        print("$name\n");
        print("</td>\n");
        print("<td>\n");
        print("<input class=\"bigButton\" name=\"email$i\" value=\"E-mail\" type=\"submit\" onclick=\"return popupform()\" title=\"$emailHint\">\n");
        print("<input type=\"hidden\" name=\"authorName$i\" value=\"$originalName\">\n");
        print("<input type=\"hidden\" name=\"authorPMIDs$i\" value=\"".$this->concatenatePMIDs($item->papers)."\">\n");
        print("</td>\n");
        print("<td>\n");
      } else {
        $ai = "-1";
        if ($item->ai != '') {
          $ai = $item->ai;
          $airank = $item->airank;
          $journalabbr = $item->journalAbbr;
          $issn = $item->issn;
        }
        $fullName = $item->name;
        if ($item->openAccess == 'true'){
          $fullName = $fullName." <div id=\"openaccess\"><a href=\"openaccess.html\" class=\"jTip\" id=\"oa$i\" name=\"Open access\">High-quality&nbsp;open&nbsp;access</a></div>";
        }
		if ($item->medlineIndexed == 'true'){
          $fullName = $fullName." <div id=\"medlineindexed\"><a href=\"medlineindexed.html\" class=\"jTip\" id=\"mi$i\" name=\"Medline-indexed\">Medline-indexed</a></div>";
        }
        if ($item->pmcMonths != '-1') {
			$fullName = $fullName." <div id=\"pubmedcentral\"><a href=\"pubmedcentral.html\" class=\"jTip\" id=\"pmc$i\" name=\"PubMed Central\">PMC</a></div>";
		}
        //if ($item->pmcMonths != '0'){
        //  $fullName = $fullName." <div id=\"pubmedcentral\"><a href=\"pubmedcentral.html\" class=\"jTip\" id=\"pmc$i\" name=\"PubMed Central\">PubMed&nbsp;Central:&nbsp;immediately</a></div>";
        //} else {
        //  $fullName = $fullName." <div id=\"pubmedcentral\"><a href=\"pubmedcentral.html\" class=\"jTip\" id=\"pmc$i\" name=\"PubMed Central\">PubMed&nbsp;Central:&nbsp;after&nbsp;".$item->pmcMonths."&nbsp;months</a></div>";
        //}

        $name = $item->name;
        $originalName = $name;
        $similarHint = "Show the articles in &#34;$name&#34;";
        $similarPubMedHint = "Show these and other similar articles in &#34;$name&#34; in PubMed";
        $colspan = 4;
        print("$fullName\n");
        print("</td>\n");
        print("<td>\n");
        if ($ai != "-1"){
          print("<div id=\"orangebar\" title=\"Article Influence = $ai. Of all journals, $airank% has a lower Article Influence score.\">\n");
          print("<div id=\"orangebarfill\" style=\"width:".$airank."px;\"></div>\n");
          //print("<div id=\"barcontent\"><a href=\"javascript:popUp('detail.php?year=2009&jrlname=$journalabbr&issnnum=$issn')\">$ai</a></div>\n");
          print("<div id=\"barcontent\">$ai</div>\n");
          print("</div>\n");
        } else {
          print("<table width=\"100\"><tr><td>&nbsp;</td></tr></table>\n");
        }
        print("</td>\n");
        print("<td>\n");

      }
      print("<input class=\"bigButton\" id=\"button$i\" value=\"Show articles\" type=\"button\" onclick=\"showInfo('info$i', 'button$i')\" title=\"$similarHint\">\n");
      print("</td>\n");
      print("</tr>\n");
      print("<tr>\n");
      print("<td colspan=\"$colspan\">\n");
      print("<div id=\"info$i\" class=\"expandable\" style=\"display:none;\">\n");
      print("The confidence score for <strong>$name</strong> is based on these articles:<br>&nbsp; \n");
      $this->printCitations($item->papers);
      print("<input class = \"lowButton\" name=\"item$i\" value=\"Explore more in PubMed\" type=\"submit\" title=\"$similarPubMedHint\" onclick=\"return defaultform()\">\n");
      print("<input type=\"hidden\" name=\"itemString$i\" value=\"$originalName\">\n");
      print("</div>\n");
      print("</td>\n");
      print("</tr>\n");
    }
    print("</table>\n");
    print("<input type=\"hidden\" name=\"count\" value=\"".sizeof($results)."\">\n");
    print("<input type=\"hidden\" name=\"text\" value=\"".escape($this->text)."\">\n");
    print("<p>");
    print("<input class = \"lowButton\" name=\"selectAll\" value=\"Select all\" type=\"button\" onclick=\"checkAll()\" title=\"Select all documents\">\n");
    print("<input class = \"lowButton\" name=\"deselectAll\" value=\"Deselect all\" type=\"button\" onclick=\"uncheckAll()\" title=\"Deselect all documents\">\n");
    print("<input class = \"lowButton\" name=\"export\" value=\"Export citations\" type=\"button\" onclick=\"exportCitations()\" title=\"Export citations in MEDLINE format\">\n");
    print("</p>");
    print("</form>\n");
    print("<p>\n");
    if (strpos($this->filter, "structured query") === false) {
      print("Click <a href=\"index.php\">here</a> to perform another search.\n");
    } else {
      print("Click <a href=\"index.php?structured=true\">here</a> to perform another search.\n");
    }
    print("</p>\n");
  }
}
?>