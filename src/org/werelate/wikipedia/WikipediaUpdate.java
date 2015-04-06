package org.werelate.wikipedia;

import org.apache.log4j.Logger;
import org.apache.commons.cli.*;
import org.werelate.parser.WikiReader;
import org.werelate.utils.MultiMap;
import org.werelate.utils.Util;
import org.werelate.editor.PageEditor;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;
import java.util.regex.Matcher;

import nu.xom.ParsingException;

public class WikipediaUpdate {
   public static final int UPDATE_DELAY_MILLIS = 3000;

   private static final Logger logger = Logger.getLogger("org.werelate.wikipedia");

   public static void main(String [] args) {
      Options opt = new Options();
      // p is for WeRelate pages file.
      opt.addOption("wr", true, "pages.xml filename");
      opt.addOption("wp", true, "wikipages.xml filename");
      opt.addOption("h", true, "wiki host");
      opt.addOption("p", true, "agent password");
      // This sets whether the input-output class is allowed to write the
      // update.
      opt.addOption("u", false, "Really update");
      opt.addOption("a", false, "Update all templates");
      opt.addOption("s", true, "Starting title");
      opt.addOption("?", false, "Print out help information");

      BasicParser parser = new BasicParser();
      try {
         CommandLine cl = parser.parse(opt, args);
         if (cl.hasOption("?") || !cl.hasOption("wr") || !cl.hasOption("wp") || !cl.hasOption("h") || !cl.hasOption("p")) {
            System.out.println("Updates the WeRelate templates with text from Wikipedia");
            HelpFormatter f = new HelpFormatter();
            f.printHelp("OptionsTip", opt);
         } else {
            String pagesXML = cl.getOptionValue("wr");
            String wikipediaXML = cl.getOptionValue("wp");
            String host = cl.getOptionValue("h");
            String password = cl.getOptionValue("p");
            boolean reallyUpdate = cl.hasOption("u");
            boolean updateAllTemplates = cl.hasOption("a");
            String startTitle = cl.getOptionValue("s");
            logger.info("werelate="+pagesXML+" wikipedia="+wikipediaXML+" host="+host+" password="+password+" update="+(reallyUpdate ? "t" : "f")+
                    " startTitle="+(startTitle != null ? startTitle : "null"));
            updatePages(pagesXML, wikipediaXML, reallyUpdate, updateAllTemplates, startTitle, host, password);
         }
      }
      catch (ParseException e) {
         System.err.println("Invalid command line arguments." + e.getMessage());
      } catch (IOException e) {
         System.err.println(e);
         e.printStackTrace();
      } catch (ParsingException e) {
         System.err.println("It looks like there might be a problem with the filename you gave.");
         System.err.println(e);
         e.printStackTrace();
      }
   }

   private static String getFinalRedirTarget(Map<String,String> alt2wp, String title) {
      while (alt2wp.containsKey(title)) {
         title = alt2wp.get(title);
      }
      return title;
   }

   // replace wp titles in wp2t with redirect targets
   public static int updateRedirects(MultiMap<String,String> wp2t, Set<String> sourceTargets, Map<String,String> alt2wp) {
      int count = 0;
      MultiMap<String,String> temp = new MultiMap<String,String>();

      // update wp2t with final redirects
      Iterator<String> i = wp2t.keySet().iterator();
      while (i.hasNext()) {
         String title = i.next();
         if (alt2wp.containsKey(title)) {
            count++;
            Set<String> templates = wp2t.get(title);
            // update title with final redir target
            title = getFinalRedirTarget(alt2wp, title);
            // add templates to new title
            for (String template : templates) {
               temp.put(title, template);
            }
            // remove old target
            i.remove();
         }
      }

      for (String title : temp.keySet()) {
         for (String template : temp.get(title)) {
            wp2t.put(title, template);
         }
      }

      // update sourceTargets with final redirects
      if (sourceTargets != null) {
         Set<String> targets = new HashSet<String>();
         i = sourceTargets.iterator();
         while (i.hasNext()) {
            String title = i.next();
            if (alt2wp.containsKey(title)) {
               targets.add(getFinalRedirTarget(alt2wp, title));
               i.remove();
            }
         }
         sourceTargets.addAll(targets);
      }

      return count;
   }

