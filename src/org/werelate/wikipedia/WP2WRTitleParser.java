package org.werelate.wikipedia;

import nu.xom.ParsingException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;

import org.werelate.utils.MultiMap;
import org.werelate.parser.WikiPageParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WP2WRTitleParser implements WikiPageParser {
   private static final Logger logger = LogManager.getLogger("org.werelate.wikipedia");

   public static Pattern pCopyWikipedia = Pattern.compile(
           "\\{\\{\\s*copy-wikipedia\\s*\\|\\s*(.+?)\\s*(#\\s*(.+?)\\s*)?\\}\\}",
           Pattern.CASE_INSENSITIVE);
   private static Pattern pWpTemplate = Pattern.compile (
           "\\{\\{\\s*(wp-[^}{]+)\\s*\\}\\}",
           Pattern.CASE_INSENSITIVE);
   // pSourceWikipedia is also used in WikipediaUpdate
   public static Pattern pSourceWikipedia = Pattern.compile(
           "(?<!<nowiki>)\\{\\{\\s*source-wikipedia\\s*\\|\\s*(.+?)\\s*(#\\s*(.+?)\\s*)?\\}\\}(?!</nowiki>)", // ignore refs inside nowiki tags
           Pattern.CASE_INSENSITIVE);
   // moreinfo wikipedia creates direct links from wp -> wr pages
   public static Pattern pMoreInfoWikipedia = Pattern.compile(
           "(?<!<nowiki>)\\{\\{\\s*moreinfo wikipedia\\s*\\|\\s*(.+?)\\s*(#\\s*(.+?)\\s*)?\\}\\}(?!</nowiki>)", // ignore refs inside nowiki tags
           Pattern.CASE_INSENSITIVE);

   // Map from wikipedia title to the werelate template titles
   private MultiMap <String, String> wp2templates = new MultiMap <String, String>();
   // Map from WeRelate wikipedia template titles to the werelate article titles that point to them.
   private MultiMap<String, String> template2Wr = new MultiMap <String, String>();
   private Map<String,String> source2template = new HashMap<String, String>();
   private Map<String,String> wp2wr = new HashMap<String, String>();
   private Set<String> sourceTargets = new HashSet<String>();

   private Matcher copyMatcher = pCopyWikipedia.matcher("blah");
   private Matcher sourceMatcher = pSourceWikipedia.matcher("blah");
   private Matcher moreInfoMatcher = pMoreInfoWikipedia.matcher("blah");
   private Matcher templateRefMatcher = pWpTemplate.matcher("blah");

   public MultiMap<String, String> getWp2templates() {
      return wp2templates;
   }

   public MultiMap<String, String> getTemplate2Wr() {
      return template2Wr;
   }

   public Map<String, String> getWp2Wr() {
      return wp2wr;  // werelate page with moreinfo wikipedia template: wp page title -> wr page title
   }

   public Map<String, String> getSource2template() {
      return source2template;  // werelate page with source-wikipedia template -> werelate template title
   }

   public Set<String> getSourceTargets() {
      return sourceTargets; // targets of source-wikipedia templates (wikipedia titles)
   }

   /**
    * This index method builds up the WP -> WR map ( call getWP2WR()), and also
    * builds a map which stores WR titles which do not refer to
    * wikipedia articles (which can be gotten by calling getNonWikipediaTitles()).
    *
    * @param title of the wikipedia article we may want to update from
    * @param text  of the wikipedia article.
    * @throws java.io.IOException
    * @throws nu.xom.ParsingException
    */
   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      templateRefMatcher.reset(text);
      while(templateRefMatcher.find())
      {
         template2Wr.put(WikiPage.standardizeTitle(templateRefMatcher.group(1)), title);
      }

      sourceMatcher.reset(text);
      if(sourceMatcher.find())
      {
         String wikiTitle = WikiPage.standardizeTitle(sourceMatcher.group(1));
         String template = "Wp-"+wikiTitle;
         sourceTargets.add(wikiTitle);
         wp2templates.put(wikiTitle, template);
         template2Wr.put(template, title);
         source2template.put(title, template);
         if (sourceMatcher.find()) {
            logger.warn("There are more than one source wikipedia tags in \"" + title + '\"');
         }
      }

      moreInfoMatcher.reset(text);
      while (moreInfoMatcher.find())
      {
         String wikiTitle = WikiPage.standardizeTitle(moreInfoMatcher.group(1));
         wp2wr.put(wikiTitle, title);
      }

      if (title.startsWith("Template:Wp-")) {
         String wrTitle = title.substring("Template:".length());
         copyMatcher.reset(text);
         if (copyMatcher.find()) {
            wp2templates.put(WikiPage.standardizeTitle(copyMatcher.group(1)), WikiPage.standardizeTitle(wrTitle));
            if (copyMatcher.find())
            {
               logger.warn("There are more than one copy wikipedia tags in \"" + title + '\"');
            }
         }
         else {
            logger.warn("Found a Template page without a copy-wikipedia template: "+title);
         }
      }
   }
}
