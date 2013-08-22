package org.werelate.scripts;

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GeneratePlaceStandard extends StructuredDataParser
{
   PrintWriter writer;

   public GeneratePlaceStandard(PrintWriter writer) {
      this.writer = writer;
   }

   private String escape(String s) {
      // for simplicity I'm writing out a pipe-delimited file, with ~'s separating multiple alt-names or also-located-in's
      // | and # shouldn't be in place names, so we shouldn't have a problem removing them
      return s.replace('|',' ').replace('#',' ');
   }

   private void addFieldValue(Element root, String elmName, StringBuilder buf) {
      Element element = root.getFirstChildElement(elmName);
      if (element != null) {
         buf.append(escape(element.getValue()));
      }
   }

   private void addFieldValues(Element root, String elmName, String attrName, StringBuilder buf) {
      Elements elements= root.getChildElements(elmName);
      for (int i = 0; i < elements.size(); i++) {
         if (i > 0) {
            buf.append('#');
         }
         buf.append(escape(elements.get(i).getAttribute(attrName).getValue()));
      }
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Place:")) {
         String[] split = splitStructuredWikiText("place", text);
         String structuredData = split[0];
         if (!Util.isEmpty(structuredData)) {
            StringBuilder buf = new StringBuilder();
            Element root = parseText(structuredData).getRootElement();

            // format = title | alt names | type | also located in's | lat | lon

            // title
            buf.append(title.substring("Place:".length()));
            buf.append('|');

            // alt names
            addFieldValues(root, "alternate_name", "name", buf);
            buf.append('|');

            // type
            addFieldValue(root, "type", buf);
            buf.append('|');

            // also located in
            addFieldValues(root, "also_located_in", "place", buf);
            buf.append('|');

            // lat + lon
            addFieldValue(root, "latitude", buf);
            buf.append('|');
            addFieldValue(root, "longitude", buf);

            writer.println(buf.toString());
         }
         else {
            logger.warn("Structured data missing for "+title);
         }
      }
   }

   // Generate the place standard
   // 0=pages.xml 1=placeStandard.txt
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      PrintWriter writer = new PrintWriter(args[1]);
      GeneratePlaceStandard self = new GeneratePlaceStandard(writer);
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      writer.close();
   }
}
