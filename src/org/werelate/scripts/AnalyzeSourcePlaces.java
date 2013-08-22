package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.utils.CountsCollector;

import java.io.*;
import java.net.URLEncoder;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class AnalyzeSourcePlaces extends StructuredDataParser
{
   private PrintWriter out;
   private PrintWriter err;
   private Map<String,String> stdPlaces;
   private Set<String> keepTitles;

   public AnalyzeSourcePlaces(PrintWriter out, PrintWriter err)
   {
      this.out = out;
      this.err = err;
      stdPlaces = new HashMap<String,String>();
   }

   public void loadStdPlaces(String filename) throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         stdPlaces.put(fields[0], fields[1]);
      }
      in.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            Element root = parseText(structuredData).getRootElement();

            boolean restandardize = false;
            Elements placeElms = root.getChildElements("place");
            for (int i = 0; i < placeElms.size(); i++) {
               Element place = placeElms.get(i);
               if (place.getValue().length() > 0) {
                  String placeText = place.getValue();
                  String[] fields = placeText.split("\\|");
                  String stdPlace = fields[0];
                  String userPlace = (fields.length == 2 ? fields[1] : fields[0]);
                  String newStdPlace = stdPlaces.get(userPlace);
                  if (newStdPlace == null) {
                     err.println(userPlace);
                  }
                  else if (!newStdPlace.equals(stdPlace)) {
                     restandardize = true;
                  }
               }
            }

            if (restandardize) {
               out.println(title);
            }
         }
      }
   }

   // determine which sources contain places that need to be updated
   // 0=pages.xml 1=std_places in 2=sources_to_update out 3=notfound_places out
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      PrintWriter out = new PrintWriter(args[2]);
      PrintWriter err = new PrintWriter(args[3]);
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      AnalyzeSourcePlaces asp = new AnalyzeSourcePlaces(out, err);
      asp.loadStdPlaces(args[1]);
      wikiReader.addWikiPageParser(asp);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      out.close();
      err.close();
   }
}
