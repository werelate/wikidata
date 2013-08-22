package org.werelate.source;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.editor.PageEditor;

import java.lang.Integer;
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

public class CategoryVerifier extends StructuredDataParser{
   private static Logger logger = Logger.getLogger("org.werelate.source");

   private FileWriter errCategoryWriter;
   private FileWriter newCategoryWriter;
   private FileWriter newTypeWriter;
   private Hashtable<String,String> categoryMapping;
   private Hashtable<String,String> newCategories;
   private Hashtable<String, Integer> categoriesPrioTable;
   private Hashtable<String, String> subjectsTable;

   public CategoryVerifier() {
      super();
      categoryMapping = new Hashtable<String,String>();
      newCategories = new Hashtable<String,String>();
      categoriesPrioTable = new Hashtable<String, Integer>();
      subjectsTable = new Hashtable<String,String>();
   }

   public void loadSubjects(String filename) throws IOException{
      BufferedReader r = new BufferedReader(new FileReader(filename));
      String line = r.readLine();
      while (line != null) {
         String[] fields = line.split("\\|");
         if (fields.length < 3) {
            System.out.println("ERROR: " + line);
         }
         else {
            String subjects = subjectsTable.get(fields[0]);
            if (subjects == null) {
               subjects = fields[2];
            }
            else {
               subjects += " " + fields[2];
            }
            subjectsTable.put(fields[0],subjects);
         }
         line = r.readLine();
      }
      r.close();
   }

   public void loadCategoryPrio(String filename) throws IOException{
      File categories = new File(filename);
      if(!categories.exists()){
         logger.warn(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(categories));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         int bar = currentLine.indexOf('-');
         if(bar >0){
            categoriesPrioTable.put(currentLine.substring(0,bar), Integer.parseInt(currentLine.substring(bar +1)));
         }
      }
      readIn.close();
   }

   public void loadCategoryMapping(String filename) throws IOException{
      File categories = new File(filename);
      if(!categories.exists()){
         logger.warn(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(categories));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         int bar = currentLine.indexOf('-');
         if(bar >0){
            categoryMapping.put(currentLine.substring(0,bar), currentLine.substring(bar +1));
         }
      }
      readIn.close();
   }

   public void openWriters(String errCategory, String newCategory, String newType) throws IOException{
      errCategoryWriter = new FileWriter(errCategory);
      newCategoryWriter = new FileWriter(newCategory);
      newTypeWriter = new FileWriter(newType);
   }

   public void closeWriters() throws IOException{
      errCategoryWriter.close();
      newCategoryWriter.close();
      newTypeWriter.close();
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if(title.startsWith("Source:")) {
         Matcher m = SourceUtil.TITLENO.matcher(text);
         if(m.find()){
            String idNum = m.group(1);
            String category = establishDominantCategory(text);
            String subjects = subjectsTable.get(idNum);
            String result = useMapping(category, title, text, subjects);
            newCategories.put(idNum, result);
            newTypeWriter.write(idNum + "|source_type|Miscellaneous\n");
         }
      }
   }

   public String useMapping(String category, String title, String text, String subjects) {
      String mapping = "";
      if(category.equals("Historical Societies"))
         category = "Other records";
      if(category.equals("Funeral Homes"))
         category = "Vital records";
      if(category.equals("Libraries and archives"))
         category = "Finding aids";
      if(category.equals("Cemeteries"))
         category = "Cemetery records";
      if(categoryMapping.containsKey(category)){
         mapping = categoryMapping.get(category);
         title = Util.romanize(title).toLowerCase();
         text = Util.romanize(text).toLowerCase();
         subjects = Util.romanize(subjects).toLowerCase();
         int question = mapping.indexOf('?');
         while(question > -1){
            mapping = mapping.substring(question + 1);
            int colon = mapping.indexOf(':');
            if(colon > -1){
               String[] keywords = mapping.substring(0,colon).toLowerCase().split("\\|");
               String result = mapping.substring(colon +1, mapping.indexOf('?'));
               for (String keyword : keywords) {
                  if (title.contains(keyword) || subjects.contains(keyword) || text.contains(keyword)) {
                     return result;
                  }
               }
            }
            question = mapping.indexOf('?');
         }
      }
      else {
         System.out.println("Category not found: " + category);
      }
      return mapping;
   }

