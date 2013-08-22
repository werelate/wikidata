package org.werelate.wikipedia;

import org.apache.log4j.Logger;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;

public class WikiPage {
   private static final Logger logger = Logger.getLogger("org.werelate.wikipedia");

   // match category links
   private static final Pattern CATEGORY_PATTERN = Pattern.compile("\\[\\[[cC]ategory:([^\\|\\]]+)(.*?)\\]", Pattern.DOTALL);

   // match any link
   private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[([^\\|\\]]+)");
   public static final Pattern LINK_TEXT_PATTERN = Pattern.compile("\\[\\[([^\\|\\]]+)\\|?([^\\]]*)\\]\\]");

   // match first-level section headings
   static final Pattern OUTERMOST_HEADING_PATTERN = Pattern.compile("(?<!=)==([^=].*?[^=])==(?!=)", Pattern.DOTALL);
   // match links within section headings. Used to remove them.
   static final Pattern OUTERMOST_HEADING_LINK = Pattern.compile("(?<=(\\n\\s{0,20}==[^=\\n\\[]{0,100}))\\[\\[(.*?)(\\|(.*?))?\\]\\]");
   // match any section heading
   private static final Pattern ANY_HEADING_PATTERN = Pattern.compile("={2,}([^=].*?[^=])={2,}", Pattern.DOTALL);
   // match section headings to save
   public static final Pattern SAVE_HEADING_PATTERN = Pattern.compile("[Hh]istor(?:y|ic)");

   // match variant names in bold, or inside parentheses in italics
   private static final Pattern BOLD_NAME_PATTERN = Pattern.compile("(?<![a-zA-Z])'{3,}([^'].{0,38}?[^'])'{3,}(?![a-zA-Z])", Pattern.DOTALL);
   private static final Pattern PARENTHESES_PATTERN = Pattern.compile("\\(([^\\)]+)\\)");
   private static final Pattern ITAL_NAME_PATTERN = Pattern.compile("(?<!['a-zA-Z])(?:'{2}|'{5})([^'].{0,38}?[^'])(?:'{2}|'{5})(?!['a-zA-Z])", Pattern.DOTALL);

   // match coordinates
   private static final Pattern COOR_PATTERN = Pattern.compile("\\{\\{coor(?:_|\\s+)(d[^\\}]+)\\}\\}");
   //  NOTE: these patterns don't get double's right - they allow multiple .'s - but I think it's good enough
   private static final Pattern LAT_LONG_PATTERN = Pattern.compile("(\\-?[\\d\\.]+(?:°|&deg;)(?:\\s*[\\d\\.]+'?(?:\\s*[\\d\\.]+\"?)?)?\\s*(?:N|North|S|South))" +
                                                             ".?\\s*(\\-?[\\d\\.]+(?:°|&deg;)(?:\\s*[\\d\\.]+'?(?:\\s*[\\d\\.]+\"?)?)?\\s*(?:E|East|W|West))");
   private static final Pattern DOUBLE_PATTERN = Pattern.compile("\\-?[\\d\\.]+");
   private static final double INVALID_LAT_LONG = -1000.0;

   // the following patterns are used in skipping the prologue
   private static final Pattern OPEN_TAG_PATTERN = Pattern.compile("(\\{\\{|\\{\\||\\[\\[|<table[^>]*>|<!--)");
   private static final Pattern CLOSE_TAG_PATTERN = Pattern.compile("(\\}\\}|\\|\\}|\\]\\]|</table>|-->)");
   private static final Pattern OPEN_CLOSE_TAG_PATTERN = Pattern.compile("\\{\\{|\\{\\||\\[\\[|<table[^>]*>|<!--|\\}\\}|\\|\\}|\\]\\]|</table>|-->");
   private static final Pattern DASH_LINE_PATTERN = Pattern.compile("--+\n");
   private static final Pattern PARA_END_PATTERN = Pattern.compile("</p>|<p>|<p |(?:<br>|<br/>|\n)|(?:<br>|<br/>|\n)(?=---|\\{|\\[)");
   private static final Pattern PARA_ITAL_END = Pattern.compile("''|</p>|<p>|<p |(?:<br>|<br/>|\n)|(?:<br>|<br/>|\n)(?=---|\\{|\\[)");
   private static final Pattern JUNK_PATTERN = Pattern.compile("[\\s\\.,;\\?!\\}\\|]+");

   // used to located templates and links
   private static final Pattern OPEN_CLOSE_TEMPLATE_LINK_CDATA = Pattern.compile("\\{\\{|\\[\\[|<!\\[CDATA\\[|\\}\\}|\\]\\](?!>)|\\]\\]>");

