package org.werelate.places;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.editor.PageEditor;
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

public class fhlcToWikiPlaceLinker extends StructuredDataParser{
   private static Logger logger = Logger.getLogger("org.werelate.places");

   private Hashtable<String,String> fhlcPlaceToNum;
   private Hashtable<String,String> numToWikiPlace;
   private Hashtable<String,String> redirects;
   private HashSet<String> titles;
   private HashSet<String> settledIds;
   private Hashtable<String, String> badPlaces;
   private Hashtable<String,String> cityCountry;
   private static final Pattern REDIRECT_PATTERN1 = Pattern.compile("#redirect\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
 private static final Pattern PLACEID = Pattern.compile("^([^|]+)\\|([0-9]+)$");

   private static final Pattern MULTIPLEID = Pattern.compile("([0-9]+)(<>)?");

   public fhlcToWikiPlaceLinker() {
      super();
      fhlcPlaceToNum = new Hashtable<String,String>();
      numToWikiPlace = new Hashtable<String,String>();
      redirects = new Hashtable<String,String>();
      titles = new HashSet<String>();
      cityCountry = new Hashtable<String,String>();
      settledIds = new HashSet<String>();  
      badPlaces = new Hashtable<String,String>();
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
         Matcher m = MULTIPLEID.matcher(idNum);
         if(m.find()){
            String realId = m.group(1);
            numToWikiPlace.put(realId, title);
            while(m.find()){
               realId = m.group(1);
               numToWikiPlace.put(realId, title);
            }
         }
         else{
            numToWikiPlace.put(idNum,title);
         }
      }
      readIn.close();
   }


   public void loadMissingPlaceHash(String fhlcPlace) throws IOException{
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
            int comma = place.lastIndexOf(',');
            if(comma != -1){
            if(place.contains(", Ireland")){
               fhlcPlaceToNum.put(place.replace(", Ireland", ", Republic of Ireland"),idnum);
               place = place.replace(", Ireland", ", Northern Ireland");
            }
            }
            fhlcPlaceToNum.put(place,idnum);
         }
         else{
            System.out.println("********************** error in fhlc map file *******************************" + currentLine);
         }

      }
      readIn.close();
   }
