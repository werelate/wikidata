package org.werelate.scripts;

/**
 * User: dallan
 * Date: 11/19/15
 */

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.CountsCollector;
import org.werelate.utils.Util;

import java.io.*;

public class CountEventTypes extends StructuredDataParser
{
   public CountsCollector cc;

   public CountEventTypes() {
      cc = new CountsCollector();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Person:") || title.startsWith("Family:")) {
         String[] split = splitStructuredWikiText(title.startsWith("Person:") ? "person" : "family", text);
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();
            Elements eventFacts = root.getChildElements("event_fact");
            for (int i = 0; i < eventFacts.size(); i++) {
               Element eventFact = eventFacts.get(i);
               String type = eventFact.getAttributeValue("type");
               System.out.println(type);
               cc.add(type);
            }
         }
      }
   }

   // Generate list of event_fact types
   // 0=pages.xml 1=types.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      CountEventTypes p = new CountEventTypes();
      wikiReader.addWikiPageParser(p);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      p.cc.writeSorted(false, 0, new PrintWriter(new FileWriter(args[1])));
   }
}
