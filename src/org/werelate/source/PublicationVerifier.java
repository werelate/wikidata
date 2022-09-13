package org.werelate.source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import nu.xom.ParsingException;

public class PublicationVerifier{
   private static Logger logger = LogManager.getLogger("org.werelate.source");

   private FileWriter errPubWriter;
   private FileWriter finalPubWriter;

   private static final Pattern SIMPPUBLICATION = Pattern.compile("<Publication>([^:<]+):([^,;<]+),([^<]+)</Publication>");
   private static final Pattern NOYEAR = Pattern.compile("<Publication>([^:<]+):([^,;<]+)</Publication>");
   private static final Pattern DOUBLE = Pattern.compile("<Publication>([^:<]+):([^,;<]+);([^:<]+):([^,;<]+),([^<]+)</Publication>");
   private static final Pattern JUSTYEAR = Pattern.compile("<Publication>[^,]*([0-9][0-9]([0-9]|-)([0-9]|-))(\\?)?(\\])?</Publication>");
   private static final Pattern PUBANDYEAR  = Pattern.compile("<Publication>([^,;<]+),([^<]+)</Publication>");
 
   private static final Pattern PUBLICATION = Pattern.compile("<Publication>([^<]+)</Publication>");

   public PublicationVerifier() {
   }

   public void openWriters(String errPub,String finalPub) throws IOException{
      errPubWriter = new FileWriter(errPub);
      finalPubWriter = new FileWriter(finalPub);
   }

   public void closeWriters() throws IOException{
      errPubWriter.close();
      finalPubWriter.close();
   }

   public void parseXmlResultFile(String filename)throws IOException{
      File newFile = new File(filename);
      if(!newFile.exists()){
         System.out.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(filename));
      String currentLine = "";
      StringBuilder currentPage = new StringBuilder("");
      Matcher m;
      while(readIn.ready()){
         currentLine = readIn.readLine();
         m = SourceUtil.START.matcher(currentLine);
         if(m.find()){//note this assumse correct xml no end tag before start  
            currentPage.append(currentLine);
            currentPage.append('\n');
         }
         else{
            currentPage.append(currentLine);
            currentPage.append('\n');
            m = SourceUtil.END.matcher(currentLine);
            if(m.find()){
               String page = currentPage.toString();
               checkPubs(page);
               currentPage = new StringBuilder("");
               continue;
            }
         }
      }
      readIn.close();
   }

   public void checkPubs(String currentPage) throws IOException{
      String pubPlace = "";
      String pubYear ="";
      String publisher = "";
      String idNum = "";
      Matcher starter = SourceUtil.START.matcher(currentPage);
      if(starter.find())
         idNum = starter.group(1);
     Matcher pub = PUBLICATION.matcher(currentPage);
      if(pub.find()){
         if(Util.unencodeXML(pub.group(1)).contains("???")){
         errPubWriter.write(idNum + '|' +Util.unencodeXML(pub.group(1)) + '\n');
         return;
         }
      }

     pub = SIMPPUBLICATION.matcher(currentPage);
      //that last letter appended so that it is clear what theat line is
      if(pub.find()){
         pubPlace = Util.unencodeXML(pub.group(1));
         pubYear = Util.unencodeXML(pub.group(3));
         publisher = Util.unencodeXML(pub.group(2));
         finalPubWriter.write(idNum + "|publisher|" + publisher + '\n');
         finalPubWriter.write(idNum + "|place_issued|"+ pubPlace + '\n');
         finalPubWriter.write(idNum + "|date_issued|"+ pubYear + '\n');
         return;
      }
      pub = NOYEAR.matcher(currentPage);
      if(pub.find()){
         pubPlace = Util.unencodeXML(pub.group(1));
         publisher = Util.unencodeXML(pub.group(2));
         finalPubWriter.write(idNum + "|publisher|" + publisher + '\n');
          finalPubWriter.write(idNum + "|place_issued|"+ pubPlace + '\n');
         return;
      }
      pub = DOUBLE.matcher(currentPage);
      if(pub.find()){
         pubPlace = Util.unencodeXML(pub.group(1)) + Util.unencodeXML(pub.group(3));
         pubYear = Util.unencodeXML(pub.group(5));
         publisher = Util.unencodeXML(pub.group(2)) + Util.unencodeXML(pub.group(4));
         finalPubWriter.write(idNum + "|publisher|" + publisher + '\n');
         finalPubWriter.write(idNum + "|place_issued|"+ pubPlace + '\n');
         finalPubWriter.write(idNum + "|date_issued|"+ pubYear + '\n');
         return;
      }
      pub = JUSTYEAR.matcher(currentPage);
     if(pub.find()){
         pubYear = Util.unencodeXML(pub.group(1));
         finalPubWriter.write(idNum + "|date_issued|" + pubYear + '\n');
         return;
      }
     pub = PUBANDYEAR.matcher(currentPage);
      if(pub.find()){
         pubYear = Util.unencodeXML(pub.group(2));
         publisher = Util.unencodeXML(pub.group(1));
         finalPubWriter.write(idNum + "|publisher|" + publisher + '\n');
         finalPubWriter.write(idNum + "|place_issued|"  + pubPlace + '\n');
         return;
      }
      pub = PUBLICATION.matcher(currentPage);
      if(pub.find()){
         errPubWriter.write(idNum + '|' + Util.unencodeXML(pub.group(1)) + '\n');
         return;
      }
   }
   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }
  
   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length <= 2) {
            System.out.println("Usage: <input xml file dir> <errPubFile> <updatePubFile>");
      }
      else {
         PublicationVerifier fhlc = new PublicationVerifier();
         fhlc.openWriters(args[1], args[2]);
          for(int i = 0;i<12200;i++){
            fhlc.parseXmlResultFile(args[0]+ pad(i,5) + "[00-99].xml");
         }
      
         fhlc.closeWriters();
      }
   }
}
