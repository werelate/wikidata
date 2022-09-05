package org.werelate.places;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

public class placeResultVerify extends StructuredDataParser{
   private static Logger logger = LogManager.getLogger("org.werelate.places");

   private HashSet<String> titles;

   public placeResultVerify() {
      super();
      titles = new HashSet<String>();
   }


     public void checkPlaces(String toUpdate) throws IOException{
      File wiki = new File(toUpdate);
      if(!wiki.exists()){
         System.out.println(toUpdate + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(wiki));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         String title = currentLine.substring(0,currentLine.indexOf('|'));
         if(!titles.contains(title)) 
            System.err.println(currentLine + " has a place that does not seem to be in the placeId file");
         }
      readIn.close();
   }
   
   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException{
   if(title.startsWith("Place:")){
     title = title.substring("Place:".length());
      titles.add(title);
   }

   }
 

   
   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 1) {
         System.out.println("Usage: <placeID file> <placesUpdate> <pages file>");
      }
      else {
         placeResultVerify fhlc = new placeResultVerify();
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         wikiReader.addWikiPageParser(fhlc);
         InputStream in = new FileInputStream(args[2]);
         wikiReader.read(in);
         in.close();

        
        fhlc.checkPlaces(args[1]);
      }
   }
}
