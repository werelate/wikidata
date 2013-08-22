package org.werelate.rename;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.werelate.utils.Util;

public class authorTrimmer{
   private static final Pattern WORDS = Pattern.compile("((county)|(clerk)|(society)|(court)|(census)|(office)|(depart)|(church)|(notar(y|i|e))|(society)|(commission)|(registra)|(administration)|(committee)|(agency)|(bureau)|(archive)|(association)|(publish)|(survey)|(library)|(service)|(institute)|(revolution)|(museum)|(superintendrent)|(coroner)|(statistics)|(research)|(main author)|(company)|(secretary)|(collection)|(histor)|(catholic))");


   public static void main(String args[]) throws IOException{
   if(args.length < 2){
      System.err.println("syntax is <inputFile> <outputFile>");
      return;
   }
   File input = new File(args[0]);
   FileWriter outputWriter = new FileWriter(args[1]);
   if(!input.exists()){
      System.err.println(args[0] + " is not a valid filename");
      return;
   }
   BufferedReader readIn = new BufferedReader(new FileReader(input));
   String nextLine;
   while(readIn.ready()){
   nextLine = Util.romanize(readIn.readLine().toLowerCase());
   Matcher m = WORDS.matcher(nextLine);
   if(!m.find()){
       outputWriter.write(nextLine + '\n');  
   }
   }
   readIn.close();
   outputWriter.close();
   }
   
}
