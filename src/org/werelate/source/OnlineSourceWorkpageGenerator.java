package org.werelate.source;

import java.io.*;
import java.net.URLEncoder;

public class OnlineSourceWorkpageGenerator
{
   private static PrintWriter openWriter(String base, int seq) throws IOException
   {
      PrintWriter w = new PrintWriter(new FileWriter(base+"-"+seq+".html"));
      w.println("<html><head><title>"+base+" - "+seq+"</title></head><body><ul>");
      return w;
   }

   private static void closeWriter(PrintWriter w) {
      w.println("</ul></body></html>");
      w.close();
   }

   private static String getListItem(String line) throws UnsupportedEncodingException
   {
      String[] pieces = line.split("\\|");
      return "<li><a href=\""+pieces[0]+"\">"+pieces[0]+"</a> &nbsp; <a href=\"https://www.werelate.org/w/index.php?title="+URLEncoder.encode(pieces[1],"UTF-8")+"&action=edit\">"+pieces[1]+"</a></li>";
   }

   public static void main(String[] args)
           throws IOException
   {
      if (args.length < 1) {
         System.out.println("Usage: workpage");
      }
      else {
         int cnt = 0;
         int seq = 1;
         PrintWriter w = openWriter(args[0], seq);
         BufferedReader r = new BufferedReader(new FileReader(args[0]+".txt"));
         String line = r.readLine();
         while (line != null) {
            if (cnt == 1000) {
               cnt = 0;
               closeWriter(w);
               seq++;
               w = openWriter(args[0], seq);
            }
            w.println(getListItem(line));
            cnt++;
            line = r.readLine();
         }
         r.close();
         closeWriter(w);
      }
   }
}
