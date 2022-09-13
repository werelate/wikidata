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

public class AuthorVerifier{
   private static Logger logger = LogManager.getLogger("org.werelate.source");

private FileWriter authorWriter;
   private static final Pattern MAINAUTHOR = Pattern.compile("<Main Author>([^<]+)</Main Author>");
   private static final Pattern AUTHORS = Pattern.compile("<(Authors|Added Author)>([^<]+)</(Authors|Added Author)>");
 
   public AuthorVerifier() {
   }

   public void openWriters(String titleAuth) throws IOException{
      authorWriter = new FileWriter(titleAuth);
   }

   public void closeWriters() throws IOException{
      authorWriter.close();
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
               checkAuthors(page);
               currentPage = new StringBuilder("");
               continue;
            }
         }
      }
      readIn.close();
   }

  public void checkAuthors(String page) throws IOException{
      Matcher idNumMatcher = SourceUtil.START.matcher(page);
      Matcher mainAuthorMatcher = MAINAUTHOR.matcher(page);
      Matcher otherAuthorsMatcher = AUTHORS.matcher(page);
      String idNum = "";
      String author = "";
      if(idNumMatcher.find())
         idNum = idNumMatcher.group(1);
      if(mainAuthorMatcher.find())
         author = Util.unencodeXML(mainAuthorMatcher.group(1));
      if(!idNum.equals("") && !author.equals(""))
         authorWriter.write(idNum + "|author|" + author + "\n");
      while(otherAuthorsMatcher.find()){
         String newAuthor = Util.unencodeXML(otherAuthorsMatcher.group(2));
         if (!newAuthor.equals("(Added Author)")) {
            if (newAuthor.contains("Subject")) System.out.println(newAuthor);
            authorWriter.write(idNum + "|author|" + newAuthor + '\n');
         }
      }
      return;
  }



  public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }

   public static void main(String[] args) throws IOException
   {
      if (args.length <= 1) {
            System.out.println("Usage: <input xml file dir> <author>");
      }
      else {
         AuthorVerifier fhlc = new AuthorVerifier();
         fhlc.openWriters(args[1]);


         for(int i = 0;i<12200;i++){
            fhlc.parseXmlResultFile(args[0]+ pad(i,5) + "[00-99].xml");
         }
      
         fhlc.closeWriters();
      }
   }
}
