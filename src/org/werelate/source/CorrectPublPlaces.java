package org.werelate.source;

import org.werelate.utils.CountsCollector;

import java.io.*;

public class CorrectPublPlaces
{
   public static void main(String[] args)
           throws IOException
   {
      if (args.length < 2) {
         System.out.println("Usage : <publication info file> <output file>");
      }
      else {
         CountsCollector cc = new CountsCollector();
         BufferedReader r = new BufferedReader(new FileReader(args[0]));
         String line = r.readLine();
         while (line != null) {
            String[] fields = line.split("\\|");
            if (fields[1].equals("place_issued") && fields.length > 2) {
               cc.add(fields[2]);
            }
            line = r.readLine();
         }
         r.close();
         PrintWriter w = new PrintWriter(new FileWriter(args[1]));
         cc.writeSorted(false, 0, w);
      }
   }
}
