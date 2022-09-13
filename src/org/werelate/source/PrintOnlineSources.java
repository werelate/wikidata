package org.werelate.source;

import org.werelate.utils.CountsCollector;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Set;
import java.util.HashSet;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Nodes;

public class PrintOnlineSources  extends StructuredDataParser {
   private static Logger logger = LogManager.getLogger("org.werelate.source");
   private static String[] SKIP = {"www.familysearch.org","boards.ancestry.com","genforum.genealogy.com","worldconnect.rootsweb.com",
                                   "worldconnect.genealogy.rootsweb.com","www.cyndislist.com","familytreemaker.genealogy.com","groups.yahoo.com",
                                   "groups.msn.com"};
   private static int WEB = 0;
   private static int ARCHIVER = 1;
   private static int CONTENT = 2;
   private static int ROOTSWEB = 3;
   private static int ANCESTRY = 4;
   private static int NUM_WRITERS = 5;

   private CountsCollector cc;
   private Set<String> skip;
   private PrintWriter[] writers;

   public PrintOnlineSources() {
      super();
      cc = new CountsCollector();
      skip = new HashSet<String>();
      for (String s : SKIP) {
         skip.add(s);
      }
      writers = new PrintWriter[NUM_WRITERS];
      for (int i = 0; i < NUM_WRITERS; i++) {
         writers[i] = null;
      }
   }

   public void openWriters(String web, String archiver, String content, String rootsweb, String ancestry) throws IOException
   {
      writers[WEB] = new PrintWriter(new FileWriter(web));
      writers[ARCHIVER] = new PrintWriter(new FileWriter(archiver));
      writers[CONTENT] = new PrintWriter(new FileWriter(content));
      writers[ROOTSWEB] = new PrintWriter(new FileWriter(rootsweb));
      writers[ANCESTRY] = new PrintWriter(new FileWriter(ancestry));
   }

   public void closeWriters() {
      for (int i = 0; i < NUM_WRITERS; i++) {
         if (writers[i] != null) {
            writers[i].close();
         }
      }
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if(title.startsWith("Source:")){
         String[] wikiText = StructuredDataParser.splitStructuredWikiText("source", text);
         if (wikiText[0] != null) {
            Document doc = parseText(wikiText[0]);
            Nodes nodes = doc.query("source/url|source/repository/@source_location");
            for (int i = 0; i < nodes.size(); i++) {
               String url = nodes.get(i).getValue();
               String host = url.replaceFirst("^(http://|https://)?([^:/]+).*$","$2").toLowerCase();
               if (skip.contains(host)) {
                  continue;
               }
               int w = WEB;
               if (host.equals("archiver.rootsweb.com")) {
                  w = ARCHIVER;
               }
               else if (host.equals("content.ancestry.com")) {
                  w = CONTENT;
               }
               else if (host.indexOf(".rootsweb.com") >= 0) {
                  w = ROOTSWEB;
               }
               else if (host.indexOf(".ancestry.com") >= 0 || host.indexOf(".genealogy.com") >= 0) {
                  w = ANCESTRY;
               }
               writers[w].println(url+"|"+title);
            }
         }
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length < 1) {
         System.out.println("Usage: <pages.xml> web archiver content rootsweb ancestry");
      }
      else {
         PrintOnlineSources parser = new PrintOnlineSources();
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(parser);
         InputStream in = new FileInputStream(args[0]);
         parser.openWriters(args[1],args[2],args[3],args[4],args[5]);
         wikiReader.read(in);
         in.close();
         parser.closeWriters();
      }
   }
}