package org.werelate.test;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashSet;

public class places{
   private static final Pattern  idNum = Pattern.compile("[^|]*\\|([0-9]+)");
   private static final Pattern errNum = Pattern.compile("taken care off yet.*?-([0-9]+)$");


   public static void main(String args[]) throws IOException{
   if(args.length < 2){
      System.err.println("syntax is <inputFile> <missingFile> <outputFile>");
      return;
   }
   File input = new File(args[0]);
   FileWriter outputWriter = new FileWriter(args[2]);
   if(!input.exists()){
      System.err.println(args[0] + " is not a valid filename");
      return;
   }
   HashSet<String> missingIds = new HashSet<String>();
   BufferedReader readIn = new BufferedReader(new FileReader(input));
   BufferedReader readIn2 = new BufferedReader(new FileReader(args[1]));
   while(readIn2.ready()){
      String currentLine = readIn2.readLine();
      Matcher m = idNum.matcher(currentLine);
      if(m.find())
         missingIds.add(m.group(1));
   }
   while(readIn.ready()){
      String currentLine = readIn.readLine();
      Matcher m = errNum.matcher(currentLine);
      if(m.find()){
      if(!missingIds.contains(m.group(1)))
         outputWriter.write(currentLine + '\n');
      }
      else
         outputWriter.write(currentLine + '\n');
   }
   readIn.close();
   outputWriter.close();
   }
   
}
