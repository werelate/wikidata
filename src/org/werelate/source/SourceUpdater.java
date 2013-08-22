package org.werelate.source;

import org.apache.log4j.Logger;
import org.werelate.editor.PageEditor;
import org.werelate.utils.Util;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.regex.Matcher;

public class SourceUpdater {
   public static final String[] SINGLE_VALUE_FIELDS = {"source_type","source_title","subtitle","publisher","date_issued","place_issued",
                                                       "subject", "ethnicity", "religion", "occupation"};
   public static final String[] MULTI_VALUE_FIELDS = {"place", "author"};

   private static Logger logger = Logger.getLogger("org.werelate.names");
   private static final Pattern AUTHOR_PATTERN = Pattern.compile("<textarea [^>]*?name=\"authors\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern PLACE_PATTERN = Pattern.compile("<textarea [^>]*?name=\"places\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern SURNAME_TEXTBOX = Pattern.compile("<textarea [^>]*?name=\"surnames\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern VALUES_PATTERN = Pattern.compile("([^|]+)\\|([^|]+)\\|([^|]*)");
   private static final Pattern TEXTBOX1_PATTERN = Pattern.compile("<textarea [^>]*?name=\"wpTextbox1\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern SOURCE_TITLE_BOX = Pattern.compile("<input [^>]*?name=\"source_title\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern SUBTITLE = Pattern.compile("<input [^>]*?name=\"subtitle\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern PUBLISHER = Pattern.compile("<input [^>]*?name=\"publisher\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern DATE_ISSUED = Pattern.compile("<input [^>]*?name=\"date_issued\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern PLACE_ISSUED = Pattern.compile("<input [^>]*?name=\"place_issued\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern SERIES_NAME = Pattern.compile("<input [^>]*?name=\"series_name\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern PAGES = Pattern.compile("<input [^>]*?name=\"pages\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern REFERENCES = Pattern.compile("<input [^>]*?name=\"references\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern FROM_YEAR = Pattern.compile("<input [^>]*?name=\"fromYear\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern TO_YEAR = Pattern.compile("<input [^>]*?name=\"toYear\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern TYPE_PATTERN = Pattern.compile("<select [^>]*?name=\"source_type\"(.*?)<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern SUBJECT_PATTERN = Pattern.compile("<select [^>]*?name=\"subject\"(.*?)<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern ETHNICITY_PATTERN = Pattern.compile("<select [^>]*?name=\"ethnicity\"(.*?)<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern RELIGION_PATTERN = Pattern.compile("<select [^>]*?name=\"religion\"(.*?)<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern OCCUPATION_PATTERN = Pattern.compile("<select [^>]*?name=\"occupation\"(.*?)<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);

   private Hashtable<String, Source> updateSources;
   private Hashtable<String, Pattern> regexTable;
   private HashSet<String> manuallyUpdated;
   private HashSet<String> idsToProcess;

   private BufferedReader readIn;

   public SourceUpdater() {
      regexTable = new Hashtable<String, Pattern>();
      manuallyUpdated = new HashSet<String>();
      updateSources = new Hashtable<String, Source>();
      idsToProcess = new HashSet<String>();

      regexTable.put("source_type",TYPE_PATTERN);
      regexTable.put("source_title", SOURCE_TITLE_BOX);
      regexTable.put("subtitle",SUBTITLE);
      regexTable.put("publisher",PUBLISHER);
      regexTable.put("date_issued",DATE_ISSUED);
      regexTable.put("place_issued",PLACE_ISSUED);
      regexTable.put("subject",SUBJECT_PATTERN);
      regexTable.put("ethnicity",ETHNICITY_PATTERN);
      regexTable.put("religion",RELIGION_PATTERN);
      regexTable.put("occupation",OCCUPATION_PATTERN);
      regexTable.put("place",PLACE_PATTERN);
      regexTable.put("author",AUTHOR_PATTERN);

  }

   public void loadIdsToProcess(String filename) throws IOException{
      File titles = new File(filename);
      if(!titles.exists()){
         System.out.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(titles));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         String num = currentLine.substring(0,currentLine.indexOf('|'));
         idsToProcess.add(num);
      }
      readIn.close();
   }

   public void loadManuallyUpdated(String filename) throws IOException{
      File titles = new File(filename);
      if(!titles.exists()){
         System.out.println(filename + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(titles));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         manuallyUpdated.add(currentLine);
      }
      readIn.close();
   }

   public void loadValues(String filename) throws IOException{
      File placeFile = new File(filename);
      if(!placeFile.exists()){
         System.out.println(filename + " does not exist");
         return;
      }
      readIn = new BufferedReader(new FileReader(placeFile));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = VALUES_PATTERN.matcher(currentLine);
         if(!m.find()){
            throw new RuntimeException("Filename: " + filename + " has incorrectly formatted data: " + currentLine);
         }
         String idNum = m.group(1);
         if (idsToProcess.contains(idNum)) {
            String fieldName = m.group(2);
            String fieldValue = m.group(3).trim();
            if (fieldValue.indexOf('�') >= 0) {
               fieldValue = fieldValue.replace('�', '?');
            }
            if (fieldName.equals("place_issued") || fieldName.equals("date_issued")) {
               fieldValue = fieldValue.trim();
               if (fieldValue.startsWith("[")) {
                  fieldValue = fieldValue.substring(1).trim();
               }
               if (fieldValue.endsWith("]") && fieldValue.indexOf("[") < 0) {
                  fieldValue = fieldValue.substring(0, fieldValue.length()-1).trim();
               }
               if (fieldValue.equalsIgnoreCase("S.l.")) {
                  fieldValue = "";
               }
            }
            else if (fieldName.equals("publisher")) {
               fieldValue = fieldValue.replaceAll("Gefilmd door de|Filmed by the|Film\\? par la|Filmet for the|Filmados por la|Film\\?s par la|Filmed by|Gefilmt durch The","").trim();
            }
            if (fieldValue.length() == 0) {
               if (!fieldName.equals("place_issued")) {
                  throw new RuntimeException("Filename: " + filename + " has missing value: " + currentLine);
               }
               continue;
            }

            Source newSource;
            if(updateSources.containsKey(idNum)){
               newSource = updateSources.get(idNum);
            }
            else{
               newSource = new Source();
            }
            newSource.addValue(fieldName, fieldValue);
            updateSources.put(idNum, newSource);
         }
      }
      readIn.close();
   }