   // map phrases and words to types
   // if you add a new type, you must add it to WikiPage, PlaceStandard and StandardMerger
   private static final String[][] TYPE_MATCHES = {
      // match phrases before single words
      {"arrondissements", "Arrondissement"},
      {"autonomous communities", "Autonomous community"},
      {"census areas", "Census area"},
      {"census divisions", "Census division"},
      {"census-designated places", "Census-designated place"},
      {"cities and towns", "City or town"},
      {"county towns", "County town"},
      {"defunct cities", "Defunct city"},
      {"former administrative counties", "Former administrative county"},
      {"former boroughs", "Former borough"},
      {"former provinces", "Former province"},
      {"ghost towns", "Ghost town"},
      {"independent cities", "Independent city"},
      {"lieutenancy areas", "Lieutenancy area"},
      {"preserved counties", "Preserved county"},
      {"principal areas", "Principal area"},
      {"provinces and territories", "Province or territory"},
      {"regierungsbezirk", "Regierungsbezirk"},
      {"regional county municipalities", "Regional county municipality"},
      {"regional districts", "Regional district"},
      {"towns and villages", "Town or village"},
      {"towns and cities", "City or town"},
      {"traditional counties", "Traditional county"},
      {"unitary authorities", "Unitary authority"},
      {"boroughs", "Borough"},
      {"cities", "City"},
      {"comarcas", "Comarca"},
      {"comarques", "Comarca"},
      {"communes", "Commune"},
      {"communities", "Community"},
      {"counties", "County"},
      {"countries", "Country"},
      {"dioceses", "Diocese"},
      {"districts", "District"},
      {"département", "Département"},
      {"municipalities", "Municipality"},
      {"oblasts", "oblast"},
      {"parishes", "Parish"},
      {"prefectures", "prefecture"},
      {"provinces", "Province"},
      {"regions", "Region"},
      {"states", "State"},
      {"towns", "Town"},
      {"townships", "Township"},
      {"villages", "Village"},
      {"voivodships", "voivodship"},
      {"{{italy}}", "Region"},
      {"{{zupanije}}", "County"},

   };
   private static final String UNKNOWN_TYPE = "Unknown";

