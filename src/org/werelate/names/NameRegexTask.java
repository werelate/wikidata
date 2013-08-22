package org.werelate.names;

import org.werelate.utils.Util;
import org.werelate.parser.WikiReader;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;

import nu.xom.ParsingException;

/**
 * Read pages.xml file and output "bad" givennames/surnames according to regular expressions
 */
public class NameRegexTask{
   private Pattern currentPattern;
   private FileWriter writer; 

   public NameRegexTask() {
      super();
   }

   public void openFile(String file) throws IOException{
      writer = new FileWriter(file); 
   }

   public void readFile(String file, String pattern, String prefix) throws IOException{
      BufferedReader readIn = new BufferedReader(new FileReader(file));
      System.out.println("reading the file and checking for matches");
      while(readIn.ready()){
         currentPattern = Pattern.compile(pattern);
         String currentName = Util.romanize(readIn.readLine());
         Matcher m = currentPattern.matcher(currentName);
         if(m.find()){
            writer.write(prefix + currentName + '\n');
         }
      }
      readIn.close();
   }

   public void close() throws IOException{
      writer.close();
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 2) {
         System.out.println("Usage: <pages.xml file> <output SurNameFile> <output GivenNameFile");
      }
      else {
         NameRegexTask reg = new NameRegexTask();
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);

         NameParser surnames = new NameParser();
         surnames.openFile("surnamefile.tmp");
         surnames.setPrefix("Surname:");
         wikiReader.addWikiPageParser(surnames);

         NameParser givennames = new NameParser();
         givennames.openFile("givennamefile.tmp");
         givennames.setPrefix("Given:");
         wikiReader.addWikiPageParser(givennames);

         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         surnames.close();
         givennames.close();

         reg.openFile(args[1]);
         reg.readFile("surnamefile.tmp" ,"[^.A-Za-z0-9' -]", "Surname:");
         reg.readFile("surnamefile.tmp", "[A-Za-z]{4}[A-Za-z]*[ -]", "Surname:");
         reg.close();
         reg.openFile(args[2]);
         reg.readFile("givennamefile.tmp", "[^A-Za-z]", "Givenname:");
         reg.close();
      }
   }
}
