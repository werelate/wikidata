package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import nu.xom.ParsingException;

public class ListExistingRenameTargets extends StructuredDataParser
{
   private Map<String,String> targetSource;
   private PrintWriter existingOut;

   public ListExistingRenameTargets(PrintWriter existingOut) throws IOException
   {
      this.existingOut = existingOut;
      this.targetSource = new HashMap<String,String>();
   }

   public void loadRenameTargets(String filename) throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         if (targetSource.get(fields[1]) != null) {
            existingOut.println("<li><a href=\"http://www.werelate.org/wiki/"+Util.wikiUrlEncoder(fields[0])+"\">"+Util.encodeXML(fields[0])+"</a>");
            existingOut.println("<br><a href=\"http://www.werelate.org/wiki/"+Util.wikiUrlEncoder(fields[1])+"\">"+Util.encodeXML(fields[1])+"</a>");
         }
         else {
            targetSource.put(fields[1],fields[0]);
         }
      }
      in.close();
   }

   public Map<String,String> getTargetSources() {
      return targetSource;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (targetSource.containsKey(title)) {
         String origTitle = targetSource.get(title);
         existingOut.println("<li><a href=\"http://www.werelate.org/wiki/"+Util.wikiUrlEncoder(origTitle)+"\">"+Util.encodeXML(origTitle)+"</a>");
         existingOut.println("<br><a href=\"http://www.werelate.org/wiki/"+Util.wikiUrlEncoder(title)+"\">"+Util.encodeXML(title)+"</a>");
         targetSource.remove(title);
      }
   }

   // List rename targets that already exist
   // 0=pages.xml 1=rename targets: old|new 2=existing.html out 3=nonexisting.txt out
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(false);
      PrintWriter out = new PrintWriter(new FileWriter(args[2]));
      out.println("<html><head></head><body><ul>");
      ListExistingRenameTargets lert = new ListExistingRenameTargets(out);
      lert.loadRenameTargets(args[1]);
      wikiReader.addWikiPageParser(lert);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      out.println("</ul></body></html>");
      out.close();

      out = new PrintWriter(new FileWriter(args[3]));
      Map<String,String> targetSource = lert.getTargetSources();
      for (String target : targetSource.keySet()) {
         String source = targetSource.get(target);
         out.println(source+"|"+target);
      }
      out.close();
   }
}
