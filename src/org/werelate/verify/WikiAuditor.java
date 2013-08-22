package org.werelate.verify;

import org.apache.commons.cli.*;
import org.werelate.parser.WikiReader;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import nu.xom.ParsingException;

public class WikiAuditor
{
   public static void main(String[] args) throws ParseException, IOException, ParsingException
   {
      Options opt = new Options();
      opt.addOption("x", true, "pages.xml filename");
      opt.addOption("places", true, "places.html output filename");
      opt.addOption("people", true, "people.html output filename");
      opt.addOption("?", false, "Print help information");

      BasicParser parser = new BasicParser();
      CommandLine cl = parser.parse(opt, args);

      if (cl.hasOption("?") || !cl.hasOption("x") || !cl.hasOption("places") || !cl.hasOption("people")) {
         System.out.println("Look for data integrity problems in the wiki");
         HelpFormatter f = new HelpFormatter();
         f.printHelp("OptionsTip", opt);
      } else
      {
         String pagesFile = cl.getOptionValue("x");

         System.out.println("Reading pages.xml");
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);

         // add audit functions
         PlaceLinkVerifier placeVerifier = new PlaceLinkVerifier();
         wikiReader.addWikiPageParser(placeVerifier);
         PersonLinkVerifier personVerifier = new PersonLinkVerifier();
         wikiReader.addWikiPageParser(personVerifier);

         InputStream in = new FileInputStream(pagesFile);
         wikiReader.read(in);
         in.close();

         placeVerifier.verify(cl.getOptionValue("places"));
         personVerifier.verify(cl.getOptionValue("people"));

         System.out.println("Total place problems="+placeVerifier.getTotalProblems());
         System.out.println("Total person problems="+personVerifier.getTotalProblems());
      }
   }
}
