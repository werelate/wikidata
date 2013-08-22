package org.werelate.scripts;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.SortedSet;
import java.util.TreeSet;
import java.text.DecimalFormat;

public class SortLineLength
{
   // Sort a file by line length
   // 0=in 1=out
   public static void main(String[] args) throws IOException
   {
      DecimalFormat f = new DecimalFormat("00000");
      SortedSet<String> lines = new TreeSet<String>();
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while (in.ready()) {
         String line = in.readLine();
         lines.add(f.format(line.length())+"|"+line);
      }
      in.close();

      PrintWriter out = new PrintWriter(args[1]);
      for (String line : lines) {
         int pos = line.indexOf('|');
         out.println(line.substring(pos+1));
      }
      out.close();
   }
}
