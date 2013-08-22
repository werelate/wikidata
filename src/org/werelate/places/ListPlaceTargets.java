package org.werelate.places;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.ParsingException;

public class ListPlaceTargets extends StructuredDataParser
{
   private static Logger logger = Logger.getLogger("org.werelate.places");
   private static final Pattern REDIRECT_PATTERN = Pattern.compile("#redirect\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);

   private Map<String,String> placeTargets;

   public ListPlaceTargets() {
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
            target = place;
            break;
         }
         if (target == null) {
            logger.warn("Redirect to non-existing place: "+place);
            target = place;
            break;
         }
      }
      return target;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Place:")) {
         String unprefixedTitle = title.substring("Place:".length());
         String target = unprefixedTitle;
         Matcher m = REDIRECT_PATTERN.matcher(text.trim());
         if (m.lookingAt()) {
            target = Util.cleanRedirTarget(m.group(1)).substring("Place:".length()).trim();
         }
         placeTargets.put(unprefixedTitle,target);
      }
   }

   // List: place title | place title or final redirect target title
   // 0=pages.xml 1=targets.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(false);
      ListPlaceTargets lpt = new ListPlaceTargets();
      wikiReader.addWikiPageParser(lpt);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      PrintWriter out = new PrintWriter(new FileWriter(args[1]));
      Map<String,String> placeTargets = lpt.getPlaceTargets();
      for (String place : placeTargets.keySet()) {
         String target = lpt.getFinalRedirect(place);
         out.println(place+"|"+target);
      }
      out.close();
   }
}
