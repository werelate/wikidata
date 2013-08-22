package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.utils.CountsCollector;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class CalcSourceTitleRenames extends StructuredDataParser
{
   private static final Pattern YEAR_END = Pattern.compile("\\((1[5-9]\\d\\d|200[0-9])\\)$");
   private static final Pattern YEAR = Pattern.compile("\\b1[5-9]\\d\\d\\b");
   private static final Pattern AUTHOR_ROLE_SUFFIX = Pattern.compile("[,;.]\\s*(comp\\.?|composer|compiler|translator|trans\\.?|editor|ed\\.)$", Pattern.CASE_INSENSITIVE);
   private static final Pattern AUTHOR_PAREN_SUFFIX = Pattern.compile("\\(.*?\\)$");
   private static final Pattern LEADING_ARTICLE_MATCHER = Pattern.compile("^(a|an|the)\\s+", Pattern.CASE_INSENSITIVE);

   public static String[] RECORD_TYPES_ARRAY = {
      "Cemetery records", "Census records", "Church records", "Deed/Land records", "Directory records", "Institutional records",
      "Legal/Court records", "Migration records", "Military records", "Obituaries", "Passenger/Immigration records",
      "Tax records", "Vital records", "Voter records", "Will/Probate records"
   };
   // Biography, Ethnic/Cultural, Family bible, Family tree, Finding aid, History, Newspaper (article), Manuscripts/Documents,
   // Maps/Gazetteers, Occupation, Periodical (article), Photograph collection,
   public static Set<String> RECORD_TYPES = new HashSet<String>();
   static {
      for (String recordType : RECORD_TYPES_ARRAY) RECORD_TYPES.add(recordType);
   }

   public static Pattern RECORD_WORDS = Pattern.compile("\\b("+
      "baptisms?|christenings?|vestry|births?|marriages?|divorces?|deaths?|vital|census|censuses|will books?|wills|"+
      "deed books?|deeds|court books?|probate|manorial|estates?|burials?|interments?|obituary|obituaries|"+
      "cemetery|cemeteries|graveyards?|gravestones|tombstones?|inscriptions?|"+
      "directory|directories|voters?|rolls?|orphans court|yearbooks?|alumni|taxpayers?|tax|enlistees|rosters?|"+
      "lists|licenss?|record books?|records|musters?|passengers?|printout|inventories|registers?|schedules?|index|indexes|indices|"+
      "pastor's record|bishop's transcripts|dower record|civil registration|church record|accounts|taxes|minutes|books|courts?|"+
      "archdeacons|land|churchwardens?|enumerations?|bonds?|certificates|affidavits|discharges|registrations|dockets?|declarations?|"+
      "chancery|depositions|record of members|rates|poor|naturalizations?|returns|"+
      "kirkebøker|kirchenbüchern|kirchenbuch|kirchenbücher|registres|registri|registrum|anyakönyvek|censo|juicios|jurídicos|intestados|"+
      "registros|kyrkoböcker|notariële akten|actes de notaire|tierras|propiedades|zivilstandsregister|parochieregisters|kirchenbuchduplikat|"+
      "folketælling|gaardersregisters|mariages|kirkebøger|volkstelling|kyrkoböcker|transportregisters|de mariage|"+
      "grundleihenbücher|kirkonkirjat|kirkebøger|volkstelling|protocolos|metrykalnych|metrykalne|matrika|matrikel|metrical|matična|"+
      "catastros?|skifteprotokoller|gerichtsakten|gerichtsbücher|recensements?|cirkevná|småprotokoll|landregisters?|tables décennales|"+
      "kirkjubækur|kirkonkirjojen|domböcker|standestabellen|musterlisten|grundbuchblätter|grundbuchindex|privilegienabschriften|"+
      "knjiga|gereja|civiles|actes|matrykuła|bouppteckningar|schepenakten|mönsterrullor|generalmönsterrullor|weesakten|folketellingen|"+
      "tingbøger|manntall|förrättningar|dénombrements?|eκκλησιαστικά|jordebøger|mетрические|lægdsruller|successie|sépultures|realregistre|"+
      "gewaltsteuerbuch|perukirjat|folketelling|Εκκλησιαστικά|αρχεία|Метрические|книги|kirkebøker|lijst van namen|Метрические|книги|" +
      "riveli|militärstammrolle|militärakten|trouwregisters|overlijdensregisters|begraafregisters|baptêmes|naissances|pantebøker|doopboeken|"+
      "ledematenboek|Kirikuraamatud|Tuomiokirjat|jegyzék|Fiches collectie|personregister|Ilmoitusasiat|kirikuraamatud|cedulas|daftar pemakaman"+
   ")\\b|protokoll|stammrolle|regnskabe", Pattern.CASE_INSENSITIVE);
   public static Pattern NOT_RECORD_WORDS = Pattern.compile("\\b("+
      "history|how to|finding|guide"+
   ")\\b", Pattern.CASE_INSENSITIVE);

   public static Pattern OTHER_SCHEDULE_WORDS = Pattern.compile("\\b("+
      "agriculture|agricultural|slave|mortality|manufacturing|territory|territorial|all schedules"+
   ")\\b", Pattern.CASE_INSENSITIVE);

   private PrintWriter out;
   private PrintWriter err;
   public int missingAuthors;
   public int missingPlaces;
   public int missingPlaceIssued;
   public int unknownAuthors;
   public int yearEndAdded;
   public int truncatedTitles;
   public int sameTitle;
   public CountsCollector ccUsers;

   public static String getSinglePlace(Element root, String sourceTitle, String subtitle, String author) {
      String placeCovered = null;
      String tsa = sourceTitle+" "+subtitle+" "+author;
      tsa = tsa.toLowerCase();
      Elements places = root.getChildElements("place");
      for (int i = 0; i < places.size(); i++) {
         Element place = places.get(i);
         if (place.getValue().length() > 0) {
            String placeText = cleanPlace(place.getValue());
            String firstLevel;
            int pos = placeText.indexOf(',');
            if (pos > 0) {
               firstLevel = placeText.substring(0,pos).trim();
            }
            else {
               firstLevel = placeText;
            }
            firstLevel = firstLevel.toLowerCase();

            if (places.size() == 1) {
               placeCovered = placeText;
            }
            else if (!tsa.contains(firstLevel)) {
               // ignore this place
            }
            else if (placeCovered == null) {
               placeCovered = placeText;
            }
            else if (placeCovered.contains(placeText)) {
               // nothing
            }
            else if (placeText.contains(placeCovered)) {
               placeCovered = placeText;
            }
            else {
               return null; // two places, both in title, one is not a substring of the other
            }
         }
      }

      return placeCovered;
   }

   public static boolean isRecordsSubject(String subject) {
      return (subject.length() > 0 && RECORD_TYPES.contains(subject));
   }

   public static boolean hasRecordsWords(String titleSubtitle) {
      Matcher m1 = RECORD_WORDS.matcher(titleSubtitle);
      Matcher m2 = NOT_RECORD_WORDS.matcher(titleSubtitle);
      return m1.find() && !m2.find();
   }

   private static String cleanPlace(String place) {
      // remove |user entered text
      int pos = place.indexOf('|');
      if (pos >= 0) {
         place = place.substring(0, pos);
      }
      return trimTrailingPeriod(place);
   }

   private static String trimTrailingPeriod(String s) {
      while (s.endsWith(".")) {
         s = s.substring(0, s.length()-1);
      }
      return s;
   }


   public CalcSourceTitleRenames(PrintWriter out, PrintWriter err)
   {
      this.out = out;
      this.err = err;
      ccUsers = new CountsCollector();

      missingAuthors = missingPlaces = missingPlaceIssued = unknownAuthors = yearEndAdded = truncatedTitles = sameTitle = 0;
   }

   private String cleanAuthor(String author) {
      if (author.equalsIgnoreCase("anon") || author.equalsIgnoreCase("anon.") || author.equalsIgnoreCase("anonymous") ||
          author.equalsIgnoreCase("(Main Author)")) return "";
      // remvoe author role suffix
      Matcher m = AUTHOR_ROLE_SUFFIX.matcher(author);
      if (m.find()) {
         author = author.substring(0, author.length() - m.group(0).length()).trim();
      }
      m = AUTHOR_PAREN_SUFFIX.matcher(author);
      if (m.find()) {
         author = author.substring(0, author.length() - m.group(0).length()).trim();
      }
      return Util.capitalizeTitleCase(trimTrailingPeriod(author));
   }

   private String cleanPlaceIssued(String place) {
      // remove standardized| place
      int pos = place.indexOf('|');
      if (pos >= 0) {
         return place.substring(pos+1);
      }
      return place;
   }

   private String cleanSourceTitle(String sourceTitle, String subtitle) {
      Matcher m = LEADING_ARTICLE_MATCHER.matcher(sourceTitle);
      if (m.find()) {
         sourceTitle = sourceTitle.substring(m.group(0).length());
      }
      if ((sourceTitle.endsWith("computer printout") || sourceTitle.endsWith("computer printouts")) && subtitle.length() > 0) {
         sourceTitle = sourceTitle+"; "+subtitle;
      }
      return Util.capitalizeTitleCase(trimTrailingPeriod(sourceTitle));
   }

   private String cleanPublisher(String publisher) {
      Matcher m = AUTHOR_PAREN_SUFFIX.matcher(publisher);
      if (m.find()) {
         publisher = publisher.substring(0, publisher.length() - m.group(0).length()).trim();
      }
      return Util.capitalizeTitleCase(publisher);
   }

   private String cleanBars(String s) {
      return s.replace('|',' ');
   }

   private void printError(String msg, String title) throws UnsupportedEncodingException
   {
      err.println("<li>"+msg+" <a href=\"http://www.werelate.org/wiki/"+URLEncoder.encode(title,"UTF-8")+"\">"+Util.encodeXML(title)+"</a>");
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            String unprefixedTitle = title.substring("Source:".length());
            Element root = parseText(structuredData).getRootElement();
            boolean isEdited = !Util.isEmpty(username) && !username.equalsIgnoreCase("WeRelate Agent");

            // get new title
            String newTitle = unprefixedTitle;

            // get source type, author, sourceTitle, place, hasFhlcRepo, hasAncestryRepo
            String sourceType = "";
            String author = "";
            String sourceTitle = "";
            String subtitle = "";
            String place = "";
            String publisher = "";
            String placeIssued = "";
            String dateIssued = "";
            String bookDateIssued = "";
            String subject = "";
            String fromYear = "";
            String toYear = "";
            String censusYear = "";

            Element elm = root.getFirstChildElement("source_type");
            if (elm != null) sourceType = elm.getValue();

            elm = root.getFirstChildElement("author");
            if (elm != null) author = elm.getValue().trim();

            elm = root.getFirstChildElement("source_title");
            if (elm != null) sourceTitle = elm.getValue().trim();
            if (sourceTitle.length() == 0) sourceTitle = unprefixedTitle;

            elm = root.getFirstChildElement("subtitle");
            if (elm != null) subtitle = elm.getValue().trim();

            elm = root.getFirstChildElement("publisher");
            if (elm != null) publisher = elm.getValue().trim();

            elm = root.getFirstChildElement("place_issued");
            if (elm != null) placeIssued = elm.getValue().trim();

            elm = root.getFirstChildElement("date_issued");
            if (elm != null) dateIssued = elm.getValue().trim();

            elm = root.getFirstChildElement("place");
            if (elm != null) place = elm.getValue().trim();

            int numPlaces = root.getChildElements("place").size();

            elm = root.getFirstChildElement("subject");
            if (elm != null) subject = elm.getValue().trim();

            elm = root.getFirstChildElement("from_year");
            if (elm != null) fromYear = elm.getValue().trim();

            elm = root.getFirstChildElement("to_year");
            if (elm != null) toYear = elm.getValue().trim();

            Matcher m = YEAR.matcher(sourceTitle);
            if (m.find()) {
               String temp = m.group(0);
               if (temp.charAt(3) == '0' && !m.find()) {   // should have only one year
                  censusYear = temp;
               }
            }
            else if (fromYear.length() == 4 && fromYear.charAt(3) == '0' && (toYear.length() == 0 || toYear.equals(fromYear))) {
               censusYear = fromYear;
            }
            m = OTHER_SCHEDULE_WORDS.matcher(sourceTitle+" "+subtitle);
            boolean isOtherSchedule = m.find();

            sourceTitle = cleanSourceTitle(sourceTitle, subtitle);

            String singlePlaceCovered = getSinglePlace(root, sourceTitle, subtitle, author);

            // truncate title at : if it's overly-long
            int pos = sourceTitle.indexOf(':');
            if (sourceTitle.length() > 110 && pos > 40 && subtitle.length() == 0) {
               sourceTitle = sourceTitle.substring(0, pos).trim();
            }

            // if county census population schedule
//            if (subject.equals("Census records") &&
//                censusYear.length() == 4 &&
//                singlePlaceCovered != null &&
//                singlePlaceCovered.endsWith("United States") &&
//                (sourceType.length() == 0 || sourceType.equals("Government / Church records") || sourceType.equals("Miscellaneous")) &&
//                Util.countOccurrences(',', singlePlaceCovered) == 2 &&
//                !isOtherSchedule) {
//               newTitle = cleanPlace(singlePlaceCovered)+". "+censusYear+" U.S. Census Population Schedule";
//            }
            // if source type == book/article, author+title
            if (sourceType.equals("Book") || sourceType.equals("Article")) {
               bookDateIssued = dateIssued;
               String cleanAuthor = cleanAuthor(author);
               newTitle = (cleanAuthor.length() > 0 ? cleanAuthor+". "+sourceTitle : sourceTitle);
               if (author.length() == 0) {
                  printError("No author", title);
                  missingAuthors++;
               }
            }
            // if source type == government/church, place+title
            else if (sourceType.equals("Government / Church records")) {
               newTitle = (place.length() > 0 ? cleanPlace(place)+". "+sourceTitle : sourceTitle);
               if (place.length() == 0) {
                  printError("No place", title);
                  missingPlaces++;
               }
            }
            else if (sourceType.equals("Newspaper")) {
               newTitle = (placeIssued.length() > 0 ? sourceTitle+" ("+cleanPlaceIssued(placeIssued)+")" : sourceTitle);
               if (placeIssued.length() == 0) {
                  printError("No place issued", title);
                  missingPlaceIssued++;
               }
            }
            else if (sourceType.equals("Periodical")) {
               newTitle = (publisher.length() > 0 ? sourceTitle+" ("+cleanPublisher(publisher)+")" : sourceTitle);
            }
            else if (sourceType.equals("Website")) {
               if (isRecordsSubject(subject) && numPlaces > 0) {
                  newTitle = cleanPlace(place)+". "+sourceTitle;
               }
               else {
                  newTitle = unprefixedTitle;
               }
            }
//            // if source type != misc/empty, title
//            else if ((sourceType.length() > 0 && !sourceType.equals("Miscellaneous"))) {
//               newTitle = sourceTitle;
//            }
            // Miscellaneous: determine whether the format is place-title or author-title
            else {
               if (isRecordsSubject(subject) && hasRecordsWords(sourceTitle+" "+subtitle) && singlePlaceCovered != null) { // place.title
                  newTitle = cleanPlace(singlePlaceCovered)+". "+sourceTitle;
               }
               else { // author.title
                  String cleanAuthor = cleanAuthor(author);
                  newTitle = (cleanAuthor.length() > 0 ? cleanAuthor+". "+sourceTitle : sourceTitle);
               }
//               if (AnalyzeAuthors.isHumanOrganizationAuthor(author)) {
//                  bookDateIssued = dateIssued;
//                  newTitle = cleanAuthor(author)+". "+sourceTitle;
//               }
//               else if (AnalyzeAuthors.isGovernmentChurchAuthor(author)) {
//                  newTitle = (place.length() > 0 ? cleanPlace(place)+". "+sourceTitle : sourceTitle);
//                  if (place.length() == 0) {
//                     printError("No place ", title);
//                     missingPlaces++;
//                  }
//               }
//               else { // assume human/org author when author is unknown
//                  bookDateIssued = dateIssued;
//                  String cleanAuthor = cleanAuthor(author);
//                  newTitle = (cleanAuthor.length() > 0 ? cleanAuthor+". "+sourceTitle : sourceTitle);
//                  if (cleanAuthor.length() > 0) {
////                     logger.warn("Unknown author: "+cleanAuthor);
//                     unknownAuthors++;
//                  }
//               }
            }

            // keep (yyyy) at end of human-edited titles
            if (isEdited) {
               m = YEAR_END.matcher(unprefixedTitle);
               if (m.find()) {
                  String year = m.group(0);
                  if (!newTitle.endsWith(year)) {
                     newTitle += " "+year;
//                     logger.warn("Year add "+unprefixedTitle);
                     yearEndAdded++;
                  }
               }
            }

            if (newTitle.length() > Util.MAX_TITLE_LEN) {
//               logger.warn("Truncating "+newTitle);
               truncatedTitles++;
            }

            newTitle = Util.prepareWikiTitle(newTitle);
            if (newTitle.equals(unprefixedTitle)) {
               sameTitle++;
            }
            else {
               ccUsers.add(username);
            }
            String titleLine = newTitle+"|"+unprefixedTitle+"|"+cleanBars(author)+"|"+cleanPlace(place)+"|"+sourceTitle+"|"+
                              cleanBars(bookDateIssued)+"|"+cleanBars(subtitle)+"|"+username;

            out.println(titleLine);
         }
      }
   }

   // pages.xml out.txt err.html
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      PrintWriter out = new PrintWriter(new FileWriter(args[1]));
      PrintWriter err = new PrintWriter(new FileWriter(args[2]));
      err.println("<html><head></head><body><ul>");
      CalcSourceTitleRenames cstr = new CalcSourceTitleRenames(out, err);
      wikiReader.addWikiPageParser(cstr);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      err.println("</ul></body></html>");
      err.close();
      in.close();
      out.close();
      System.out.println("Missing authors="+cstr.missingAuthors);
      System.out.println("Missing places="+cstr.missingPlaces);
      System.out.println("Missing place issued="+cstr.missingPlaceIssued);
      System.out.println("Unknown author="+cstr.unknownAuthors);
      System.out.println("Year end added="+cstr.yearEndAdded);
      System.out.println("Truncated titles="+cstr.truncatedTitles);
      System.out.println("Same title="+cstr.sameTitle);
//      cstr.ccUsers.writeSorted(false, 10, new PrintWriter(System.out));
   }
}
