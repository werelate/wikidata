package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.utils.CountsCollector;
import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;

public class AnalyzeVariousSources extends StructuredDataParser
{
   private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("\\b1[5-9]\\d\\d-1[5-9]\\d\\d\\b");

   private PrintWriter webRecordsPlaceOut;
   private PrintWriter webRecordsNoPlaceOut;
   private PrintWriter webFindingAidOut;
   private PrintWriter webOtherOut;
   private PrintWriter miscRecordsNoPlaceOut;
   private PrintWriter miscRecordsOnePlaceOut;
   private PrintWriter miscRecordsManyPlacesOut;
   private PrintWriter miscRecordsNotRecordsOut;
   private PrintWriter miscRecordsManyPlacesOneOut;
   private PrintWriter gcwOut;
   private PrintWriter manuOut;
   private PrintWriter periOut;

   private CountsCollector ccManuAuthors;
   private CountsCollector ccManuPubls;
   private CountsCollector ccPeriAuthors;
   private CountsCollector ccPeriPubls;
   private CountsCollector ccNotRecords;
   private int manuCount;
   private int manuAuthorsCount;
   private int manuPublsCount;
   private int periCount;
   private int periAuthorsCount;
   private int periPublsCount;
   private double sampleRate;

   public AnalyzeVariousSources(String dir, double sampleRate) throws IOException
   {
      this.sampleRate = sampleRate;

      webRecordsPlaceOut = new PrintWriter(dir+"/WebRecordPlace.html");
      webRecordsPlaceOut.println("<html><head></head><body><ul>");
      webRecordsNoPlaceOut = new PrintWriter(dir+"/WebRecordNoPlace.html");
      webRecordsNoPlaceOut.println("<html><head></head><body><ul>");
      webFindingAidOut = new PrintWriter(dir+"/WebFindingAid.html");
      webFindingAidOut.println("<html><head></head><body><ul>");
      webOtherOut = new PrintWriter(dir+"/WebOther.html");
      webOtherOut.println("<html><head></head><body><ul>");

      miscRecordsNoPlaceOut = new PrintWriter(dir+"/MiscNoPlace.html");
      miscRecordsNoPlaceOut.println("<html><head></head><body><ul>");
      miscRecordsOnePlaceOut = new PrintWriter(dir+"/MiscOnePlace.html");
      miscRecordsOnePlaceOut.println("<html><head></head><body><ul>");
      miscRecordsManyPlacesOut = new PrintWriter(dir+"/MiscManyPlaces.html");
      miscRecordsManyPlacesOut.println("<html><head></head><body><ul>");
      miscRecordsNotRecordsOut = new PrintWriter(dir+"/MiscNotRecords.html");
      miscRecordsNotRecordsOut.println("<html><head></head><body><ul>");
      miscRecordsManyPlacesOneOut = new PrintWriter(dir+"/MiscManyPlacesOne.html");
      miscRecordsManyPlacesOneOut.println("<html><head></head><body><ul>");

      gcwOut = new PrintWriter(dir+"/gcw.html");
      gcwOut.println("<html><head></head><body><ul>");

      manuOut = new PrintWriter(dir+"/Manuscript.html");
      manuOut.println("<html><head></head><body><ul>");

      periOut = new PrintWriter(dir+"/Periodical.html");
      periOut.println("<html><head></head><body><ul>");

      ccManuAuthors = new CountsCollector();
      ccManuPubls = new CountsCollector();
      ccPeriAuthors = new CountsCollector();
      ccPeriPubls = new CountsCollector();
      ccNotRecords = new CountsCollector();

      manuCount = manuAuthorsCount = manuPublsCount = periCount = periAuthorsCount = periPublsCount = 0;
   }

   public void close() {
      webRecordsPlaceOut.println("</ul></body></html>");
      webRecordsPlaceOut.close();
      webRecordsNoPlaceOut.println("</ul></body></html>");
      webRecordsNoPlaceOut.close();
      webFindingAidOut.println("</ul></body></html>");
      webFindingAidOut.close();
      webOtherOut.println("</ul></body></html>");
      webOtherOut.close();
      miscRecordsNoPlaceOut.println("</ul></body></html>");
      miscRecordsNoPlaceOut.close();
      miscRecordsOnePlaceOut.println("</ul></body></html>");
      miscRecordsOnePlaceOut.close();
      miscRecordsManyPlacesOut.println("</ul></body></html>");
      miscRecordsManyPlacesOut.close();
      gcwOut.println("</ul></body></html>");
      gcwOut.close();
      manuOut.println("</ul></body></html>");
      manuOut.close();
      periOut.println("</ul></body></html>");
      periOut.close();
   }

