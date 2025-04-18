package org.werelate.duplicates;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.parser.WikiReader;
import org.werelate.util.SharedUtils;
import org.werelate.parser.StructuredDataParser;
import org.werelate.utils.Util;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class ProduceFamilyDuplicates extends StructuredDataParser
{
   private static final Logger logger = LogManager.getLogger("org.werelate.duplicates");

   private static final Pattern FAMILY_NAME_PATTERN = Pattern.compile("([^ ]*)\\s+(.*?)\\s+and\\s+([^ ]*)\\s+(.*)");
   private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("([^ ]*)\\s+(.*)");
   private static final Pattern NOMERGE_PATTERN = Pattern.compile("\\{\\{nomerge\\s*\\|(.*?)\\}\\}", Pattern.CASE_INSENSITIVE);

   private TreeMap<String,List<String>> duplicateMap;
   private HashMap<String, Integer> pageIdMap;
   private HashSet<String> nomerges;

   public ProduceFamilyDuplicates() {
      super();
      duplicateMap = new TreeMap<String,List<String>>();
      pageIdMap = new HashMap<String, Integer>();
      nomerges = new HashSet<String>();
   }

   private String cleanSurname(String surname) {
      String surnamelc = surname.toLowerCase();
      if (surnamelc.length() >= 4 && surnamelc.startsWith("de ")) {
         surname = surname.substring(3);
      }
      else if (surnamelc.length() >= 3 && surnamelc.startsWith("de") && Character.isUpperCase(surname.charAt(2))) {
         surname = surname.substring(2);
      }

      if (surnamelc.length() >= 2 && !Character.isUpperCase(surname.charAt(0))) {
         surname = surname.substring(0, 1).toUpperCase() + surname.substring(1);
      }
      else if (surnamelc.length() == 1) {
         surname = surname.toUpperCase();
      }
      
      return surname;
   }

   private String getFamilyKey(String title) {
      String key = "";
      String titleNoIndexNumber = SharedUtils.removeIndexNumber(title);
      Matcher m = FAMILY_NAME_PATTERN.matcher(titleNoIndexNumber);
      if (m.matches()) {
         StringBuilder buf = new StringBuilder();
         buf.append(cleanSurname(m.group(2)));
         buf.append(' ');
         buf.append(m.group(1));
         buf.append(' ');
         buf.append(cleanSurname(m.group(4)));
         buf.append(' ');
         buf.append(m.group(3));
         key = buf.toString();
      }
      return key;
   }

   private String getPersonKey(String title) {
      String key = "";
      String titleNoIndexNumber = SharedUtils.removeIndexNumber(title);
      Matcher m = PERSON_NAME_PATTERN.matcher(titleNoIndexNumber);
      if (m.matches()) {
         StringBuilder buf = new StringBuilder();
         buf.append(cleanSurname(m.group(2)));
         buf.append(' ');
         buf.append(m.group(1));
         key = buf.toString();
      }
      return key;
   }

   private void addSpouses(Element root, String title, String role)
   {
      Elements spouses = root.getChildElements(role);
      if (spouses.size() > 1) {
         List<String>titles = new ArrayList<String>();
         String spouseTitle = null;
         for (int i = 0; i < spouses.size(); i++) {
            Element spouse = spouses.get(i);
            spouseTitle = Util.translateHtmlCharacterEntities(spouse.getAttributeValue("title"));
            titles.add(spouseTitle);
         }
         String key = "_Multiple spouses: " + title + role + "|Person";
         duplicateMap.put(key, titles);
      }
   }

   private boolean areSimilarTitles(String t1, String t2) {
      String[] words1 = t1.trim().split("\\s+");
      String[] words2 = t2.trim().split("\\s+");
      int cnt = 0;
      // count the number of Unknown's
      for (String w : words1) {
         if (w.equals("Unknown")) cnt++;
      }
      for (String w : words2) {
         if (w.equals("Unknown")) cnt++;
      }
      if (cnt < 2) {
         // add the number of matching words
         for (String w1 : words1) {
            if (!w1.equals("Unknown") && !w1.equals("and") && w1.indexOf("(") < 0) {
               for (String w2 : words2) {
                  if (w1.equals(w2)) {
                     cnt++;
                     break;
                  }
               }
               if (cnt >= 2) break;
            }
         }
      }
      return (cnt >= 2);
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (title.startsWith("Family:")) {
         pageIdMap.put(title, pageId);
         String noprefixTitle = title.substring("Family:".length());
         String key = getFamilyKey(noprefixTitle);
         if (key.length() > 0 &&
             !noprefixTitle.startsWith("Unknown Unknown and Unknown Unknown") &&
             !noprefixTitle.startsWith("Unknown and Unknown") &&
             !noprefixTitle.startsWith("X Unknown and X Unknown") &&
             !noprefixTitle.startsWith("Name Withheld and Name Withheld") &&
             !noprefixTitle.startsWith("Living ") && !noprefixTitle.contains("and Living ")) {
            key = key+"|Family";
            List<String> titles = duplicateMap.get(key);
            if (titles == null) {
               titles = new ArrayList<String>();
               duplicateMap.put(key, titles);
            }
            titles.add(noprefixTitle);
         }

         // add duplicate husbands/wives to duplicateMap
         String[] split = splitStructuredWikiText("family", text);
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(structuredData);
            Element elm = doc.getRootElement();

            addSpouses(elm, title, "husband");
            addSpouses(elm, title, "wife");
         }
      }
      else if (title.startsWith("Person:")) {
         pageIdMap.put(title, pageId);

         // add duplicate parents to duplicateMap
         String[] split = splitStructuredWikiText("person", text);
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(structuredData);
            Element elm = doc.getRootElement();

            Elements parents = elm.getChildElements("child_of_family");
            if (parents.size() > 1) {
               List<String>titles = new ArrayList<String>();
               for (int i = 0; i < parents.size(); i++) {
                  Element parent = parents.get(i);
                  String parentTitle = Util.translateHtmlCharacterEntities(parent.getAttributeValue("title"));
                  titles.add(parentTitle);
               }

               // add duplicate parents only if they are similar
               List<String>similarTitles = new ArrayList<String>();
               for (String t1 : titles) {
//                  boolean foundDup = false;
                  for (String t2 : titles) {
                     if (!t1.equals(t2)) {
                        if (areSimilarTitles(t1,t2)) {
//                           foundDup = true;
                           if (!similarTitles.contains(t1)) similarTitles.add(t1);
                           if (!similarTitles.contains(t2)) similarTitles.add(t2);
                        }
                     }
                  }
//if (!foundDup) {
//   logger.warn("Not adding duplicate parent: "+t1+" for "+title);
//}
               }
               if (similarTitles.size() > 1) {
                  String key = "_Multiple parents: " + title + "|Family";
                  duplicateMap.put(key, titles);
               }
            }
         }
      }
      else if (title.startsWith("Family talk:") || title.startsWith("Person talk:")) {
         String namespaceColon = null;
         Matcher m = NOMERGE_PATTERN.matcher(text);
         while (m.find()) {
            String nomergeTitle = Util.standardizeWikiLink(m.group(1));
            if (namespaceColon == null) {
               namespaceColon = (title.startsWith("Family talk:") ? "Family:" : "Person:");
               title = namespaceColon + title.substring("Family talk:".length());
            }
            if (!nomergeTitle.startsWith(namespaceColon)) {
               nomergeTitle = namespaceColon + nomergeTitle;
            }
            nomerges.add(title+"|"+nomergeTitle);
            nomerges.add(nomergeTitle+"|"+title);
         }
      }
   }

   public void countDuplicates() {
      int singletons = 0;
      int duplicates = 0;

      for (List<String> titles : duplicateMap.values()) {
         if (titles.size() > 1) {
            duplicates++;
         }
         else {
            singletons++;
         }
      }

      System.out.println("Singletons="+singletons+" Duplicates="+duplicates);
   }

   public void removeInvalidDups()
   {
      Iterator<String> keyIter = duplicateMap.keySet().iterator();
      while (keyIter.hasNext()) {
         String key = keyIter.next();
         String namespace = key.substring(key.indexOf('|')+1);
         List<String> titles = duplicateMap.get(key);
         Iterator<String> titleIter = titles.iterator();
         while (titleIter.hasNext()) {
            String title = titleIter.next();
            String fullTitle = namespace+":"+title;

            boolean remove = true;


            Integer id = pageIdMap.get(fullTitle);
            if (id != null) { // remove pages that don't exist
               // remove pages unless they are mergable with another page
               for (String otherTitle : titles) {
                  if (!otherTitle.equals(title) && !nomerges.contains(fullTitle+"|"+namespace+":"+otherTitle)) {
                     remove = false;
                     break;
                  }
               }
            }
            if (remove) {
               titleIter.remove();
            }
         }

         // remove singleton duplicate-lists
         if (titles.size() < 2) {
            keyIter.remove();
         }
      }
   }

   public void removeDuplicateDups() {
      Set<String> titleSet = new HashSet<String>();
      StringBuilder buf = new StringBuilder();
      Iterator<String> keyIter = duplicateMap.keySet().iterator();
      while (keyIter.hasNext()) {
         String key = keyIter.next();
         String namespace = key.substring(key.indexOf('|')+1);
         buf.setLength(0);
         buf.append(namespace);
         for (String title : duplicateMap.get(key)) {
            buf.append(title);
         }
         // if this title set already exists, remove this entry
         if (!titleSet.add(buf.toString())) {
            keyIter.remove();
         }
      }
   }

   // no longer using this code; php code will update database
