package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.CountsCollector;
import org.werelate.utils.Util;

import java.io.*;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class AnalyzePlaces extends StructuredDataParser
{
   public CountsCollector cc;

   public AnalyzePlaces() {
      cc = new CountsCollector();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Place:")) {
         String[] split = splitStructuredWikiText("place", text);
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();
            Element elm = root.getFirstChildElement("type");
            if (elm != null) {
               String type = elm.getValue();
               cc.add(type);
            }
         }
      }
   }

   // Generate various lists of places
   // 0=pages.xml 1=types.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      AnalyzePlaces ap = new AnalyzePlaces();
      wikiReader.addWikiPageParser(ap);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      ap.cc.writeSorted(false, 0, new PrintWriter(new FileWriter(args[1])));
   }
}
