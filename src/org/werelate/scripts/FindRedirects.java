package org.werelate.scripts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.editor.PageEditor;

import java.util.Set;
import java.util.Scanner;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class FindRedirects  extends StructuredDataParser {
   private static Logger logger = LogManager.getLogger("org.werelate.redirect");
   private static final Pattern REDIRECT_PATTERN1 = Pattern.compile("#redirect\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
   private Hashtable<String,String> Redirects = new Hashtable<String,String>(100000);

   public FindRedirects() {
      super();
   }
   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (title.startsWith("Place:")) {
         Matcher m = REDIRECT_PATTERN1.matcher(text.trim());
         if (m.lookingAt()) {
            String redirTarget = Util.translateHtmlCharacterEntities(m.group(1));
            Redirects.put(title.substring("Place:".length()),redirTarget.trim().substring("Place:".length()));
         }
      }
   }

   public void writeRedirects(String filename) throws IOException{
      PrintWriter out = new PrintWriter(new FileWriter(filename));

      Set Keys = Redirects.keySet();
      Iterator KeyIter = Keys.iterator();
      while(KeyIter.hasNext()){
         String startingTitle = (String)KeyIter.next();
         String currentTarget = Redirects.get(startingTitle);
         int cnt = 0;
         while(Redirects.get(currentTarget) != null){
            currentTarget = Redirects.get(currentTarget);
            if (++cnt > 5) {
               System.out.println("Redirect loop: " + startingTitle);
               break;
            }
         }
         out.println(startingTitle+"|"+currentTarget);
      }
      out.close();
   }



   public static void main(String[] args) throws IOException, ParsingException
   {

      if (args.length <= 1) {
         System.out.println("Usage: <pages.xml file> <file to write redirects to>");
      }
      else {
         FindRedirects redirs = new FindRedirects();
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         wikiReader.addWikiPageParser(redirs);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         redirs.writeRedirects(args[1]); 
      }
   }
}
