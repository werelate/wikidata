package org.werelate.source;

import org.apache.log4j.Logger;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;

import java.lang.Integer;
import java.util.Set;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class PlaceVerifier extends StructuredDataParser{
   private static Logger logger = Logger.getLogger("org.werelate.source");

   private FileWriter errPlaceWriter;
   private FileWriter placeChangesWriter;
   private Hashtable<String,String> fhlcPlaceToNum;
   private Hashtable<String,String> numToWikiPlace;
   private HashSet<String> wikiPlaceTitles;
   private Hashtable<String,Set<String>> wikiSourceToPlace;
   private Hashtable<String,String> redirects;
   private Hashtable<String,String> titles;
   private HashSet<String> exceptionIds;

   private static final Pattern PLACE = Pattern.compile("<place>([^<]+)</place>");
   private static final Pattern PLACEID = Pattern.compile("^([^|]+)\\|([0-9]+)$");
//   private static final Pattern MULTIPLEID = Pattern.compile("([0-9]+)(<>)?");
   private static final Set KNOWN_CHANGES = new HashSet<String>();
   static {
      KNOWN_CHANGES.add("City of Bradford, West Yorkshire");
      KNOWN_CHANGES.add("Kastrup, Copenhagen County, Denmark");
      KNOWN_CHANGES.add("Kingston upon Hull (unitary authority), England");
      KNOWN_CHANGES.add("Metropolitan Borough of Doncaster, South Yorkshire");
      KNOWN_CHANGES.add("Serbia and Montenegro");
      KNOWN_CHANGES.add("Yorkshire and the Humber, England");
      KNOWN_CHANGES.add("City of Edinburgh, Scotland");
      KNOWN_CHANGES.add("Greater London, England");
      KNOWN_CHANGES.add("City of Glasgow, Scotland");
      KNOWN_CHANGES.add("City of Leeds, West Yorkshire");
      KNOWN_CHANGES.add("City of Aberdeen, Scotland");
      KNOWN_CHANGES.add("Allegany (town), Cattaraugus County, New York");
      KNOWN_CHANGES.add("Slovakia");
      KNOWN_CHANGES.add("Coesfeld (district), North Rhine-Westphalia");
      KNOWN_CHANGES.add("Croydon, Surrey");
      KNOWN_CHANGES.add("London Borough of Tower Hamlets, England");
      KNOWN_CHANGES.add("Ceredigion, Wales");
      KNOWN_CHANGES.add("Bombay, India");
      KNOWN_CHANGES.add("Stirling (district), Scotland");
      KNOWN_CHANGES.add("Frankfurt, Brandenburg");
      KNOWN_CHANGES.add("Christchurch, New Zealand");
      KNOWN_CHANGES.add("Dunedin, New Zealand");
      KNOWN_CHANGES.add("Gwynedd, Wales");
      KNOWN_CHANGES.add("Ithaca (town), Tompkins County, New York");
      KNOWN_CHANGES.add("Gera (national district), Thuringia");
      KNOWN_CHANGES.add("Groton (city), New London County, Connecticut");
      KNOWN_CHANGES.add("Metropolitan Borough of Rotherham, South Yorkshire");
      KNOWN_CHANGES.add("City of Wakefield, West Yorkshire");
      KNOWN_CHANGES.add("Middlesbrough (district), North Yorkshire");
   }

   public PlaceVerifier() {
      super();
      exceptionIds = new HashSet<String>();
      fhlcPlaceToNum = new Hashtable<String,String>();
      numToWikiPlace = new Hashtable<String,String>();
      wikiSourceToPlace = new Hashtable<String,Set<String>>();
      redirects = new Hashtable<String,String>();
      titles = new Hashtable<String,String>();
      wikiPlaceTitles = new HashSet<String>();
   }

   public void loadExceptionSet(String filename) throws IOException{
      File excep = new File(filename);
      if(!excep.exists()){
         System.err.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(excep));
      while(readIn.ready()){
         exceptionIds.add(readIn.readLine());
      }
      readIn.close();
   }

   public void openWriters(String goodPlaces,String badPlaces) throws IOException{
      placeChangesWriter = new FileWriter(goodPlaces);
      errPlaceWriter = new FileWriter(badPlaces);
      errPlaceWriter.write("<html>\n<body>\n<ol>");
   }

   public void closeWriters() throws IOException{
      placeChangesWriter.close();
      errPlaceWriter.write("</ol>\n</body>\n</html>");
      errPlaceWriter.close();

   }

   //this removes some erroneous paren where the first exists and the second doesn't
   public String fixNames(String toFix){
      int paren = toFix.indexOf('(');
      int backParen = toFix.indexOf(')');
      if(backParen < paren){
         int comma = toFix.indexOf(',');
         if(comma < toFix.length()){
            StringBuilder sb = new StringBuilder(toFix);
            if(comma == -1){
               comma = toFix.length() -1;
            }
            sb.insert(comma,')');
            return sb.toString();
         }
      }
      return toFix;
   }

   public Set<String> findValidPlaces(String fhlcPlaceName){
      String wikiPlaceName = "";
      Set<String> returnValues = new HashSet<String>();
      String temp = "";
      if(fhlcPlaceName.endsWith("Ireland")){
         //note on some places we need to retain the Ireland tag and it is transfered
         //When we use the id's
         if(fhlcPlaceName.length() > "Ireland".length()){
            String tempor = isValidFhlc(fhlcPlaceName);
            if(!tempor.equals("")){
               returnValues.add(tempor);
            }
         }

         String intermediate = fhlcPlaceName.replaceAll("Ireland","Northern Ireland");
         if(wikiPlaceTitles.contains(intermediate))
            returnValues.add(chainedRedirect(intermediate));
         intermediate = fhlcPlaceName.replaceAll("Ireland","Republic of Ireland");
         if(wikiPlaceTitles.contains(intermediate))
            returnValues.add(chainedRedirect(intermediate));
         return returnValues;
      }
      if(fhlcPlaceName.endsWith("Great Britain")){
         String intermediate = fhlcPlaceName.replaceAll("Great Britain","England");
         temp = isValidFhlc(intermediate);
         if(!temp.equals(""))
            returnValues.add(chainedRedirect(temp));
         intermediate = fhlcPlaceName.replaceAll("Great Britain","Scotland");
         temp = isValidFhlc(intermediate);
         if(!temp.equals(""))
            returnValues.add(chainedRedirect(temp));
         intermediate = fhlcPlaceName.replaceAll("Great Britain","Wales");
         temp = isValidFhlc(intermediate);
         if(!temp.equals(""))
            returnValues.add(chainedRedirect(temp));
         return returnValues;
      }

      temp = isValidFhlc(fhlcPlaceName);
      if(!temp.equals(""))
         returnValues.add(chainedRedirect(temp));


      return returnValues;
   }

   public String isValidFhlc(String fhlcPlaceName){
    String fhlcPlaceId = fhlcPlaceToNum.get(fhlcPlaceName);
    String wikiPlaceName = "";
    if(fhlcPlaceId != null){
         wikiPlaceName = numToWikiPlace.get(Integer.parseInt(fhlcPlaceId) + "");
    if(wikiPlaceName != null && !"".equals(wikiPlaceName)){
         return wikiPlaceName;
      }
      else{
      System.err.println("missing wikiPlaceName " + fhlcPlaceName + '-' + fhlcPlaceId);
      }
      }
      else{
         System.err.println("missing fhlcPlaceId " + fhlcPlaceName);
      }
   return "";
   }

   public void checkPlaces(String currentPage) throws IOException{
      Set<String> wikiToPlacesSet;
      Set<String> fhlcToPlacesSet;
      String fhlcSourceId;
      String fhlcPlaceName;
      String fhlcPlaceId;
      String wikiPlaceName = "";
      String urlLink = "<a href=\"http://www.werelate.org/wiki/";
      Matcher startTagMatcher = SourceUtil.START.matcher(currentPage);
      Matcher fhlcPlacesMatcher = PLACE.matcher(currentPage);
      if(!startTagMatcher.find()){
         System.out.println("error in checkPlaces");
         return;
      }
      fhlcSourceId = startTagMatcher.group(1);
      if(fhlcSourceId == null){
         System.out.println(currentPage);
         throw new IOException();//not really io but close enough  
      }
      wikiToPlacesSet = wikiSourceToPlace.get(fhlcSourceId);
      fhlcToPlacesSet = new HashSet<String>();
      if(wikiToPlacesSet == null){
         //new sources we are ignoring these
         return;
      }
      // this loop populates fhlcToPlacesSet
      while(fhlcPlacesMatcher.find()){
         fhlcPlaceName = Util.unencodeXML(fhlcPlacesMatcher.group(1));
         if(fhlcPlaceName.indexOf('(')!= -1){
            fhlcPlaceName = fixNames(fhlcPlaceName).trim();
         }
         fhlcToPlacesSet.addAll(findValidPlaces(Util.romanize(fhlcPlaceName)));

        }
      //this is for any exception that we want to rollover old places
      if(exceptionIds.contains(fhlcSourceId)){
         Iterator<String> wikiIter = wikiToPlacesSet.iterator();
         while(wikiIter.hasNext()){
            String rolloverPlace = chainedRedirect(wikiIter.next());
            if(rolloverPlace != null && !("").equals(rolloverPlace)){
               if(!fhlcToPlacesSet.contains(rolloverPlace)){
                  fhlcToPlacesSet.add(chainedRedirect(rolloverPlace));
               }
            }
         }
      }
      //special exception
      if(wikiToPlacesSet.contains("Hong Kong"))
         fhlcToPlacesSet.add("Hong Kong");
      //Once it fails and going into here I am going to subject it to some new tests to try and narrow things down further
      if(!setIsSubSet(wikiToPlacesSet, fhlcToPlacesSet) ){
         Iterator<String> wikiLinks = wikiToPlacesSet.iterator();
         Iterator<String> fhlcLinks = fhlcToPlacesSet.iterator();
         boolean written = false;
         while(wikiLinks.hasNext() || fhlcLinks.hasNext()){
            String currentWikiLink = "";
            String currentFhlcLink = "";
            if(wikiLinks.hasNext())
               currentWikiLink = wikiLinks.next();
            if(fhlcLinks.hasNext())
               currentFhlcLink = fhlcLinks.next();
            if (!written && !fhlcLinks.hasNext() && !wikiLinks.hasNext() && KNOWN_CHANGES.contains(currentWikiLink)) {
               break;
            }
            if (!written) {
               errPlaceWriter.write("<li>"+ urlLink +"Source:" + titles.get(fhlcSourceId)+ "\" > " +titles.get(fhlcSourceId) + "</a>\n");
               errPlaceWriter.write("<pre>|" + fhlcSourceId +"|\n</pre>");
            }
            written = true;
            errPlaceWriter.write(urlLink + "Place:" +currentWikiLink+"\" >\"" + currentWikiLink + "\"</a>\n");
            errPlaceWriter.write("-----");
            errPlaceWriter.write(urlLink + "Place:" +currentFhlcLink+"\" >\"" + currentFhlcLink + "\"</a>\n");
            errPlaceWriter.write("<pre>\n</pre>");
         }
      }
         Iterator<String> placeIter = fhlcToPlacesSet.iterator();
         while(placeIter.hasNext()){
            placeChangesWriter.write(fhlcSourceId+ "|place|" + placeIter.next()+"\n");
         }
   }
   /* this method is used to verify that no wiki places are being removed*/
   public boolean setIsSubSet(Set<String> subSet, Set<String> superSet){
      Iterator<String> myIter = subSet.iterator();
      Iterator<String> superIter = superSet.iterator();
      String wikiLink;
      String fhlcVersionWikiLink;
      String convertedLink = "";
      String newLink = "";
      boolean hook = true;
      while(myIter.hasNext()){
         wikiLink = myIter.next();
         if(superSet.contains(wikiLink))
            continue;
         if(superSet.contains(chainedRedirect(wikiLink)))
            continue;
         if(!wikiPlaceTitles.contains(chainedRedirect(wikiLink))){
            continue;
         }
         fhlcVersionWikiLink = fhlcPlaceToNum.get(Util.romanize(wikiLink));
         if(fhlcVersionWikiLink != null)
            convertedLink = numToWikiPlace.get(fhlcVersionWikiLink);
         if(convertedLink != null && !"".equals(convertedLink) && superSet.contains(convertedLink))
            continue;
         hook = false;
         wikiLink = Util.romanize(chainedRedirect(wikiLink));
         superIter = superSet.iterator();
         while(superIter.hasNext()){
            newLink = Util.romanize(superIter.next());
            if(newLink.endsWith(wikiLink)){
               hook = true;
               break;
            }
            if(startEndSame(newLink,wikiLink)){
               hook = true;
               break;
            }
         }
         return hook;
      }
      return true;
   }

   public boolean startEndSame(String placeOne,String placeTwo){
      int commaOne = placeOne.indexOf(',');
      int commaTwo = placeTwo.indexOf(',');
      if(commaOne == -1 || commaOne != commaTwo){
         if(placeOne.equals(placeTwo))
            return true;
         return false;
      }
      //checking if the first word is the same
      if(!placeOne.substring(0,commaOne).equals(placeTwo.substring(0,commaTwo)))
         return false;
      commaOne = placeOne.lastIndexOf(',');
      commaTwo = placeTwo.lastIndexOf(',');
      if(commaOne == -1 || commaTwo == -1){
         return false;
      }
      //checking if the first word is the same
      if(!placeOne.substring(commaOne).equals(placeTwo.substring(commaTwo)))
         return false;
      return true;
   }

   public boolean containsPlace(Set<String> values, String place){
      if(place == null || "".equals(place)){
         return false;
      }
      if(values.contains(place))
         return true;
      if(values.contains(chainedRedirect(place)))
         return true;
      return false;
   }

   public String chainedRedirect(String origTitle){
      String newTitle = origTitle;
      while(redirects.containsKey(newTitle)){
         newTitle = redirects.get(newTitle);
         if(newTitle == null){
            System.out.println("******************** error in redirect table*************" + origTitle);
            return origTitle;
         }
      }
      if(newTitle == null)
         return "";
      return newTitle;
   }

   public void loadWikiTitleHash(String titleMap) throws IOException{
      File wiki = new File(titleMap);
      if(!wiki.exists()){
         System.out.println(titleMap + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(wiki));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         String idNum = currentLine.substring(0,currentLine.indexOf('|'));
         String title = currentLine.substring(currentLine.indexOf('|') + 1);
         titles.put(idNum,title);
      }
      readIn.close();

   }

   public void loadWikiPlaceHash(String wikiPlace) throws IOException{
      File wiki = new File(wikiPlace);
      if(!wiki.exists()){
         System.out.println(wikiPlace + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(wiki));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         String idNum = currentLine.substring(0,currentLine.indexOf('|'));
         String title = currentLine.substring(currentLine.indexOf('|') + 1);
         numToWikiPlace.put(Integer.parseInt(idNum) + "",title);
      }
      readIn.close();
   }


   public void loadFhlcPlaceHash(String fhlcPlace) throws IOException{
      File placeFile = new File(fhlcPlace);
      if(!placeFile.exists()){
         System.out.println(fhlcPlace + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(placeFile));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = PLACEID.matcher(currentLine);
         if(m.find()){
            String place = m.group(1);
            String idnum = m.group(2);
            //   fhlcPlaceToNum.put(place,idnum);
            fhlcPlaceToNum.put(Util.romanize(place),idnum);
         }
         else{
            System.out.println("********************** error in fhlc map file *******************************" + currentLine);
         }

      }
      readIn.close();
   }

   public void parseXmlResultFile(String filename)throws IOException{
      File newFile = new File(filename);
      if(!newFile.exists()){
         System.out.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(filename));
      String currentLine = "";
      StringBuilder currentPage = new StringBuilder("");
      Matcher m;
      boolean inSource = false;
      while(readIn.ready()){
         currentLine = readIn.readLine();
         m = SourceUtil.START.matcher(currentLine);
         if(m.find()){//note this assumse correct xml no end tag before start  
            inSource = true;
            currentPage = new StringBuilder("");
            currentPage.append(currentLine);
            currentPage.append('\n');
         }
         else{
            if(inSource){
               currentPage.append(currentLine);
               currentPage.append('\n');
               m = SourceUtil.END.matcher(currentLine);
               if(m.find()){
                  inSource = false;
                  String page = currentPage.toString();
                  checkPlaces(page);
                  currentPage = new StringBuilder("");//clearing it in both cases to a
                  continue;
               }
            }
         }
      }
      readIn.close();
   }


   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if(title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         title = Util.translateHtmlCharacterEntities(title.substring("Source:".length()));
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(split[0]);
            Element root = doc.getRootElement();

            HashSet<String> places = new HashSet<String>(); 
            Elements elms = root.getChildElements("place");
            for (int i = 0; i < elms.size(); i++) {
               Element elm = elms.get(i);
               places.add(Util.translateHtmlCharacterEntities(elm.getValue()));
            }
            String idNum = "";
            StringBuilder url = new StringBuilder("");;
            elms = root.getChildElements("url");
            for (int i = 0; i < elms.size(); i++) { // old format
               Element elm = elms.get(i);
               url.append(Util.translateHtmlCharacterEntities(elm.getValue()));
            }
            Elements repositories = root.getChildElements("repository"); // new format
            for (int i = 0; i < repositories.size(); i++) {
               String repoUrl = repositories.get(i).getAttributeValue("source_location");
               if (!Util.isEmpty(repoUrl)) {
                  url.append(Util.translateHtmlCharacterEntities(repoUrl));
               }
            }

            Matcher m = SourceUtil.TITLENO.matcher(url.toString());
            if(m.find()){
               idNum = m.group(1);
               wikiSourceToPlace.put(idNum,places);
            }
         }
      }
      if(title.startsWith("Place:")){
         Matcher m = SourceUtil.REDIRECT_PATTERN.matcher(text);
         title = title.substring("Place:".length());

         if (m.lookingAt()) {
            String redirTarget = Util.translateHtmlCharacterEntities(m.group(1));
            redirTarget = redirTarget.substring("Place:".length());

            redirects.put(title,redirTarget);   
         }
         wikiPlaceTitles.add(title);
      }
   }


   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 5) {
         System.out.println("Usage: <input xml file dir> <pages.xml> <fhlc-Places> <wikPlaceToNumMap> <wikiTitleToNumMap> <exceptionFile>");
      }
      else {
         PlaceVerifier fhlc = new PlaceVerifier();
         fhlc.openWriters("/data/newPlaceLinks.txt", "/data/errPlaceLinks.html");
         fhlc.loadFhlcPlaceHash(args[2]);
         fhlc.loadWikiPlaceHash(args[3]);
         fhlc.loadWikiTitleHash(args[4]);
         fhlc.loadExceptionSet(args[5]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         wikiReader.addWikiPageParser(fhlc);
         InputStream in = new FileInputStream(args[1]);
         wikiReader.read(in);
         in.close();
         for(int i = 0;i<12200;i++){
            fhlc.parseXmlResultFile(args[0]+ pad(i,5) + "[00-99].xml");
         }

         fhlc.closeWriters();
      }
   }
}