   // Category substitutes are strings that assign pages to categories
   // like categories but used when there isn't an appropriate category available
   // need to represent each template with and without the first letter capitalized, since both go the the same template in Wikipedia
   // also represent templates with and without spaces, since both go to the same template in wikipedia
   private static final Pattern[] CATEGORY_TEMPLATES = {
      Pattern.compile("\\{\\{[dD]épartement\\}\\}"),
      Pattern.compile("\\{\\{[dD]épartement[_\\s]list\\}\\}"),
      Pattern.compile("\\{\\{[iI]taly\\}\\}"),
      Pattern.compile("\\{\\{[rR]egions[_\\s]of[_\\s]Italy\\}\\}"),
      Pattern.compile("\\{\\{[rR]egions[_\\s]of[_\\s]France\\}\\}"),
      Pattern.compile("\\{\\{[zZ]upanije\\}\\}"),
      Pattern.compile("\\{\\{[iI]nfobox[_\\s]Canton"),
      Pattern.compile("\\{\\{[rR]egions[_\\s]of[_\\s]Slovakia"),
      Pattern.compile("\\{\\{[cC]ities[_\\s]of[_\\s]Bosnia[_\\s]and[_\\s] Herzegovina"),
      Pattern.compile("\\{\\{[oO]blasti\\}\\}"),
      Pattern.compile("\\{\\{[pP]rovinces[_\\s]of[_\\s]Turkey\\}\\}"),
      Pattern.compile("\\{\\{[pP]rovinces[_\\s]of[_\\s]the[_\\s]Netherlands\\}\\}"),
      Pattern.compile("\\{\\{[fF]ooter[_\\s]Provinces[_\\s]of[_\\s]the[_\\s]Netherlands\\}\\}"),
      Pattern.compile("\\{\\{[jJ]udete\\}\\}"),
      Pattern.compile("\\{\\{[mM]unicipalities[_\\s]of[_\\s]Lithuania\\}\\}"),
      Pattern.compile("\\{\\{[pP]rovinces[_\\s]of[_\\s]South[_\\s]Africa\\}\\}"),
      Pattern.compile("\\{\\{[cC]olombianDepartments\\}\\}"),
      Pattern.compile("\\{\\{[dD]epartments[_\\s]of[_\\s]Colombia\\}\\}"),
      Pattern.compile("\\{\\{[rR]egions[_\\s]of[_\\s]Chile\\}\\}"),
      Pattern.compile("\\{\\{[pP]rovinces[_\\s]of[_\\s]Cuba\\}\\}"),
      Pattern.compile("\\{\\{[dD]epartments[_\\s]of[_\\s]Paraguay\\}\\}"),
      Pattern.compile("\\{\\{[sS]omalia[_\\s][rR]egions\\}\\}"),
      Pattern.compile("\\{\\{[rR]egions[_\\s]of[_\\s][Tt]anzania\\}\\}"),
      Pattern.compile("\\{\\{[mM]ali[_\\s][Rr]egions\\}\\}"),
      Pattern.compile("\\{\\{[Bb]eijing\\}\\}"),
      Pattern.compile("\\{\\{[Tt]ianjin\\}\\}"),
      Pattern.compile("\\{\\{[Hh]ebei\\}\\}"),
      Pattern.compile("\\{\\{Shanxi\\}\\}"),
      Pattern.compile("\\{\\{Inner Mongolia\\}\\}"),
      Pattern.compile("\\{\\{Liaoning\\}\\}"),
      Pattern.compile("\\{\\{Jilin\\}\\}"),
      Pattern.compile("\\{\\{Heilongjiang\\}\\}"),
      Pattern.compile("\\{\\{Shanghai\\}\\}"),
      Pattern.compile("\\{\\{Jiangsu\\}\\}"),
      Pattern.compile("\\{\\{Zhejiang\\}\\}"),
      Pattern.compile("\\{\\{Anhui\\}\\}"),
      Pattern.compile("\\{\\{Fujian\\}\\}"),
      Pattern.compile("\\{\\{Jiangxi\\}\\}"),
      Pattern.compile("\\{\\{Shandong\\}\\}"),
      Pattern.compile("\\{\\{Henan\\}\\}"),
      Pattern.compile("\\{\\{Hubei\\}\\}"),
      Pattern.compile("\\{\\{Hunan\\}\\}"),
      Pattern.compile("\\{\\{Guangdong\\}\\}"),
      Pattern.compile("\\{\\{Guangxi\\}\\}"),
      Pattern.compile("\\{\\{Hainan\\}\\}"),
      Pattern.compile("\\{\\{Chongqing\\}\\}"),
      Pattern.compile("\\{\\{Sichuan\\}\\}"),
      Pattern.compile("\\{\\{Guizhou\\}\\}"),
      Pattern.compile("\\{\\{Yunnan\\}\\}"),
      Pattern.compile("\\{\\{TAR\\}\\}"),
      Pattern.compile("\\{\\{Shaanxi\\}\\}"),
      Pattern.compile("\\{\\{Gansu\\}\\}"),
      Pattern.compile("\\{\\{Qinghai\\}\\}"),
      Pattern.compile("\\{\\{Ningxia\\}\\}"),
      Pattern.compile("\\{\\{Xinjiang\\}\\}"),
      Pattern.compile("\\{\\{Islands\\sof\\sthe\\sMaldives\\}\\}"),
      Pattern.compile("\\{\\{[Pp]rovinces\\sof\\s[Tt]hailand\\}\\}"),
      Pattern.compile("\\{\\{[Cc]ape\\s[Vv]erde/[Mm]unicipalities"),
      Pattern.compile("\\{\\{[Dd]istricts[\\s_]of[\\s_][Ss]uriname\\}\\}"),
   };
   private static final String[] CATEGORY_TEMPLATE_TITLES = {
      "{{Département}}",
      "{{Département list}}",
      "{{Italy}}",
      "{{Regions of Italy}}",
      "{{Regions of France}}",
      "{{Zupanije}}",
      "{{Infobox Canton",
      "{{Regions of Slovakia",
      "{{Cities of Bosnia and Herzegovina}}",
      "{{Oblasti}}",
      "{{Provinces of Turkey}}",
      "{{Provinces of the Netherlands}}",
      "{{Footer Provinces of the Netherlands}}",
      "{{judete}}",
      "{{Municipalities of Lithuania}}",
      "{{Provinces of South Africa}}",
      "{{ColombianDepartments}}",
      "{{Departments of Colombia}}",
      "{{Regions of Chile}}",
      "{{Provinces of Cuba}}",
      "{{Departments of Paraguay}}",
      "{{Somalia Regions}}",
      "{{Regions of Tanzania}}",
      "{{Mali regions}}",
           "{{Beijing}}",
           "{{Tianjin}}",
           "{{Hebei}}",
           "{{Shanxi}}",
           "{{Inner Mongolia}}",
           "{{Liaoning}}",
           "{{Jilin}}",
           "{{Heilongjiang}}",
           "{{Shanghai}}",
           "{{Jiangsu}}",
           "{{Zhejiang}}",
           "{{Anhui}}",
           "{{Fujian}}",
           "{{Jiangxi}}",
           "{{Shandong}}",
           "{{Henan}}",
           "{{Hubei}}",
           "{{Hunan}}",
           "{{Guangdong}}",
           "{{Guangxi}}",
           "{{Hainan}}",
           "{{Chongqing}}",
           "{{Sichuan}}",
           "{{Guizhou}}",
           "{{Yunnan}}",
           "{{TAR}}",
           "{{Shaanxi}}",
           "{{Gansu}}",
           "{{Qinghai}}",
           "{{Ningxia}}",
           "{{Xinjiang}}",
           "{{Islands of the Maldives}}",
      "{{Provinces of Thailand}}",
      "{{Municipalities of Cape Verde}}",
      "{{Districts of Suriname}}"
   };

