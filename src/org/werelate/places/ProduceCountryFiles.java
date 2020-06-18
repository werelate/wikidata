package org.werelate.places;

import java.io.*;
import java.util.TreeSet;
import java.util.Set;
import java.util.Collection;

import nu.xom.ParsingException;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.werelate.utils.Util;
import org.werelate.parser.WikiReader;
import org.werelate.parser.PlaceStandardParser;

public class ProduceCountryFiles {

   private static Logger logger = Logger.getLogger("org.werelate.scripts.check_pages");

   private static int MAX_DEPTH = 10;

   public static String getCountry(Place p) {
      String country = p.getTitle();
      String parentTitle = p.getParentTitle();
      int c = 0;
      while (!Util.isEmpty(parentTitle)) {
         if (c++ >= MAX_DEPTH) {
            logger.warn("max depth reached for " + p.getTitle());
            break;
         }
         Place parent = p.getStandard().getPlace(parentTitle);
         if (parent == null) {
            break;
         }
         country = parent.getTitle();
         parentTitle = parent.getParentTitle();
      }
      return country;
   }

   public static PlaceStandard readPlaceStandard(InputStream in) throws IOException, ParsingException {
      WikiReader wikiReader = new WikiReader();
      PlaceStandardParser placeStandardParser = new PlaceStandardParser(false);
      wikiReader.addWikiPageParser(placeStandardParser);
      wikiReader.setSkipRedirects(false);
      wikiReader.read(in);
      wikiReader.setSkipRedirects(true);
      PlaceStandard ps = placeStandardParser.getPlaceStandard();
      wikiReader.removeWikiPageParser(placeStandardParser);
      return ps;
   }

   public static void main(String [] args)
         throws ParseException, IOException, ParsingException
   {
      Options opt = new Options();
      opt.addOption("p", true, "pages.xml filename");
      opt.addOption("o", true, "hierarchical output directory");
      opt.addOption("a", true, "alpha output directory");
      opt.addOption("h", false, "Print out help information");

      BasicParser parser = new BasicParser();
      CommandLine cl = parser.parse(opt, args);

      if (cl.hasOption("h") || !cl.hasOption("p") || !cl.hasOption("o") || !cl.hasOption("a")) {
         System.out.println("Produces hierarchical and alphabetical lists of countries in the WeRelate database");
         HelpFormatter f = new HelpFormatter();
         f.printHelp("OptionsTip", opt);
      } else
      {
         String hierDir = cl.getOptionValue("o");
         String alphaDir = cl.getOptionValue("a");
         String pagesFile = cl.getOptionValue("p");
         PlaceStandard ps = readPlaceStandard(new FileInputStream(pagesFile));
         System.out.println("Setting contained places");

         for (Place p : ps.getPlaces())
         {
            if (!Util.isEmpty(p.getParentTitle()))
            {
               Place parent = ps.getPlace(p.getParentTitle());
               if (parent != null)
               {
                  parent.addContainedPlace(p.getTitle(), p.getType(), false);

               }
               for (Place.PreviousParent pp : p.getPreviousParents()) {
                  Place prevParent = ps.getPlace(pp.getParentTitle());
                  if (prevParent != null) {
                     prevParent.addContainedPlace(p.getTitle(), p.getType(), true);
                  }
               }
            }

         }

         PrintWriter hierOut = new PrintWriter(new FileWriter(hierDir + "/index.html"));
         hierOut.println("<HTML><HEAD><TITLE>Countries Index File</TITLE></HEAD><BODY>");
         PrintWriter alphaOut = new PrintWriter(new FileWriter(alphaDir + "/index.html"));
         alphaOut.println("<HTML><HEAD><TITLE>Countries Index File</TITLE></HEAD><BODY>");

         Set<Place> countries = new TreeSet<Place>();
         for (Place p : ps.getPlaces())
         {
            if (p.getParentTitle() == null)
            {
               countries.add(p);
            }
         }
         for (Place p : countries)
         {
            // Add the country to the index files
            String fileName = Util.romanize(p.getTitle()).replace(' ','_');
            hierOut.println("<a href=\"" + fileName + ".html\">" + p.getTitle() + "</a><br>");
            alphaOut.println("<a href=\"" + fileName + ".html\">" + p.getTitle() + "</a><br>");

            // Create the hierarchical country file
            PrintWriter countryOut = new PrintWriter(
                  new FileWriter(hierDir + '/' + fileName + ".html"));
            countryOut.println("<HTML><HEAD><TITLE>" + p.getTitle() + "</TITLE></HEAD><BODY>");
            countryOut.println("<H1>" + p.getTitle() + "</H1>");
            countryOut.println("<p>This page is re-generated daily from information in the place wiki.</p>");
            if(p.getContainedPlaces().size() > 0)
            {
               printContainedPlaces(countryOut, ps, p.getContainedPlaces(), 0);
            }
            countryOut.println("</BODY></HTML>");
            countryOut.close();

            // Create the alphabetical country file
            countryOut = new PrintWriter(
                  new FileWriter(alphaDir + '/' + fileName + ".html"));
            countryOut.println("<HTML><HEAD><TITLE>" + p.getTitle() + "</TITLE></HEAD><BODY>");
            countryOut.println("<H1>" + p.getTitle() + "</H1>");
            countryOut.println("<p>This page is re-generated daily from information in the place wiki.</p>");
            printAlphaPlaces(countryOut, ps, p.getTitle());
            countryOut.println("</BODY></HTML>");
            countryOut.close();
         }
         hierOut.println("</BODY></HTML>");
         hierOut.close();
         alphaOut.println("</BODY></HTML>");
         alphaOut.close();
      }
   }

