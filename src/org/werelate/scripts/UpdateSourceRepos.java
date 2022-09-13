package org.werelate.scripts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.editor.PageEditor;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class UpdateSourceRepos {
   private static Logger logger = LogManager.getLogger("org.werelate.scripts");
   private static final Pattern AUTHOR_PATTERN = Pattern.compile("<textarea [^>]*?name=\"authors\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern PLACE_PATTERN = Pattern.compile("<textarea [^>]*?name=\"places\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern SURNAME_TEXTBOX = Pattern.compile("<textarea [^>]*?name=\"surnames\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern PLACE_TEXTBOX = Pattern.compile("<textarea [^>]*?name=\"places\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
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

   public UpdateSourceRepos(String host, String password) {
      stdPlaces = new HashMap<String,String>();
      editor = new PageEditor(host, password);
   }

   public void updateRepos(String sourceTitle, String[] repoInfos) {
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
      editor.setPostVariable("places",editor.readVariable(PLACE_TEXTBOX).trim());
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
      for (String repoInfo : repoInfos) {
         String[] fields = repoInfo.split("~",3); // title~avail~location
         editor.setPostVariable("repository_id" + number,String.valueOf(number + 1));
         editor.setPostVariable("repository_title"+number, fields[0].trim());
         editor.setPostVariable("repository_location"+number, fields[2].trim());
         String availability = fields[1].trim();
         if (Util.isEmpty(availability)) availability = "Other";
         editor.setPostVariable("availability"+number, availability);
         number++;
      }

      String cmt = ":''This source may refer to multiple editions of the same book. If it is important to you to refer " +
              "to a specific edition, you may create a separate Source page for that edition with the year of the edition in parentheses after the title.''\n\n";
      editor.setPostVariable("wpTextbox1", (cmt + editor.readVariable(TEXTBOX1_PATTERN)).trim());

      editor.setPostVariable("wpSummary","add repository information from merged sources");
      editor.setPostVariable("wpMinoredit", "1");
      editor.doPost();
   }

   // Update sources with additional repositories
   // 0=source_repos.txt 1=host 2=password
   public static void main(String[] args) throws IOException
   {
      UpdateSourceRepos usr = new UpdateSourceRepos(args[1], args[2]);
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while(in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|", 2);
         String sourceTitle = fields[0];
         if (!Util.isEmpty(fields[1])) {
            String[] repos = fields[1].split("\\|");
            usr.updateRepos("Source:"+sourceTitle, repos);
         }
         else {
            logger.warn("Cannot update: "+line);
         }
      }
      in.close();
   }
}
