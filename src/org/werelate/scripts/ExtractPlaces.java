package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class ExtractPlaces extends StructuredDataParser
{
   private Map<Integer,Place> placeMap;
   private Map<String,Integer> titleMap;
   private Map<String,String> redirectMap;
   private Set<String> linkedPlacesSet;

   // keep in sync with places standardizer.properties
   private static Map<String,String> ABBREVS = new HashMap<String, String>();
   static {
      ABBREVS.put("no","north");
      ABBREVS.put("so","south");
      ABBREVS.put("e","east");
      ABBREVS.put("w","west");
      ABBREVS.put("cem","cemetery");
      ABBREVS.put("cemetary","cemetery");
      ABBREVS.put("co","county");
      ABBREVS.put("cnty","county");
      ABBREVS.put("cty","county");
      ABBREVS.put("is","island");
      ABBREVS.put("isl","island");
      ABBREVS.put("lk", "lake");
      ABBREVS.put("mt", "mount");
      ABBREVS.put("par", "parish");
      ABBREVS.put("sainte","saint");
      ABBREVS.put("st","saint");
      ABBREVS.put("ste","saint");
      ABBREVS.put("tp","township");
      ABBREVS.put("tsp","township");
      ABBREVS.put("twp","township");
      ABBREVS.put("twsp","township");
      ABBREVS.put("ft","fort");
   }

   // keep in sync with places standardizer.properties
   private Set<String> TYPE_WORDS = new HashSet<String>(Arrays.asList(
           "amt",
           "amtsgericht",
           "area",
           "arrondissement",
           "authority",
           "bantustan",
           "barangays",
           "bezirk",
           "borough",
           "buurtschap",
           "canton",
           "capital",
           "cemetery",
           "city",
           "civil",
           "comarca",
           "commune",
           "community",
           "concelho",
           "constituency",
           "county",
           "departement",
           "department",
           "diocese",
           "district",
           "division",
           "duchy",
           "federal",
           "freguesia",
           "gehucht",
           "gemeente",
           "gerichtsbezirk",
           "governorate",
           "grafschaft",
           "hameau",
           "hundred",
           "independent",
           "kanton",
           "kerulet",
           "kreis",
           "landkreis",
           "marke",
           "metropolitan",
           "municipal",
           "municipality",
           "national",
           "oblast",
           "okres",
           "parish",
           "partido ",
           "perfecture",
           "periphery",
           "powiat",
           "prefecture",
           "presbytery",
           "principal",
           "principality",
           "province",
           "provincie",
           "raion",
           "rayon",
           "regency",
           "regierungsbezirk",
           "region",
           "regional",
           "rione",
           "sahar",
           "stad",
           "state",
           "statutarstadt",
           "stift",
           "subprefecture",
           "synod",
           "territory",
           "town",
           "townland",
           "township",
           "unitary",
           "uyezd",
           "village",
           "voivodship"
   ));

   // {{wikipedia-notice|wikipedia page name}}
   private static final Pattern WIKIPEDIA_PATTERN = Pattern.compile("\\{\\{wikipedia-notice\\|(.+?)\\}\\}", Pattern.CASE_INSENSITIVE);
   // {{moreinfo wikipedia|wikipedia page name}}
   private static final Pattern WIKIPEDIA2_PATTERN = Pattern.compile("\\{\\{moreinfo wikipedia\\|(.+?)\\}\\}", Pattern.CASE_INSENSITIVE);
   // {{source-getty|id}}
   private static final Pattern GETTY_PATTERN = Pattern.compile("\\{\\{source-getty\\|(.+?)\\}\\}", Pattern.CASE_INSENSITIVE);
   // {{source-fhlc|id}}
   private static final Pattern FHLC_PATTERN = Pattern.compile("\\{\\{source-fhlc\\|(.+?)\\}\\}", Pattern.CASE_INSENSITIVE);

   private static class Place {
      String name;
      List<String> altNames;
      List<String> types;
      String locatedIn;
      List<String> alsoLocatedIns;
      double latitude;
      double longitude;
      List<String> sources;

      Place() {
         name = "";
         altNames = new ArrayList<String>();
         types = new ArrayList<String>();
         locatedIn = "";
         alsoLocatedIns = new ArrayList<String>();
         latitude = 0.0f;
         longitude = 0.0f;
         sources = new ArrayList<String>();
      }
   }

   public ExtractPlaces() {
      placeMap = new TreeMap<Integer,Place>();
      titleMap = new HashMap<String, Integer>();
      redirectMap = new HashMap<String,String>();
      linkedPlacesSet = new HashSet<String>();
   }

   private static String noTilde(String place) {
      return place.replace("~"," ");
   }

   private static String noColon(String place) {
      return place.replace(":"," ");
   }

   private static String noLink(String source) {
      if (source.startsWith("[[") && source.endsWith("]]")) {
         source = source.substring(2, source.length()-2);
         int pos = source.indexOf('|');
         if (pos > 0) {
            source = source.substring(pos+1);
         }
         else {
            pos = source.indexOf(':');
            if (pos > 0) {
               source = source.substring(pos+1);
            }
         }
      }
      return source;
   }

   private static boolean addSource(Pattern p, String label, String text, List<String> sources) {
      Matcher m = p.matcher(text);
      if (m.find()) {
         sources.add(label+":"+m.group(1));
         return true;
      }
      return false;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      Matcher m = Util.REDIRECT_PATTERN.matcher(text.trim());
      if (title.startsWith("Place:")) {
         title = title.substring("Place:".length());
         if (m.lookingAt()) {
            String target = Util.cleanRedirTarget(m.group(1));
            if (target.startsWith("Place:")) {
               target = target.substring("Place:".length()).trim();
               redirectMap.put(title, target);
            }
         }
         else {
            String[] split = splitStructuredWikiText("place", text);
            String structuredData = split[0];
            String unstructuredData = split[1];
            if (!Util.isEmpty(structuredData)) {
               Place p = new Place();
               Element root = parseText(structuredData).getRootElement();
               Element elm;
               Elements elms;

               // set name + locatedIn
               int pos = title.indexOf(",");
               if (pos > 0) {
                  p.name = title.substring(0,pos).trim();
                  p.locatedIn = title.substring(pos+1).trim();
               }
               else {
                  p.name = title;
                  p.locatedIn = "";
               }

               // set altNames
               elms = root.getChildElements("alternate_name");
               for (int i = 0; i < elms.size(); i++) {
                  elm = elms.get(i);
                  String name = elm.getAttributeValue("name");
                  String source = elm.getAttributeValue("source");
                  if (name != null && name.length() > 0) {
                     if (source != null && source.length() > 0) {
                        name = noColon(name)+":"+noLink(source);
                     }
                     p.altNames.add(noTilde(name));
                  }
               }

               // add altNames for danish oe
               String n = p.name.replace("Ø", "O").replace("ø", "o");
               if (!n.equals(p.name)) {
                  p.altNames.add(noTilde(n));
               }

               // set types
               elm = root.getFirstChildElement("type");
               if (elm != null) {
                  String types = elm.getValue();
                  if (types != null && types.length() > 0) {
                     for (String type : types.split(",")) {
                        type = type.trim();
                        if (type.length() > 0) {
                           p.types.add(noTilde(type));
                        }
                     }
                  }
               }

               // set alsoLocatedIns
               elms = root.getChildElements("also_located_in");
               for (int i = 0; i < elms.size(); i++) {
                  elm = elms.get(i);
                  String name = elm.getAttributeValue("place");
                  if (name != null && name.length() > 0) {
                     p.alsoLocatedIns.add(name);
                  }
               }

               // set lat+lon
               p.latitude = getLatLon(root.getFirstChildElement("latitude"), true);
               p.longitude = getLatLon(root.getFirstChildElement("longitude"), false);

               // add sources
               if (!addSource(WIKIPEDIA_PATTERN, "wikipedia", unstructuredData, p.sources)) {
                  addSource(WIKIPEDIA2_PATTERN, "wikipedia", unstructuredData, p.sources);
               }
               addSource(GETTY_PATTERN, "getty", unstructuredData, p.sources);
               addSource(FHLC_PATTERN, "fhlc", unstructuredData, p.sources);

               // add to maps
               placeMap.put(pageId, p);
               titleMap.put(title, pageId);
            }
         }
      }
      else if (title.startsWith("Person:") || title.startsWith("Family:")) {
         if (!m.lookingAt()) {
            String[] split = splitStructuredWikiText(title.startsWith("Person:") ? "person" : "family", text);
            String structuredData = split[0];
            if (!Util.isEmpty(structuredData)) {
               Element root = parseText(structuredData).getRootElement();
               Elements eventFacts = root.getChildElements("event_fact");
               for (int i = 0; i < eventFacts.size(); i++) {
                  Element eventFact = eventFacts.get(i);
                  String place = eventFact.getAttributeValue("place");
                  if (place != null) {
                     int pos = place.indexOf('|');
                     if (pos >= 0) {
                        place = place.substring(0, pos);
                     }
                     if (place.length() > 0) {
                        linkedPlacesSet.add(place);
                     }
                  }
               }
            }
         }
      }
   }

   private static double getLatLon(Element elm, boolean isLat) {
      if (elm != null) {
         String ll = elm.getValue().trim();
         if (ll != null && ll.length() > 0) {
            try {
               double d = Double.parseDouble(ll);
               if ((isLat && d >= -90.0 && d <= 90.0) ||
                       (!isLat && d >= -180.0 && d <= 180.0)) {
                  return d; // must be a valid double
               }
            }
            catch (NumberFormatException e) {
               // ignore
            }
         }
      }
      return 0.0;
   }

   private String getNameToken(String name) {
      String[] tokens = Util.romanize(name.toLowerCase()).split("[^a-z0-9]+");
      StringBuilder buf = new StringBuilder();
      String result = null;
      boolean foundNameWord = false;
      for (int i = tokens.length-1; i >= 0; i--) {
         String token = tokens[i];
         if (token.length() > 0) {
            // expand only if >1 word
            if (tokens.length > 1) {
               String expansion = ABBREVS.get(token);
               if (expansion != null) {
                  token = expansion;
               }
            }
            if (!TYPE_WORDS.contains(token)) {
               // ignore type words after a name word
               if (!foundNameWord && buf.length() > 0) {
                  buf.setLength(0);
               }
               foundNameWord= true;
            }
            buf.insert(0,token);
         }
      }
      if (buf.length() > 0) {
         result = buf.toString();
      }
//      if (!foundNameWord) {
//         logger.info("No name words found: "+name);
//      }
      return result;
   }

   private boolean addName(int id, String name, Map<String,Set<Integer>> map) {
      String token = getNameToken(name);
      if (token != null) {
         Set<Integer> ids = map.get(token);
         if (ids == null) {
            ids = new TreeSet<Integer>();
            map.put(token, ids);
         }
         ids.add(id);
      }
      return (token != null);
   }

   public Map<String,Set<Integer>> generateWordMap() {
      Map<String,Set<Integer>> map = new TreeMap<String, Set<Integer>>();

      for (Map.Entry<Integer,Place> entry : placeMap.entrySet()) {
         int id = entry.getKey();
         Place p = entry.getValue();
         if (!addName(id, p.name, map)) {
            logger.error("Primary name token not found for: " + p.name + ", " + p.locatedIn);
         }
         for (String altName : p.altNames) {
            int pos = altName.indexOf(':');
            if (pos >= 0) {
               altName = altName.substring(0,pos);
            }
            addName(id, altName, map);
         }
      }
      return map;
   }

   public int getPlaceId(String title) {
      if (title == null || title.length() == 0) {
         return 0;
      }

      int cnt = 0;
      while (redirectMap.get(title) != null && cnt <= 3) {
         title = redirectMap.get(title);
         cnt++;
      }
      Integer id = titleMap.get(title);
      if (id == null) {
         logger.error("Title not found: " + title);
         return -1;
      }
      return id;
   }

   public Map<Integer,Place> getPlaceMap() {
      return placeMap;
   }

   private static String noTab(String s) {
      if (s == null) return "";
      return s.replace("\t", " ");
   }

   private static void append(StringBuilder buf, String s) {
      buf.append("\t");
      if (s != null && s.length() > 0) {
         buf.append(s);
      }
   }

   private boolean containsCycle(int id, String otherTitle, int level) {
      if (level >= 10) {
         return false;
      }

      int otherId = getPlaceId(otherTitle);
      if (otherId == 0) { // not found
         logger.warn("id not found for title "+otherTitle);
         return false;
      }
      Place other = placeMap.get(otherId);
      if (other == null) { // not found
         logger.warn("place not found for id "+otherId+" title "+otherTitle);
         return false;
      }

      // check same id
      if (id == otherId) {
         return true;
      }
      // check locatedIn and alsoLocatedIns
      if (other.locatedIn != null && !other.locatedIn.isEmpty() && containsCycle(id, other.locatedIn, level+1)) {
         return true;
      }
      for (String ali : other.alsoLocatedIns) {
         if (containsCycle(id, ali, level+1)) {
            return true;
         }
      }

      return false;
   }

   private void removeCyclicAlsoLocatedIns() {
      Map<Integer,Set<String>> removals = new HashMap<Integer,Set<String>>();

      for (Map.Entry<Integer, Place> entry : placeMap.entrySet()) {
         int id = entry.getKey();
         Place p = entry.getValue();
         for (String ali : p.alsoLocatedIns) {
            if (getPlaceId(ali) == 0 || containsCycle(id, ali, 0)) {
               Set<String> alis = removals.get(id);
               if (alis == null) {
                  alis = new HashSet<String>();
                  removals.put(id, alis);
               }
               alis.add(ali);
            }
         }
      }

      for (Map.Entry<Integer, Set<String>> entry : removals.entrySet()) {
         int id = entry.getKey();
         Place p = placeMap.get(id);
         for (String ali : entry.getValue()) {
            logger.warn("Removing cycle from "+p.name+", "+p.locatedIn+" to "+ali);
            p.alsoLocatedIns.remove(ali);
         }
      }
   }

   // Generate various lists of places
   // args array: 0=pages.xml 1=place_words.tsv 2=places.tsv 3=linkedplaces.tsv
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(false);
      ExtractPlaces self = new ExtractPlaces();
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();

      self.removeCyclicAlsoLocatedIns();

      Map<String,Set<Integer>> wordMap = self.generateWordMap();
      PrintWriter out = new PrintWriter(args[1], "UTF-8");
      for (Map.Entry<String,Set<Integer>> entry : wordMap.entrySet()) {
         String word = entry.getKey();
         Set<Integer> ids = entry.getValue();
         if (ids.size() > 400) {
            logger.warn("large id list: " + word + "=" + ids.size());
         }
         String idsString = Util.join(",",ids);
         if (word.length() > 191) {
            logger.error("word too long: "+ word);
         }
         else if (idsString.length() > 8192) {
            logger.error("Ids too long: " + word);
         }
         else {
            out.println(word+"\t"+idsString);
         }
      }
      out.close();

      out = new PrintWriter(args[2], "UTF-8");
      Map<Integer,Place> placeMap = self.getPlaceMap();
      for (Map.Entry<Integer,Place> entry : placeMap.entrySet()) {
         Place p = entry.getValue();

         int placeId = entry.getKey();
         int locatedInId = self.getPlaceId(p.locatedIn);
         if (locatedInId < 0) {
            logger.error("Bad locatedInId for: " + placeId);
            continue;
         }

         List<Integer> aliIds = new ArrayList<Integer>();
         for (String ali : p.alsoLocatedIns) {
            int aliId = self.getPlaceId(ali);
            if (aliId > 0) {
               aliIds.add(aliId);
            }
            else {
               logger.error("Bad alsoLocatedIn for: " + placeId);
            }
         }

         String altNames = Util.join("~", p.altNames);
         if (altNames.length() > 4096) {
            logger.error("Alt names too long: " + altNames + "=" + altNames.length());
            altNames = "";
         }

         int countryId = placeId;
         int level = 1;
         int parentId = locatedInId;
         while (parentId > 0) {
            countryId = parentId;
            level++;
            parentId = self.getPlaceId(placeMap.get(parentId).locatedIn);
            if (parentId < 0) {
               logger.error("Bad country for: " + placeId);
            }
         }

         NumberFormat nf = DecimalFormat.getInstance();
         nf.setMaximumIntegerDigits(3);
         nf.setMaximumIntegerDigits(1);
         nf.setMaximumFractionDigits(6);
         nf.setMinimumFractionDigits(1);

         StringBuilder buf = new StringBuilder();
         buf.append(placeId);
         append(buf, noTab(p.name));
         append(buf, noTab(altNames));
         append(buf, noTab(Util.join("~", p.types)));
         append(buf, Integer.toString(locatedInId));
         append(buf, Util.join("~", aliIds));
         append(buf, Integer.toString(level));
         append(buf, Integer.toString(countryId));
         append(buf, nf.format(p.latitude));
         append(buf, nf.format(p.longitude));
         append(buf, noTab(Util.join("~", p.sources)));
         out.println(buf.toString());
      }
      out.close();

      out = new PrintWriter(args[3], "UTF-8");
      for (String place : self.linkedPlacesSet) {
         out.println(place);
      }
      out.close();

   }
}
