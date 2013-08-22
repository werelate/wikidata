package org.werelate.duplicates;

import org.apache.commons.cli.*;
import org.werelate.parser.WikiReader;
import org.werelate.parser.StructuredDataParser;
import org.werelate.utils.Util;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class CountDuplicates extends StructuredDataParser
{
   private int totalPeople;
   private int totalFamilies;

   public CountDuplicates() {
      super();
      totalPeople = totalFamilies = 0;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (comment.startsWith("merge into ")) {
         Matcher m = Util.REDIRECT_PATTERN.matcher(text);
         if (m.lookingAt()) {
            if (title.startsWith("Family:")) {
               totalFamilies++;
            }
            else if (title.startsWith("Person:")) {
               totalPeople++;
            }
         }
      }
   }

   public static void main(String[] args)
           throws ParseException, IOException, ParsingException
   {
      Options opt = new Options();
      opt.addOption("x", true, "pages.xml filename");
      opt.addOption("?", false, "Print help information");

      BasicParser parser = new BasicParser();
      CommandLine cl = parser.parse(opt, args);

      if (cl.hasOption("?") || !cl.hasOption("x")) {
         System.out.println("Count the number of people and family pages that have been merged so far");
         HelpFormatter f = new HelpFormatter();
         f.printHelp("OptionsTip", opt);
      } else
      {
         String pagesFile = cl.getOptionValue("x");

         System.out.println("Reading pages.xml");
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         CountDuplicates cd = new CountDuplicates();
         wikiReader.addWikiPageParser(cd);
         InputStream in = new FileInputStream(pagesFile);
         wikiReader.read(in);
         in.close();

         System.out.println("Total people="+cd.totalPeople);
         System.out.println("Total families="+cd.totalFamilies);
      }
   }
}
