package org.werelate.places;

import org.apache.log4j.Logger;
import org.werelate.editor.PageEditor;
import org.werelate.utils.Util;

import java.util.HashSet;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

public class PlaceEditor {
   private static Logger logger = Logger.getLogger("org.werelate.names");
   private static final Pattern ALTERNATE_NAMES_BOX = Pattern.compile("<textarea[^>]*?name=\"alternateNames\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern SEE_ALSO_BOX = Pattern.compile("<textarea[^>]*?name=\"seeAlso\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern ALSO_LOCATED_IN_BOX = Pattern.compile("<textarea[^>]*?name=\"alsoLocatedIn\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern TYPE_BOX = Pattern.compile("<input[^>]*?name=\"type\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern LATITUDE_BOX = Pattern.compile("<input[^>]*?name=\"latitude\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern LONGITUDE_BOX = Pattern.compile("<input[^>]*?name=\"longitude\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern FROMYEAR_BOX = Pattern.compile("<input[^>]*?name=\"fromYear\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);
   private static final Pattern TOYEAR_BOX = Pattern.compile("<input[^>]*?name=\"toYear\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);



   private BufferedReader readIn;

   public PlaceEditor() {
   }

   public void openFile(String file) throws IOException{
      readIn = new BufferedReader(new FileReader(file));
   }

   public void close() throws IOException {
      readIn.close();
   }

   public void editPages(String host, String password)throws IOException{
      PageEditor edit = new PageEditor(host,password);
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         String title = currentLine.substring(0,currentLine.indexOf('|'));//both of these need some error checking
         String num = currentLine.substring(currentLine.indexOf('|') +1);
         edit.doGet("Place:" + title,true);//will be true later now just for testing
         String text = edit.readVariable(PageEditor.TEXTBOX1_PATTERN);
         if (Util.isEmpty(text)) {
            logger.warn("PLACE NOT FOUND: " + title);
         }
         else if(text.contains("{{source-fhlc|" + num + "}}") || text.contains("{{Source-fhlc|" + num + "}}")) {
            logger.warn("FHLC_ID EXISTS: " + title);
         }
         else {
            text = "{{source-fhlc|" +num +"}}" + text;
            System.out.println("title="+title);
            edit.setPostVariable("alternateNames", edit.readVariable(ALTERNATE_NAMES_BOX));
            edit.setPostVariable("seeAlso", edit.readVariable(SEE_ALSO_BOX));
            edit.setPostVariable("alsoLocatedIn", edit.readVariable(ALSO_LOCATED_IN_BOX));
            edit.setPostVariable("toYear", edit.readVariable(TOYEAR_BOX));
            edit.setPostVariable("fromYear", edit.readVariable(FROMYEAR_BOX));
            edit.setPostVariable("longitude", edit.readVariable(LONGITUDE_BOX));
            edit.setPostVariable("latitude", edit.readVariable(LATITUDE_BOX));
            edit.setPostVariable("type",edit.readVariable(TYPE_BOX));
            edit.setPostVariable("wpSummary","automated edit to add fhlc link");
            edit.setPostVariable("wpTextbox1", text);
            edit.doPost();
         }
      }
   }

   public static void main(String[] args) throws IOException
   {
      if (args.length <= 2) {
         System.out.println("Usage: <placeFile to process> <host> <password>");
      }
      else {
         PlaceEditor places = new PlaceEditor();
         places.openFile(args[0]);
         places.editPages(args[1], args[2]);
         places.close();
      }
   }
}
