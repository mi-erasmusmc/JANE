<?php
  $url = 'http://www.ncbi.nlm.nih.gov/pubmed/';
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
  $url = $url . '?report=medline&format=text';

  header('Content-type: text/plain');
  header('Content-Disposition: attachment; filename="MEDLINECitationsFromJane.nbib"');
  readfile($url);
?>
