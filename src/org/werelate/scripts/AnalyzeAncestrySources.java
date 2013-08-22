package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class AnalyzeAncestrySources extends StructuredDataParser
{
   private PrintWriter sourcesOut;

   public AnalyzeAncestrySources(PrintWriter sourcesOut) throws IOException
   {
      this.sourcesOut = sourcesOut;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();

            // is author == Ancestry.com and place empty?
            String author = "";
            Element authorElm = root.getFirstChildElement("author");
            if (authorElm != null) author = authorElm.getValue();

            String place = "";
            Element placeElm = root.getFirstChildElement("place");
            if (placeElm != null) place = placeElm.getValue();

            if (author.equals("Ancestry.com")) {
               sourcesOut.println("<li><a href=\"http://www.werelate.org/wiki/"+URLEncoder.encode(title,"UTF-8")+"\">"+Util.encodeXML(title)+"</a>");
            }
         }
      }
   }

   // pages.xml sources_out.html
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      PrintWriter sourcesOut = new PrintWriter(new FileWriter(args[1]));
      sourcesOut.println("<html><head></head><body><ul>");
      AnalyzeAncestrySources aas = new AnalyzeAncestrySources(sourcesOut);
      wikiReader.addWikiPageParser(aas);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      sourcesOut.println("</ul></body></html>");
      sourcesOut.close();
   }
}