   public void editPages(String host, String password, String wikiSources)throws IOException{
      PageEditor edit = new PageEditor(host,password);
      BufferedReader wikiSource = new BufferedReader(new FileReader(wikiSources));

      while(wikiSource.ready()){
         String sourceLine = wikiSource.readLine();
         String num = sourceLine.substring(0,sourceLine.indexOf('|'));
         String title = sourceLine.substring(sourceLine.indexOf('|')+1);
         Source currentSource = updateSources.get(num);
         if (currentSource == null) {
            throw new RuntimeException("Source not found: "+title);
         }
         edit.doGet("Source:"+title,true);
         if(edit.readVariable(TEXTBOX1_PATTERN).equals("")) {
            throw new RuntimeException("Source empty: " + title);
         }
         else {
            boolean manuallyDone = manuallyUpdated.contains(title);
            Hashtable<String,String> currentSourceValues = currentSource.getSingleValues();
            for (String name : SINGLE_VALUE_FIELDS) {
               String oldValue = edit.readVariable(regexTable.get(name), false);
               String newValue = currentSourceValues.get(name);
               if (name.equals("source_type") && newValue == null) {
                  newValue = "Miscellaneous";
               }
               if(newValue != null && (!manuallyDone || Util.isEmpty(oldValue))) {
                      edit.setPostVariable(name,newValue);
               }
               else if (oldValue != null) {
                     edit.setPostVariable(name,oldValue);
               }
            }

            Hashtable<String,List<String>> currentLists = currentSource.getMultiValues();
            for (String name : MULTI_VALUE_FIELDS) {
               String oldValue = edit.readVariable(regexTable.get(name)).trim();
               StringBuilder sb = new StringBuilder();
               if(manuallyDone)
                  sb.append(oldValue + '\n');
               List<String> newValues = currentLists.get(name);
               if (newValues != null) {
                  for (String currValue : newValues) {
                     if(!sb.toString().contains(currValue))//this is so no duplicates are added
                        sb.append(currValue + '\n');
                  }
               }
               edit.setPostVariable(name+"s",sb.toString());
            }

            edit.setPostVariable("fromYear", edit.readVariable(FROM_YEAR));
            edit.setPostVariable("toYear", edit.readVariable(TO_YEAR));
            edit.setPostVariable("series_name", edit.readVariable(SERIES_NAME));
            edit.setPostVariable("surnames",edit.readVariable(SURNAME_TEXTBOX).trim());
            edit.setPostVariable("pages", edit.readVariable(PAGES));
            edit.setPostVariable("references", edit.readVariable(REFERENCES));

            String textbox = edit.readVariable(TEXTBOX1_PATTERN).trim();

            int number = 0;
            while(edit.readVariable(Pattern.compile("<input [^>]*?name=\"repository_id"+number+"\"[^>]*?value=\"(.*?)\"[^/]*/>"), false) != null) {
               edit.setPostVariable("repository_id" + number,String.valueOf(number + 1));
               String repoTitle = edit.readVariable(Pattern.compile("<input [^>]*?name=\"repository_title"+ number+ "\"[^>]*?value=\"(.*?)\"[^/]*/>"));
               edit.setPostVariable("repository_location"+number, edit.readVariable(Pattern.compile("<input [^>]*?name=\"repository_location"+ number+ "\"[^>]*?value=\"(.*?)\"[^/]*/>")));
               String avail = edit.readVariable(Pattern.compile("<select [^>]*?name=\"availability"+ number+ "\".*?<option value=\"([^\"]*)\" selected>", Pattern.DOTALL));
               if (avail.equals("")) {
                  if (repoTitle.contains("Ancestry.com")) {
                     avail = "Paid website";
                  }
                  else {
                     avail = "Free website";
                  }
               }
               else if (avail.equals("Family history center") && textbox.indexOf("Available at the [[Source:Family History Library") >= 0) {
                  repoTitle = "Family History Library";
                  avail = "Other";
               }
               edit.setPostVariable("repository_title"+number, repoTitle);
               edit.setPostVariable("availability"+number, avail);
               number++;
            }

//            textbox = textbox.replaceAll("\\[\\[Category:((Cemeteries)|(Finding aids)|(Church records)|(Ethnic and Cultural)|(Military)|(Cemetery records)|(Census records)|(Family bibles)|(Family histories)|(Funeral homes)|(General history)|(Historic newspapers)|(Historical societies)|(Land records)|(Legal records)|(Libraries and archives)|(Maps and gazetteers)|(Migration records)|(Obituaries)|(Occupations)|(Other records)|(Periodicals)|(Town and area histories)|(Vital records))\\]\\]","");
            textbox = textbox.replaceAll("\\[\\[Category:((Cemeteries)|(Cemetery records)|(Census records)|(Church records)|(Churches)|(Directories)|(Ethnic and Cultural)|(Ethnic and cultural)|" +
                    "(Family bibles)|(Family histories)|(Finding aids)|(Funeral homes)|(General history)|(Historic newspapers)|(Historic Newspapers)|(Historical societies)|" +
                    "(Land records)|(Legal records)|(Libraries and archives)|(Manuscripts)|(Maps and gazetteers)|(Migration records)|(Military)|(Obituaries)|" +
                    "(Occupations)|(Other records)|(Periodicals)|(Town and area histories)|(Vital records))\\]\\]","");

            edit.setPostVariable("wpTextbox1", textbox);
            edit.setPostVariable("wpSummary","automated edit to update source information");
            edit.doPost();
            System.out.println(title);
         }
      }
   }

   //TODO fix _PATTERN's to handle the case where no option is selected
   public static void main(String[] args) throws IOException
   {
      if (args.length < 5) {
         System.out.println("Usage: <configFile> <host> <password> <manuallyUpdated> <partialTitleMap>");
      }
      else {
         File configFile = new File(args[0]);
         if(!configFile.exists()){
            System.err.println(args[0] + " does not exist");
            return;
         }
         SourceUpdater su = new SourceUpdater();

         System.out.println("loading ids to process");
         su.loadIdsToProcess(args[4]);

         BufferedReader readIn = new BufferedReader(new FileReader(configFile));
         while(readIn.ready()){
            String filename = readIn.readLine();
            System.out.println("loading " + filename);
            su.loadValues(filename);
         }

         System.out.println("loading manually updated");
         su.loadManuallyUpdated(args[3]);

         System.out.println("starting to edit pages");
         su.editPages(args[1], args[2],args[4]);
      }
   }
}
