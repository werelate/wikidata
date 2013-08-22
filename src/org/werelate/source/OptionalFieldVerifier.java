package org.werelate.source;

import org.werelate.utils.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.Hashtable;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class OptionalFieldVerifier{
   private static Pattern MAPPING = Pattern.compile("'([^']+)'\\s*=>\\s*'([^']+)'");
   private static Pattern CATEGORY = Pattern.compile("([0-9]+)\\|subject\\|(.*)");

   private FileWriter optionalWriter;
   private Hashtable<String, String> categories;
   private List<String[]> ethnicMapping;
   private List<String[]> occupationMapping;
   private List<String[]> religionMapping;
   public OptionalFieldVerifier(){
      categories = new Hashtable<String, String>();
      ethnicMapping = new ArrayList<String[]>();
      occupationMapping = new ArrayList<String[]>();
      religionMapping = new ArrayList<String[]>();
   }

   public void openWriter(String filename)throws IOException{
      optionalWriter = new FileWriter(filename);
   }

   public void closeWriter() throws IOException{
      optionalWriter.close();
   }

   public void loadEthnic(String filename) throws IOException {
      File ethnic = new File(filename);
      if(!ethnic.exists()){
         System.err.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(ethnic));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = MAPPING.matcher(currentLine);
         if(m.find()) {
            String[] map = new String[2];
            map[0] = m.group(1).toLowerCase();
            map[1] = m.group(2);
            ethnicMapping.add(map);
         }
      }
   }

   public void loadOccupation(String filename) throws IOException {
      File occu = new File(filename);
      if(!occu.exists()){
         System.err.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(occu));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = MAPPING.matcher(currentLine);
         if(m.find()) {
            String[] map = new String[2];
            map[0] = m.group(1).toLowerCase();
            map[1] = m.group(2);
            occupationMapping.add(map);
         }
      }
   }

   public void loadReligion(String filename) throws IOException {
      File religion = new File(filename);
      if(!religion.exists()){
         System.err.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(religion));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = MAPPING.matcher(currentLine);
         if(m.find()) {
            String[] map = new String[2];
            map[0] = m.group(1).toLowerCase();
            map[1] = m.group(2);
            religionMapping.add(map);
         }
      }
   }

   public void loadCategories(String filename) throws IOException{
      File categ = new File(filename);
      if(!categ.exists()){
         System.err.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(categ));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = CATEGORY.matcher(currentLine);
         if(m.find()){
            categories.put(m.group(1), m.group(2));
            }
      }
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
      StringBuilder currentAuthors = new StringBuilder();
      Matcher m;
      boolean inSource = false;
      while(readIn.ready()){
         currentLine = readIn.readLine();
         m = SourceUtil.START.matcher(currentLine);
         if(m.find()){//note this assumse correct xml no end tag before start  
            inSource = true;
            currentPage.setLength(0);
            currentPage.append(currentLine);
            currentPage.append('\n');
            currentAuthors.setLength(0);
         }
         else if (inSource) {
            if(currentLine.indexOf("<Title>") >= 0 ||
               currentLine.indexOf("<Notes>") >= 0 ||
               currentLine.indexOf("<Subjects>") >= 0) {
                  currentPage.append(currentLine);
                  currentPage.append('\n');
            }
            else if(currentLine.indexOf("<Main Author>") >= 0 ||
               currentLine.indexOf("<Authors>") >= 0 ||
               currentLine.indexOf("<Added Author") >= 0) {
                  currentAuthors.append(currentLine);
                  currentAuthors.append('\n');
            }
            m = SourceUtil.END.matcher(currentLine);
            if(m.find()){
               inSource = false;
               String page = currentPage.toString();
               String authors = currentAuthors.toString();
               checkCategory(page, authors);
               currentPage.setLength(0);
               currentAuthors.setLength(0);
               continue;
            }
         }
      }
      readIn.close();
   }


   //the letter after the bar is for easy distiguishing later as they are all going into 
   //the same file
   public void checkCategory(String page, String authors) throws IOException{
      Matcher m = SourceUtil.START.matcher(page);
      if(!m.find()) {
         System.out.println("ERROR: id not found");
         return;
      }
      String idNum = m.group(1);
      String newCategory = categories.get(idNum);
      if(newCategory == null){
         System.out.print(",");
         //most of these are chinese or japanese
         return;
      }
      page = Util.romanize(page).toLowerCase();
      authors = Util.romanize(authors).toLowerCase();
      if (newCategory.equals("Church records")){
         boolean found = false;
         for (String[] religion : religionMapping) {
            if(page.contains(religion[0]) || (!religion[1].equals("Church of Jesus Christ of Latter-day Saints") && authors.contains(religion[0]))) {
               optionalWriter.write(idNum + "|religion|" +religion[1] + '\n');
               found = true;
               break;
            }
         }
         if (!found) {
//            System.out.println("Church: " + idNum);
         }
      }
      else if (newCategory.equals("Ethnic/Cultural")){
         boolean found = false;
         for (String[] ethnic : ethnicMapping) {
            if(page.contains(ethnic[0])) {
               optionalWriter.write(idNum + "|ethnicity|" + ethnic[1] + '\n');
               found = true;
               break;
            }
         }
         if (!found) {
            System.out.println("Ethnic: " + idNum);
         }
      }
      else if (newCategory.equals("Occupation")){
         boolean found = false;
         for (String[] occu : occupationMapping) {
            if(page.contains(occu[0])) {
               optionalWriter.write(idNum + "|occupation|" + occu[1] + '\n');
               found = true;
               break;
            }
         }
         if (!found) {
            System.out.println("Occupation: " + idNum);
         }
      }
   }

   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);
   }



   public static void main(String args[]) throws IOException{
      if(args.length < 5){
         System.out.println(" <xmlInputDir> <ethnicMappping> <occupationMapping> <religionMapping> <categoriesUpdate> <optionalUpdates>");
      }
      else{
         OptionalFieldVerifier fhlc = new OptionalFieldVerifier(); 
         fhlc.loadEthnic(args[1]);
         fhlc.loadOccupation(args[2]);
         fhlc.loadReligion(args[3]);
         fhlc.loadCategories(args[4]);
         fhlc.openWriter(args[5]);
         for(int i = 0;i<12200;i++){
            fhlc.parseXmlResultFile(args[0]+ pad(i,5) + "[00-99].xml");
         }  
         fhlc.closeWriter();
      }
   }
}
