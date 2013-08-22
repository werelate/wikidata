package org.werelate.sample;

import org.apache.log4j.Logger;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class PlaceParser extends StructuredDataParser {
   private static Logger logger = Logger.getLogger("org.werelate.sample");
   private static final Pattern REDIRECT_PATTERN = Pattern.compile("#redirect\\s*\\[\\[Place:(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);


   public PlaceParser() {
       super();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (title.startsWith("Place:")) {
         String[] split = splitStructuredWikiText("place", text);
         title = title.substring("Place:".length());
         String structuredData = split[0];
         String wikiText = split[1];

         if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(split[0]);
            Element elm = doc.getRootElement();

            if (elm.getFirstChildElement("type") != null) {
               String type = Util.translateHtmlCharacterEntities(elm.getFirstChildElement("type").getValue());
            }

            Elements prevParents = elm.getChildElements("also_located_in");
            for (int i = 0; i < prevParents.size(); i++) {
               Element prevParent = prevParents.get(i);
               String place = Util.translateHtmlCharacterEntities(prevParent.getAttributeValue("place"));
               String fromYear = Util.translateHtmlCharacterEntities(prevParent.getAttributeValue("from_year"));
               String toYear = Util.translateHtmlCharacterEntities(prevParent.getAttributeValue("to_year"));
            }
         }
         else {
            Matcher m = REDIRECT_PATTERN.matcher(wikiText);
            if (m.lookingAt()) {
               String redirTarget = Util.translateHtmlCharacterEntities(m.group(1));
            }
         }
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length == 0) {
         System.out.println("Usage: <pages.xml file>");
      }
      else {
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         PlaceParser placeParser = new PlaceParser();
         wikiReader.addWikiPageParser(placeParser);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
      }
   }
}
