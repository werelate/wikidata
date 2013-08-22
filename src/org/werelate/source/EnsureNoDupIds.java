package org.werelate.source;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class EnsureNoDupIds
{
   public static void main(String[] args) throws IOException
   {
      if (args.length == 0) {
         System.out.println("Usage: <map file>");
      }
      else {
         Map<String,String> ids = new HashMap<String,String>();
         BufferedReader r = new BufferedReader(new FileReader(args[0]));
         String line = r.readLine();
         while (line != null) {
            String[] fields = line.split("\\|");
            String id = fields[0];
            String existing = ids.get(id);
            if (existing != null) {
               System.out.println("Duplicate: " + existing + " : " + line);
            }
            else {
               ids.put(id, line);
            }
            line = r.readLine();
         }
         r.close();
      }
   }
}
