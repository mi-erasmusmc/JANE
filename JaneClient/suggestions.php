<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
	"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />

<title>Journal / Author Name Estimator</title>
<style type="text/css" media="all">
@import url(home.css);
</style>
<link rel="SHORTCUT ICON" href="jane.ico" />
<script src="js/jquery.js" type="text/javascript"></script>
<script src="js/jtip.js" type="text/javascript"></script>
<script language="JavaScript" type="text/javascript">
	<!--
	function showInfo(infoName, buttonName){
	  if (document.getElementById(infoName).style.display == 'none'){
	    document.getElementById(infoName).style.display = 'block';
	    document.getElementById(buttonName).value = 'Hide articles';
	  } else {
	    document.getElementById(infoName).style.display = 'none';
	    document.getElementById(buttonName).value = 'Show articles';
	  }
	}

	function popupform(){
	  if (! window.focus)
	    return true;
	  newwindow = window.open('', 'email', 'height=200,width=400,scrollbars=yes');
	  document.suggestionsform.target='email';
	  document.suggestionsform.action="email.php";
	  if (window.focus)
	    newwindow.focus();
	  return true;
	}

	function defaultform() {
	  if (! window.focus)
	    return true;
	  document.suggestionsform.target="_blank";
	  document.suggestionsform.action="similar.php";
	  return true;
	}

	function popUp(URL) {
	  window.open("http://eigenfactor.org/"+URL, 'detail', 'toolbar=0,scrollbars=0,location=0,statusbar=0,menubar=0,resizable=1,width=825,height=750');
	}

	if (top.location!= self.location) {
		top.location = self.location.href
	}

	function checkAll(){
	  for (i = 0; i < document.suggestionsform.length; i++)
	  	if (document.suggestionsform[i].className == "styledCheckbox"){
	      document.suggestionsform[i].checked = true;
	      setCheckboxDisplay(document.suggestionsform[i]);
	    }
	}

	function uncheckAll(){
	  for (i = 0; i < document.suggestionsform.length; i++)
	  	if (document.suggestionsform[i].className == "styledCheckbox"){
	      document.suggestionsform[i].checked = false ;
	      setCheckboxDisplay(document.suggestionsform[i]);
	    }
	}

	function exportCitations(){
	  save = document.suggestionsform.action;
	  count = 0;
	  for (i = 0; i < document.suggestionsform.length; i++)
	    if (document.suggestionsform[i].checked)
	      count++;
	  if (count == 0)
	    checkAll();
	  document.suggestionsform.action="export.php";
	  document.suggestionsform.submit();
	  document.suggestionsform.action = save;
	}


function setCheckboxDisplay(box){
      var spans = document.getElementsByTagName('span');
	  for (var i = 0; i < spans.length; i++)
	    if (spans.item(i).getAttribute('name') == 'Wrap_'+box.id){
  	      if (box)
	        if(box.checked)
	          spans.item(i).className = "styledCheckboxWrap wrapChecked";
	        else
		      spans.item(i).className = "styledCheckboxWrap";
	    }
	}

//-->
</script>




</script>
    </script>
</head>
<body>

<div id="pagewrapper">
<div id="smallheader">
<a href="index.php"><h1>Journal / Author Name Estimator</h1></a>
</div>
<?php
include 'JaneSuggestions.php';
include 'AccessControl.php';
$accessControl = new AccessControl();
if ($accessControl->blackListed($_SERVER['REMOTE_ADDR']))
  $accessControl->denyAccess();
else {
  $client = new JaneSuggestions();
  $client->printUserQuery();
  $client->printSuggestions();
}
?>
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

