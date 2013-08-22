package org.werelate.wikipedia;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.werelate.parser.WikiReader;
import org.werelate.parser.WikiPageParser;
import org.werelate.utils.CountsCollector;
import org.werelate.utils.Util;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;

import nu.xom.ParsingException;

public class AnalyzeWPPeople implements WikiPageParser
{
   private static final Logger logger = Logger.getLogger("org.werelate.wikipedia");
   public static Pattern pBirthsDeaths = Pattern.compile("\\[\\[\\s*Category:(\\d{3,4})s?\\s*(births|deaths)\\s*(\\]|\\|)", Pattern.CASE_INSENSITIVE);
   public static Pattern pWpCategory = Pattern.compile("\\[\\[\\s*Category:([^\\]\\|]+)", Pattern.CASE_INSENSITIVE);
   public static Pattern pSpouseParm = Pattern.compile("\\|\\s*spouse\\s*=", Pattern.CASE_INSENSITIVE);
   public static Pattern pParenthesized = Pattern.compile("\\((.*?)\\)");
   public static Pattern pLifetime = Pattern.compile("\\{\\{Lifetime\\s*\\|\\s*(\\d*)\\s*\\|\\s*(\\d*)", Pattern.CASE_INSENSITIVE);

   private CountsCollector categoryCC;
   private CountsCollector spouseTemplateCC;
   private PrintWriter pageTitles = null;

   private static final String[] SURNAME_PREFIXES_ARRAY = { "a", "al",
      "d", "da", "du", "de", "di", "do", "dal", "der", "del", "den", "dem", "des", "dell", "dela", "dalla", "della", "delle", "dello", "delos",
      "el", "fitz", "l", "le", "la", "lo", "los", "m", "mac", "mc", "o", "san", "saint", "st", "ste", "st.", "ste.",
      "t", "te", "ten", "ter", "van", "ver", "von", "vom", "vor", "vander" };
   public static final Set<String> SURNAME_PREFIXES = new HashSet<String>();
   static {
      for (int i = 0; i < SURNAME_PREFIXES_ARRAY.length; i++) {
         SURNAME_PREFIXES.add(SURNAME_PREFIXES_ARRAY[i]);
      }
   }

   private static final String[] TITLE_SUFFIXES_ARRAY = { "jr", "sr", "jr.", "sr.", "i", "ii", "iii", "iv", "v"};
   public static final Set<String> TITLE_SUFFIXES = new HashSet<String>();
   static {
      for (int i = 0; i < TITLE_SUFFIXES_ARRAY.length; i++) {
         TITLE_SUFFIXES.add(TITLE_SUFFIXES_ARRAY[i]);
      }
   }

   private static final String[] TITLE_PREFIXES_ARRAY = {"sir", "baron", "duke", "duchess", "grand", "lord", "lady", "prince", "princess", "pope"};
   public static final Set<String> TITLE_PREFIXES = new HashSet<String>();
   static {
      for (int i = 0; i < TITLE_PREFIXES_ARRAY.length; i++) {
         TITLE_PREFIXES.add(TITLE_PREFIXES_ARRAY[i]);
      }
   }

   public AnalyzeWPPeople() {
      categoryCC = new CountsCollector();
      spouseTemplateCC = new CountsCollector();
   }

   public void setPageTitlesWriter(PrintWriter pw) {
      pageTitles = pw;
   }

   public static final Pattern p = Pattern.compile("\\d{3,}");

   private static int skipNonDates(String dates, String year)
   {
      // skip past other non-death dates
      Matcher m = p.matcher(dates);
      int dateStart = 0;
      while (m.find()) {
         if (!year.equals(m.group(0))) {
            dateStart = m.end();
         }
         else {
            break;
         }
      }
      return dateStart;
   }

