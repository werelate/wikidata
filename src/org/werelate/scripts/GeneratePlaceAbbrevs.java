package org.werelate.scripts;

import org.werelate.utils.Util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: dallan
 * Date: 10/21/15
 */
public class GeneratePlaceAbbrevs {
   public static final int LEVEL_PRIORITY = 10;
   public static final int STD_PRIORITY = 0;
   public static final int ALI_PRIORITY = 1;
   public static final int TYPE_PRIORITY = 3;
   public static final int ALT_PRIORITY = 14;

   private static class Place {
      String id;
      String name;
      List<String> altNames;
      Set<String> types;
      String locatedInId;
      Set<String> alsoLocatedInIds;
   }

   public static Place constructPlace(String line) {
      String[] fields = line.split("\\|");
      Place p = new Place();
      p.id = fields[0];
      p.name = fields[1];
      p.altNames = new ArrayList<String>();
      if (fields[2].length() > 0) {
         for (String altNameSource : fields[2].split("~")) {
            String altName = altNameSource.split(":")[0].trim();
            p.altNames.add(altName);
         }
      }
      if (fields[3].length() > 0) {
         p.types = new HashSet<String>(Arrays.asList(fields[3].split("~")));
      }
      else {
         p.types = new HashSet<String>();
      }
      p.locatedInId = fields[4];
      if (fields[5].length() > 0) {
         p.alsoLocatedInIds = new HashSet<String>(Arrays.asList(fields[5].split("~")));
      }
      else {
         p.alsoLocatedInIds = new HashSet<String>();
      }
      return p;
   }

   private static class Ancestor {
      List<String> path;
      int priority;
      Ancestor(List<String> path, int priority) {
         this.path = path;
         this.priority = priority;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Ancestor ancestor = (Ancestor) o;

         return path.equals(ancestor.path);

      }

      @Override
      public int hashCode() {
         return path.hashCode();
      }
   }

   public static String getTitle(Map<String, Place> placeMap, Place p) {
      StringBuilder buf = new StringBuilder();
      buf.append(p.name);
      while (!"0".equals(p.locatedInId)) {
         p = placeMap.get(p.locatedInId);
         buf.append(", ");
         buf.append(p.name);
      }
      return buf.toString();
   }

   public static Set<Ancestor> getAncestors(Map<String, Place> placeMap, String placeId, int priority, int recursion) {
      Set<Ancestor> results = new HashSet<Ancestor>();

      if (recursion >= 8) {
         return results;
      }

      if ("0".equals(placeId)) {
         results.add(new Ancestor(new ArrayList<String>(), priority + recursion * LEVEL_PRIORITY));
         return results;
      }

      // get suffixes
      Place p = placeMap.get(placeId);
      List<Ancestor> ancestors = new ArrayList<Ancestor>();
      ancestors.addAll(getAncestors(placeMap, p.locatedInId, STD_PRIORITY, recursion + 1));
      if (!"0".equals(p.locatedInId)) {
         for (String ali : p.alsoLocatedInIds) {
            if (!ali.equals(p.locatedInId)) {
               ancestors.addAll(getAncestors(placeMap, ali, ALI_PRIORITY, recursion + 1));
            }
         }
      }

      for (Ancestor ancestor : ancestors) {
         List<String> path = new ArrayList<String>(ancestor.path);
         if (recursion > 0) {
            path.add(0, p.name);
         }
         results.add(new Ancestor(path, priority + ancestor.priority));
      }
      return results;
   }

   public static final Pattern LOWERCASE_PATTERN = Pattern.compile("\\(([^)]*)");
   public static final Pattern CITY_PATTERN = Pattern.compile("\\(Independent City\\)|\\(City Of\\)");

