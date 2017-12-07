<?php
include 'Utilities.php';

class JaneSimilar {
  private $text;
  private $item;
  private $type;

  public function __construct(){
    $this->client = new SoapClient("http://localhost:8080/JaneServer/services/JaneSOAPServer?wsdl");
    $this->parseParams();
  }

  private function parseParams(){
    $this->item = "";
    $count = $_POST["count"];
    for ($i = 0; $i < $count; $i++) {
      if (isSet($_POST["item$i"])){
        $this->item = $_POST["itemString$i"];
        break;
      }
    }
    $this->type = $_POST["type"];
    $this->text = $_POST["text"];
  }

  private function generateURL($pmids){
    $url = "http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?cmd=Retrieve&db=PubMed&list%5fuids=";
    return $url.join(",",$pmids);
  }

  public function printSimilar(){
    if ($this->type == "journal"){
      $pmids = $this->client->getSimilarDocumentsInJournal(array('text' => $this->text, 'journal' =>$this->item))->return;
    } else {
      $pmids = $this->client->getSimilarDocumentsOfAuthor(array('text' => $this->text, 'author' =>$this->item))->return;
    }
    $url = $this->generateURL($pmids);
    print("<META HTTP-EQUIV=\"Refresh\" CONTENT=\"0;url=$url\">");
    print("</head>");
    print("<body>");
    print("You are being redirected to <a href = \"$url\">PubMed</a><br>\n");
  }
}
?>