   // added on the line after <!-- wikipedia(#section)
   private static final String COMMENT_START = "\n<!-- wikipedia";
   private static final String COMMENT_END =
           " is updated periodically from the Wikipedia article.  " +
           "If you wish to modify it, you need to make the same change to the Wikipedia article so your edits aren't lost in the next update.  " +
           "Alternatively, if you feel that the text isn't sufficiently relevant to genealogy you can replace it with your " +
           "own text and remove this comment, which will discontinue future updates.\n-->\n";
   private static final String OPENING_COMMENT =
           "\nThis text before the first heading";
   private static final String HEADER_COMMENT =
           "\nThe text under this heading";

   private String title;
   private List earlyLinks;
   private List categories;
   private String text;
   private String openingPara;

   public static String getCommentText(String header) {
      if (header == null || header.length() == 0) {
         return COMMENT_START + OPENING_COMMENT + COMMENT_END;
      }
      else {
         return COMMENT_START + "#" + header + HEADER_COMMENT + COMMENT_END;
      }
   }

   public static String standardizeTitle(String text) {
      int pos = 0;
      if (text.startsWith("{{")) { // why are we doing this?
         pos = 2;
      }
      if (Character.isLowerCase(text.charAt(pos))) {
         text = text.substring(0, pos) + text.substring(pos, pos+1).toUpperCase() + text.substring(pos+1);
      }
      if (text.indexOf("_") >= 0) {
         text = text.replace('_', ' ');
      }
      return text.trim();
   }

   public static List getCategories(String text) {
      List categories = new ArrayList(5);
      Matcher m = CATEGORY_PATTERN.matcher(text);
      while (m.find()) {
         String category = m.group(1).trim();
         String linkText = m.group(2);
         // skip categories with empty or * link text
         if (category.length() > 0 && !(linkText.equals("|") || linkText.equals("| ") || linkText.startsWith("|*"))) {
            categories.add(standardizeTitle(category));
         }
      }
      for (int i = 0; i < CATEGORY_TEMPLATES.length; i++) {
         m = CATEGORY_TEMPLATES[i].matcher(text);
         if (m.find()) {
            categories.add(standardizeTitle(CATEGORY_TEMPLATE_TITLES[i]));
         }
      }
      return categories;
   }

   public static String getRedirectTitle(String text) {
      Matcher m = Util.REDIRECT_PATTERN.matcher(text);
      if (m.lookingAt()) {
         return standardizeTitle(m.group(1));
      }
      return null;
   }

   private static String getUpToHeading(String text) {
      Matcher m = ANY_HEADING_PATTERN.matcher(text);
      if (m.find()) {
         return text.substring(0, m.start());
      }
      else {
         return text;
      }
   }

   public static String getFirstPara(String text) {
      Matcher m = PARA_END_PATTERN.matcher(text);
      if (m.find()) {
         return text.substring(0, m.start());
      }
      return text;
   }

   private static List getLinks(String text) {
      List links = new ArrayList(5);
      Matcher m = LINK_PATTERN.matcher(text);
      while (m.find()) {
         String link = standardizeTitle(m.group(1));
         // don't keep links to other namespaces
         if (link.indexOf(":") == -1 && !links.contains(link)) {
            links.add(link);
         }
      }
      return links;
   }

