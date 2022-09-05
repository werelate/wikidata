package org.werelate.names;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.utils.Util;
import org.werelate.editor.PageEditor;

import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Scanner;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * Add common names to wiki
 */
public class NamePageCreator {
   private static Logger logger = LogManager.getLogger("org.werelate.names");
   private static final Pattern RELATEDBOX_PATTERN = Pattern.compile("<textarea[^>]*?related[^>]*?>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern TEXTBOX1_PATTERN = Pattern.compile("<input[^>]*?wpTextbox1[^>]*?value=(.*?)/>", Pattern.DOTALL);

   private BufferedReader readIn;

   public NamePageCreator() {
   }

   public void openFile(String file) throws IOException{
      readIn = new BufferedReader(new FileReader(file));
   }

   public void close() throws IOException {
      readIn.close();
   }

   // obviously this function does not work
   public void editPages()throws IOException{
      PageEditor edit = new PageEditor("www.werelate.org","password");
      Scanner scan;
      while(readIn.ready()){
         StringBuilder sb = new StringBuilder();
         String NewPage = readIn.readLine();
         scan = new Scanner(NewPage.substring(NewPage.indexOf('=')+1));
         System.out.println(NewPage.substring(NewPage.indexOf('=')+1));
         scan.useDelimiter("\\|");
         while(scan.hasNext()){
            sb.append(scan.next());
            sb.append("|WeRelate - similar spelling\n");
         }
         edit.doGet(NewPage.substring(0,NewPage.indexOf('=')),true);
         logger.info("done" + NewPage);
         edit.setPostVariable("related",sb.toString());
         edit.setPostVariable("wpSummary","add common names");
         edit.setPostVariable("wpTextbox1","");
         edit.doPost(); //TODO does this still work?
      }
   }

   public static void main(String[] args) throws IOException
   {
      if (args.length < 1) {
         System.out.println("Usage: <File to process>");
      }
      else {
         NamePageCreator surnames = new NamePageCreator();
         surnames.openFile(args[0]); 
         surnames.editPages();
         surnames.close();
      }
   }
}
