package org.werelate.scripts;

import org.werelate.editor.PageEditor;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class UpdateWebSources {
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

   private PageEditor editor;

   public UpdateWebSources(String host, String password) {
      editor = new PageEditor(host, password);
   }

   public void updateSource(String sourceTitle) {
      try {
         editor.doGet(sourceTitle,true);
      }
      catch (RuntimeException e) {
         System.out.println("Error: "+sourceTitle+" => "+ e);
         return;
      }

      String type = editor.readVariable(TYPE_PATTERN);
      String authors = editor.readVariable(AUTHOR_PATTERN);
      String title = editor.readVariable(SOURCE_TITLE_BOX);
      String subtitle = editor.readVariable(SUBTITLE);
      String publisher = editor.readVariable(PUBLISHER);
      String dateIssued = editor.readVariable(DATE_ISSUED);
      String placeIssued = editor.readVariable(PLACE_ISSUED);
      String seriesName = editor.readVariable(SERIES_NAME);
      String pages = editor.readVariable(PAGES);
      String references = editor.readVariable(REFERENCES);
      String surnames = editor.readVariable(SURNAME_TEXTBOX).trim();
      String places = editor.readVariable(PLACE_PATTERN);
      String fromYear = editor.readVariable(FROM_YEAR);
      String toYear = editor.readVariable(TO_YEAR);
      String subject = editor.readVariable(SUBJECT_PATTERN);
      String ethnicity = editor.readVariable(ETHNICITY_PATTERN);
      String religion = editor.readVariable(RELIGION_PATTERN);
      String occupation = editor.readVariable(OCCUPATION_PATTERN);

      // update source type
      type = "Website";

      editor.setPostVariable("source_type", type);
      editor.setPostVariable("authors", authors);
      editor.setPostVariable("source_title", title);
      editor.setPostVariable("subtitle", subtitle);
      editor.setPostVariable("publisher", publisher);
      editor.setPostVariable("date_issued", dateIssued);
      editor.setPostVariable("place_issued", placeIssued);
      editor.setPostVariable("series_name", seriesName);
      editor.setPostVariable("pages", pages);
      editor.setPostVariable("references", references);
      editor.setPostVariable("surnames", surnames);
      editor.setPostVariable("places", places);
      editor.setPostVariable("fromYear", fromYear);
      editor.setPostVariable("toYear", toYear);
      editor.setPostVariable("subject", subject);
      editor.setPostVariable("ethnicity", ethnicity);
      editor.setPostVariable("religion", religion);
      editor.setPostVariable("occupation", occupation);
      int number = 0;
      while(editor.readVariable(Pattern.compile("<input [^>]*?name=\"repository_id"+number+"\"[^>]*?value=\"(.*?)\"[^/]*/>"), false) != null) {
         String repoTitle = editor.readVariable(Pattern.compile("<input [^>]*?name=\"repository_title"+ number+ "\"[^>]*?value=\"(.*?)\"[^/]*/>"));
         String repoLocation = editor.readVariable(Pattern.compile("<input [^>]*?name=\"repository_location"+ number+ "\"[^>]*?value=\"(.*?)\"[^/]*/>"));
         String repoAvail = editor.readVariable(Pattern.compile("<select [^>]*?name=\"availability"+ number+ "\".*?<option value=\"([^\"]*)\" selected>", Pattern.DOTALL));
         if (repoAvail.length() == 0) {
            repoAvail = "Free website";
         }
         editor.setPostVariable("repository_id" + number,String.valueOf(number + 1));
         editor.setPostVariable("repository_title"+number, repoTitle);
         editor.setPostVariable("repository_location"+number, repoLocation);
         editor.setPostVariable("availability"+number, repoAvail);
         number++;
      }
      editor.setPostVariable("wpTextbox1", editor.readVariable(TEXTBOX1_PATTERN).trim());

      editor.setPostVariable("wpSummary","change source type to website");
      editor.setPostVariable("wpMinoredit", "1");
      editor.doPost();
   }

   // 0=titles to update 1=host 2=password
   //TODO fix _PATTERN's to handle the case where no option is selected
   public static void main(String[] args) throws IOException
   {
      UpdateWebSources uws = new UpdateWebSources(args[1], args[2]);

      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while(in.ready()){
         String line = in.readLine();
         uws.updateSource(line);
      }
      in.close();
   }
}