   private static String cleanName(String name, boolean removeParenthetical) {
      // remove everything after comma
      name = name.replaceAll(",.*", "");
      if (removeParenthetical) {
         // remove everything between parentheses
         name = name.replaceAll("\\(.*?\\)", "");
      }
      // collapse multiple spaces to single space and trim
      return name.replaceAll("\\s+", " ").replaceAll(" ,", ",").trim();
   }

//   private String removeOutermostHeadingLinks(String text) {
//       StringBuffer buf = new StringBuffer();
//       Matcher m = WikiPage.OUTERMOST_HEADING_LINK.matcher(text);
//       while (m.find())
//       {
//           if (Util.isEmpty(m.group(3))) m.appendReplacement(buf, "$2");
//           else m.appendReplacement(buf, "$3");
//       }
//       m.appendTail(buf);
//       return buf.toString();
//   }

   public WikiPage(String title, String text) {
      this.title = title;
      this.text = text;
      this.categories = getCategories(text);
      String postPrologue = skipPrologue(text);
      String openingSection = getUpToHeading(postPrologue);
      this.openingPara = getFirstPara(openingSection);
      // get early links from the prologue and opening section
      this.earlyLinks = getLinks(text.substring(0, text.length() - postPrologue.length() + openingSection.length()));
   }

   public String getTitle() {
      return title;
   }

   public int getEarlyLinkPos(String link) {
      return earlyLinks.indexOf(link);
   }

   public List getEarlyLinks() {
      return earlyLinks;
   }

   public String getPreferredName() {
      return cleanName(standardizeTitle(title), true);
   }

   private void addVariantName(Set variantNames, String variantName, int start) {
      // add names only if they don't contain embedded links or italic tags, start within the first 150 characters of
      // the beginning of the para, and start with an uppercase
      variantName = variantName.trim();
      if (variantName.indexOf("[") == -1 &&
              variantName.indexOf("]") == -1 &&
              variantName.indexOf("''") == -1 &&
              variantName.length() > 0 &&
              Character.isUpperCase(variantName.charAt(0)) &&
              start <= 150) {
         // need to remove things in parentheses, after comma - just like for the preferred name
         variantName = cleanName(standardizeTitle(variantName), true);
         if (variantName.length() > 0) {
            variantNames.add(variantName);
         }
      }
   }

   public Set getVariantNames() {
      Set variantNames = new HashSet();
      // get all of the bolded phrases
      Matcher m = BOLD_NAME_PATTERN.matcher(openingPara);
      while (m.find()) {
         addVariantName(variantNames, m.group(1), m.start());
      }

      // get the first two parenthetical phrases
      Matcher pm = PARENTHESES_PATTERN.matcher(openingPara);
      int cnt = 0;
      while (pm.find() && cnt++ < 2) {
         String parentheticalPhrase = pm.group(0);
         int pmStart = pm.start();
         m = ITAL_NAME_PATTERN.matcher(parentheticalPhrase);
         while (m.find()) {
            addVariantName(variantNames, m.group(1), pmStart + m.start());
         }
      }
      return variantNames;
   }

   public List getPageCategories() {
      return categories;
   }

   public String getType() {
      Matcher pm = PARENTHESES_PATTERN.matcher(title);
      if (pm.find()) {
         String parentheticalPhrase = pm.group(1);
         for (int j = 0; j < TYPE_MATCHES.length; j++) {
            if (parentheticalPhrase.equalsIgnoreCase(TYPE_MATCHES[j][1])) {
               return TYPE_MATCHES[j][1];
            }
         }
      }
      Iterator i = getPageCategories().iterator();
      while (i.hasNext()) {
         String category = ((String)i.next()).toLowerCase();
         for (int j = 0; j < TYPE_MATCHES.length; j++) {
            if (category.indexOf(TYPE_MATCHES[j][0]) >= 0) {
               return TYPE_MATCHES[j][1];
            }
         }
      }
      return UNKNOWN_TYPE;
   }

   public String getFullTitle(Map titleToPage, Map titleToParentTitle, String countryTitle) {
      StringBuffer buf = new StringBuffer();
      String finalTitle = cleanName(standardizeTitle(title), false);
      buf.append(finalTitle);
      String parentTitle = (String)titleToParentTitle.get(title);
      int c = 0;
      while (parentTitle != null) {
         WikiPage parent = (WikiPage)titleToPage.get(parentTitle);
         if (parent == null) {
            logger.error("Page not found: " + parentTitle + " for: " + getTitle());
            break;
         }
         buf.append(", ");
         finalTitle = parent.getPreferredName();
         buf.append(finalTitle);
         parentTitle = (String)titleToParentTitle.get(parentTitle);
         if (++c > 10) {
            logger.error("Infinite loop for parent title of: " + title);
            break;
         }
      }
      // append the country at the very end if it hasn't already been appended
      if (!finalTitle.equals(countryTitle)) {
         buf.append(", ");
         buf.append(countryTitle);
      }
      return buf.toString();
   }


