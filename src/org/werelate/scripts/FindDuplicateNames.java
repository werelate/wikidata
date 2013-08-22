package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class FindDuplicateNames extends StructuredDataParser
{
   private PrintWriter out;

   public FindDuplicateNames(PrintWriter pw) {
      this.out = pw;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Person:")) {
         String[] split = splitStructuredWikiText("person", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();
            Elements names = root.getChildElements("name");
            if (names.size() > 1) {
               out.println("<li><a href=\"http://www.werelate.org/wiki/"+URLEncoder.encode(title,"UTF-8")+"?action=edit\">"+Util.encodeXML(title)+"</a>");
            }
         }
      }
   }

   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      PrintWriter pw = new PrintWriter(new FileWriter(args[1]));
      pw.println("<html><head></head><body><ul>");
      FindDuplicateNames fdn = new FindDuplicateNames(pw);
      wikiReader.addWikiPageParser(fdn);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      pw.println("</ul></body></html>");
      pw.close();
   }

}