   public String establishDominantCategory(String text){
      Matcher m = SourceUtil.CATEGORY.matcher(text);
      String tempCat = "";
      String result = "blank";
      int num = 0;
      while(m.find()){
         tempCat = m.group(1).trim();
         Integer priority = categoriesPrioTable.get(tempCat);
         if (priority == null) {
            if (tempCat.indexOf("census") >= 0 || tempCat.indexOf("Census") >= 0) {
               tempCat = "Census records";
               priority = categoriesPrioTable.get(tempCat);
               if (priority == null) {
                  System.out.println("Error: Census records not found");
               }
            }
            else {
               System.out.println("Category not found="+tempCat);
            }
         }
         if (priority != null && num < priority.intValue()) {
            result = tempCat;
            num = priority.intValue();
         }      
      }
      return result;
   }



   public void writeCategories() throws IOException{
      Iterator<String> myIter = newCategories.keySet().iterator();
      while(myIter.hasNext()){
         String num = myIter.next();
         newCategoryWriter.write(num + "|subject|" + newCategories.get(num) + '\n');
      }
   }

//   public void parseXmlResultFile(String filename)throws IOException{
//      File newFile = new File(filename);
//      if(!newFile.exists()){
//         System.out.println(filename + " does not exist");
//         return;
//      }
//      BufferedReader readIn = new BufferedReader(new FileReader(filename));
//      String currentLine = "";
//      StringBuilder currentPage = new StringBuilder("");
//      Matcher m;
//      while(readIn.ready()){
//         currentLine = readIn.readLine();
//         m = SourceUtil.START.matcher(currentLine);
//         if(m.find()){//note this assumse correct xml no end tag before start
//            currentPage.append(currentLine);
//            currentPage.append('\n');
//         }
//         else{
//            currentPage.append(currentLine);
//            currentPage.append('\n');
//            m = SourceUtil.END.matcher(currentLine);
//            if(m.find()){
//               String page = currentPage.toString();
//               //sourcePages.put(currentIdNum, page);
//               checkType(page);
//               currentPage = new StringBuilder("");
//               continue;
//            }
//         }
//      }
//      readIn.close();
//   }

//   public void checkType(String currentPage) throws IOException{
//      Matcher m = SourceUtil.START.matcher(currentPage);
//      if(m.find()){
//         String id = m.group(1);
//
//         //      String categ = newCategories.get(id);
//         currentPage = currentPage.toLowerCase();
//         if(currentPage.contains("newspaper")){
//            newTypeWriter.write(id + "|source_type|Newspaper\n");
//
//            return;
//         }
//         if(currentPage.contains("manuscript")){
//            newTypeWriter.write(id + "|source_type|Manuscript\n");
//            return;
//         }
//         if(currentPage.contains("periodical")){
//            newTypeWriter.write(id + "|source_type|Periodical\n");
//            return;
//         }
//         if(currentPage.contains("government")){
//            newTypeWriter.write(id + "|source_type|Government / Church records\n");
//            return;
//         }
//         if(currentPage.contains("church")){
//            newTypeWriter.write(id + "|source_type|Government / Church records\n");
//            return;
//         }
//         if(currentPage.contains("book")){
//            newTypeWriter.write(id + "|source_type|Book\n");
//            return;
//         }
//         if(currentPage.contains("article")){
//            newTypeWriter.write(id + "|source_type|Article\n");
//            return;
//         }
//         newTypeWriter.write(id + "|source_type|Miscellaneous\n");
//         return;
//      }
//   }

   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }


   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length < 7) {
         System.out.println("Usage: <pages.xml> <errCat> <newCategory> <categoryMapping> <newType> <priorityTable> <subjects>");
      }
      else {
         CategoryVerifier fhlc = new CategoryVerifier();
         fhlc.loadCategoryMapping(args[3]);
         fhlc.loadCategoryPrio(args[5]);
         fhlc.loadSubjects(args[6]);
         fhlc.openWriters(args[1], args[2], args[4]);
//         for(int i = 0;i<12199;i++){
//               fhlc.parseXmlResultFile(args[5]+ pad(i,5) + "[00-99].xml");
//         }
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(fhlc);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         fhlc.writeCategories(); 
         fhlc.closeWriters();
      }
   }
}
