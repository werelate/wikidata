package org.werelate.source;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

public class EnsureIdsExist
{
   public static void main(String[] args) throws IOException
   {
      if (args.length < 2) {
         System.out.println("Usage: <wiki placemap file> <fhlc placemap file>");
      }
      else {
         Set<String> ids = new HashSet<String>();
         BufferedReader r = new BufferedReader(new FileReader(args[0]));
         String line = r.readLine();
         while (line != null) {
            String[] fields = line.split("\\|");
            String id = fields[0];
            ids.add(id);
            line = r.readLine();
         }
         r.close();
         r = new BufferedReader(new FileReader(args[1]));
         line = r.readLine();
         while (line != null) {
            String[] fields = line.split("\\|");
            if (fields.length < 2) {
               System.out.println("ERROR: " + line);
            }
            else {
               String id = fields[1];
               if (!ids.contains(id)) {
                  System.out.println("Missing: "+line);
               }
            }
            line = r.readLine();
         }
         r.close();
      }
   }
}
