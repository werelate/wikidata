package org.werelate.source;

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

public class MapTitleToNum  extends StructuredDataParser {
   private FileWriter writer;
   private FileWriter errWriter; 
   public MapTitleToNum() {
      super();
   }

   public void openFile(String file) throws IOException{
      writer = new FileWriter(file); 
   }

   public void openErrFile(String file) throws IOException{
      errWriter = new FileWriter(file);
   }

   public void close() throws IOException{
      writer.close();
      errWriter.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if(title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         title = title.substring("Source:".length());
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(split[0]);
            Element elm = doc.getRootElement();

            StringBuilder url = new StringBuilder();
            Elements urls = elm.getChildElements("url"); // old format
            for (int i = 0; i < urls.size(); i++) {
               url.append(Util.translateHtmlCharacterEntities(urls.get(i).getValue()));
            }
            Elements repositories = elm.getChildElements("repository"); // new format
            for (int i = 0; i < repositories.size(); i++) {
               String repoUrl = repositories.get(i).getAttributeValue("source_location");
               if (!Util.isEmpty(repoUrl)) {
                  url.append(Util.translateHtmlCharacterEntities(repoUrl));
               }
            }

            Matcher m = SourceUtil.TITLENO.matcher(url.toString());
            boolean found = false;
            while (m.find()) {
               String titleno = m.group(1);
               if (found) {
                  errWriter.write(titleno + "|" + title + '\n');
               }
               else {
                  writer.write(titleno + "|" + title + '\n');
               }
               found = true;
            }
         }
      }
   }


  public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 1) {
         System.out.println("Usage: <pages.xml file> <output TitleNoMapFile> <output TwoMappingErrorFile>");
      }
      else {
         MapTitleToNum sources = new MapTitleToNum();
         sources.openFile(args[1]);
         sources.openErrFile(args[2]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(sources);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         sources.close();
      }
   }
}
