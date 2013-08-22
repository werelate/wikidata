package org.werelate.source;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.editor.PageEditor;

import java.util.Set;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class TitleVerifier {
   private static Logger logger = Logger.getLogger("org.werelate.source");

   private FileWriter titleLengthWriter;
   private FileWriter finalTitleWriter;
   private Hashtable<String,String> fhlcPlaceToNum;
//   private Hashtable<String,Set<String>> wikiSourceToPlace;
//   private Hashtable<String,String> redirects;

   private static final int MAX_LEN = 160;
   private static final Pattern TITLE = Pattern.compile("<Title>([^<]+)</Title>");

   public TitleVerifier() {
      fhlcPlaceToNum = new Hashtable<String,String>();
//      wikiSourceToPlace = new Hashtable<String,Set<String>>();
//      redirects = new Hashtable<String,String>();
   }

   public void openWriters(String titleLen, String output) throws IOException{
      titleLengthWriter = new FileWriter(titleLen);
      finalTitleWriter = new FileWriter(output);
   }

   public void closeWriters() throws IOException{
      titleLengthWriter.close();
      finalTitleWriter.close();
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
               checkTitles(page);
               currentPage = new StringBuilder("");
               continue;
            }
         }
      }
      readIn.close();
   }

   public void checkTitles(String currentPage) throws IOException{
      String idNum = "";
      String finalTitle = "";
      String subtitle = "";
      Matcher m = SourceUtil.START.matcher(currentPage);
      if(m.find()){
         idNum = m.group(1);
      }
      else {
         System.out.println("ERROR");
         return;
      }
      m = TITLE.matcher(currentPage);
      if(m.find()){
         String title = Util.unencodeXML(m.group(1));
         //System.out.println(idNum + '|' + title);
         int colon = title.indexOf(':');
         int semicolon = title.indexOf(';');
         colon = (semicolon > 0 && (colon < 0 || semicolon < colon)) ? semicolon : colon;
         if(title.length() > 80){
            if (colon <= 0) {
               finalTitle = title;
            }
            else{
               subtitle = title.substring(colon + 1);
               finalTitle = title.substring(0,colon);
            }
         }
         else{
            finalTitle = title;
         }
         if(finalTitle.length() > MAX_LEN){
            titleLengthWriter.write(idNum+"|"+finalTitle+"\n");
         }

         finalTitleWriter.write(idNum +"|source_title|"+ finalTitle + '\n');
         if(!subtitle.equals("")) {
            finalTitleWriter.write(idNum + "|subtitle|"+ subtitle + '\n');
         }
      }
   }

   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }

   public static void main(String[] args) throws IOException
   {
      if (args.length < 3) {
            System.out.println("Usage: <input xml file dir> <titleTooLong> <outputTitles>");
      }
      else {
         TitleVerifier fhlc = new TitleVerifier();
         fhlc.openWriters(args[1], args[2]);

         for(int i = 0;i<12200;i++){
            fhlc.parseXmlResultFile(args[0]+ pad(i,5) + "[00-99].xml");
         }
      
         fhlc.closeWriters();
      }
   }
}