//   public void updateDatabase(String host, String userName, String password) throws ClassNotFoundException, IllegalAccessException, InstantiationException, SQLException
//   {
//      Class.forName("com.mysql.jdbc.Driver").newInstance();
//      Connection conn = DriverManager.getConnection("jdbc:mysql://"+host+"/wikidb", userName, password);
//      PreparedStatement delete = conn.prepareStatement("DELETE FROM duplicates");
//      PreparedStatement insert = conn.prepareStatement("INSERT IGNORE INTO duplicates (SELECT page_namespace, page_title FROM page WHERE page_id = ?)");
//      delete.execute();
//      conn.setAutoCommit(false);
//
//      for (String key : duplicateMap.keySet()) {
//         String namespace = key.substring(key.indexOf('|')+1);
//         List<String> titles = duplicateMap.get(key);
//         if (titles.size() > 1) {
//            List<Integer> ids = new ArrayList<Integer>();
//            for (String title : titles) {
//               Integer id = pageIdMap.get(namespace+":"+title);
//               if (id != null) {
//                  ids.add(id);
//               }
////               else {
////                  System.out.println("Page ID not found: " + namespace+":"+title);
////               }
//            }
//            if (ids.size() > 1) {
//               for (Integer id : ids) {
//                  insert.setInt(1, id.intValue());
//                  insert.executeUpdate();
//               }
//            }
//         }
//      }
//
//      conn.commit();
//      conn.close();
//   }

   public void writeHtml(String filename) throws IOException
   {
      DateFormat dateFormat = new SimpleDateFormat("d MMM yyyy");
      Date date = new Date();
      PrintWriter out = new PrintWriter(new FileWriter(filename+".html"));
      out.println("<html><head><title>Duplicates report</title></head><body><h2>Family duplicates report dated "+dateFormat.format(date)+"</h2><ul>");
      Character prevParentsChar = null;
      Character prevSpousesChar = null;
      for (String key : duplicateMap.keySet()) {
         String namespace = key.substring(key.indexOf('|')+1);
         List<String> titles = duplicateMap.get(key);

         if (key.indexOf("_Multiple parents: ") == 0) {
            Character firstChar = key.charAt("_Multiple parents: Person:".length());
            if (prevParentsChar == null || firstChar != prevParentsChar) {
               prevParentsChar = firstChar;
               out.println("<li><b>Multiple parents "+firstChar+"</b></li>");
            }
         }
         else if (key.indexOf("_Multiple spouses: ") == 0) {
            Character firstChar = key.charAt("_Multiple spouses: Family:".length());
            if (prevSpousesChar == null || firstChar != prevSpousesChar) {
               prevSpousesChar = firstChar;
               out.println("<li><b>Multiple spouses "+firstChar+"</b></li>");
            }
         }

         if (titles.size() > 10) {
            out.print("<li>Too many to merge at once: ");
            for (String title : titles) {
               out.print(" <a href=\"https://www.werelate.org/wiki/"+namespace+":"+URLEncoder.encode(title, "UTF-8")+"\">"+Util.encodeXML(namespace+":"+title)+"</a>");
            }
            out.println("</li>");
         }
         else if (titles.size() > 1) {
            out.print("<li><a href=\"https://www.werelate.org/wiki/Special:Compare?ns="+namespace);
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < titles.size(); i++) {
               String title = titles.get(i);
               out.print("&compare_"+i+"="+URLEncoder.encode(title, "UTF-8"));
               buf.append(" ");
               buf.append(title);
            }
            out.println("\">"+namespace+":"+Util.encodeXML(buf.toString())+"</a></li>");
         }
      }

      out.println("</ul></body></html>");
      out.close();
   }

   public void writeText(String filename) throws IOException
   {
      PrintWriter out = new PrintWriter(new FileWriter(filename));

      for (String key : duplicateMap.keySet()) {
         String namespace = key.substring(key.indexOf('|')+1);
         List<String> titles = duplicateMap.get(key);
         if (titles.size() > 20) {
            System.out.println("PROBLEM: key=" + key + " count="+titles.size()+" title="+titles.get(0));
         }
         else if (titles.size() > 1) {
            out.print(namespace);
            for (String title : titles) {
               out.print('|'+title);
            }
            out.println();
         }
      }

      out.close();
   }

   public void writeNomerges(String filename) throws IOException
   {
      PrintWriter out = new PrintWriter(new FileWriter(filename));

      for (String pair : nomerges) {
         out.println(pair);
      }

      out.close();
   }

   public static void main(String[] args)
           throws ParseException, IOException, ParsingException
   {
      Options opt = new Options();
      opt.addOption("x", true, "pages.xml filename");
      opt.addOption("o", true, "html output filename");
      opt.addOption("t", true, "text output filename");
      opt.addOption("n", true, "nomerge output filename");
      opt.addOption("?", false, "Print help information");

      BasicParser parser = new BasicParser();
      CommandLine cl = parser.parse(opt, args);

      if (cl.hasOption("?") || !cl.hasOption("x") || !cl.hasOption("o") || !cl.hasOption("t") || !cl.hasOption("n")) {
         System.out.println("Produces html and text lists of duplicate family titles");
         HelpFormatter f = new HelpFormatter();
         f.printHelp("OptionsTip", opt);
      } else
      {
         String htmlOut = cl.getOptionValue("o");
         String textOut = cl.getOptionValue("t");
         String nomergeOut = cl.getOptionValue("n");
         String pagesFile = cl.getOptionValue("x");

         System.out.println("Reading pages.xml");
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         ProduceFamilyDuplicates pfd = new ProduceFamilyDuplicates();
         wikiReader.addWikiPageParser(pfd);
         InputStream in = new FileInputStream(pagesFile);
         wikiReader.read(in);
         in.close();

         System.out.println("Removing Invalid Duplicates");
         pfd.removeInvalidDups();

         System.out.println("Removing Duplicate Duplicates");
         pfd.removeDuplicateDups();

         System.out.println("Writing html output");
         pfd.writeHtml(htmlOut);
         System.out.println("Writing text output");
         pfd.writeText(textOut);
         System.out.println("Writing nomerge output");
         pfd.writeNomerges(nomergeOut);
      }
   }
}
