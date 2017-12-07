<html>
<head>
<title>Journal / Author Name Estimator</title>
<style type="text/css" media="screen">
@import url(home.css);
</style>
<link rel="SHORTCUT ICON" href="jane.ico">
</head>
<body>
<div id="pagewrapper">
<div id="header"><h1><a href="index.php">
Journal / Author Name Estimator
</a></h1></div>
<h2>Frequently Asked Questions</h2>
<ul>
	<li><a href="#confidentiality">What about confidentiality? Is it safe
	to send my abstract to Jane?</a></li>
	<li><a href="#how">How does Jane work?</a></li>
	<li><a href="#updates">How often is the data behind Jane updated?</a></li>
	<li><a href="#whichjournals">Which journals are included in Jane?</a></li>
	<li><a href="#whichauthors">Which authors are included in Jane?</a></li>
	<li><a href="#whichpapers">Which papers are included in Jane?</a></li>
	<li><a href="#article influence">What is the Article Influence score?</a></li>
	<li><a href="#api">I would like to include Jane in my application. Is
	it possible to access Jane programmatically?</a></li>
</ul>
<p><a href="index.php">Return to Jane</a></p>

<a name="confidentiality"></a>
<h3>What about confidentiality? Is it safe to send my abstract to Jane?</h3>
<p>The information sent to the Jane server is not stored. It is kept in
memory for as long as needed to calculate the scores and formulate the
response page, and then it is discarded from memory. The server itself
is protected using standard protection measures. However, we understand
that there is still the possibility that someone could intercept the
transmission, and of course you do not know whether you can trust us. We
therefore included an option in Jane to 'scramble' your input (see the
button below the input box). Scrambling simply entails putting all the
words in alphabetical order, and this is done by your browser (i.e. no
information is sent for the scrambling). We admit that putting the words
in alphabetical order does not completely disguise your input, but it
does make it extremely hard to read, and it has no effect on the
performance of Jane.</p>
<a name="how"></a>
<h3>How does Jane work?</h3>
<p>Jane first searches for the 50 articles that are most similar to your
input*. For each of these articles, a similarity score between that
article and your input is calculated. The similarity scores of all the
articles belonging to a certain journal or author are summed to
calculate the confidence score for that journal or author. The results
are ranked by confidence score. For more information, you can read <a
	href="http://www.ncbi.nlm.nih.gov/pubmed/18227119?ordinalpos=2&itool=EntrezSystem2.PEntrez.Pubmed.Pubmed_ResultsPanel.Pubmed_RVDocSum">
our paper</a>.</p>
<p>* For the computer geeks: we use the open source search engine <a
	href="http://lucene.apache.org">Lucene</a>. Queries using keywords are
parsed with the QueryParser class, titles and abstracts are parsed using
the MoreLikeThis parser class. <a name="updates"></a>


<h3>How often is the data behind Jane updated?</h3>
<p>We are currently updating the data once every month.</p>
<a name="whichjournals"></a>
<h3>Which journals are included in Jane?</h3>
<p>Basically, all journals included in PubMed are included in Jane.
However, in order to show only active journals, we do not show journals
for which no entry was found in PubMed in the last year. </p>
<a name="whichauthors"></a>
<h3>Which authors are included in Jane?</h3>
<p>All authors that have published one or more articles in the last 10
years that have been included in PubMed, are included in Jane.</p>
<a name="whichpapers"></a>
<h3>Which papers are included in Jane?</h3>
<p>All records in PubMed have been included that:


<ul>
	<li>contained an abstract,</li>
	<li>were published in the last 10 years,</li>
	<li>did not belong to one of these categories: comment, editorial,
	news, historical article, congresses, biography, newspaper ar-ticle,
	practice guideline, interview, bibliography, legal cases, lectures,
	consensus development conference, addresses, clini-cal conference,
	patient education handout, directory, technical report, festschrift,
	retraction of publication, retracted publica-tion, duplicate
	publication, scientific integrity review, pub-lished erratum,
	periodical index, dictionary, legislation or government publication.</li>
</ul>
<a name="article influence"></a>
<h3>What is the Article Influence score?</h3>
<p>The Article Influence (AI) measures how often articles in the journal
are cited within the first five years after its publication. These
citations are weighted based the influence of the journals from which
citations are received: being cited in an article in Science can boost a
journal's AI more than being cited in an article in an obscure journal.
For more detailed information, see the <a
	href="http://www.eigenfactor.org/whyeigenfactor.htm">eigenfactor.org</a>
website.</p>
<a name="api"></a>
<h3>I would like to include Jane in my application. Is it possible to
access Jane programmatically?</h3>
<p>Yes, there is an API that is freely available. A brief description can be found <a href="JANE API description.pdf">here</a>.
<h2>Contact</h2>
If you still have unanswered questions, or would like to make a remark
regarding Jane, you can always send an e-mail to <script
	language=javascript>
	  <!--
	    var username = "schuemie";
	    var hostname = "ohdsi.org";
	    var linktext = username + "@" + hostname;
	    document.write("<a href=" + "mail" + "to:" + username + "@" + hostname + ">" + linktext + "</a>")
	  //-->
	  	  </script>.
<p><a href="index.php">Return to Jane</a></p>


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
</body>
</html>