   public String[] getBirthDeathDates(String birthYear, String deathYear, String dateRange) {
//   String[] dates = line.split("[ 0-9;&\\?\\}\\{\\]](‑|−|―|-|\u2013|\u2014|ndash|mdash|hdash)");
//   dates = line.split("( to |Died|died|\\bd\\b|dead|death|Death|buried|\\bbur\\b|†)");
      int byearPos = -1;
      if (!Util.isEmpty(birthYear)) {
         byearPos = dateRange.indexOf(birthYear);
      }
      String bdate = null;
      if (byearPos >= 0) {
         // skip past non birth dates
         int bdateStart = skipNonDates(dateRange, birthYear);
         if (bdateStart <= byearPos) { // this isn't true very rarely
            bdate = Util.getDateSortKey(dateRange.substring(bdateStart, Math.min(dateRange.length(),byearPos+4)));
            // skip past the birth year
            if (byearPos+birthYear.length() < dateRange.length()) {
               dateRange = dateRange.substring(byearPos+birthYear.length());
            }
            else {
               dateRange = "";
            }
         }
      }
      int dyearPos = -1;
      if (!Util.isEmpty(deathYear)) {
         dyearPos = dateRange.indexOf(deathYear);
      }
      String ddate = null;
      if (dyearPos >= 0) {
         int ddateStart = skipNonDates(dateRange, deathYear);
         if (ddateStart <= dyearPos) {
            ddate = Util.getDateSortKey(dateRange.substring(ddateStart, Math.min(dateRange.length(),dyearPos+deathYear.length())));
         }
      }
      if (!Util.isEmpty(bdate) || !Util.isEmpty(ddate)) {
         String[] dates = new String[2];
         dates[0] = Util.isEmpty(bdate) ? birthYear : bdate;
         dates[1] = Util.isEmpty(ddate) ? deathYear : ddate;
         return dates;
      }
      return null;
   }

   private static String cleanName(String title) {
      int pos = title.indexOf(',');
      if (pos > 0) title = title.substring(0, pos);
      pos = title.indexOf(" of ");
      if (pos > 0) title = title.substring(0, pos);
      pos = title.indexOf(" Of ");
      if (pos > 0) title = title.substring(0, pos);
      return title.replaceAll("\\(.*?\\)", " ");
   }

   public String[] getGivenSurname(String title) {
      String[] tokens = cleanName(title).split("\\s+");

      int lastToken = tokens.length-1;
      while (lastToken >= 0 && TITLE_SUFFIXES.contains(tokens[lastToken].toLowerCase())) {
         lastToken--;
      }
      int startToken = 0;
      while (startToken < lastToken-1 && TITLE_PREFIXES.contains(tokens[startToken].toLowerCase())) {
         startToken++;
      }
      int givennameToken = lastToken-1;
      while (givennameToken > startToken && SURNAME_PREFIXES.contains(tokens[givennameToken].toLowerCase())) {
         givennameToken--;
      }

      StringBuilder buf = new StringBuilder();
      String[] pieces = new String[2];
      for (int i = startToken; i <= givennameToken; i++) {
         if (buf.length() > 0) buf.append(" ");
         buf.append(tokens[i]);
      }
      pieces[0] = buf.toString();
      buf.setLength(0);
      for (int i = givennameToken+1; i <= lastToken; i++) {
         if (buf.length() > 0) buf.append(" ");
         buf.append(tokens[i]);
      }
      pieces[1] = buf.toString();

      return pieces;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      // if this page is in nnnn(s) births/deaths category
      Matcher m = pBirthsDeaths.matcher(text);
      boolean found = false;
      String birthDate = null;
      String deathDate = null;
      while (m.find()) {
         String year = m.group(1);
         int y = Integer.parseInt(year);
         boolean isBirth = m.group(2).equalsIgnoreCase("births");
         if (isBirth) {
            birthDate = year;
         }
         else {
            deathDate = year;
         }
         if ((isBirth && y >= 100 && y <= 1900) ||
             (!isBirth && y >= 100 && y <= 2009)) {
            found = true;
         }
      }
      m = pLifetime.matcher(text);
      while (m.find()) {
         try {
            birthDate = m.group(1);
            deathDate = m.group(2);
            if (birthDate.length() > 0) {
               int y = Integer.parseInt(birthDate);
               if (y >= 100 && y <= 1900) found = true;
            }
            if (deathDate.length() > 0) {
               int y = Integer.parseInt(deathDate);
               if (y >= 100 && y <= 2009) found = true;
            }
            if (found) break;
         }
         catch (NumberFormatException e) {
            logger.warn("Invalid birth="+birthDate+" or death="+deathDate+" for title="+title);
         };
      }

      if (found) {
         // strip out comments
         text = text.replaceAll("<!--.*?-->", "");

         // get birth and death dates
         WikiPage wp = new WikiPage(title, text);
         String openingPara = wp.getOpeningPara();
         m = pParenthesized.matcher(openingPara);
         String[] dates = null;
         while (m.find()) {
            dates = getBirthDeathDates(birthDate, deathDate, m.group(1));
            if (dates != null) {
               birthDate = dates[0];
               deathDate = dates[1];
               break;
            }
         }
         if (birthDate == null) birthDate = "";
         if (deathDate == null) deathDate = "";

         // get name pieces
         String[] namePieces = getGivenSurname(title);

         // gather all categories
         StringBuilder catBuf = new StringBuilder();
         m = pWpCategory.matcher(text);
         while (m.find()) {
            String catName = m.group(1);
            if (catName.indexOf("\n") >= 0) catName = catName.substring(0, catName.indexOf("\n"));
            if (catName.indexOf("births") < 0 && catName.indexOf("deaths") < 0 && catName.indexOf("People from") < 0) {
               categoryCC.add(catName);
               if (catBuf.length() > 0 ) catBuf.append('|');
               catBuf.append(catName);
            }
         }

         // gather all templates
         for (String template : Util.getTemplates(text)) {
            int pos = template.indexOf("|");
            if (pos < 0) pos = template.length();
            if (pos >= 0) {
               String templateName = template.substring(0, pos).trim();
//               templateCC.add(templateName);
               // gather all templates with spouse field
               m = pSpouseParm.matcher(template);
               if (m.find()) {
                  spouseTemplateCC.add(templateName);
               }
            }
         }

         // save page title|gn|sn|bdate|ddate|categories
         pageTitles.println(title+"|"+namePieces[0]+"|"+namePieces[1]+"|"+birthDate+"|"+deathDate+"|"+catBuf.toString());
      }
   }