   private static int replaceSourceWikipediaTemplates(Map<String,String> s2t, Map<String,String> alt2wp, Set<String> foundWpTitles, String host, String password, boolean reallyUpdate) {
      PageEditor editor = new PageEditor(host, password);
      int updateCount = 0;

      for (String wrTitle : s2t.keySet()) {
         String template = s2t.get(wrTitle);
         String wpTitle = template.substring("Wp-".length());
         wpTitle = getFinalRedirTarget(alt2wp, wpTitle);
         if (foundWpTitles.contains(wpTitle)) {
            if (reallyUpdate) {
               editor.doGet(wrTitle, true, "xml=1");
               String wrText = editor.readVariable(PageEditor.TEXTBOX1_PATTERN, true).trim();

               // replace source-wikipedia template with new template title
               StringBuffer wrBuf = new StringBuffer();
               Matcher m = WP2WRTitleParser.pSourceWikipedia.matcher(wrText);
               boolean found = false;
               boolean addTemplate = (wrText.indexOf("{{"+template+"}}") < 0); // add template ref if not already found
               while (m.find()) {
                  if (found) {
                     logger.warn("Replacing multiple source-wikipedia templates in: "+wrTitle);
                  }
                  if (addTemplate) {
                     m.appendReplacement(wrBuf, "{{" + Util.protectDollarSlash(template) + "}}");
                  }
                  else {
                     m.appendReplacement(wrBuf, "");
                  }
                  found = true;
               }
               if (!found) {
                  logger.warn("Could not find a source-wikipedia template in: "+wrTitle);
               }
               m.appendTail(wrBuf);

               // add wikipedia-notice template at bottom if not already found
               String wikipediaNotice = "{{wikipedia-notice|"+wpTitle+"}}";
               if (wrBuf.indexOf(wikipediaNotice) < 0) {
                  // TODO be very careful here!!! Insert notice before footer
                  int pos = wrBuf.indexOf("<show_sources_images_notes/>");
                  if (pos >= 0) {
                     wrBuf.insert(pos, wikipediaNotice+"\n");
                  }
                  else {
                     wrBuf.append("\n");
                     wrBuf.append(wikipediaNotice);
                  }
               }

               // update page
               String updatedText = wrBuf.toString();
               if (!wrText.equals(updatedText)) {
                  editor.setPostVariable("wpTextbox1", updatedText);
                  editor.setPostVariable("wpSummary", "Replace source-wikipedia template with new template reference");
                  editor.setPostVariable("xml", "1");
                  editor.doPost();

                  updateCount++;
                  if (updateCount % 100 == 0) {
                     System.out.print(".");
                  }
               }
               Util.sleep(UPDATE_DELAY_MILLIS);
            }
            else {
               updateCount++;
               logger.info("Update "+wrTitle+" with template reference: "+template+" that will reference wikipedia: "+wpTitle);
            }
         }
         else {
            logger.warn("Wikipedia title not found: "+wpTitle+" referenced by: "+wrTitle);
         }
      }

      return updateCount;
   }

   public static void updatePages(String werelateXML, String wikipediaXML,
                                  boolean reallyUpdate, boolean refreshTemplates, String startTitle, String host, String password)
           throws ParsingException, IOException {
      // This class is designed to build the wp2wr mapping by adding entries
      // as it finds copy-wikipedia or source-wikipedia templates in the
      // WeRelate pages.

      logger.warn("Running through the werelate file to generate 2 maps: wp2templates, templates2pages");
      WikiReader wr = new WikiReader();
      WP2WRTitleParser tp = new WP2WRTitleParser();
      wr.addWikiPageParser(tp);
      wr.read(werelateXML);
      MultiMap<String,String> wp2t = tp.getWp2templates();
      MultiMap<String,String> t2wr = tp.getTemplate2Wr();
      Map<String,String> s2t = tp.getSource2template();
      Set<String> sourceTargets = tp.getSourceTargets();
      Map<String,String> wp2wr = tp.getWp2Wr(); // moreinfo wikipedia templates link wp -> wr pages directly, without an intermediate template
      logger.warn("Number of wikipedia pages referenced in wp- templates: "+wp2t.size());
      logger.warn("Number of wikipedia templates referenced in werelate pages: "+t2wr.size());
      logger.warn("Number of wikipedia pages referenced in moreinfo templates:"+wp2wr.size());
      logger.warn("Number of werelate pages with source-wikipedia templates: "+s2t.size());

      Set<String>wpTitles = new HashSet<String>();
      wpTitles.addAll(wp2t.keySet());
      wpTitles.addAll(wp2wr.keySet());
      WikipediaAltNamesParser wanp = new WikipediaAltNamesParser(wpTitles);
      for (int i=0; i < 2; i++) {
         logger.warn("Starting run "+i+" through the wikipedia file to add redirects for wp titles.");
         wr = new WikiReader();
         wr.setSkipRedirects(false);
         wr.addWikiPageParser(wanp);
         wr.read(wikipediaXML);
         logger.warn("Found " + wanp.getAlt2wp().size() + " redirects.");
      }
      Map<String,String> alt2wp = wanp.getAlt2wp();

      // UpdateRedirects
      int updatedCount = updateRedirects(wp2t, sourceTargets, alt2wp);
      logger.warn("Updated "+ updatedCount +" redirects");

      //Now we're ready to get down to actually Updating!
      logger.warn("Starting final run through the wikipedia files to actually update pages.");
      // This class is designed to go through each Wikipedia page and update the corresponding WeRelate page(s).
      WikipediaUpdateParser p =  new WikipediaUpdateParser(wp2t, t2wr, wp2wr, alt2wp, sourceTargets, host, password, refreshTemplates);
      if (reallyUpdate) p.setWriteEnabled();
      p.setStartTitle(startTitle);
      wr = new WikiReader();
      wr.addWikiPageParser(p);
      wr.read(wikipediaXML);
      Set<String> foundWpTitles = p.getFoundWpTitles();

      logger.warn("Completed updates from wiki pages.  Updated "+ p.getUpdateCount()+" template pages");

      logger.warn("Replacing source-wikipedia templates");
      int cnt = replaceSourceWikipediaTemplates(s2t, alt2wp, foundWpTitles, host, password, reallyUpdate);
      logger.warn("Replaced source-wikipedia templates on "+cnt+" pages");
   }
}
