package org.werelate.wikipedia;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.parser.WikiPageParser;
import org.werelate.utils.Util;

import java.util.*;
import java.util.regex.Matcher;
import java.io.IOException;

import nu.xom.ParsingException;

public class WikipediaAltNamesParser implements WikiPageParser {

   private static Logger logger = LogManager.getLogger("org.werelate.wikipedia");

   // Map redirects -> targets
   private Map<String, String> alt2wp;

   // Wp titles that we are interested in
   private Set<String> titles;

   public WikipediaAltNamesParser(Set<String> titles) {
      this.alt2wp = new HashMap<String,String>();
      this.titles = new HashSet<String>();
      for (String title : titles) {
         this.titles.add(title);
      }
   }

   /**
    * @return WP alternate name -> WP article title map
    *         this map is generated within this class.
    */
   public Map<String, String> getAlt2wp() {
      return alt2wp;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      Matcher mRedirect = Util.REDIRECT_PATTERN.matcher(text);
      if (mRedirect.lookingAt()) {
         String target = WikiPage.standardizeTitle(mRedirect.group(1));
         if (titles.contains(title) || titles.contains(target)) {
            alt2wp.put(title, target);
            titles.add(title);
            titles.add(target);
         }
      }
   }
}
