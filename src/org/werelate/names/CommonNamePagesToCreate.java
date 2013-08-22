package org.werelate.names;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.editor.PageEditor;

import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class CommonNamePagesToCreate  extends StructuredDataParser {
   private String currentPagePrefix;
   private FileWriter writer; 
   private Hashtable<String,Set<String>> names_Links = new Hashtable<String,Set<String>>(20000);
   public CommonNamePagesToCreate() {
      super();
   }

   private String clean(String s) {
      return s.toLowerCase().replaceAll("[ .']+", "");
   }

   public void loadCommonNames(String fileName) throws IOException{
      BufferedReader readIn = new BufferedReader(new FileReader(fileName));
      while(readIn.ready()){
         String name = clean(Util.romanize(readIn.readLine()));
         names_Links.put(name, new HashSet<String>());
      }
      readIn.close();
   }

   public void openFile(String file) throws IOException{
      writer = new FileWriter(file); 
   }

   public void writeFile() throws IOException{
      Set KeySet = names_Links.keySet();
      Iterator KeyIter = KeySet.iterator();
      Iterator LinkedToIter;
      while(KeyIter.hasNext()){
         String title = (String)KeyIter.next();
         LinkedToIter = names_Links.get(title).iterator();
         writer.write(currentPagePrefix + Util.toMixedCase(title) + "=");
         while(LinkedToIter.hasNext())
            writer.write((String)LinkedToIter.next() + "|");
         writer.write('\n');
      }
   }

   public void setPrefix(String newPrefix){
      currentPagePrefix = newPrefix;
   }

   public void close() throws IOException{
      writer.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if(title.startsWith(currentPagePrefix)) {
         String[] split = splitStructuredWikiText(currentPagePrefix.toLowerCase().substring(0,currentPagePrefix.indexOf(':')), text);
         title = title.substring(currentPagePrefix.length());
         String structuredData = split[0];
         String wikiText = split[1];
         if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(split[0]);
            Element elm = doc.getRootElement();

            Elements prevParents = elm.getChildElements("related");
            for (int i = 0; i < prevParents.size(); i++) {
               Element prevParent = prevParents.get(i);
               String name = Util.translateHtmlCharacterEntities(prevParent.getAttributeValue("name"));
               if(name != null){//shouldn't happen unless some incorrect xml
                  String key = clean(name);
                  if(names_Links.containsKey(key)){
                     Set<String> temp = names_Links.get(key);
                     temp.add(title);
                  }
               }
            }
         }
         names_Links.remove(clean(title));
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 3) {
         System.out.println("Usage: <pages.xml file> <Prefix to do> <output NameFile> <Common Names>");
      }
      else {
         CommonNamePagesToCreate surnames = new CommonNamePagesToCreate();
         surnames.setPrefix(args[1]);
         surnames.loadCommonNames(args[3]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(surnames);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         surnames.openFile(args[2]);
         surnames.writeFile();
         surnames.close();   
      }
   }
}