   private double parseLatLong(String text) {

      Matcher m = DOUBLE_PATTERN.matcher(text);
      double divisor = 1.0;
      double latLong = 0.0;
      while (m.find()) {
         try {
            latLong += Double.parseDouble(m.group(0))/divisor;
            divisor *= 60.0;
         }
         catch (NumberFormatException e) {
            return INVALID_LAT_LONG;
         }
      }
      if (text.endsWith("S") || text.endsWith("South") ||
          text.endsWith("W") || text.endsWith("West")) {
         latLong = - latLong;
      }
      return latLong;
   }

   private static class LatLong {
      double latitude;
      double longitude;
      LatLong(double latitude, double longitude) {
         this.latitude = latitude;
         this.longitude = longitude;
      }
   }

   private LatLong getCoords() {
      Matcher m = COOR_PATTERN.matcher(text);
      while (m.find()) {
         String[] fields = m.group(1).split("\\|");
         if (fields.length > 0) {
            try {
               double latitude = 0.0;
               double longitude = 0.0;
               int nsField = -1;
               int ewField = -1;
               if (fields[0].equalsIgnoreCase("d") && fields.length == 5) {
                  latitude = Double.parseDouble(fields[1]);
                  longitude = Double.parseDouble(fields[3]);
                  nsField = 2;
                  ewField = 4;
               }
               else if (fields[0].equalsIgnoreCase("dm") && fields.length == 7) {
                  latitude = Double.parseDouble(fields[1]) + Double.parseDouble(fields[2])/60.0;
                  longitude = Double.parseDouble(fields[4]) + Double.parseDouble(fields[5])/60.0;
                  nsField = 3;
                  ewField = 6;
               }
               else if (fields[0].equalsIgnoreCase("dms") && fields.length == 9) {
                  latitude = Double.parseDouble(fields[1]) + Double.parseDouble(fields[2])/60.0 + Double.parseDouble(fields[3])/3600.0;
                  longitude = Double.parseDouble(fields[5]) + Double.parseDouble(fields[6])/60.0 + Double.parseDouble(fields[7])/3600.0;
                  nsField = 4;
                  ewField = 8;
               }
               if (nsField >= 0 && ewField >= 0) {
                  if (fields[nsField].startsWith("s") || fields[nsField].startsWith("S")) {
                     latitude = -latitude;
                  }
                  if (fields[ewField].startsWith("w") || fields[ewField].startsWith("W")) {
                     longitude = -longitude;
                  }
                  return new LatLong(latitude, longitude);
               }
            }
            catch (NumberFormatException e) {
               continue;
            }
         }
      }

      m = LAT_LONG_PATTERN.matcher(text);
      while (m.find()) {
         double latitude = parseLatLong(m.group(1));
         double longitude = parseLatLong(m.group(2));
         if (latitude > INVALID_LAT_LONG && longitude > INVALID_LAT_LONG) {
            return new LatLong(latitude, longitude);
         }
      }
      return null;
   }

   public String getLatitude() {
      LatLong latLong = getCoords();
      if (latLong != null) {
         return Double.toString(latLong.latitude);
      }
      return null;
   }

   public String getLongitude() {
      LatLong latLong = getCoords();
      if (latLong != null) {
         return Double.toString(latLong.longitude);
      }
      return null;
   }

   public String getFullText() {
      return text;
   }

   // Remove templates and links to other namespaces and CData sections
   public static String cleanText(StringBuffer buf) {
      List templatesLinks = locateOutermostTemplatesLinksCdata(buf);
      for (int i = templatesLinks.size()-1; i >= 0; i--) {
         StartEnd range = (StartEnd)templatesLinks.get(i);
         String tag = buf.substring(range.start, range.end);
         if (tag.startsWith("{{")) {
            // keep only coordinate templates
            Matcher m = COOR_PATTERN.matcher(tag);
            if (!m.matches()) {
               buf.delete(range.start, range.end);
            }
         }
         else if (tag.startsWith("[[")) {
            Matcher m = LINK_PATTERN.matcher(tag);
            String href = null;
            if (m.lookingAt()) {
               href = m.group(1);
            }
            if (href == null || href.indexOf(":") >= 0 || tag.indexOf("[[", 2) >= 0 || tag.indexOf("{{") >= 0) {
               // malformed or points to another namespace
               buf.delete(range.start, range.end);
            }
         }
         else { // CData
            buf.delete(range.start, range.end);
         }
      }
      return buf.toString().replaceAll("''''", "").replaceAll("<gallery>[^<>]*</gallery>", "").
            replaceAll("<ref[^<>]*>[^<>]*</ref>", "").replaceAll("\\s*\\[\\]", "").
            replaceAll("\\(\\s*,\\s*", "(").replaceAll("\\s*,\\s*\\)", ")").
            replaceAll("\\s*\\([^()]*\\[\\[(IPA|International Phonetic Alphabet)\\|[^\\[\\]()]*\\]\\][^()]*\\)", "").
            replaceAll("\\s*\\(([,;]|\\s)*\\)", "");
   }

