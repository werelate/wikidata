package org.werelate.scripts;

import nu.xom.ParsingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListPlaceRenames extends StructuredDataParser
{
   private static Logger logger = LogManager.getLogger("org.werelate.scripts");

   private Map<String,String> placeTargets;

   public ListPlaceRenames() {
      placeTargets = new HashMap<String,String>();
   }

   public Map<String,String> getPlaceTargets() {
      return placeTargets;
   }

   public String getFinalRedirect(String place) {
      int cnt = 0;
      String target = place;
      while (!target.equals(placeTargets.get(target))) {
         target = placeTargets.get(target);
         if (++cnt > 5) {
            logger.warn("Redirect loop: "+place);
            return null;
         }
         if (target == null) {
            logger.warn("Redirect to non-existing place: "+place);
            return null;
         }
      }
      return target;
   }

   public String getRename(String place) {
      String origPlace = place;
      StringBuilder buf = new StringBuilder();
      while (true) {
         String[] fields = place.split(",", 2);
         buf.append(fields[0].trim());
         if (fields.length < 2) {
            break;
         }
         buf.append(", ");
         place = getFinalRedirect(fields[1].trim());
         if (place == null) {
            logger.warn("Problem with: "+origPlace);
            return null;
         }
      }
      return buf.toString();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Place:")) {
         String unprefixedTitle = title.substring("Place:".length());
         String target = unprefixedTitle;
         Matcher m = Util.REDIRECT_PATTERN.matcher(text.trim());
         if (m.lookingAt()) {
            target = Util.cleanRedirTarget(m.group(1)).substring("Place:".length()).trim();
         }
         placeTargets.put(unprefixedTitle,target);
      }
   }

   // List: place title | title to move it to
   // 0=pages.xml 1=renames.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(false);
      ListPlaceRenames self = new ListPlaceRenames();
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();

      // calculate renames needed
      // ensure that higher-level places are renamed before lower-level places
      SortedSet<String> renames = new TreeSet<String>(new Comparator<String>() {
         public int compare(String o1, String o2) {
            int c1 = Util.countOccurrences(',', o1.substring(0, o1.indexOf('|')));
            int c2 = Util.countOccurrences(',', o2.substring(0, o2.indexOf('|')));
            if (c1 < c2) {
               return -1;
            }
            else if (c1 > c2) {
               return 1;
            }
            else {
               return (o1.compareTo(o2));
            }
         }
      });
      Map<String,String> placeTargets = self.getPlaceTargets();
      for (String place : placeTargets.keySet()) {
         String rename = placeTargets.get(place);
         // if the place is not a redirect
         if (place.equals(rename)) {
            rename = self.getRename(rename);
            // if this place needs to be renamed
            if (rename != null && !place.equals(rename)) {
               renames.add(place+"|"+rename);
            }
         }
      }

      // print renames
      PrintWriter out = new PrintWriter(new FileWriter(args[1]));
      for (String rename : renames) {
         out.println(rename);
      }
      out.close();
   }
}
