package org.werelate.names;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.werelate.utils.Util;
import org.werelate.utils.CountsCollector;

import java.io.*;
import java.util.Set;

public class FilterFrequentNames
{
   public static void main(String[] args)
           throws IOException
   {
      if (args.length < 3) {
         System.out.println("Usage: cutoff <input counts filename> <output bucket counts filename>");
      }
      DoubleMetaphone dmp = new DoubleMetaphone();
//        dmp.setMaxCodeLen(6);
//       Soundex sdx = new Soundex();
      CountsCollector ccBucket = new CountsCollector();
      CountsCollector ccFreq = new CountsCollector();

      int cutoff = Integer.parseInt(args[0]);
      int largeBucketSize = 30;
      BufferedReader reader = new BufferedReader(new FileReader(args[1]));
      PrintWriter out = new PrintWriter(args[2]);

      int cntNames = 0;
      int totalFreq = 0;
      int keptFreq = 0;
      String line = reader.readLine();
      while (line != null) {
         String[] fields = line.split("\t");
         String name = fields[0].trim().toLowerCase();
         String bucket = dmp.doubleMetaphone(Util.romanize(name));
//            String bucket = sdx.soundex(name);
         int freq = Integer.parseInt(fields[1]);
         ccFreq.add(bucket, freq);
         totalFreq += freq;
         if (freq >= cutoff) {
            cntNames++;
            keptFreq += freq;
            ccBucket.add(bucket);
            out.println(name);
         }
         line = reader.readLine();
      }
      reader.close();

      int cntLargeBuckets = 0;
      int freqLargeBuckets = 0;
      for (String bucket : (Set<String>)ccBucket.getKeys()) {
         int count = ccBucket.getCount(bucket);
         if (count > largeBucketSize) {
            cntLargeBuckets++;
            freqLargeBuckets += ccFreq.getCount(bucket);
         }
      }

      System.out.println("Names above cutoff="+cntNames);
      System.out.println("Kept names freq %="+(keptFreq*100.0/totalFreq));
      System.out.println("Large bucket count="+cntLargeBuckets);
      System.out.println("Large bucket freq %="+(freqLargeBuckets*100.0/totalFreq));
      System.out.println("Cutoff freq ppm="+(cutoff*1000000.0/totalFreq));
      out.close();
   }
}
