package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.util.Set;
import java.util.HashSet;
import java.io.*;

import nu.xom.ParsingException;

public class ListHumanSources extends StructuredDataParser
{
   private Set<String> wlhSources;
   private PrintWriter out;

   public ListHumanSources(BufferedReader wlhSourcesIn, PrintWriter out) throws IOException
   {
      wlhSources = new HashSet<String>();
      while (wlhSourcesIn.ready()) {
         String line = wlhSourcesIn.readLine();
         wlhSources.add(line.replace('_', ' '));
      }

      this.out = out;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            String unprefixedTitle = title.substring("Source:".length());
            boolean isEdited = !Util.isEmpty(username) && !username.equalsIgnoreCase("WeRelate Agent");

            if (isEdited || wlhSources.contains(unprefixedTitle)) {
               out.println(unprefixedTitle);
            }
         }
      }
   }

   // pages.xml wlhsources humanOut.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      BufferedReader wlhSourcesIn = new BufferedReader(new FileReader(args[1]));
      PrintWriter out = new PrintWriter(new FileWriter(args[2]));
      ListHumanSources lhs = new ListHumanSources(wlhSourcesIn, out);
      wikiReader.addWikiPageParser(lhs);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      wlhSourcesIn.close();
      out.close();
   }
}
