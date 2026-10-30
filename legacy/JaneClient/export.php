<?php
  $url = 'https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id=';
  $first = true;
  foreach ($_POST as $var => $value) {
    if (strpos($var, 'PMID') === 0){
      if ($first)
        $first = false;
      else
        $url = $url . ',';
      $url = $url . $value;
    }
  }
  $url = $url . '&retmode=text&rettype=medline';

  header('Content-type: text/plain');
  header('Content-Disposition: attachment; filename="MEDLINECitationsFromJane.nbib"');
  readfile($url);
?>
