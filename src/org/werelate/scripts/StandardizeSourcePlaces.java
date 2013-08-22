package org.werelate.scripts;

import org.apache.log4j.Logger;
import org.werelate.editor.PageEditor;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;

public class StandardizeSourcePlaces {
   private static Logger logger = Logger.getLogger("org.werelate.names");
   private static final Pattern AUTHOR_PATTERN = Pattern.compile("<textarea [^>]*?name=\"authors\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern PLACE_PATTERN = Pattern.compile("<textarea [^>]*?name=\"places\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern SURNAME_TEXTBOX = Pattern.compile("<textarea [^>]*?name=\"surnames\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
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
   private static final Pattern TYPE_PATTERN = Pattern.compile("<select [^>]*?name=\"source_type\".*?<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern SUBJECT_PATTERN = Pattern.compile("<select [^>]*?name=\"subject\".*?<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern ETHNICITY_PATTERN = Pattern.compile("<select [^>]*?name=\"ethnicity\".*?<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern RELIGION_PATTERN = Pattern.compile("<select [^>]*?name=\"religion\".*?<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);
   private static final Pattern OCCUPATION_PATTERN = Pattern.compile("<select [^>]*?name=\"occupation\".*?<option value=\"([^\"]*)\" selected>",Pattern.DOTALL);

   private Map<String,String> stdPlaces;
   private PageEditor editor;

   public StandardizeSourcePlaces(String host, String password) {
      stdPlaces = new HashMap<String,String>();
      editor = new PageEditor(host, password);
   }

   public void loadStdPlaces(String filename) throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         stdPlaces.put(fields[0], fields[1]);
      }
      in.close();
   }

   public void standardizePlaces(String sourceTitle) {
      editor.doGet(sourceTitle,true);
      editor.setPostVariable("source_type", editor.readSelectVariable(TYPE_PATTERN));
      editor.setPostVariable("authors", editor.readVariable(AUTHOR_PATTERN));
      editor.setPostVariable("source_title", editor.readVariable(SOURCE_TITLE_BOX));
      editor.setPostVariable("subtitle", editor.readVariable(SUBTITLE));
      editor.setPostVariable("publisher", editor.readVariable(PUBLISHER));
      editor.setPostVariable("date_issued", editor.readVariable(DATE_ISSUED));
      editor.setPostVariable("place_issued", editor.readVariable(PLACE_ISSUED));
      editor.setPostVariable("series_name", editor.readVariable(SERIES_NAME));
      editor.setPostVariable("pages", editor.readVariable(PAGES));
      editor.setPostVariable("references", editor.readVariable(REFERENCES));
      editor.setPostVariable("surnames",editor.readVariable(SURNAME_TEXTBOX).trim());
      editor.setPostVariable("fromYear", editor.readVariable(FROM_YEAR));
      editor.setPostVariable("toYear", editor.readVariable(TO_YEAR));
      editor.setPostVariable("subject", editor.readSelectVariable(SUBJECT_PATTERN));
      editor.setPostVariable("ethnicity", editor.readSelectVariable(ETHNICITY_PATTERN));
      editor.setPostVariable("religion", editor.readSelectVariable(RELIGION_PATTERN));
      editor.setPostVariable("occupation", editor.readSelectVariable(OCCUPATION_PATTERN));
      int number = 0;
      while(editor.readVariable(Pattern.compile("<input [^>]*?name=\"repository_id"+number+"\"[^>]*?value=\"(.*?)\"[^/]*/>"), false) != null) {
         editor.setPostVariable("repository_id" + number,String.valueOf(number + 1));
         editor.setPostVariable("repository_title"+number, editor.readVariable(Pattern.compile("<input [^>]*?name=\"repository_title"+ number+ "\"[^>]*?value=\"(.*?)\"[^/]*/>")));
         editor.setPostVariable("repository_location"+number, editor.readVariable(Pattern.compile("<input [^>]*?name=\"repository_location"+ number+ "\"[^>]*?value=\"(.*?)\"[^/]*/>")));
         String availability = editor.readVariable(Pattern.compile("<select [^>]*?name=\"availability"+ number+ "\".*?<option value=\"([^\"]*)\" selected>", Pattern.DOTALL));
         if (Util.isEmpty(availability)) availability = "Other";
         editor.setPostVariable("availability"+number, availability);
         number++;
      }
      editor.setPostVariable("wpTextbox1", editor.readVariable(TEXTBOX1_PATTERN).trim());

      // standardize places
      String placesText = editor.readVariable(PLACE_PATTERN);
      String[] places = placesText.split("\n");
      List<String> result = new ArrayList<String>();
      for (String place : places) {
         String[] fields = place.split("\\|");
//         String userPlace = (fields.length == 2 ? fields[1] : fields[0]);
         String userPlace = fields[0];
         String stdPlace = stdPlaces.get(userPlace);
         if (Util.isEmpty(stdPlace)) {
            if (!result.contains(place)) result.add(place);
         }
         else {
//            if (!result.contains(stdPlace)) result.add(stdPlace);
            if (fields.length == 2 && !stdPlace.equals(fields[1])) {
               place = stdPlace+"|"+fields[1];
            }
            else {
               place = stdPlace;
            }
            place = (fields.length == 2 && !fields[1].equals(stdPlace) ? stdPlace+"|"+fields[1] : stdPlace);
            if (!result.contains(place)) result.add(place);
         }
      }
      editor.setPostVariable("places", Util.join("\n", result));

      editor.setPostVariable("wpSummary","standardize places");
      editor.setPostVariable("wpMinoredit", "1");
      editor.doPost();
   }

   // Update sources with new places
   // 0=titles to update 1=std_places 2=host 3=password
   public static void main(String[] args) throws IOException
   {
      StandardizeSourcePlaces ssp = new StandardizeSourcePlaces(args[2], args[3]);
      ssp.loadStdPlaces(args[1]);

      int cnt = 0;
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while(in.ready()){
         String line = in.readLine();
         ssp.standardizePlaces(line);
         if (++cnt % 100 == 0) {
            logger.info(cnt+" : "+line);
         }
      }
      in.close();
      System.out.println("Updated "+cnt+" pages");
   }
}
