package org.werelate.wikipedia;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.werelate.parser.WikiReader;
import org.werelate.parser.WikiPageParser;
import org.werelate.utils.MultiMap;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Map;

import nu.xom.ParsingException;

public class WP2WR
{
   private static final Logger logger = Logger.getLogger("org.werelate.wikipedia");

   private static void outputFile(MultiMap<String,String> wp2t, MultiMap<String,String> t2wr, String filename) throws FileNotFoundException
   {
      PrintWriter out = new PrintWriter(filename);
      for (String wpTitle : wp2t.keySet()) {
         for (String templateTitle : wp2t.get(wpTitle)) {
            if (t2wr.containsKey(templateTitle)) {
               for (String wrTitle : t2wr.get(templateTitle)) {
                  out.println(wpTitle+"|"+wrTitle);
               }
            }
            else {
               logger.warn("Template not referenced: "+templateTitle);
            }
         }
      }
      out.close();
   }

   public static void main(String[] args)
   {
      Options opt = new Options();
      // p is for WeRelate pages file.
      opt.addOption("wp", true, "wikipedia xml filename");
      opt.addOption("wr", true, "pages.xml filename");
      opt.addOption("o", true, "Output wp|wr file");
      opt.addOption("?", false, "Print out help information");

      BasicParser parser = new BasicParser();
      try {
         CommandLine cl = parser.parse(opt, args);
         if (cl.hasOption("?") || !cl.hasOption("wp") || !cl.hasOption("wr") || !cl.hasOption("o")) {
            System.out.println("Generate a list of Wikipedia title|WeRelate title");
            HelpFormatter f = new HelpFormatter();
            f.printHelp("OptionsTip", opt);
         } else {
            String wikipediaXML = cl.getOptionValue("wp");
            String pagesXML = cl.getOptionValue("wr");

            // read WeRelate file looking for WP references
            logger.warn("Reading WeRelate file looking for wp references.");
            WikiReader wr = new WikiReader();
            WP2WRTitleParser titleParser = new WP2WRTitleParser();
            wr.addWikiPageParser(titleParser);
            wr.read(pagesXML);
            MultiMap<String,String> wp2t = titleParser.getWp2templates();
            MultiMap<String,String> t2wr = titleParser.getTemplate2Wr();

            // read Wikipedia file looking for redirects
            WikipediaAltNamesParser wanp = new WikipediaAltNamesParser(wp2t.keySet());
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
            int updatedCount = WikipediaUpdate.updateRedirects(wp2t, null, alt2wp);
            logger.warn("Updated "+ updatedCount +" redirects");

            // output the wp2wr file
            outputFile(wp2t, t2wr, cl.getOptionValue("o"));
         }
      }
      catch (ParseException e) {
         System.err.println("Invalid command line arguments." + e.getMessage());
      } catch (IOException e)
      {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      } catch (ParsingException e)
      {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
   }
}