/**
 * There are several cases to try and test
 * 1. is if there is and identical name in the wiki
 * 2. remove any parentheses they tend to be a clarification and not part of the name
 * 3. last check all id +-2 and write these options out to a file to be manually reviewed
 * 4. check if it contains Korea or Ireland split it into two parts and check both 
 * **/
   public void linkPlaces(String resultFile) throws IOException {
      FileWriter placeChangesWriter = new FileWriter(resultFile);
      Iterator<String> myIter = fhlcPlaceToNum.keySet().iterator();
      while(myIter.hasNext()){
         String place = myIter.next();
         String num = fhlcPlaceToNum.get(place);
         if(settledIds.contains(num))
            continue;
         if(titles.contains(place)){
            settledIds.add(num);
            placeChangesWriter.write(appendCountry(chainedRedirect(appendCountry(place))) + '|' + num + '\n');
            continue;
         }
         int first = place.indexOf('(');
          int second = place.indexOf(')');
         while(first != -1 && second != -1){
            if(second != -1){
               if(second < place.length() -1 && first < second){
                if(first != 0){
                   place = place.substring(0,first -1) + place.substring(second + 1);
                   //System.out.println(place);
                   }
                else
                   place = place.substring(second + 1);

                }
               else{
                  place = place.substring(0,first -1);
               }
               first = place.indexOf('(');
               second = place.indexOf(')');
              if(titles.contains(place)){
                  settledIds.add(num);
                  placeChangesWriter.write(appendCountry(chainedRedirect(appendCountry(place))) + '|' + num + '\n');
                  continue;
               }
            }
         }
         if(!settledIds.contains(num)){
         String result = titlesCheck(place);
         if(!result.equals("")){
            settledIds.add(num);
                placeChangesWriter.write(appendCountry(chainedRedirect(appendCountry(result)))+ '|' + num + '\n');
            continue;
            }
      }


            badPlaces.put(num, place);
      }
   placeChangesWriter.close();      
   }

   public void writeErrors(String errFile) throws IOException{
      FileWriter errPlaceWriter = new FileWriter(errFile);
      Iterator<String> myIter = badPlaces.keySet().iterator();
      String wikiPlace = "";
      while(myIter.hasNext()){
         String id = myIter.next();
         if(settledIds.contains(id))
            continue;
         wikiPlace = appendCountry(badPlaces.get(id));
      
         errPlaceWriter.write(wikiPlace + '|' + id + '\n');
      }
      errPlaceWriter.close();
   }
   
   
   public String titlesCheck(String place){
   int first = place.indexOf(',');
   int last = place.lastIndexOf(',');
   if(first == -1){
     return "";  
   }
   String simplePlace = place.substring(0,first) + ", " + place.substring(last + 1);
   if(cityCountry.containsKey(simplePlace)){
      String result = cityCountry.get(simplePlace);
      if(place.equals(result))
         return result;
  /*    String tempPlace = place.substring(first + 1);
      String tempResult = result.substring(first + 1);
      first = tempPlace.indexOf(',');
      if(first != -1 && first == tempResult.indexOf(',')){
         if(tempPlace.substring(0,first).equals(tempResult.substring(0,first)))
               return result;
      }
      tempPlace = place.substring(0,last);
      tempResult = result.substring(0,result.lastIndexOf(','));
      last = tempPlace.lastIndexOf(',');
      if(last != -1 ){
         if(tempPlace.substring(last + 1).equals(tempResult.substring(tempResult.lastIndexOf(',') + 1)))
               return result;
      }*/
      return cityCountry.get(simplePlace)/* + "<>" + place*/;
      }
   return "";
   }

     

    public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      
      if(title.startsWith("Place:")){
         title = title.substring("Place:".length());
         if(title.contains(", United States")){
            titles.add(title);
            title = title.substring(0, title.length() - 15);  
            }
         if(title.contains(", Canada")){
            titles.add(title);
            title = title.substring(0, title.length() - 8);
            }
         if(title.contains(", Ireland")){
            titles.add(title.substring(0, title.length() - 9) + ", Northern Ireland");
              
            title = title.substring(0, title.length() - 9) + ", Republic of Ireland";
            }

         
         int comma = title.indexOf(',');
         if(comma != -1){
            cityCountry.put(title.substring(0,comma) + ", " + title.substring(title.lastIndexOf(',') + 1), title);
         }
         titles.add(title);
         Matcher m = REDIRECT_PATTERN1.matcher(text);
         if (m.lookingAt()) {
            String redirTarget = Util.translateHtmlCharacterEntities(m.group(1));
            redirTarget = redirTarget.substring("Place:".length());
            redirects.put(title,redirTarget);   
         }
      }
   }

   public String appendCountry(String wikiPlace){
    if(wikiPlace.equals("Wentworth, Ontario"))
      System.err.println("big stuff happenin" + wikiPlace + '-');
    if(titles.contains(wikiPlace +", Canada"))
            wikiPlace =wikiPlace + ", Canada";
    if(titles.contains(wikiPlace +", United States"))
            wikiPlace =wikiPlace + ", United States";
    if(wikiPlace.startsWith("Wentworth, On"))
      System.err.println("what happenend you say "+wikiPlace + '-');
   return wikiPlace;
   }

   public String chainedRedirect(String original){
      String temp = original;
      while(redirects.containsKey(temp))
         temp = redirects.get(temp);
      if(original.equals(temp) || temp == null || "".equals(temp))
         return original;
      return temp;

   }

   
   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 1) {
         System.out.println("Usage: <pages file> <fhlc-Places> <wikPlaceToNumMap> <pagesToEdit> <needManualInspectionPages>");
      }
      else {
         fhlcToWikiPlaceLinker fhlc = new fhlcToWikiPlaceLinker();
         fhlc.loadMissingPlaceHash(args[1]);
         fhlc.loadWikiPlaceHash(args[2]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         wikiReader.addWikiPageParser(fhlc);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();

         fhlc.linkPlaces(args[3]);
         fhlc.writeErrors(args[4]);
      }
   }
}
