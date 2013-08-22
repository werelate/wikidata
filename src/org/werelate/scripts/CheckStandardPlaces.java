package org.werelate.scripts;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CheckStandardPlaces
{
   public static void main(String[] args)
           throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         String place = fields[0];
         String stdPlace = fields[1];
         if (stdPlace.endsWith(", United States")) {
            String[] levels = stdPlace.split(",");
            String stdState = levels[levels.length - 2].trim();
            if (place.indexOf(stdState) < 0) {
               System.out.println(line);
            }
         }
      }
   }
}