   private static void printAlphaPlaces(PrintWriter out, PlaceStandard ps, String country) {
      Set<Place> sortedPlaces = new TreeSet<Place> ();
      for (Place p : ps.getPlaces()) {
         if (getCountry(p).equals(country) && !p.getTitle().equals(country)) {
            sortedPlaces.add(p);
         }
      }
      out.println("<ul>");
      for (Place p : sortedPlaces) {
         String url = "https://www.werelate.org/wiki/Place:" + p.getTitle();
         out.print("<li><a href=\"" + url + "\">" + p.getTitle() + "</a>");
         out.println(" [" + p.getType() + "]</li>");
      }
      out.println("</ul>");
   }

   private static void printContainedPlaces(PrintWriter out, PlaceStandard ps,
                                            Collection<Place.ContainedPlace> containedPlaces, int level)
   {
      out.println(tabs(level) + "<ul>");
      int insideLevel = level+1;
      // Sort the contained places list
      Set <Place> sortedPlaces = new TreeSet <Place> ();
      for (Place.ContainedPlace cp : containedPlaces)
      {
//         if (!cp.isPrevious()) {
            sortedPlaces.add(ps.getPlace(cp.getPlaceTitle()));
//         }
      }
      for (Place p : sortedPlaces)
      {
         //System.out.println("Printing out a sorted place");
         String url = "https://www.werelate.org/wiki/Place:" + p.getTitle();
         out.print(tabs(insideLevel) + "<li><a href=\"" + url + "\">" + p.getTitle() + "</a>");
         out.print(" [" + p.getType() + "]");
         if (p.getContainedPlaces().size() > 0)
         {
            out.print('\n');
            if (level > MAX_DEPTH) {
               logger.error("Error: max depth reached: " + p.getTitle());
               return;
            }
            printContainedPlaces(out, ps, p.getContainedPlaces(), insideLevel + 1);
         }
         out.println(tabs(insideLevel) + "</li>");
      }
      out.println(tabs(level) + "</ul>");
   }

   private static String tabs (int num)
   {
      String rval = "";

      for (int i=0; i < num; i++)
      {
         rval += '\t';
      }
      return rval;
   }
}
