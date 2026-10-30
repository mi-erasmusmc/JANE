<?php


class AccessControl {
  private $client;
  private $filter = "";
  private $types = "";
  private $languages = "";
  private $openaccessString = "";
  private $pubmedcentralString = "";
  private $text = "";
  public $paperURLTemplate = "http://www.ncbi.nlm.nih.gov/sites/entrez?cmd=Retrieve&db=PubMed&list_uids=%pmid";


  public function blackListed($ip) {
    //if ($ip == '222.73.104.108' || $ip =='222.73.104.104')
    //  return TRUE;
    //else
      return FALSE;
  }

  public function denyAccess(){
    print("<p>\n");
    print("  These journals have articles most similar to your input:<br/>\n");
    print("  &quot;<em>The website you are viewing is illegally copying the content of JANE. Please use JANE directly at \n");
    //print("  <a href = \"http://156.83.20.12/jane/\">http://biosemantics.org/jane</a></em>&quot;<br/>\n");
    print("  <script language=javascript>\n");
    print("  <!--\n");
	print("     var part1 = \"biosemantics.\";\n");
	print("     var part2 = \"org/jane\";\n");
	print("     document.write(\"<a href=\" + \"http\" + \"://\" + part1 + part2 + \">\"+\"http\" + \"://\" + part1 + part2 + \"</a></em>&quot;<br/>\")\n");
	print("   //-->\n");
	print("   </script>\n");
    print("</p>\n");
  }

}

?>