   public CountsCollector getCategoryCounts() {
      return categoryCC;
   }

   public CountsCollector getSpouseTemplateCounts() {
      return spouseTemplateCC;
   }

   public static void main(String[] args)
   {
      Options opt = new Options();
      // p is for WeRelate pages file.
      opt.addOption("wp", true, "wikipages.xml filename");
      opt.addOption("c", true, "Category counts file");
      opt.addOption("s", true, "Templates with spouses counts file");
      opt.addOption("p", true, "Page titles file");
      opt.addOption("?", false, "Print out help information");

      BasicParser parser = new BasicParser();
      try {
         CommandLine cl = parser.parse(opt, args);
         if (cl.hasOption("?") || !cl.hasOption("wp") || !cl.hasOption("c") || !cl.hasOption("s") || !cl.hasOption("p")) {
            System.out.println("Analyze the WP file looking for people");
            HelpFormatter f = new HelpFormatter();
            f.printHelp("OptionsTip", opt);
         } else {
            String wikipediaXML = cl.getOptionValue("wp");
            String categoryFilename = cl.getOptionValue("c");
            String spouseFilename = cl.getOptionValue("s");
            PrintWriter pageTitles = new PrintWriter(cl.getOptionValue("p"));
            WikiReader wr = new WikiReader();
            AnalyzeWPPeople awp = new AnalyzeWPPeople();
            awp.setPageTitlesWriter(pageTitles);
            wr.addWikiPageParser(awp);
            wr.read(wikipediaXML);
            pageTitles.close();
            CountsCollector cc = awp.getCategoryCounts();
            PrintWriter out = new PrintWriter(categoryFilename);
            cc.writeSorted(false, 10, out);
            out.close();
            cc = awp.getSpouseTemplateCounts();
            out = new PrintWriter(spouseFilename);
            cc.writeSorted(false, 10, out);
            out.close();
         }
      }
      catch (ParseException e) {
         System.err.println("Invalid command line arguments." + e.getMessage());
      } catch (IOException e)
      {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      } catch (ParsingException e)
      {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
   }
}
