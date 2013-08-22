package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.utils.CountsCollector;

import java.io.*;
import java.net.URLEncoder;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class AnalyzeSources extends StructuredDataParser
{
   public AnalyzeSources() throws IOException
   {
   }

   private static String[] SOURCE_TYPE_ARRAY = {"Article", "Book", "Government / Church records", "Newspaper", "Periodical",
                                                "Miscellaneous", "Website", "MySource"};
   private static Set<String> SOURCE_TYPES = new HashSet<String>();
   static {
      for (String sourceType : SOURCE_TYPE_ARRAY) SOURCE_TYPES.add(sourceType);
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();

            String unprefixedTitle = title.substring("Source:".length());

            String sourceType = "";
            Element elm = root.getFirstChildElement("source_type");
            if (elm != null) {
               sourceType = elm.getValue();
            }
            if (!"".equals(sourceType) && !SOURCE_TYPES.contains(sourceType)) {
               logger.warn("Invalid type: ("+sourceType+"): "+title);
            }

            elm = root.getFirstChildElement("ethnicity");
            if (elm != null) {
               System.out.println("ETHNICITY "+unprefixedTitle);
            }
            elm = root.getFirstChildElement("religion");
            if (elm != null) {
               System.out.println("RELIGION "+unprefixedTitle);
            }
            elm = root.getFirstChildElement("occupation");
            if (elm != null) {
               System.out.println("OCCUPATION "+unprefixedTitle);
            }
         }
      }
   }

   // pages.xml wlhSources.txt authors.txt ancestryUrls.txt reviewTitles.html periodicals.txt deleteTitles.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      AnalyzeSources as = new AnalyzeSources();
      wikiReader.addWikiPageParser(as);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
   }
}
