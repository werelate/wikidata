package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.CountsCollector;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class AnalyzeWebSources extends StructuredDataParser
{
   private PrintWriter miscOut;
   private PrintWriter nonMiscOut;

   public AnalyzeWebSources(PrintWriter miscOut, PrintWriter nonMiscOut)
   {
      this.miscOut = miscOut;
      this.nonMiscOut = nonMiscOut;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            String unprefixedTitle = title.substring("Source:".length());
            Element root = parseText(structuredData).getRootElement();

            String sourceType = "";
            String author = "";
            String url = "";
            boolean hasFhlcRepo = false;
            boolean hasAncestryRepo = false;
            boolean hasAncestryRootswebRepo = false;
            boolean hasGoogleRepo = false;
            boolean hasGenealogyRepo = false;
            boolean hasRepo = false;

            Element sourceTypeElm = root.getFirstChildElement("source_type");
            if (sourceTypeElm != null) sourceType = sourceTypeElm.getValue();

            Element authorElm = root.getFirstChildElement("author");
            if (authorElm != null) author = authorElm.getValue().trim();

            Element urlElm = root.getFirstChildElement("url");
            if (urlElm != null) url = urlElm.getValue().trim();
            if (url.length() > 0) {
               url = url.toLowerCase();
               hasRepo = true;
               if (url.indexOf("search.ancestry.com") >= 0) hasAncestryRepo = true;
               if (url.indexOf("content.ancestry.com") >= 0) hasAncestryRepo = true;
               if (url.indexOf("rootsweb.ancestry.com") >= 0) hasAncestryRootswebRepo = true;
               if (url.indexOf("familysearch.org") >= 0) hasFhlcRepo = true;
               if (url.indexOf("google.com") >= 0) hasGoogleRepo = true;
               if (url.indexOf("genealogy.com") >= 0) hasGenealogyRepo = true;
            }

            Elements repositories = root.getChildElements("repository");
            for (int i = 0; i < repositories.size(); i++) {
               Element repository = repositories.get(i);
               url = repository.getAttributeValue("source_location");
               if (url != null && url.length() > 0) {
                  url = url.toLowerCase();
                  hasRepo = true;
                  if (url.indexOf("search.ancestry.com") >= 0) hasAncestryRepo = true;
                  if (url.indexOf("content.ancestry.com") >= 0) hasAncestryRepo = true;
                  if (url.indexOf("rootsweb.ancestry.com") >= 0) hasAncestryRootswebRepo = true;
                  if (url.indexOf("familysearch.org") >= 0) hasFhlcRepo = true;
                  if (url.indexOf("google.com") >= 0) hasGoogleRepo = true;
                  if (url.indexOf("genealogy.com") >= 0) hasGenealogyRepo = true;
               }
            }

//            if (hasRepo && !hasAncestryRepo && !hasFhlcRepo && !hasGoogleRepo && !hasGenealogyRepo &&  !(sourceType.equals("Book") && author.length() > 0)) {
            if (hasAncestryRootswebRepo && !hasAncestryRepo && !hasFhlcRepo && !hasGoogleRepo && !hasGenealogyRepo) {
               if (sourceType.equals("Miscellaneous") || sourceType.length() == 0) {
                  miscOut.println(title);
               }
               else {
                  nonMiscOut.println("<li><a href=\"https://www.werelate.org/wiki/"+URLEncoder.encode(title,"UTF-8")+"\">"+Util.encodeXML(unprefixedTitle)+"</a>");
               }
            }
         }
      }
   }

   // pages.xml miscOut.txt nonMiscOut.html
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      PrintWriter miscOut = new PrintWriter(new FileWriter(args[1]));
      PrintWriter nonMiscOut = new PrintWriter(new FileWriter(args[2]));
      nonMiscOut.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body><ul>");
      AnalyzeWebSources aws = new AnalyzeWebSources(miscOut, nonMiscOut);
      wikiReader.addWikiPageParser(aws);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      miscOut.close();
      nonMiscOut.println("</ul></body></html>");
      nonMiscOut.close();
   }
}