   // standardize a few things and remove ()'s
   public static String clean(String s) {
      StringBuffer sb = new StringBuffer();
      Matcher m = LOWERCASE_PATTERN.matcher(s);
      while (m.find()) {
         m.appendReplacement(sb, "("+Util.toMixedCase(m.group(1)));
      }
      m.appendTail(sb);
      s = sb.toString();
      return CITY_PATTERN.matcher(s).replaceAll("(City)")
              .replace('(', ' ')
              .replace(')', ' ')
              .replaceAll("\\s+", " ")
              .replaceAll(" ,", ",")
              .trim();
   }

   public static String cleanAbbrev(String s) {
      return Util.romanize(clean(s).toLowerCase())
              .replaceAll("'", "")
              .replaceAll("[^a-z0-9]", " ")
              .replaceAll("\\s+", " ")
              .replaceAll(" ,", ",")
              .trim();
   }

   private static HashSet<String> seenAbbrevTitles = new HashSet<String>();

   public static void write(PrintWriter out, String abbrev, String name, String primaryName, String title, int priority) {
      // track so we avoid dup primary keys
      if (seenAbbrevTitles.contains(abbrev+"|"+title)) {
         return;
      }
      seenAbbrevTitles.add(abbrev + "|" + title);

      out.printf("%s\t%s\t%s\t%s\t%d\n", abbrev, name, primaryName, title, priority);
   }

   public static void writeAbbrevs(PrintWriter out, String name, String primaryName, List<String> path, String title, int priority) {
      title = title.replace(' ', '_');

      if (path.size() == 0) {
         write(out, cleanAbbrev(name), clean(name), clean(primaryName), title, priority);
         return;
      }

      String suffix =  ", " + Util.join(", ", path);
      String primaryFullName = clean(primaryName + suffix);
      String fullName = clean(name + suffix);

      for (int i = 0; i < path.size(); i++) {
         suffix = ", " + Util.join(", ", path.subList(i, path.size()));
         String abbrev = cleanAbbrev(name + suffix);
         write(out, abbrev, fullName, primaryFullName, title, priority);
         if (name.indexOf('(') > 0) {
            abbrev = cleanAbbrev(name.substring(0, name.indexOf('(')) + suffix);
            write(out, abbrev, fullName, primaryFullName, title, priority);
         }
      }
   }

   // 0=places.csv 1=place_abbrevs.tsv
   public static void main(String[] args) throws IOException {
      Map<String,Place> placeMap = new HashMap<String,Place>();
      HashSet<String> seenTitles = new HashSet<String>();

      // read places into map of places
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while (in.ready()) {
         String line = in.readLine();
         Place p = constructPlace(line);
         if (p != null) {
            placeMap.put(p.id, p);
         }
      }
      in.close();

      PrintWriter out = new PrintWriter(new FileWriter(args[1]));

      // for each place in map
      for (String id : placeMap.keySet()) {
         Place p = placeMap.get(id);
         String title = getTitle(placeMap, p);

//         if (!"173773".equals(id)) {
//            continue;
//         }
//         if (!"Norfolk (independent city), Virginia, United States".equals(title)) {
//            continue;
//         }

         // it's rare, but possible for two places to have the same constructed title because of missing spaces in the real page title
         // skip these if it happens
         if (seenTitles.contains(title)) {
            System.out.println("Skipping "+title);
            continue;
         }
         seenTitles.add(title);

         // get ancestors
         Set<Ancestor> ancestors = getAncestors(placeMap, p.id, 0, 0);
         for (Ancestor ancestor : ancestors) {
            // write primary name
            writeAbbrevs(out, p.name, p.name, ancestor.path, title, ancestor.priority);
            // write alt names
            for (String altName : p.altNames) {
               if (altName.length() > 0 && // ignore empty alt names
                   !altName.equals(altName.toUpperCase())) { // ignore abbrevs
                  writeAbbrevs(out, altName, p.name, ancestor.path, title, ALT_PRIORITY + ancestor.priority);
               }
            }
         }
      }
      out.close();
   }
}
