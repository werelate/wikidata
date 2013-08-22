package org.werelate.places;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.HashMap;

public class CalcPlaceRenames
{
   private static Logger logger = Logger.getLogger("org.werelate.places");

   private static String getPlaceName(String place, Map<String,String> placeTargets) {
      // split place name from located-in place name
      String[] placeLocatedIn = place.split(",", 2);
      if (placeLocatedIn.length == 1) return place;

      // get target for located-in place
      String target = placeTargets.get(placeLocatedIn[1].trim());
      if (target == null) {
         logger.warn("Place located in not found: "+place);
         return place;
      }

      // return place name + getPlaceName(located-in place target)
      return placeLocatedIn[0].trim()+", "+getPlaceName(target, placeTargets);
   }

   // Calculate places to rename based upon parent places being renamed
   // 0=place_targets.txt 1=places to rename: old|new
   public static void main(String[] args) throws IOException
   {
      Map<String,String> placeTargets = new HashMap<String,String>();

      // load place targets
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while (in.ready()) {
         String line = in.readLine();
         String[] placeTarget = line.split("\\|");
         placeTargets.put(placeTarget[0],placeTarget[1]);
      }
      in.close();

      // output places that need to be renamed
      PrintWriter out = new PrintWriter(args[1]);
      for (String place : placeTargets.keySet()) {
         // if not already a redirect
         if (place.equals(placeTargets.get(place))) {
            // see if it needs to be renamed
            String rename = getPlaceName(place, placeTargets);
            if (!place.equals(rename)) {
               out.println("Place:"+place+"|Place:"+rename);
            }
         }
      }
      out.close();
   }
}
