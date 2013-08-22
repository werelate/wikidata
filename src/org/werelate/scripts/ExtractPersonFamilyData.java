package org.werelate.scripts;

import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;

public class ExtractPersonFamilyData extends StructuredDataParser {
   PrintWriter out;

   public ExtractPersonFamilyData(String outPath) throws IOException {
      out = new PrintWriter(new FileWriter(outPath));
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Person:") || title.startsWith("Family:")) {
         String[] split = splitStructuredWikiText(title.startsWith("Person:") ? "person" : "family", text);
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            out.println(title+"|"+structuredData.replace("\n",""));
         }
      }
   }

   public void close() {
      out.close();
   }

   // Generate various lists of places
   // 0=pages.xml 1=out
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      ExtractPersonFamilyData self = new ExtractPersonFamilyData(args[1]);
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.close();
   }
}
