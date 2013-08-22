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

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

/**
 * Extract surname/givenname page titles from pages.xml
 */
public class NameParser  extends StructuredDataParser {
   private String currentPagePrefix;
   private FileWriter writer; 

   public NameParser() {
      super();
   }

   public void openFile(String file) throws IOException{
      writer = new FileWriter(file); 
   }

   public void setPrefix(String newPrefix){
      currentPagePrefix = newPrefix;
   }

   public void close() throws IOException{
      writer.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if(title.startsWith(currentPagePrefix)) {
         title = title.substring((currentPagePrefix).length());   
         writer.write(title + "\n");
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 2) {
         System.out.println("Usage: <pages.xml file> <Prefix to do> <output NameFile>");
      }
      else {
         NameParser surnames = new NameParser();
         surnames.openFile(args[2]);
         surnames.setPrefix(args[1]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(surnames);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         surnames.close();   
      }
   }
}
