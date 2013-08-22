package org.werelate.scripts;

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.CountsCollector;
import org.werelate.utils.Util;

import java.io.*;

public class AnalyzeNames extends StructuredDataParser
{
   String prefix;
   String tagName;
   PrintWriter outAll;
//   PrintWriter outSome;

   public AnalyzeNames(String prefix, String outAllPath, String outSomePath) throws IOException {
      this.prefix = prefix+":";
      this.tagName = prefix.toLowerCase();
      outAll = new PrintWriter(new FileWriter(outAllPath));
//      outSome = new PrintWriter(new FileWriter(outSomePath));
   }

   private void addName(String name, StringBuilder buf) {
      buf.append(' ');
      buf.append(name);
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith(prefix)) {
         String name = title.substring(prefix.length()).toLowerCase().replaceAll("\\s+","");
         String[] split = splitStructuredWikiText(tagName, text);
         String structuredData = split[0];
			String userText = split[1].replace("{{name-stub}}", "").trim();
			if (!Util.isEmpty(userText)) {
				outAll.println(title);
			}
//         if (!Util.isEmpty(structuredData)) {
//            Element root = parseText(structuredData).getRootElement();
//            Elements elements = root.getChildElements("related");
//            StringBuilder allNames = new StringBuilder();
//            StringBuilder someNames = new StringBuilder();
//            for (int i = 0; i < elements.size(); i++) {
//               Element elm = elements.get(i);
//               String relatedName = elm.getAttributeValue("name").toLowerCase().replaceAll("\\s+","");
//               String source = elm.getAttributeValue("source");
//               if (source == null) source = "";
//               if (!"WeRelate - similar spelling".equals(source) &&               // ignore old similar spellings
//                   !(source.equals("") && username.equals("WeRelate agent"))) {   // ignore werelate-agent added names without sources
//                  addName(relatedName, allNames);                                 //     some are good, but many are not
//                  if (!source.startsWith("[[Source:The New American Dictionary of Baby Names") &&
//                      !source.startsWith("[[Source:A Dictionary of Surnames")) {
//                     addName(relatedName, someNames);
//                  }
//               }
//            }
//            if (allNames.length() > 0) {
//               outAll.println(name+allNames);
//            }
//            if (someNames.length() > 0) {
//               outSome.println(name+someNames);
//            }
//         }
      }
   }

   public void close() {
      outAll.close();
//      outSome.close();
   }

   // Generate various lists of places
   // 0=pages.xml 1=prefix 2=out non similar spelling 3=out non similar spelling and non book
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      AnalyzeNames self = new AnalyzeNames(args[1], args[2], args[3]);
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.close();
   }
}
