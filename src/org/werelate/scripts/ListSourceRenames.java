package org.werelate.scripts;

import org.werelate.utils.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListSourceRenames
{
   private static Pattern CENSUS_PATTERN = Pattern.compile("1\\d\\d0 U.S. Census Population Schedule$");
   private static final String[] USERS_ARRAY = {"JBS66","Jstump","Amelia.Gerlicher","Mksmith","Beth","DFree","Jillaine",
     "Quolla6","Kennebec1","Gewurztraminer","Ceyockey","Jlanoux","Jrich","BobC", "Solveig", "Skater", "Taylor", "Leo Bijl", "Dallan", "Moonknight125"};

   private static final int MAX_OTHER_SETS = 7;

   // 0=source renames 1=wlhSources 2=renames dir 3=robot sample rate (.nn)
   public static void main(String[] args) throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      String dir = args[2];
      double sampleRate = Double.parseDouble(args[3]);

      BufferedReader wlhSourcesIn = new BufferedReader(new FileReader(args[1]));
      Set<String> wlhSources = new HashSet<String>();
      while (wlhSourcesIn.ready()) {
         String line = wlhSourcesIn.readLine();
         wlhSources.add(line.replace('_', ' '));
      }
      wlhSourcesIn.close();

      Set<String>[] otherTitles = new HashSet[MAX_OTHER_SETS];
      for (int i = 0; i < MAX_OTHER_SETS; i++) {
         otherTitles[i] = new HashSet<String>();
         BufferedReader otherIn = new BufferedReader(new FileReader(dir+"/other"+i+".txt"));
         while (otherIn.ready()) {
            String line = otherIn.readLine();
            otherTitles[i].add(line);
         }
         otherIn.close();
      }

      PrintWriter robotOut = new PrintWriter(dir+"/robot_renames.html");
      robotOut.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>");

      PrintWriter censusOut = new PrintWriter(dir+"/census_renames.html");
      censusOut.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>");

      PrintWriter[] otherOut = new PrintWriter[MAX_OTHER_SETS+1];
      for (int i = 0; i <= MAX_OTHER_SETS; i++) {
         otherOut[i] = new PrintWriter(dir+"/other"+i+".html");
         otherOut[i].println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>");
      }

      Map<String,PrintWriter> userOut = new HashMap<String,PrintWriter>();
      for (String user : USERS_ARRAY) {
         PrintWriter pw = new PrintWriter(dir+"/"+user.replace(' ','_').replace('.','_')+".html");
         pw.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>");
         userOut.put(user, pw);
      }

      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         if (fields.length < 2 || fields[0].length() == 0 || fields[1].length() == 0) {
            System.out.println("Invalid line:"+line);
            continue;
         }
         String newTitle = fields[0];
         String oldTitle = fields[1];
         String username = fields.length == 8 ? fields[7] : "";
         boolean isHuman = (!Util.isEmpty(username) && !username.equalsIgnoreCase("WeRelate Agent")) || wlhSources.contains(oldTitle);
         Matcher m = CENSUS_PATTERN.matcher(newTitle);
         boolean isCensus = m.find();

         if (!newTitle.equals(oldTitle)) {
            double r = Math.random();
            String lineOut = "<a href=\"https://www.werelate.org/wiki/Source:"+Util.wikiUrlEncoder(oldTitle)+"\">"+Util.encodeXML(oldTitle)+"</a><br>"+
                        "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+newTitle+"<br>";
            if (isHuman) {
               PrintWriter pw = userOut.get(username);
               if (pw != null) {
                  pw.println(lineOut);
               }
               else {
                  boolean found = false;
                  for (int i = 0; i < MAX_OTHER_SETS; i++) {
                     if (otherTitles[i].contains(oldTitle)) {
                        otherOut[i].println(lineOut);
                        found = true;
                        break;
                     }
                  }
                  if (!found) {
                     otherOut[MAX_OTHER_SETS].println(lineOut);
                  }
               }
            }
            else if (r < sampleRate) {
               robotOut.println(lineOut);
            }
            if (isCensus) {
               censusOut.println(lineOut);
            }
         }
      }
      in.close();
      for (PrintWriter pw : userOut.values()) {
         pw.println("</body></html>");
         pw.close();
      }
      for (int i = 0; i <= MAX_OTHER_SETS; i++) {
         otherOut[i].println("</body></html>");
         otherOut[i].close();
      }
      robotOut.println("</body></html>");
      robotOut.close();
      censusOut.println("</body></html>");
      censusOut.close();
   }
}