   private String makeLink(String unprefixedTitle) {
      return "<li><a href=\"https://www.werelate.org/wiki/Source:"+Util.wikiUrlEncoder(unprefixedTitle)+"\">"+Util.encodeXML(unprefixedTitle)+"</a>";
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
            if (elm != null) sourceType = elm.getValue();

            String sourceTitle = "";
            elm = root.getFirstChildElement("source_title");
            if (elm != null) sourceTitle = elm.getValue().trim();
            if (sourceTitle.length() == 0) sourceTitle = unprefixedTitle;

            String author = "";
            elm = root.getFirstChildElement("author");
            if (elm != null) author = elm.getValue();

            String publisher = "";
            elm = root.getFirstChildElement("publisher");
            if (elm != null) publisher = elm.getValue();

            String subtitle = "";
            elm = root.getFirstChildElement("subtitle");
            if (elm != null) subtitle = elm.getValue();

            int numPlaces = root.getChildElements("place").size();

            String subject = "";
            elm = root.getFirstChildElement("subject");
            if (elm != null) subject = elm.getValue();

            boolean isRecordsSubject = CalcSourceTitleRenames.isRecordsSubject(subject);

            if (sourceType.equals("Website")) {
               if (isRecordsSubject) {
                  if (numPlaces > 0) {
                     webRecordsPlaceOut.println(makeLink(unprefixedTitle)+" "+subject);
                  }
                  else {
                     webRecordsNoPlaceOut.println(makeLink(unprefixedTitle)+" "+subject);
                  }
               }
               else if (subject.equals("Finding aid")) {
                  webFindingAidOut.println(makeLink(unprefixedTitle));
               }
               else {
                  webOtherOut.println(makeLink(unprefixedTitle)+" "+subject);
               }
            }
            else if ((sourceType.equals("Miscellaneous") || sourceType.length() == 0) &&
//                    (AnalyzeAuthors.isHumanOrganizationAuthor(author) || !AnalyzeAuthors.isGovernmentChurchAuthor(author)) &&
                    isRecordsSubject) {
               double r = Math.random();
               if (r < sampleRate) {
                  if (!CalcSourceTitleRenames.hasRecordsWords(sourceTitle+" "+subtitle)) {
                     Matcher m = YEAR_RANGE_PATTERN.matcher(sourceTitle);
                     if (m.find()) {
                        for (String word : sourceTitle.split(" ")) {
                           ccNotRecords.add(word.toLowerCase());
                        }
                     }
                     miscRecordsNotRecordsOut.println(makeLink(unprefixedTitle));
                  }
                  else if (numPlaces == 0) {
                     miscRecordsNoPlaceOut.println(makeLink(unprefixedTitle));
                  }
                  else if (numPlaces == 1) {
                     miscRecordsOnePlaceOut.println(makeLink(unprefixedTitle));
                  }
                  else {
                     String placeCovered = CalcSourceTitleRenames.getSinglePlace(root, sourceTitle, subtitle, author);
                     if (placeCovered == null) {
                        miscRecordsManyPlacesOut.println(makeLink(unprefixedTitle));
                     }
                     else {
                        miscRecordsManyPlacesOneOut.println(makeLink(unprefixedTitle));
                     }
                  }
               }
            }
            else if (sourceType.equals("Government / Church records - Website")) {
               gcwOut.println(makeLink(unprefixedTitle));
            }
            else if (sourceType.equals("Manuscript collection")) {
               manuOut.println(makeLink(unprefixedTitle));
               manuCount++;
               if (author.length() > 0) {
                  manuAuthorsCount++;
                  ccManuAuthors.add(author);
               }
               if (publisher.length() > 0) {
                  manuPublsCount++;
                  ccManuPubls.add(author);
               }
            }
            else if (sourceType.equals("Periodical")) {
               periOut.println(makeLink(unprefixedTitle));
               periCount++;
               if (author.length() > 0) {
                  periAuthorsCount++;
                  ccPeriAuthors.add(author);
               }
               if (publisher.length() > 0) {
                  periPublsCount++;
                  ccPeriPubls.add(author);
               }
            }
         }
      }
   }

   // 0=pages.xml 1=output dir 2=sample rate
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      double sampleRate = Double.parseDouble(args[2]);
      AnalyzeVariousSources avs = new AnalyzeVariousSources(args[1], sampleRate);
      wikiReader.addWikiPageParser(avs);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      avs.close();

      System.out.println("MANUSCRIPTS");
      System.out.println("total="+avs.manuCount+" authors="+avs.manuAuthorsCount+" publishers="+avs.manuPublsCount);
      System.out.println("  AUTHORS");
      avs.ccManuAuthors.writeSorted(false, 50, new PrintWriter(System.out));
      System.out.println("  PUBLISHERS");
      avs.ccManuPubls.writeSorted(false, 50, new PrintWriter(System.out));

      System.out.println("PERIODICALS");
      System.out.println("total="+avs.periCount+" authors="+avs.periAuthorsCount+" publishers="+avs.periPublsCount);
      System.out.println("  AUTHORS");
      avs.ccPeriAuthors.writeSorted(false, 50, new PrintWriter(System.out));
      System.out.println("  PUBLISHERS");
      avs.ccPeriPubls.writeSorted(false, 50, new PrintWriter(System.out));

      System.out.println("NOT RECORDS WORDS");
      avs.ccNotRecords.writeSorted(false, 10, new PrintWriter(System.out));
   }
}
