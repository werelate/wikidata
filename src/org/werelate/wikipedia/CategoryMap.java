package org.werelate.wikipedia;

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class CategoryMap
{
   public static class Category {
      public String name;
      public Pattern pattern;

      public Category(String name, Pattern pattern) {
         this.name = name;
         this.pattern = pattern;
      }
   }

   public static List<Category> readCategoryMapFile(String filename) throws IOException
   {
      List<Category> categories = new ArrayList<Category>();

      // read category map
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while (in.ready()) {
         String line = in.readLine().trim();
         if (line.length() > 0) {
            String[] fields = line.split(":");
            if (fields[1].endsWith("|")) fields[1] = fields[1].substring(0, fields[1].length()-1);
            String keywords = fields[1].toLowerCase();
            Pattern pattern = Pattern.compile("\\b("+keywords+")\\b");
            categories.add(new Category(fields[0], pattern));
         }
      }
      in.close();

      return categories;
   }

   // args[0] = category map
   // args[1] = category list
   // args[2] = output directory
   public static void main(String[] args) throws IOException
   {
      // read category map
      List<Category> categories = readCategoryMapFile(args[0]);

      // set up output files
      int[] counts = new int[categories.size()];
      PrintWriter[] writers = new PrintWriter[categories.size()];
      for (int i = 0; i < categories.size(); i++) {
         counts[i] = 0;
         writers[i] = new PrintWriter(args[2]+"/"+categories.get(i).name+".txt");
      }

      // set up otherWriter
      PrintWriter otherWriter = new PrintWriter(args[2]+"/other.txt");

      // read categories file
      int otherCnt = 0;
      BufferedReader in = new BufferedReader(new FileReader(args[1]));
      while (in.ready()) {
         boolean found = false;
         String line = in.readLine().trim();
         int pos = line.lastIndexOf("\t");
         String catName = line.substring(0, pos);
         int cnt = Integer.parseInt(line.substring(pos+1));
         for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            Matcher m = c.pattern.matcher(catName.toLowerCase());
            if (m.find()) {
               found = true;
               counts[i] += cnt;
               writers[i].println("* [[Wikipedia:Category:"+catName+"]] "+cnt);
            }
         }
         if (!found) {
            otherCnt += cnt;
            otherWriter.println("* [[Wikipedia:Category:"+catName+"]] "+cnt);
         }
      }
      in.close();

      // close everything
      for (int i = 0; i < categories.size(); i++) {
         Category c = categories.get(i);
         System.out.println("Category "+c.name+" = "+counts[i]);
         writers[i].close();
      }
      System.out.println("Other = "+otherCnt);
      otherWriter.close();
   }
}
