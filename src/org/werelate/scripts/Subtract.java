package org.werelate.scripts;

import java.io.*;
import java.util.Set;
import java.util.HashSet;

public class Subtract
{
   // 0=orig 1=minus 2=result
   public static void main(String[] args) throws IOException
   {
      BufferedReader orig = new BufferedReader(new FileReader(args[0]));
      BufferedReader minus = new BufferedReader(new FileReader(args[1]));
      PrintWriter result = new PrintWriter(args[2]);

      Set<String> removing = new HashSet<String>();
      while (minus.ready()) {
         String line = minus.readLine().trim();
         if (line.length() > 0) removing.add(line);
      }
      minus.close();

      while (orig.ready()) {
         String line = orig.readLine();
         if (!removing.contains(line)) {
            result.println(line);
         }
      }
      orig.close();
      result.close();
   }
}