   public String getReducedText() {
      StringBuffer buf = new StringBuffer();

      String postPrologue = skipPrologue(text);

      // add the opening section
      String openingSection = getUpToHeading(postPrologue);
      if (openingSection.length() > 0) {
         buf.insert(0, getCommentText(null));
      }
      buf.append(openingSection);

      Matcher headMatch = OUTERMOST_HEADING_PATTERN.matcher(postPrologue);
      int saveHeadingStart = -1;
      int saveHeadingEnd = -1;
      String saveHeading = null;
      while (headMatch.find()) {
         int headingStart = headMatch.start();
         int headingEnd = headMatch.end();
         String heading = headMatch.group(1).replaceAll("\\s", " ").trim();
         if (saveHeadingStart >=0) {
            buf.append(postPrologue.substring(saveHeadingStart, saveHeadingEnd));
            buf.append(getCommentText(saveHeading));
            buf.append(postPrologue.substring(saveHeadingEnd, headingStart));
            saveHeadingStart = -1;
         }

         Matcher saveMatch = SAVE_HEADING_PATTERN.matcher(heading);
         if (saveMatch.find()) {
            saveHeadingStart = headingStart;
            saveHeadingEnd = headingEnd;
            saveHeading = heading;
         }
      }
      if (saveHeadingStart >= 0) {
         buf.append(postPrologue.substring(saveHeadingStart, saveHeadingEnd));
         buf.append(getCommentText(saveHeading));
         buf.append(postPrologue.substring(saveHeadingEnd));
      }

      return cleanText(buf);
   }

   public String getOpeningSection() {
      return getUpToHeading(skipPrologue(text));
   }

   public String getOpeningPara() {
      return openingPara;
   }

   public Set <String> getSectionHeadings() {
      Set <String> sectionHeadings = new HashSet <String> ();

      String postPrologue = skipPrologue(text);
      Matcher headMatch = OUTERMOST_HEADING_PATTERN.matcher(postPrologue);
      while (headMatch.find()) {
         String heading = headMatch.group(1).replaceAll("\\s", " ").trim();
         sectionHeadings.add(heading);
      }

      return sectionHeadings;
   }

   // case-insensitive match on the secionName
   public String getSection(String sectionName) {
      String postPrologue = skipPrologue(text);
      Matcher headMatch = OUTERMOST_HEADING_PATTERN.matcher(postPrologue);
      int startFrom = 0;
      sectionName = sectionName.toLowerCase();
      while (headMatch.find(startFrom)) {
         String heading = headMatch.group(1).replaceAll("\\s", " ").toLowerCase();
         if (heading.indexOf(sectionName) != -1)
         {
             int secStart = headMatch.end();
             if (headMatch.find(secStart))
             {
                 return postPrologue.substring(secStart, headMatch.start());
             }
             else return postPrologue.substring(secStart, postPrologue.length());
         }
         startFrom = headMatch.end();
      }
      return null;
   }

   public static class StartEnd {
      int start;
      int end;
      public StartEnd(int start, int end) {
         this.start = start;
         this.end = end;
      }
   }

   private static List locateOutermostTemplatesLinksCdata(CharSequence text) {
      List templatesLinks = new ArrayList();
      int openTags = 0;
      int outermostStart = 0;
      Matcher openClose = OPEN_CLOSE_TEMPLATE_LINK_CDATA.matcher(text);
      while (openClose.find()) {
         String tag = openClose.group(0);
         if (tag.equals("{{") || tag.equals("[[") || tag.equals("<![CDATA[")) {
            if (openTags == 0) {
               outermostStart = openClose.start();
            }
            openTags++;
         }
         else {
            openTags--;
            if (openTags == 0) {
               templatesLinks.add(new StartEnd(outermostStart, openClose.end()));
            }
            else if (openTags < 0) {
               openTags = 0;  // malformed
            }
         }
      }
      return templatesLinks;
   }

