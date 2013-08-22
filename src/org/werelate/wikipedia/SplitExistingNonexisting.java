package org.werelate.wikipedia;

import org.apache.commons.cli.*;
import org.werelate.utils.Util;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.net.URLEncoder;

public class SplitExistingNonexisting
{
   private static Map<String,String> readWp2Wr(String filename) throws IOException
   {
      Map<String,String> titles = new HashMap<String,String>();

      BufferedReader in = new BufferedReader(new FileReader(filename));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         if (fields[1].startsWith("Person:") || fields[1].startsWith("Family:")) {
            titles.put(fields[0], fields[1]);
         }
      }
      in.close();

      return titles;
   }

   public static List<String> getFamousCategories(String catString, List<CategoryMap.Category> catMap) {
      List<String> famousCategories = new ArrayList<String>();
      String[] categories = catString.toLowerCase().split("\\|");
      for (CategoryMap.Category c : catMap) {
         boolean found = false;
         for (String catEntry : categories) {
            if (c.pattern.matcher(catEntry).find()) {
               found = true;
               break;
            }
         }
         if (found) {
            famousCategories.add(c.name);
         }
      }
      return famousCategories;
   }

   public static void main(String[] args)
   {
      // given titles, category map, and wp|wr
      // split titles into existing (w/ famous categories) and non-existing (w/o categories)
      Options opt = new Options();
      // p is for WeRelate pages file.
      opt.addOption("t", true, "wikipedia titles filename");
      opt.addOption("c", true, "category map filename");
      opt.addOption("r", true, "wp2wr filename");
      opt.addOption("e", true, "Output existing txt");
      opt.addOption("n", true, "Output non-existing txt");
      opt.addOption("u", true, "Output unmatched html");
      opt.addOption("?", false, "Print out help information");

      BasicParser parser = new BasicParser();
      try {
         CommandLine cl = parser.parse(opt, args);
         if (cl.hasOption("?") || !cl.hasOption("t") || !cl.hasOption("c") || !cl.hasOption("r") || !cl.hasOption("e") || !cl.hasOption("n") || !cl.hasOption("u")) {
            System.out.println("Split titles into existing and non-existing and list unmatched wp titles in wp2wr");
            HelpFormatter f = new HelpFormatter();
            f.printHelp("OptionsTip", opt);
         } else {
            String titlesFilename = cl.getOptionValue("t");
            String categoryMapFilename = cl.getOptionValue("c");
            String wp2wrFilename = cl.getOptionValue("r");
            String existingFilename = cl.getOptionValue("e");
            String nonexistingFilename = cl.getOptionValue("n");
            String unmatchedFilename = cl.getOptionValue("u");

            List<CategoryMap.Category> categories = CategoryMap.readCategoryMapFile(categoryMapFilename);
            Map<String,String> wp2wr = readWp2Wr(wp2wrFilename);
            Set<String> matchedWpTitles = new HashSet<String>();

            PrintWriter existing = new PrintWriter(existingFilename);
            PrintWriter nonexisting = new PrintWriter(nonexistingFilename);
            BufferedReader in = new BufferedReader(new FileReader(titlesFilename));
            int lineCnt = 0;
            while (in.ready()) {
               String line = in.readLine();
               lineCnt++;
               String[] fields = line.split("\\|", 6);
               if (fields.length == 6) {
                  String wrTitle = wp2wr.get(fields[0]);
                  if (wrTitle != null) {
                     matchedWpTitles.add(fields[0]);
                     List<String> famousCategories = getFamousCategories(fields[5], categories);
                     if (famousCategories.size() > 0) {
                        existing.println(wrTitle+"|"+Util.join("|", famousCategories));
                     }
                  }
                  else {
                     nonexisting.println(fields[0]+"|"+fields[1]+"|"+fields[2]+"|"+fields[3]+"|"+fields[4]);
                  }
               }
               else {
                  System.out.println("Bad line number="+lineCnt+" line="+line);
               }
            }
            in.close();
            existing.println("</ul></body></html>");
            existing.close();
            nonexisting.close();

            PrintWriter unmatched = new PrintWriter(unmatchedFilename);
            existing.println("<html><head></head><body><ul>");
            int unmatchedCnt = 0;
            for (String wpTitle : wp2wr.keySet()) {
               if (!matchedWpTitles.contains(wpTitle)) {
                  unmatchedCnt++;
                  String wrTitle = wp2wr.get(wpTitle);
                  unmatched.println("<li><a href=\"http:www.werelate.org/wiki/"+URLEncoder.encode(wrTitle, "UTF-8")+"\">"+wrTitle+"</a></li>");
               }
            }
            existing.println("</ul></body></html>");
            unmatched.close();

            System.out.println("titles size="+lineCnt);
            System.out.println("wp2wr size="+wp2wr.size());
            System.out.println("matches count="+matchedWpTitles.size());
            System.out.println("unmatched count="+unmatchedCnt);
         }
      }
      catch (ParseException e) {
         System.err.println("Invalid command line arguments." + e.getMessage());
      } catch (IOException e)
      {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }

   }
}
