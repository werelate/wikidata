package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.util.Map;
import java.util.HashMap;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class FhlcExporter extends StructuredDataParser
{
   private static final String[] FIELDS = {"fhlc_id", "title", "author", "category", "availability", "surname_stored", "place_stored", "year_range",
                                           "url", "film_number_stored"};
   private ICsvMapWriter out;

   public FhlcExporter(ICsvMapWriter out) {
      this.out = out;
   }

   private void addValue(Map<String,String> values, String tag, String value) {
      value = value.replace('~', ' ').replace('|', ' ');
      String v = values.get(tag);
      if (Util.isEmpty(v)) {
         values.put(tag, value);
      }
      else {
         values.put(tag, v+"~"+value);
      }
   }

   private void writeSource(String titleno, Element root, String text) throws IOException
   {
      Map<String,String> values = new HashMap<String,String>();

      Elements children = root.getChildElements();
      for (int i = 0; i < children.size(); i++) {
         Element child = children.get(i);
         String tag = child.getLocalName();
         String value = child.getValue();
         if (tag.equals("source_title")) {
            addValue(values, "title", value);
         }
         else if (tag.equals("subtitle")) {
            addValue(values, "title", value);
         }
         else if (tag.equals("author")) {
            addValue(values, "author", value);
         }
         else if (tag.equals("surname")) {
            addValue(values, "surname_stored", value);
         }
         else if (tag.equals("place")) {
            int pos = value.indexOf('|');
            if (pos >= 0) value = value.substring(pos+1);
            addValue(values, "place_stored", value);
         }
         else if (tag.equals("from_year")) {
            addValue(values, "from_year", value);
         }
         else if (tag.equals("to_year")) {
            addValue(values, "to_year", value);
         }
         else if (tag.equals("subject")) {
            addValue(values, "category", value);
         }
         else if (tag.equals("repository")) {
            String repoTitle = child.getAttributeValue("title");
            if (repoTitle != null && (repoTitle.equals("Family History Center") || repoTitle.equals("Family History Library"))) {
               String repoLocation = child.getAttributeValue("source_location");
               if (repoLocation != null && repoLocation.startsWith("http://")) {
                  addValue(values, "url", repoLocation);
               }
               String repoAvailability = child.getAttributeValue("availability");
               if ("Other".equals(repoAvailability)) {
                  repoAvailability = "Family history library";
               }
               if ("Family history center".equals(repoAvailability) || "Family history library".equals(repoAvailability)) {
                  addValue(values, "availability", repoAvailability);
               }
            }
         }
      }

      // fix up: add certain field values
      values.put("fhlc_id", titleno);

      if (values.get("subtitle") != null) {
         if (values.get("title") != null) {
            values.put("title", values.get("title")+" : "+values.get("subtitle"));
         }
         else {
            values.put("title", values.get("subtitle"));
         }
      }

      if (values.get("from_year") != null || values.get("to_year") != null) {
         StringBuilder yearRange = new StringBuilder();
         if (values.get("from_year") != null) yearRange.append(values.get("from_year"));
         if (values.get("to_year") != null) {
            if (yearRange.length() > 0) yearRange.append("-");
            yearRange.append(values.get("to_year"));
         }
         values.put("year_range", yearRange.toString());
      }

      // get film numbers from text
      int pos = text.indexOf("FHL film numbers");
      if (pos > 0) {
         pos = text.indexOf("\n*", pos);
      }
      if (pos > 0) {
         for (String line : text.substring(pos).trim().split("\n")) {
            if (line.startsWith("*")) {
               addValue(values, "film_number_stored", line.substring(1).trim());
            }
            else {
               break;
            }
         }
      }

      // do I need to do this?
      for (String field : FIELDS) {
         if (values.get(field) == null) values.put(field, "");
      }

      // write
      out.write(values, FIELDS);
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();
            Elements repos = root.getChildElements("repository");
            String titleno = null;
            for (int i = 0; i < repos.size(); i++) {
               Element repo = repos.get(i);
               String repoLocation = repo.getAttributeValue("source_location");
               if (repoLocation != null &&
                   repoLocation.startsWith("http://www.familysearch.org/Eng/Library/fhlcatalog/supermainframeset.asp?display=titledetails&titleno=")) {
                  int pos = repoLocation.indexOf("titleno=");
                  titleno = repoLocation.substring(pos + "titleno=".length());
                  break;
               }
            }

            if (titleno != null) {
               writeSource(titleno, root, split[1]);
            }
         }
      }
   }

   // args[0] = pages.xml
   // args[1] = output.csv
   public static void main(String[] args) throws IOException, ParsingException
   {
      CsvPreference csvPrefs = new CsvPreference('"', '|', "\n");
      ICsvMapWriter out = new CsvMapWriter(new FileWriter(args[1]), csvPrefs);
      out.writeHeader(FIELDS);

      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      FhlcExporter fe = new FhlcExporter(out);
      wikiReader.addWikiPageParser(fe);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      out.close();
   }
}
