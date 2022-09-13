package org.werelate.source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

public class MapPlaceToNum  extends StructuredDataParser {
   private FileWriter writer;
   private FileWriter errWriter; 
   private static final Pattern PLACEID = Pattern.compile("source-fhlc\\|([0123456789]+)", Pattern.CASE_INSENSITIVE);
   private static final Pattern ALTPLACEID = Pattern.compile("link-fhlc\\|([0-9]+)\\|", Pattern.CASE_INSENSITIVE);
   public MapPlaceToNum() {
      super();
   }

   public void openFile(String file) throws IOException{
      writer = new FileWriter(file); 
   }

   public void close() throws IOException{
      writer.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (title.startsWith("Place:")) {
         title = Util.translateHtmlCharacterEntities(title.substring("Place:".length()));
         Matcher m = PLACEID.matcher(text);
         while (m.find()) {
            String placeid = m.group(1);
            writer.write(placeid + "|" + title + '\n');
         }
         m = ALTPLACEID.matcher(text);
         while (m.find()) {
            String idNum = m.group(1);
            writer.write(idNum + '|' + title + '\n');
         }
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 1) {
         System.out.println("Usage: <pages.xml file> <output PlaceMapFile> ");
      }
      else {
         MapPlaceToNum surnames = new MapPlaceToNum();
         surnames.openFile(args[1]);
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
