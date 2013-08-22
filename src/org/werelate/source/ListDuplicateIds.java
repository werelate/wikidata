package org.werelate.source;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

public class ListDuplicateIds
{
   public static void main(String[] args) throws IOException
   {
      if (args.length == 0) {
         System.out.println("Usage: <input IdMapFile>");
      }
      else {
         Set<String> seenIds = new HashSet<String>();

         BufferedReader r = new BufferedReader(new FileReader(args[0]));
         String line = r.readLine();
         while (line != null) {
            String[] pieces = line.split("\\|");
            if (pieces.length == 2) {
               if (!seenIds.add(pieces[0])) {
                  System.out.println(pieces[0]);
               }
            }
            line = r.readLine();
         }
         r.close();
      }
   }
}
