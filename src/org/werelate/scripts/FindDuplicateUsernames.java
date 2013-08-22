package org.werelate.scripts;

import org.werelate.utils.Util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class FindDuplicateUsernames {
   public static void main(String[] args) throws IOException {
      Map<String,List<String>> map = new TreeMap<String,List<String>>();
      // read user names into hash map
      BufferedReader r = new BufferedReader(new FileReader(args[0]));
      while (r.ready()) {
         String name = r.readLine().trim();
         String lcName = name.toLowerCase();
         List<String> names = map.get(lcName);
         if (names == null) {
            names = new ArrayList<String>();
            map.put(lcName, names);
         }
         names.add(name);
      }

      // print out names in hash map
      for (List<String> names : map.values()) {
         if (names.size() > 1) {
            System.out.println(Util.join(", ", names));
         }
      }
   }
}
