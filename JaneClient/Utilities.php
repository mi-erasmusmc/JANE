<?php
function isParamSet($paramName){
  return (isSet($_POST[$paramName]) || isSet($_GET[$paramName]));
}

function getParam($paramName){
  if (isSet($_POST[$paramName]))
  return $_POST[$paramName];
  else
  return $_GET[$paramName];
}

function escape($string){
  $string = str_replace("\\\"", "&quot;", $string);
  $string = str_replace("\"", "&quot;", $string);
  return $string;
}

function unescape($string){
  $string = str_replace("&quot;", "\"", $string);
  return $string;
}

function safePrintEMail($email){
  $parts = explode("@", $email);

  print("            <script language=\"javascript\" type=\"text/javascript\">\n");
  print("              <!--\n");
  print("              var h = \"$parts[1]\";\n");
  print("              var u = \"$parts[0]\";\n");
  print("              var lt = u + \"&#64;\" + h;\n");
  print("              document.write(\"<a href=\\\"\" + \"mai\" + \"lto:\" + lt + \"\\\">\"+lt+\"<\/a>\");\n");
  print("              //-->\n");
  print("            </script>\n");
}

function unicode2ascii($string) {
  $result = "";
  for ($i = 0; $i < strlen($string); $i++ ) {
    $ch = $string[$i];
    $value = ord($ch);
    if (($value > 32 && $value < 127))
    $result .= $ch;
    else
    $result .= " ";
  }
  return $result;
}

function uniord($c) {
  $h = ord($c);
  if ($h <= 0x7F) {
    return $h;
  } else if ($h < 0xC2) {
    return false;
  } else if ($h <= 0xDF) {
    return ($h & 0x1F) << 6  | (ord($c{1}) & 0x3F);
  } else if ($h <= 0xEF) {
    return ($h & 0x0F) << 12 | (ord($c{1}) & 0x3F) << 6
    | (ord($c{2}) & 0x3F);
  } else if ($h <= 0xF4) {
    return ($h & 0x0F) << 18 | (ord($c{1}) & 0x3F) << 12
    | (ord($c{2}) & 0x3F) << 6
    | (ord($c{3}) & 0x3F);
  } else {
    return false;
  }
}
?>