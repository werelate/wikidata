package org.werelate.scripts;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;

public class Sample
{
   // 0=orig 1=.nn sample rate 2=result
   public static void main(String[] args) throws IOException
   {
      BufferedReader orig = new BufferedReader(new FileReader(args[0]));
      double sampleRate = Double.parseDouble(args[1]);
      PrintWriter result = new PrintWriter(args[2]);

      while (orig.ready()) {
         String line = orig.readLine();
         double r = Math.random();
         if (r < sampleRate) {
            result.println(line);
         }
      }
      orig.close();
      result.close();
   }
}
