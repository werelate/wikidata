package org.werelate.source;

import org.werelate.utils.Util;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetSubjects
{
   private static final Pattern SUBJECT = Pattern.compile("<Subjects>([^<]+)</Subjects>");
   private FileWriter subjectsWriter;

   public void openWriters(String subjectsFile) throws IOException{
      subjectsWriter = new FileWriter(subjectsFile);
   }

   public void closeWriters() throws IOException{
      subjectsWriter.close();
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
               //sourcePages.put(currentIdNum, page);
               getSubjects(page);
               currentPage = new StringBuilder("");
               continue;
            }
         }
      }
      readIn.close();
   }

   public void getSubjects(String currentPage) throws IOException{
      Matcher m = SourceUtil.START.matcher(currentPage);
      if(m.find()){
         String id = m.group(1);
         m = SUBJECT.matcher(currentPage);
         while (m.find()) {
            String subject = Util.unencodeXML(m.group(1));
            subjectsWriter.write(id + "|subject|"+subject+"\n");
         }
      }
   }

   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }


   public static void main(String[] args) throws IOException
   {
      if (args.length < 2) {
         System.out.println("Usage: <input dir> <out subjects>");
      }
      else {
         GetSubjects fhlc = new GetSubjects();
         fhlc.openWriters(args[1]);
         for(int i = 0;i<12200;i++){
               fhlc.parseXmlResultFile(args[0]+ pad(i,5) + "[00-99].xml");
         }
         fhlc.closeWriters();
      }
   }
}
