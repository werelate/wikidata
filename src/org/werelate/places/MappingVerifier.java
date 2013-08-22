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

public class MappingVerifier{
   private static Logger logger = Logger.getLogger("org.werelate.source");

   private FileWriter errPlaceWriter;
   private FileWriter placeChangesWriter;
   private Hashtable<String,String> fhlcPlaceToNum;
   private Hashtable<String,String> numToWikiPlace;
   private Hashtable<String,Set<String>> wikiSourceToPlace;
   private Hashtable<String,String> redirects;
   private Hashtable<String,String> titles;
   private static final Pattern REDIRECT_PATTERN1 = Pattern.compile("#redirect\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
   private static final Pattern TITLENO = Pattern.compile("www\\.familysearch\\.org.*?titleno=([0123456789]+)");
   private static final Pattern CATEGORY = Pattern.compile("\\[\\[Category:([^\\]]*)\\]\\]");

   private static final Pattern START = Pattern.compile("<source id\\s*=\\s*([0-9]+)\\s*>");
   private static final Pattern END = Pattern.compile("</source>");
   private static final Pattern MAINAUTHOR = Pattern.compile("<Main Author>([^<]*)</Main Author>");
   private static final Pattern PLACE = Pattern.compile("<place>([^<]+)</place>");
   private static final Pattern PLACEID = Pattern.compile("^([^|]+)\\|([0-9]+)$");
   private static final Pattern SUBJECTPLACE = Pattern.compile("<Subjects>([^-<]*)");
   private static final Pattern TITLE = Pattern.compile("<Title>([^<]*)</Title>");
   private static final Pattern ALSOTITLE = Pattern.compile("<Title Also Known As>([^<]*)</Title Also Known As>");
   private static final Pattern FORMAT = Pattern.compile("<Format>([^<]*)</Format>");
   private static final Pattern PAIR = Pattern.compile("<([a-zA-Z0-9]*\\s*)>([^<]*)</\\1>"); 
   private static final Pattern MULTIPLEID = Pattern.compile("([0-9]+)(<>)?");
   public MappingVerifier() {
      super();
      fhlcPlaceToNum = new Hashtable<String,String>();
      numToWikiPlace = new Hashtable<String,String>();
      wikiSourceToPlace = new Hashtable<String,Set<String>>();
      redirects = new Hashtable<String,String>();
      titles = new Hashtable<String,String>();

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
            fhlcPlaceToNum.put(place,idnum);
         }
         else{
            System.out.println("********************** error in fhlc map file *******************************" + currentLine);
         }

      }
      readIn.close();
   }

   public void checkPlaces(){
   Iterator<String> myIter = fhlcPlaceToNum.keySet().iterator();
   while(myIter.hasNext()){
   String place = myIter.next();
   String num = fhlcPlaceToNum.get(place);
   if(num == null || "".equals(num)){
      System.out.println("*********error with " + place + " missing from fhlc");
      continue;
      }
   String wikiPlace = numToWikiPlace.get(num);
   if(wikiPlace == null || "".equals(wikiPlace))
      System.out.println(place + '|'+ num);
   }

   }

   
   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 1) {
         System.out.println("Usage: <fhlc-Places> <wikPlaceToNumMap>");
      }
      else {
         MappingVerifier fhlc = new MappingVerifier();
         fhlc.loadFhlcPlaceHash(args[0]);
         fhlc.loadWikiPlaceHash(args[1]);
        fhlc.checkPlaces(); 

      }
   }
}
