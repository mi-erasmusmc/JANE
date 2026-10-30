<?php
include 'Utilities.php';

class JaneEMail {
  private $author;
  private $pmids;
  private $client;

  public function __construct(){
    $this->client = new SoapClient("http://localhost:8080/JaneServer/services/JaneSOAPServer?wsdl");
    $this->parseParams();
  }

  private function parseParams(){
    $item = "";
    $count = $_POST["count"];
    for ($i = 0; $i < $count; $i++) {
      if (isSet($_POST["email$i"])){
        $this->author = $_POST["authorName$i"];
        $this->pmids = $_POST["authorPMIDs$i"];
        break;
      }
    }
  }

  public function printEMails(){
    $emails = $this->client->getEMail(array('author' => $this->author, 'pmids' => $this->pmids));
    print("<h2>E-mail</h2>\n");

    if (is_null($emails) || sizeof(get_object_vars($emails)) == 0 ||sizeof($emails->return) == 0){
      print("      <p>No e-mail address was found for ".$this->author."</p>\n");
    } else {
      $emails = $emails->return;
      if (!is_array($emails))

      $emails = array($emails);
      print("      <p>These e-mail addresses were found for ".$this->author.":</p>\n");
      print("      <table>\n");
      print("        <th>Year</th><th>E-mail address</th><th>Article</th>\n");
      for ($p = 0; $p < sizeof($emails); $p++) {
        $email = $emails[$p];
        if ($p % 2 == 0)
        print("        <tr>\n");
        else
        print("        <tr class=\"line\">\n");
        print("          <td>\n");
        print("            $email->year\n");
        print("          </td>\n");
        print("          <td width=\"100%\">\n");
        safePrintEMail($email->eMail);

        print("          </td>\n");
        print("          <td>\n");
        print("            <input class=\"lowButton\" value=\"Show article\" type=\"button\" onclick=\"window.open('http://www.ncbi.nlm.nih.gov/sites/entrez?cmd=Retrieve&db=PubMed&list_uids=$email->pmid')\" title=\"Show the article where the e-mail address was found.\">\n");
        print("          </td>\n");
        print("        </tr>\n");
      }
    }
    print("      </table>\n");
  }
}
?>