package org.werelate.source;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.editor.PageEditor;

import java.util.Set;
import java.util.HashSet;
import java.lang.Integer;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.FileInputStream;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class PrintCategories  extends StructuredDataParser {
   private FileWriter writer;
   private int numPages;
   private Hashtable<String,Integer> numCategories;
   private FileWriter errWriter; 
   private static final Pattern TITLENO = Pattern.compile("www\\.familysearch\\.org.*?titleno=([0123456789]+)");
   private static final Pattern CATEGORY = Pattern.compile("\\[\\[Category:([^\\]]*)\\]\\]");
   public PrintCategories() {
      super();
      numPages = 0;
      numCategories = new Hashtable<String, Integer>();
   }

   public void printResults(){
   Iterator<String> myIter = numCategories.keySet().iterator();
   System.out.println("There are " + numPages + " source that have at least two categories");
   while(myIter.hasNext()){
      String category = myIter.next();
      System.out.println(category + " happens "+ numCategories.get(category).intValue() + " times");
   }
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      boolean increment = false;
      if(title.startsWith("Source:")){ 
         Matcher m = TITLENO.matcher(text);
         if(!m.find())
            return;
         m = CATEGORY.matcher(text);
         boolean doubleCat = false;
         boolean alreadyCounted = false;
         String prevTry = "";
         while(m.find()){
            if(doubleCat){
               String current = prevTry + '+' + m.group(1);
               if(numCategories.containsKey(current))
                  numCategories.put(current,new Integer(numCategories.get(current).intValue()+1));
               else
                  numCategories.put(current, new Integer(1));

               if(!alreadyCounted){
                  alreadyCounted = true;
                  numPages++;
               }
            }
            else{
               doubleCat = true;
            }
            prevTry = m.group(1);
            if(numCategories.containsKey(prevTry))
               numCategories.put(prevTry,new Integer(numCategories.get(prevTry).intValue()+1));
            else
               numCategories.put(prevTry, new Integer(1));
         }

      }
   }



  public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length < 1) {
         System.out.println("Usage: <pages.xml file>");
      }
      else {
         PrintCategories surnames = new PrintCategories();
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(surnames);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         surnames.printResults();
       //  surnames.close();   
      }
   }
}  
