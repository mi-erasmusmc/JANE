<html>
<head>
<title>Journal / Author Name Estimator</title>
<link rel="SHORTCUT ICON" href="jane.ico">
<style type="text/css" media="screen">
@import url(home.css);
</style>
</head>
<body>
<div id="emailwrapper"><?php
include 'JaneEMail.php';
$client = new JaneEMail();
$client->printEMails();

?> <input class="lowButton" value="Close window" type="button"
	onclick="javascript:window.close();" title="Close windows"></div>
</body>
</html>
