package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

public class CalcMergeSourceInfo extends StructuredDataParser
{
   private Map<String,String> repoInfos;

   public CalcMergeSourceInfo(String duplicatesFilename) throws IOException
   {
      repoInfos = new HashMap<String,String>();

      BufferedReader in = new BufferedReader(new FileReader(duplicatesFilename));
      while (in.ready()) {
         String[] fields = in.readLine().split("\\|");
         for (int i = 1; i < fields.length; i++) {
            repoInfos.put(fields[i],"NOT FOUND");
         }
      }
      in.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:") && repoInfos.get(title.substring("Source:".length())) != null) {
         String unprefixedTitle = title.substring("Source:".length());
         String[] split = splitStructuredWikiText("source", text);
         String structuredData = split[0];

         if (!Util.isEmpty(structuredData)) {
            StringBuilder buf = new StringBuilder();
            Element root = parseText(structuredData).getRootElement();
            Elements children = root.getChildElements("repository");

            for (int i = 0; i < children.size(); i++) {
               Element repo = children.get(i);
               String repoTitle = repo.getAttributeValue("title");
               String repoLoc = repo.getAttributeValue("source_location");
               String repoAvail = repo.getAttributeValue("availability");

               if (repoLoc != null && repoLoc.indexOf('|') >= 0) {
                  logger.warn("Illegal source_location character: "+title);
                  repoLoc.replace('|','~');
               }

               if (buf.length() > 0) buf.append("|");
               buf.append(Util.isEmpty(repoTitle) ? " " : repoTitle);
               buf.append("~");
               buf.append(Util.isEmpty(repoAvail) ? " " : repoAvail);
               buf.append("~");
               buf.append(Util.isEmpty(repoLoc) ? " " : repoLoc);
            }

            repoInfos.put(unprefixedTitle,buf.toString());
         }
      }
   }

   public void calcMergeInfo(String humanSourcesFilename, String duplicatesFilename, String updatesFilename, String deletesFilename) throws IOException
   {
      Set<String> humanSources = new HashSet<String>();
      int keeperCount = 0;
      int noKeeperCount = 0;

      BufferedReader in = new BufferedReader(new FileReader(humanSourcesFilename));
      while (in.ready()) {
         String line = in.readLine();
         humanSources.add(line);
      }
      in.close();

      PrintWriter updates = new PrintWriter(updatesFilename);
      PrintWriter deletes = new PrintWriter(deletesFilename);
      in = new BufferedReader(new FileReader(duplicatesFilename));

      while (in.ready()) {
         String[] fields = in.readLine().split("\\|");

         // choose a keeper
         int keeper = 0;
         for (int i = 1; i < fields.length; i++) {
            if (humanSources.contains(fields[i]) || fields[i].startsWith("Ancestry.com")) {
               if (keeper > 0) {
                  keeper = -1;
                  break;
               }
               keeper = i;
            }
         }
         if (keeper < 0) {
            logger.warn("Multiple human-edited / Ancestry.com sources; keeping first one: "+fields[0]);
            keeper = 1;
         }

         if (keeper == 0) {
            keeper = 1;
            noKeeperCount++;
         }
         else {
            keeperCount++;
         }

         if (repoInfos.get(fields[keeper]) == null || repoInfos.get(fields[keeper]).equals("NOT FOUND")) {
            logger.warn("Keeper not found: "+fields[0]+ " > "+fields[keeper]);
            continue;
         }

         // gather repo info
         StringBuilder buf = new StringBuilder();
         buf.append(fields[keeper]);
         for (int i = 1; i < fields.length; i++) {
            if (i != keeper) {
               buf.append("|");
               String repoInfo = repoInfos.get(fields[i]);
               if (repoInfo == null) {
                  logger.warn("Source not found: "+fields[0]+" > "+fields[i]);
               }
               else if (repoInfo.equals("NOT FOUND")) {
                  logger.warn("Source missing: "+fields[0]+" > "+fields[i]);
               }
               else {
                  buf.append(repoInfo);
               }
            }
         }
         updates.println(buf.toString());

         // write titles to delete
         for (int i = 1; i < fields.length; i++) {
            if (i != keeper) {
               deletes.println("Source:"+fields[i]);
            }
         }
      }
      in.close();
      updates.close();
      deletes.close();
      System.out.println("Keeper found="+keeperCount+" not found="+noKeeperCount);
   }

   // 0=pages.xml 1=wlh(human)sources.txt 2=duplicates.txt in 3=updates.txt out 4=deletes.txt out
   public static void main(String[] args) throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      CalcMergeSourceInfo cmsi = new CalcMergeSourceInfo(args[2]);
      wikiReader.addWikiPageParser(cmsi);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      cmsi.calcMergeInfo(args[1],args[2],args[3], args[4]);
   }
}