   // Skip [[*]], {{*}}, tables (all of which can be nested), <tag *>, --- lines, and paragraphs that begin with : or ''
   // also line breaks, spaces, and .,; (junk)
   private static final String [][] openCloseTable = {
           {"{{", "}}"},
           {"{|", "|}"},
           {"[[", "]]"},
           {"<table>", "</table>"},
           {"<!--", "-->"},
   };
   private static final Map <String, String> closeOpenMap = new HashMap <String, String>();
   { for (String [] tableEntry : openCloseTable) closeOpenMap.put(tableEntry[1], tableEntry[0]); }
   private String skipPrologue(String text) {
      StringBuffer buf = new StringBuffer(text);
      int openTags = 0;
      boolean match = true;
      Stack <String> tagStack = new Stack <String> ();
      while (match && buf.length() > 0) {
         match = false;
         Matcher open = OPEN_TAG_PATTERN.matcher(buf);
         Matcher dashLine = DASH_LINE_PATTERN.matcher(buf);
         Matcher junk = JUNK_PATTERN.matcher(buf);
         Matcher close = CLOSE_TAG_PATTERN.matcher(buf);
         if (open.lookingAt()) { // must match at the beginning
            String openTag = open.group(1);
            buf.delete(0, open.end());
            Matcher openClose = OPEN_CLOSE_TAG_PATTERN.matcher(buf);
            if (tagStack.size() > 0 && tagStack.peek().compareTo("[[") == 0
                    && openTag.compareTo("[[") == 0)
            {
                // We will ignore this double link opening and print out a warning.
                logger.info("Wikipedia article \"" + getTitle() + "\" has [[[[");
            }
            else
            {
               if (openTag.contains("table")) tagStack.push("<table>");
               else tagStack.push(openTag);
               openTags++;  // don't require that the closing tag is the counterpart to this opening tag - too much trouble
            }
            if (openClose.find()) {
               buf.delete(0, openClose.start());  // delete up to but not including the next open/close tag
               match = true;
            }
            else {
               // malformed
               return "";
            }
         }
         else if (openTags > 0) {
            if (close.lookingAt()) {
               String closeTag = close.group(1);
               String openTag = closeOpenMap.get(closeTag);
               String interTag;
               if (tagStack.search(openTag) == -1)
               {
                   logger.info("Wikipedia article \"" + getTitle() + "\" has " + closeTag +
                           " with no opening " + openTag);
               }
               else
               {
                   while (openTag.compareTo(tagStack.peek()) != 0)
                   {
                       openTags--;
                       interTag=tagStack.pop();
                       logger.info("Wikipedia article \"" + getTitle() + "\" has misplaced " + interTag);
                   }
                   openTags--;
                   tagStack.pop();
               }
               buf.delete(0, close.end());
               // if we're nested, the skip up to the next open/close tag
               if (openTags > 0) {
                  Matcher openClose = OPEN_CLOSE_TAG_PATTERN.matcher(buf);
                  if (openClose.find()) {
                     buf.delete(0, openClose.start());
                  }
                  else {
                     // malformed
                     return "";
                  }
               }
               match = true;
            }
            else {
               // malformed
               return "";
            }
         } else if (close.lookingAt())
         {
             String closeTag = close.group(1);
             String openTag = closeOpenMap.get(closeTag);
             logger.info("Wikipedia article \"" + getTitle() + "\" has " + closeTag +
                           " with no opening " + openTag);
             // We're going to skip it and move on, ignoring this tag.
             buf.delete(0, close.end());
             match = true;
         }
         // for <tag>'s, skip everything up to the end > of the tag
         else if (buf.charAt(0) == '<' && buf.length() >= 2 && (buf.charAt(1) == '/' || Character.isLetter(buf.charAt(1)))) {
            int pos = buf.indexOf(">");
            if (pos != -1) {
               buf.delete(0, pos+1);
               match = true;
            }
            else {
               // malformed
               return "";
            }
         }
         else if (junk.lookingAt()) {
            buf.delete(0, junk.end());
            match = true;
         }
         else if (dashLine.lookingAt()) {
            buf.delete(0, dashLine.end());
            match = true;
         }
         else if (buf.indexOf(":") == 0) {
            Matcher m = PARA_END_PATTERN.matcher(buf);
            if (m.find()) {
               buf.delete(0, m.end());
               match = true;
            }
            else {
               return "";
            }
         }
         else if (buf.indexOf("''") == 0 && buf.length() >= 3 && buf.charAt(2) != '\'') {
            buf.delete(0, 2);
            Matcher m = PARA_ITAL_END.matcher(buf);
            if (m.find()) {
               buf.delete(0, m.end());
               match = true;
            }
            else {
               return "";
            }
         }
      }
      return buf.toString();
   }
}
