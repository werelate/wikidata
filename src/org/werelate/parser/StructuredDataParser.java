package org.werelate.parser;

import nu.xom.*;

import java.io.IOException;
import java.io.StringReader;

import org.apache.log4j.Logger;

public abstract class StructuredDataParser implements WikiPageParser {
   protected static final Logger logger = Logger.getLogger("org.werelate.parser");

   public static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

   private Builder builder;

   /**
    *
    */
   public StructuredDataParser() {
      builder = new Builder();
   }

   protected nu.xom.Document parseText(String text) throws ParsingException, IOException {
      return builder.build(new StringReader(XML_HEADER + text));
   }

   /**
    * Returns the structured text in position 0 of the array, wiki text in position 1
    * @param text
    */
   public static String[] splitStructuredWikiText(String tagName, String text) {
      String[] split = new String[2];
      String endTag = "</" + tagName + ">";
      int pos = text.indexOf(endTag);
      if (pos >= 0) {
         pos += endTag.length();
         // skip over \n if present
         split[0] = text.substring(0, pos);
         split[1] = text.substring(pos);
      }
      else {
         split[0] = null;
         split[1] = text;
      }
      return split;
   }
}
