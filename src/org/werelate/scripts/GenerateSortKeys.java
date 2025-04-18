package org.werelate.scripts;

import nu.xom.*;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.util.SharedUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class GenerateSortKeys extends StructuredDataParser {
   private static final String[] FIELDS = {"key", "value"};

   private SortedSet<String> sortKeys = new TreeSet<String>();

   // keep in sync with PersonPageIndexer in indexer project
   private void appendAttr(String attr, StringBuilder buf) {
      if (!Util.isEmpty(attr)) {
         if (buf.length() > 0) {
            buf.append(" ");
         }
         buf.append(attr);
      }
   }

   // keep in sync with PersonPageIndexer in indexer project
   private String getFullname(Element name) {
      StringBuilder buf = new StringBuilder();
      appendAttr(name.getAttributeValue("title_prefix"), buf);
      appendAttr(name.getAttributeValue("given"), buf);
      appendAttr(name.getAttributeValue("surname"), buf);
      appendAttr(name.getAttributeValue("title_suffix"), buf);
      return buf.toString();
   }

   // keep in sync with PersonPageIndexer in indexer project
   private String getPersonSortTitle(String title, Document doc) {
      if (doc != null) {
         Nodes nodes  = doc.query("person/name");
         if (nodes.size() > 0) {
            String name = getFullname((Element)nodes.get(0));
            if (name.indexOf(' ') > 0) {
               return name;
            }
         }
      }
      return SharedUtils.removeIndexNumber(title);
   }

   // keep in sync with FamilyPageIndexer in indexer project
   private String getFamilySortTitle(String title) {
      return SharedUtils.removeIndexNumber(title);
   }

   // keep in sync with TitleSorter in indexer project
   private String generateSortKey(String title) {
      title = Util.romanize(title);
      if (title.length() > 80) {
         title = title.substring(0, 80);
      }
      return title.toLowerCase().trim();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      String[] namespaceTitle = Util.splitNamespaceTitle(title);
      if (namespaceTitle[0].equals("Person")) {
         String[] split = splitStructuredWikiText("person", text);
         Document doc = null;
         if (!Util.isEmpty(split[0])) {
            doc = parseText(split[0]);
         }
         namespaceTitle[1] = getPersonSortTitle(namespaceTitle[1], doc);
      }
      else if (namespaceTitle[0].equals("Family")) {
         namespaceTitle[1] = getFamilySortTitle(namespaceTitle[1]);
      }

      String sortKey = generateSortKey(namespaceTitle[1]);
      sortKeys.add(sortKey);
   }

   public void writeSortKeys(String filename) throws IOException {
      PrintWriter out = new PrintWriter(new FileWriter(filename));
      Map<String,String> row = new HashMap<String,String>();

      // determine gap between keys
      int gaps = sortKeys.size()+1;
      long space = (long)Integer.MAX_VALUE - (long)Integer.MIN_VALUE;
      int gapSize = (int)(space / gaps);
      int value = Integer.MIN_VALUE;
      for (String key : sortKeys) {
         value += gapSize;
         // mysql uses \ as an escape character, so double it
         out.println(key.replace("\\", "\\\\")+"\t"+Integer.toString(value));
      }
      out.close();
   }

   // args[0] = pages.xml
   // args[1] = output.csv
   public static void main(String[] args) throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      GenerateSortKeys self = new GenerateSortKeys();
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.writeSortKeys(args[1]);
   }
}
