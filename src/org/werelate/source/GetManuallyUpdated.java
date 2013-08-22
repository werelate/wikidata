package org.werelate.source;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class GetManuallyUpdated
{
   private final static Pattern TITLE = Pattern.compile("<title>(.*?)</title>");
   private final static Pattern USER = Pattern.compile("<(ip|username)>(.*?)</(ip|username)>");

   // Before running this program, run:
   //   grep -E "(<title>|<ip>|<username>)" pages.xml
   public static void main(String[] args) throws IOException
   {
      if (args.length < 1) {
         System.out.println("Usage: <grep result file>");
      }
      else {
         BufferedReader r = new BufferedReader(new FileReader(args[0]));
         String line = r.readLine();
         while (line != null) {
            Matcher m = TITLE.matcher(line);
            if (m.find()) {
               String title = m.group(1);
               line = r.readLine();
               m = USER.matcher(line);
               if (m.find()) {
                  String user = m.group(2);
                  if (title.startsWith("Source:") && !user.equalsIgnoreCase("werelate agent")) {
                     System.out.println(title.substring("Source:".length()));
                  }
               }
               else {
//                  System.err.println("User not found for: " + title);
               }
            }
            else {
//               System.err.println("Title not found on: " + line);
            }

            line = r.readLine();
         }
      }
   }
}
