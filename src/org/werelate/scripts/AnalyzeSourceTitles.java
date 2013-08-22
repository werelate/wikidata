package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.utils.CountsCollector;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

import nu.xom.ParsingException;
import nu.xom.Element;

public class AnalyzeSourceTitles extends StructuredDataParser
{
   public CountsCollector cc;

   PrintWriter out;

   public AnalyzeSourceTitles(PrintWriter out)
   {
      cc = new CountsCollector();
      this.out = out;
   }

   private static final String[] LOWERCASE_WORDS_ARRAY = {
      "against", "as", "before", "between", "during", "under", "versus", "within", "through", "up",
   };
   public static final Set<String> LOWERCASE_WORDS = new HashSet<String>();
   static {
      for (String word : LOWERCASE_WORDS_ARRAY) LOWERCASE_WORDS.add(word);
   }
   public static final Pattern WORD_REGEX = Pattern.compile("[^ '`~!@#$%&_=:;<>,./{}()?+*|\"\\-\\[\\]\\\\]+");

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
//         String[] split = splitStructuredWikiText("source", text);
//         String structuredData = split[0];

//         if (!Util.isEmpty(structuredData)) {
            String unprefixedTitle = title.substring("Source:".length());
//            Element root = parseText(structuredData).getRootElement();
//            String sourceTitle = "";

//            Element sourceTitleElm = root.getFirstChildElement("source_title");
//            if (sourceTitleElm != null) sourceTitle = sourceTitleElm.getValue().trim();
//            if (sourceTitle.length() == 0) sourceTitle = unprefixedTitle;

            Matcher m = WORD_REGEX.matcher(unprefixedTitle);
            while (m.find()) {
               String word = m.group(0).toLowerCase();
               cc.add(word);
            }
//         }
      }
   }

   // pages.xml us_sources
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      PrintWriter out = new PrintWriter(args[1]);
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      AnalyzeSourceTitles ast = new AnalyzeSourceTitles(out);
      wikiReader.addWikiPageParser(ast);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      ast.cc.writeSorted(false, 100, out);
   }
}
