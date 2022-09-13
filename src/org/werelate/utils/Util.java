package org.werelate.utils;

import com.ibm.icu.text.Normalizer;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Util
{
   private static final Logger logger = LogManager.getLogger("org.werelate.utils");

   /** max wiki title length */
   public static final int MAX_TITLE_LEN = 150;

   // match redirect titles
   public static final Pattern REDIRECT_PATTERN = Pattern.compile("\\s*#redirect:?\\s*\\[\\[([^\\|\\]]+)[^\\]]*\\]\\]", Pattern.CASE_INSENSITIVE);


// Google romanizes the following
//   private static final char[] SPECIAL_CHARS =            {'´', 'ß',  'ј', 'ð', 'æ',  'ł', 'đ',  'ø',  'ŀ', 'і', 'þ',  'ı', 'œ',  'ĳ',
//                                                                      'Ј', 'Ð', 'Æ',  'Ł', 'Đ',  'Ø',  'Ŀ', 'І', 'Þ',       'Œ',  'Ĳ'};
//   private static final char[] SPECIAL_CHARS =              {180, 223, 1112, 240, 230,  322, 273,  248,  320,1110, 254,  305, 339,  307,
//                                                                       1032, 208, 198,  321, 272,  216,  319,1030, 222,       338,  306};
//   private static final String[] SPECIAL_TRANSLITERATIONS = {"'", "ss", "j", "d", "ae", "l", "dj", "oe", "l", "i", "th", "i", "oe", "y",
//                                                                        "J", "D", "Ae", "L", "Dj", "Oe", "L", "I", "Th",      "Oe", "Y"};

   /**
    * Convert non-roman but roman-like letters in the specified string to their roman (a-zA-Z) equivalents.
    * For example, strip accents from characters, and expand ligatures.
    * From Ancestry names code by Lee Jensen and Dallan Quass
    * @param s string to romanize
    * @return romanized word, may contain non-roman characters from non-roman-like alphabets like greek, arabic, hebrew
    */
   public static String romanize(String s) {
      if (s == null) {
         return "";
      }
      if (isAscii(s)) {
         return s;
      }

      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         String replacement;
         if ((int)c > 127 && (replacement = CHARACTER_MAPPINGS.get(c)) != null) {
            buf.append(replacement);
         }
         else {
            buf.append(c);
         }
      }
      return buf.toString();
   }

   public static String translateHtmlCharacterEntities(String in) {
      if (in == null) {
         return in;
      }
      StringBuffer buf = null;
      Matcher m = HTML_ENTITY_PATTERN.matcher(in);
      while (m.find()) {
         if (buf == null) {
            buf = new StringBuffer();
         }
         m.appendReplacement(buf, (String)HTML_ENTITY_MAP.get(m.group(1)));
      }
      if (buf == null) {
         return in;
      }
      else {
         m.appendTail(buf);
         return buf.toString();
      }
   }

   public static String standardizeWikiLink(String t) {
      // trim
      // convert _ to space
      // convert %nn -- URLDecoder doesn't do exactly the right thing here, but it should be close enough
      try
      {
         return URLDecoder.decode(t.replace('_',' ').trim(),"utf8");
      }
      catch (UnsupportedEncodingException e)
      {
         throw new RuntimeException(e.getMessage());
      }
   }

   private static final String [][] XML_CHARS = {
      {"&", "&amp;"},
      {"<", "&lt;"},
      {">", "&gt;"},
      {"\"", "&quot;"},
      {"'", "&apos;"},
   };

   public static String encodeXML(String text) {
      for (int i=0; i < XML_CHARS.length; i++) {
         text = text.replace(XML_CHARS[i][0], XML_CHARS[i][1]);
      }
      return text;
   }

   public static String unencodeXML(String text) {
      for (int i=XML_CHARS.length-1; i >= 0; i--) {
         text = text.replace(XML_CHARS[i][1], XML_CHARS[i][0]);
      }
      return text;
   }

   /**
    * Returns whether the specified string is null or has a zero length
    * @param s
    * @return boolean
    */
   public static boolean isEmpty(String s) {
      return (s == null || s.trim().length() == 0);
   }

   /**
    * Returns true if the specified string contains only 7-bit ascii characters
    * @param in
    * @return boolean
    */
   public static boolean isAscii(String in) {
      for (int i = 0; i < in.length(); i++) {
         if (in.charAt(i) > 127) {
            return false;
         }
      }
      return true;
   }

   /**
    * Return the number of occurrences of the specified character in the specified string
    */
   public static int countOccurrences(char ch, String in) {
      int cnt = 0;
      int pos = in.indexOf(ch);
      while (pos >= 0) {
         cnt++;
         pos = in.indexOf(ch, pos+1);
      }
      return cnt;
   }

   public static void sleep(int miliseconds) {
      try
      {
         Thread.sleep(miliseconds);
      } catch (InterruptedException e)
      {
         logger.warn(e);
      }
   }

   public static String toMixedCase(String s) {
      StringBuilder buf = new StringBuilder();
      boolean followsSpace = true;
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (followsSpace) {
            buf.append(s.substring(i, i+1).toUpperCase()); // javadocs recommend this function instead of Character.toUpperCase(c)
         }
         else {
            buf.append(c);
         }
         followsSpace = (c == ' ');
      }
      return buf.toString();
   }

   /**
    * Translate \ to \\ and $ to \$, in preparation for using the specified string in a regexp replacement
    * @param text
    * @return
    */
   public static String protectDollarSlash(String text) {
      return text.replaceAll("\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$");
   }

   private static final Pattern OPEN_CLOSE_TEMPLATE = Pattern.compile("\\{\\{|\\}\\}");

   public static List<String> getTemplates(String text) {
      List<String> templates = new ArrayList<String>();
      int openTags = 0;
      int outermostStart = 0;
      Matcher openClose = OPEN_CLOSE_TEMPLATE.matcher(text);
      while (openClose.find()) {
         if (openClose.group(0).equals("{{")) {
            if (openTags == 0) {
               outermostStart = openClose.end();
            }
            openTags++;
         }
         else {
            openTags--;
            if (openTags == 0) {
               templates.add(text.substring(outermostStart, openClose.start()).trim());
            }
            else if (openTags < 0) {
               openTags = 0; // malformed
            }
         }
      }
      return templates;
   }

   private static final Map <String, Integer> MONTHS = new HashMap<String, Integer>();
   static
   {
      MONTHS.put("january", 1);
      MONTHS.put("february", 2);
      MONTHS.put("march", 3);
      MONTHS.put("april", 4);
      MONTHS.put("may", 5);
      MONTHS.put("june", 6);
      MONTHS.put("june", 6);
      MONTHS.put("july", 7);
      MONTHS.put("august", 8);
      MONTHS.put("september", 9);
      MONTHS.put("october", 10);
      MONTHS.put("november", 11);
      MONTHS.put("december", 12);
      MONTHS.put("jan", 1);
      MONTHS.put("feb", 2);
      MONTHS.put("mar", 3);
      MONTHS.put("apr", 4);
      MONTHS.put("may", 5);
      MONTHS.put("jun", 6);
      MONTHS.put("jul", 7);
      MONTHS.put("aug", 8);
      MONTHS.put("sep", 9);
      MONTHS.put("oct", 10);
      MONTHS.put("nov", 11);
      MONTHS.put("dec", 12);
      MONTHS.put("febr", 2);
      MONTHS.put("sept", 9);
   }

   private static final Map <Integer, Integer> MONTH_DAYS = new HashMap<Integer, Integer>();
   static
   {
      MONTH_DAYS.put(1, 31);
      MONTH_DAYS.put(2, 28);
      MONTH_DAYS.put(3, 31);
      MONTH_DAYS.put(4, 30);
      MONTH_DAYS.put(5, 31);
      MONTH_DAYS.put(6, 30);
      MONTH_DAYS.put(7, 31);
      MONTH_DAYS.put(8, 31);
      MONTH_DAYS.put(9, 30);
      MONTH_DAYS.put(10, 31);
      MONTH_DAYS.put(11, 30);
      MONTH_DAYS.put(12, 31);
   }

   private static Pattern pAlphaNumRegExp =  Pattern.compile("\\d+|[^0-9\\s`~!@#$%^&*()_+\\-={}|:'<>?;,/\"\\[\\]\\.\\\\]+");
   private static boolean isYear(int y) {
      return y >= 700 && y <= 2200;
   }
   private static int getAlphaMonth(String mon) {
     mon = mon.toLowerCase();
     if (MONTHS.get(mon) != null) {
             return MONTHS.get(mon);
     }
     return 0;
   }
   private static boolean isDay(int d) {
      return d >= 1 && d <= 31;
   }
   private static boolean isNumMonth(int m) {
      return m >= 1 && m <= 12;
   }

   private static final boolean isNumeric(final String s) {
     final char[] numbers = s.toCharArray();
     for (int x = 0; x < numbers.length; x++) {
       final char c = numbers[x];
       if ((c >= '0') && (c <= '9')) continue;
       return false; // invalid
     }
     return true; // valid
   }

   private static int parseInt(String field)
   {
      if (isNumeric(field))
      {
         try {
            return Integer.parseInt(field);
         }
         catch (NumberFormatException e) {
            // ignore
         }
      }
      return 0;
   }

   public static String getDateSortKey(String date) {
      String result = "";
      if (!Util.isEmpty(date))
      {
         int year = 0;
         int month = 0;
         int day = 0;
         Matcher mFields = pAlphaNumRegExp.matcher(date);
         List<String> fields = new ArrayList<String>();

         while (mFields.find())
         {
            fields.add(mFields.group(0));
         }

         for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            int num = parseInt(field);

            if (isYear(num))
            {
               if (year == 0) year = num;
            } else if (getAlphaMonth(field) > 0)
            {
               if (month == 0) {
                  month = getAlphaMonth(field);
               }
            } else if (isDay(num) && (!isNumMonth(num) ||
                  (i > 0 && getAlphaMonth(fields.get(i - 1)) > 0) ||
                  (i < fields.size() - 1 && getAlphaMonth(fields.get(i + 1)) > 0)))
            {
               if (day == 0) {
                  day = num;
               }
            } else if (i > 0 && isYear(parseInt(fields.get(i - 1))))
            {
               // ignore -- probably 1963/4
            } else if (isNumMonth(num))
            {
               if (month == 0) {
                  month = num;
               }
            }
         }

         if (year > 0) {
            result = Integer.toString(year);
            if (result.length() < 4) result = "0000".substring(0, 4 - result.length()) + result;
            if (month > 0) {
               result += (month < 10 ? "0" +
                     Integer.toString(month) : Integer.toString(month));
               if (day > 0) {
                  result += (day < 10 ? "0" +
                        Integer.toString(day) : Integer.toString(day));
               }
            }
         }
      }
      return result;
   }

   public static String join(String glue, Collection<? extends Object> c, String encloseString) {
      StringBuilder buf = new StringBuilder();
      for (Object o : c) {
         if (buf.length() > 0) {
            buf.append(glue);
         }
         buf.append(encloseString+o.toString()+encloseString);
      }
      return buf.toString();
   }

   public static String join(String glue, Collection<? extends Object> c) {
      return join(glue, c, "");
   }

   public static String prepareWikiTitle(String title) {
      return prepareWikiTitle(title, MAX_TITLE_LEN);
   }

   // keep in sync with Utils.NAMESPACE_MAP in indexer project
   public static final String NS_PLACE_TEXT = "Place";
   public static final String NS_PERSON_TEXT = "Person";
   public static final String NS_SOURCE_TEXT = "Source";
   public static final String NS_ARTICLE_TEXT = "Article";
   public static final int NS_MAIN = 0;
   public static final int NS_USER = 2;
   public static final int NS_PROJECT = 4;
   public static final int NS_IMAGE = 6;
   public static final int NS_MEDIAWIKI = 8;
   public static final int NS_TEMPLATE = 10;
   public static final int NS_HELP = 12;
   public static final int NS_CATEGORY = 14;
   public static final int NS_GIVENNAME = 100;
   public static final int NS_SURNAME = 102;
   public static final int NS_SOURCE = 104;
   public static final int NS_PLACE = 106;
   public static final int NS_PERSON = 108;
   public static final int NS_FAMILY = 110;
   public static final int NS_MYSOURCE = 112;
   public static final int NS_REPOSITORY = 114;
   public static final int NS_PORTAL = 116;
   public static final int NS_TRANSCRIPT = 118;
   public static final Map<String,Integer> NAMESPACE_MAP = new HashMap<String,Integer>();
   static {
      NAMESPACE_MAP.put("Talk",NS_MAIN+1);
      NAMESPACE_MAP.put("User",NS_USER);
      NAMESPACE_MAP.put("User talk",NS_USER+1);
      NAMESPACE_MAP.put("WeRelate",NS_PROJECT);
      NAMESPACE_MAP.put("WeRelate talk",NS_PROJECT+1);
      NAMESPACE_MAP.put("Image",NS_IMAGE);
      NAMESPACE_MAP.put("Image talk",NS_IMAGE+1);
      NAMESPACE_MAP.put("MediaWiki",NS_MEDIAWIKI);
      NAMESPACE_MAP.put("MediaWiki talk",NS_MEDIAWIKI);
      NAMESPACE_MAP.put("Template",NS_TEMPLATE);
      NAMESPACE_MAP.put("Template talk",NS_TEMPLATE+1);
      NAMESPACE_MAP.put("Help",NS_HELP);
      NAMESPACE_MAP.put("Help talk",NS_HELP+1);
      NAMESPACE_MAP.put("Category",NS_CATEGORY);
      NAMESPACE_MAP.put("Category talk",NS_CATEGORY+1);
      NAMESPACE_MAP.put("Givenname",NS_GIVENNAME);
      NAMESPACE_MAP.put("Givenname talk",NS_GIVENNAME+1);
      NAMESPACE_MAP.put("Surname",NS_SURNAME);
      NAMESPACE_MAP.put("Surname talk",NS_SURNAME+1);
      NAMESPACE_MAP.put("Source",NS_SOURCE);
      NAMESPACE_MAP.put("Source talk",NS_SOURCE+1);
      NAMESPACE_MAP.put(NS_PLACE_TEXT,NS_PLACE);
      NAMESPACE_MAP.put("Place talk",NS_PLACE+1);
      NAMESPACE_MAP.put(NS_PERSON_TEXT,NS_PERSON);
      NAMESPACE_MAP.put("Person talk",NS_PERSON+1);
      NAMESPACE_MAP.put("Family",NS_FAMILY);
      NAMESPACE_MAP.put("Family talk",NS_FAMILY+1);
      NAMESPACE_MAP.put("MySource",NS_MYSOURCE);
      NAMESPACE_MAP.put("MySource talk",NS_MYSOURCE+1);
      NAMESPACE_MAP.put("Repository",NS_REPOSITORY);
      NAMESPACE_MAP.put("Repository talk",NS_REPOSITORY+1);
      NAMESPACE_MAP.put("Portal",NS_PORTAL);
      NAMESPACE_MAP.put("Portal talk",NS_PORTAL+1);
      NAMESPACE_MAP.put("Transcript",NS_TRANSCRIPT);
      NAMESPACE_MAP.put("Transcript talk",NS_TRANSCRIPT+1);
   }

   public static String[] splitNamespaceTitle(String fullTitle) {
      String[] fields = new String[2];
      fields[0] = "";
      fields[1] = fullTitle;

      int i = fullTitle.indexOf(":");
      if (i > 0) {
         String namespace = fullTitle.substring(0,i);
         Integer ns = NAMESPACE_MAP.get(namespace);
         if (ns != null) {
            fields[0] = namespace;
            fields[1] = fullTitle.substring(i+1);
         }
      }
      return fields;
   }

   /**
    * Convert a string into a form that can be used for a wiki title
    */
   public static String prepareWikiTitle(String title, int maxTitleLen) {
      title = title.replace('<','(').replace('[','(').replace('{','(').replace('>',')').replace(']',')').replace('}',')').
                    replace('|','-').replace('_',' ').replace('/','-').replace("#"," ").replace("?", " ").replace("+"," and ").replace("&"," and ");
      title = title.replaceAll("%([0-9a-fA-F][0-9a-fA-F])", "% $1").replaceAll("\\s+", " ").replaceAll("//+", "/").trim();
      StringBuffer dest = new StringBuffer();
      for (int i = 0; i < title.length(); i++) {
         char c = title.charAt(i);
         // omit control characters, unicode unknown character
         if ((int)c >= 32 && c != 0xFFFD &&
            !(c == 0x007F) &&
            // omit Hebrew characters (right-to-left)
            !(c >= 0x0590 && c <= 0x05FF) && !(c >= 0xFB00 && c <= 0xFB4F) &&
            // omit Arabic characters (right-to-left)
            !(c >= 0x0600 && c <= 0x06FF) && !(c >= 0x0750 && c <= 0x077F) && !(c >= 0xFB50 && c <= 0xFC3F) && !(c >= 0xFE70 && c <= 0xFEFF)
         ) {
            dest.append(c);
         }
      }
      title = dest.toString().trim();
      while (title.length() > 0 &&
              (title.charAt(0) == ' ' || title.charAt(0) == '.' || title.charAt(0) == '/' ||
               title.charAt(0) == ':' || title.charAt(0) == '-' || title.charAt(0) == ',')) {
         title = title.substring(1);
      }
      if (title.length() > maxTitleLen) {
         int pos = maxTitleLen;
         if (maxTitleLen > 20) {
            while (maxTitleLen - pos < 20 && title.charAt(pos) != ' ') pos--;
         }
         title = title.substring(0, pos).trim();
      }
      return uppercaseFirstLetter(title);
   }

   /**
    * Uppercase the first letter (only) of the specified string
    * @param in
    * @return
    */
   public static String uppercaseFirstLetter(String in) {
      if (in.length() > 0 && Character.isLowerCase(in.charAt(0))) {
         return in.substring(0,1).toUpperCase() + in.substring(1);
      }
      return in;
   }

   private static final String[] UPPERCASE_WORDS_ARRAY = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};
   private static final Set<String> UPPERCASE_WORDS = new HashSet<String>();
   static {
      for (String word : UPPERCASE_WORDS_ARRAY) UPPERCASE_WORDS.add(word);
   }
   private static final String[] LOWERCASE_WORDS_ARRAY = {
      "a","an","and","at","but","by","for","from","in","into",
      "nor","of","on","or","over","the","to","upon","vs","with",
      "against", "as", "before", "between", "during", "under", "versus", "within", "through", "up",
      // french
      "à", "apres", "après", "avec", "contre", "dans", "dès", "devant", "dévant", "durant", "de", "avant", "des",
      "du", "et", "es", "jusque", "le", "les", "par", "passe", "passé", "pendant","pour", "pres", "près", "la",
      "sans", "suivant", "sur", "vers", "un", "une",
      // spanish
      "con", "depuis", "durante", "ante", "antes", "contra", "bajo",
      "en", "entre", "mediante", "para", "pero", "por", "sobre", "el", "o", "y",
      // dutch
      "aan", "als", "bij", "eer", "min", "na", "naar", "om", "op", "rond", "te", "ter", "tot", "uit", "voor",
      // german
      "auf", "gegenuber", "gegenüber", "gemäss", "gemass", "hinter", "neben",
      "über", "uber", "unter", "vor", "zwischen", "die", "das", "ein", "der",
      "ans", "aufs", "beim", "für", "fürs", "im", "ins", "vom", "zum", "am",
      // website extensions
      "com", "net", "org",
   };
   public static final Set<String> LOWERCASE_WORDS = new HashSet<String>();
   static {
      for (String word : LOWERCASE_WORDS_ARRAY) LOWERCASE_WORDS.add(word);
   }
   private static final String[] NAME_WORDS_ARRAY = {
           "a", "à", "aan", "af", "auf",
           "bei", "ben", "bij",
           "contra",
           "da", "das", "de", "dei", "del", "della", "dem", "den", "der", "des", "di", "die", "do", "don", "du",
           "ein", "el", "en",
           "het",
           "im", "in",
           "la", "le", "les", "los",
           "met",
           "o", "of", "op",
           "'s", "s'", "sur",
           "'t", "te", "ten", "ter", "tho", "thoe", "to", "toe", "tot",
           "uit",
           "van", "ver", "von", "voor", "vor",
           "y",
           "z", "zum", "zur"
   };
   private static final Set<String> NAME_WORDS = new HashSet<String>();
   static {
      for (String word : NAME_WORDS_ARRAY) NAME_WORDS.add(word);
   }
   public static final Pattern WORD_DELIM_REGEX = Pattern.compile("([ `~!@#$%&_=:;<>,./{}()?+*|\"\\-\\[\\]\\\\]+|[^ `~!@#$%&_=:;<>,./{}()?+*|\"\\-\\[\\]\\\\]+)");

   // keep in sync with wiki/StructuredData.captitalizeTitleCase and gedcom/Utils.capitalizeTitleCase
   public static String capitalizeTitleCase(String s, boolean isName) {
      StringBuilder result = new StringBuilder();
      boolean mustCap = true;
      if (s != null) {
         Matcher m = WORD_DELIM_REGEX.matcher(s);
         while (m.find()) {
            String word = m.group(0);
            String ucWord = word.toUpperCase();
            String lcWord = word.toLowerCase();
            if (isName && word.length() > 1 && word.equals(ucWord)) { // turn all-uppercase names into all-lowercase
               // can't split on apostrophes due to polynesian names
               if (word.length() > 3 && (word.startsWith("MC") || word.startsWith("O'"))) {
                  word = word.substring(0,1)+lcWord.substring(1,2)+word.substring(2,3)+lcWord.substring(3);
               }
               else {
                  word = lcWord;
               }
            }
            if (UPPERCASE_WORDS.contains(ucWord) ||    // upper -> upper
                word.equals(ucWord)) { // acronym or initial
               result.append(ucWord);
            }
            else if (!mustCap && NAME_WORDS.contains(lcWord)) {  // if word is a name-word entered mixed case, keep as-is
               result.append(word);
            }
            else if (!isName && !mustCap && LOWERCASE_WORDS.contains(lcWord)) { // upper/lower -> lower
               result.append(lcWord);
            }
            else if (word.equals(lcWord)) { // lower -> mixed
               result.append(word.substring(0,1).toUpperCase());
               result.append(word.substring(1).toLowerCase());
            }
            else { // mixed -> mixed
               result.append(word);
            }
            word = word.trim();
            mustCap = !isName && (word.equals(":") || word.equals("?") || word.equals("!"));
         }
      }
      return result.toString();
   }
   public static String capitalizeTitleCase(String s) {
      return capitalizeTitleCase(s, false);
   }

   public static String wikiUrlEncoder(String s) {
      try
      {
         return URLEncoder.encode(s,"UTF-8").replace("%2F", "/");
      } catch (UnsupportedEncodingException e)
      {
         return ""; // should never happen
      }
   }

   public static String cleanRedirTarget(String s) {
      String target = Util.translateHtmlCharacterEntities(s);
      int pos = target.indexOf('|');
      if (pos >= 0) {
         target = target.substring(0, pos);
      }
      return target.replace('_',' ').trim();
   }

   private static final String[] CHARACTER_REPLACEMENTS = {
      "æ","ae",
      "ǝ","ae",
      "ǽ","ae",
      "ǣ","ae",
      "Æ","Ae",
      "Ə","Ae",
      "ß","ss",
      "đ","dj",
      "Đ","Dj",
      "ø","oe",
      "œ","oe",
      "Œ","Oe",
      "Ø","Oe",
      "þ","th",
      "Þ","Th",
      "ĳ","y",
      "Ĳ","Y",
      "á","a",
      "à","a",
      "â","a",
      "ä","a",
      "å","a",
      "ą","a",
      "ã","a",
      "ā","a",
      "ă","a",
      "ǎ","a",
      "ȃ","a",
      "ǻ","a",
      "ȁ","a",
      "Ƌ","a",
      "ƌ","a",
      "ȧ","a",
      "Ã","A",
      "Ą","A",
      "Á","A",
      "Ä","A",
      "Å","A",
      "À","A",
      "Â","A",
      "Ā","A",
      "Ă","A",
      "Ǻ","A",
      "ĉ","c",
      "ć","c",
      "č","c",
      "ç","c",
      "ċ","c",
      "Ĉ","C",
      "Č","C",
      "Ć","C",
      "Ç","C",
      "ð","d",
      "ď","d",
      "Ď","D",
      "Ð","D",
      "Ɖ","D",
      "ê","e",
      "é","e",
      "ë","e",
      "è","e",
      "ę","e",
      "ė","e",
      "ě","e",
      "ē","e",
      "ĕ","e",
      "ȅ","e",
      "Ė","E",
      "Ę","E",
      "Ê","E",
      "Ë","E",
      "É","E",
      "È","E",
      "Ě","E",
      "Ē","E",
      "Ĕ","E",
      "ƒ","f",
      "ſ","f",
      "ğ","g",
      "ģ","g",
      "ǧ","g",
      "ġ","g",
      "Ğ","G",
      "Ĝ","G",
      "Ģ","G",
      "Ġ","G",
      "Ɠ","G",
      "ĥ","h",
      "Ħ","H",
      "í","i",
      "і","i",
      "ī","i",
      "ı","i",
      "ï","i",
      "î","i",
      "ì","i",
      "ĭ","i",
      "ĩ","i",
      "ǐ","i",
      "į","i",
      "Í","I",
      "İ","I",
      "Î","I",
      "Ì","I",
      "Ï","I",
      "І","I",
      "Ĩ","I",
      "Ī","I",
      "ј","j",
      "ĵ","j",
      "Ј","J",
      "Ĵ","J",
      "ķ","k",
      "Ķ","K",
      "ĸ","K",
      "ł","l",
      "ŀ","l",
      "ľ","l",
      "ļ","l",
      "ĺ","l",
      "Ļ","L",
      "Ľ","L",
      "Ŀ","L",
      "Ĺ","L",
      "Ł","L",
      "ñ","n",
      "ņ","n",
      "ń","n",
      "ň","n",
      "ŋ","n",
      "ǹ","n",
      "Ň","N",
      "Ń","N",
      "Ñ","N",
      "Ŋ","N",
      "Ņ","N",
      "ô","o",
      "ö","o",
      "ò","o",
      "õ","o",
      "ó","o",
      "ő","o",
      "ơ","o",
      "ǒ","o",
      "ŏ","o",
      "ǿ","o",
      "ȍ","o",
      "ō","o",
      "ȯ","o",
      "ǫ","o",
      "Ó","O",
      "Ő","O",
      "Ô","O",
      "Ö","O",
      "Ò","O",
      "Õ","O",
      "Ŏ","O",
      "Ō","O",
      "Ơ","O",
      "Ƿ","P",
      "ƽ","q",
      "Ƽ","Q",
      "ř","r",
      "ŕ","r",
      "ŗ","r",
      "Ř","R",
      "Ʀ","R",
      "Ȓ","R",
      "Ŗ","R",
      "Ŕ","R",
      "š","s",
      "ś","s",
      "ş","s",
      "ŝ","s",
      "ș","s",
      "Ş","S",
      "Š","S",
      "Ś","S",
      "Ș","S",
      "Ŝ","S",
      "ť","t",
      "ţ","t",
      "ŧ","t",
      "ț","t",
      "Ť","T",
      "Ŧ","T",
      "Ţ","T",
      "Ț","T",
      "ũ","u",
      "ú","u",
      "ü","u",
      "ư","u",
      "û","u",
      "ů","u",
      "ù","u",
      "ű","u",
      "ū","u",
      "µ","u",
      "ǔ","u",
      "ŭ","u",
      "ȕ","u",
      "Ū","U",
      "Ű","U",
      "Ù","U",
      "Ú","U",
      "Ü","U",
      "Û","U",
      "Ũ","U",
      "Ư","U",
      "Ů","U",
      "Ǖ","U",
      "Ʊ","U",
      "ŵ","w",
      "Ŵ","W",
      "ÿ","y",
      "Ŷ","Y",
      "Ÿ","Y",
      "ý","y",
      "ȝ","y",
      "Ȝ","Y",
      "Ý","Y",
      "ž","z",
      "ź","z",
      "ż","z",
      "Ź","Z",
      "Ž","Z",
      "Ż","Z"
   };
   private static final HashMap<Character,String> CHARACTER_MAPPINGS = new HashMap<Character,String>();
   static {
      for (int i = 0; i < CHARACTER_REPLACEMENTS.length; i+=2) {
         CHARACTER_MAPPINGS.put(CHARACTER_REPLACEMENTS[i].charAt(0), CHARACTER_REPLACEMENTS[i+1]);
      }
   }

   private static final String[][] HTML_ENTITIES = {
      {"nbsp","32"}, // use normal space instead of hard space
      {"iexcl","161"},
      {"cent","162"},
      {"pound","163"},
      {"curren","164"},
      {"yen","165"},
      {"brvbar","166"},
      {"sect","167"},
      {"uml","168"},
      {"copy","169"},
      {"ordf","170"},
      {"laquo","171"},
      {"not","172"},
      {"shy","173"},
      {"reg","174"},
      {"macr","175"},
      {"deg","176"},
      {"plusmn","177"},
      {"sup2","178"},
      {"sup3","179"},
      {"acute","180"},
      {"micro","181"},
      {"para","182"},
      {"middot","183"},
      {"cedil","184"},
      {"sup1","185"},
      {"ordm","186"},
      {"raquo","187"},
      {"frac14","188"},
      {"frac12","189"},
      {"frac34","190"},
      {"iquest","191"},
      {"Agrave","192"},
      {"Aacute","193"},
      {"Acirc","194"},
      {"Atilde","195"},
      {"Auml","196"},
      {"Aring","197"},
      {"AElig","198"},
      {"Ccedil","199"},
      {"Egrave","200"},
      {"Eacute","201"},
      {"Ecirc","202"},
      {"Euml","203"},
      {"Igrave","204"},
      {"Iacute","205"},
      {"Icirc","206"},
      {"Iuml","207"},
      {"ETH","208"},
      {"Ntilde","209"},
      {"Ograve","210"},
      {"Oacute","211"},
      {"Ocirc","212"},
      {"Otilde","213"},
      {"Ouml","214"},
      {"times","215"},
      {"Oslash","216"},
      {"Ugrave","217"},
      {"Uacute","218"},
      {"Ucirc","219"},
      {"Uuml","220"},
      {"Yacute","221"},
      {"THORN","222"},
      {"szlig","223"},
      {"agrave","224"},
      {"aacute","225"},
      {"acirc","226"},
      {"atilde","227"},
      {"auml","228"},
      {"aring","229"},
      {"aelig","230"},
      {"ccedil","231"},
      {"egrave","232"},
      {"eacute","233"},
      {"ecirc","234"},
      {"euml","235"},
      {"igrave","236"},
      {"iacute","237"},
      {"icirc","238"},
      {"iuml","239"},
      {"eth","240"},
      {"ntilde","241"},
      {"ograve","242"},
      {"oacute","243"},
      {"ocirc","244"},
      {"otilde","245"},
      {"ouml","246"},
      {"divide","247"},
      {"oslash","248"},
      {"ugrave","249"},
      {"uacute","250"},
      {"ucirc","251"},
      {"uuml","252"},
      {"yacute","253"},
      {"thorn","254"},
      {"yuml","255"},
   };
   public static final Pattern HTML_ENTITY_PATTERN;
   static {
      StringBuffer buf = new StringBuffer();
      buf.append("&(");
      for (int i = 0; i < HTML_ENTITIES.length; i++) {
         if (i > 0) {
            buf.append("|");
         }
         buf.append(HTML_ENTITIES[i][0]);
      }
      buf.append(");");
      HTML_ENTITY_PATTERN = Pattern.compile(buf.toString());
   }

   public static final Map HTML_ENTITY_MAP = new HashMap<String,String>();
   static {
      char[] chars = new char[1];
      for (int i = 0; i < HTML_ENTITIES.length; i++) {
         chars[0] = (char)Integer.parseInt(HTML_ENTITIES[i][1]);
         HTML_ENTITY_MAP.put(HTML_ENTITIES[i][0], new String(chars));
      }
   }


}
