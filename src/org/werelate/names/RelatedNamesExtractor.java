package org.werelate.names;

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;

public class RelatedNamesExtractor extends StructuredDataParser {
   private String currentPagePrefix;
   private PrintWriter givennameWriter;
   private PrintWriter surnameWriter;

   public RelatedNamesExtractor(String givennames, String surnames) throws FileNotFoundException {
      super();
      givennameWriter = new PrintWriter(givennames);
      surnameWriter = new PrintWriter(surnames);
   }

   public void close() throws IOException{
      givennameWriter.close();
      surnameWriter.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      Element root = null;
      PrintWriter writer = null;
      if (title.startsWith("Givenname:")) {
         String[] split = splitStructuredWikiText("givenname", text);
         if (!Util.isEmpty(split[0])) {
            title = title.substring("Givenname:".length());
            root = parseText(split[0]).getRootElement();
            writer = givennameWriter;
         }
      }
      else if (title.startsWith("Surname:")) {
         String[] split = splitStructuredWikiText("surname", text);
         if (!Util.isEmpty(split[0])) {
            title = title.substring("Surname:".length());
            root = parseText(split[0]).getRootElement();
            writer = surnameWriter;
         }
      }

      if (root != null) {
         Elements links = root.getChildElements("related");
         for (int i = 0; i < links.size(); i++) {
            Element link = links.get(i);
            String name = Util.translateHtmlCharacterEntities(link.getAttributeValue("name"));
            writer.println(name + "," + title);
         }
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 2) {
         System.out.println("Usage: <pages.xml file> <givennames out> <surnames out>");
      }
      else {
         RelatedNamesExtractor extractor = new RelatedNamesExtractor(args[1], args[2]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(extractor);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         extractor.close();
      }
   }
}
