package org.werelate.names;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.editor.PageEditor;

import java.util.HashSet;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * Remove rare related names from givenname and surname pages
 */
public class NameEditor {
   private static Logger logger = LogManager.getLogger("org.werelate.names");
   private static final Pattern RELATEDBOX_PATTERN = Pattern.compile("<textarea[^>]*?related[^>]*?>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern TEXTBOX1_PATTERN = Pattern.compile("<textarea[^>]*?wpTextbox1[^>]*?>(.*?)</textarea>", Pattern.DOTALL);

   private HashSet<String> commonNames = new HashSet<String>();
   private String currentPagePrefix;
   private int totalRelatedNames;
   private int numNames;
   private int over30Names;
   private BufferedReader readIn;

   public NameEditor() {
      totalRelatedNames = 0;
      numNames = 0;
      over30Names = 0;
   }

   public void openFile(String file) throws IOException{
      readIn = new BufferedReader(new FileReader(file));
   }

   public void close() throws IOException {
      readIn.close();
   }

   public double getAvg(){
      return ((double)totalRelatedNames)/numNames;
   }

   public int Over30Names(){
      return over30Names;
   }

   public void setPrefix(String newPrefix){
      currentPagePrefix = newPrefix;
   }

   public void LoadNames(String filename) throws IOException{
      BufferedReader temp = new BufferedReader(new FileReader(filename));
      while(temp.ready()){
         String newName = temp.readLine();
         commonNames.add(newName);
      }
      temp.close();
   }

   public void editPages(String host, String password)throws IOException{
      PageEditor edit = new PageEditor(host,password);
      Scanner scan;
      while(readIn.ready()){
         int current = totalRelatedNames;
         String title = currentPagePrefix + readIn.readLine();
         numNames++;
         edit.doGet(title,true);//will be true later now just for testing
         if(edit.readVariable(RELATEDBOX_PATTERN) != null)
            scan = new Scanner(edit.readVariable(RELATEDBOX_PATTERN));
         else
            continue;
         scan.useDelimiter("\n+");
         StringBuilder sb = new StringBuilder();
         while(scan.hasNext()){
            String newName = scan.next();
            int barPos = newName.indexOf('|');
            if(barPos != -1){
               String namePiece = newName.substring(0,barPos);
               String sourcePiece = newName.substring(barPos);
               if(commonNames.contains(namePiece.toLowerCase() ) ||
                     (!sourcePiece.equals("|WeRelate - similar spelling") &&
                      !sourcePiece.equals("|[[Source:A Dictionary of Surnames|A Dictionary of Surnames]]")&&
                      !sourcePiece.equals("|[[Source:The New American Dictionary of Baby Names|The New American Dictionary of Baby Names]]")&&
                      !(currentPagePrefix.equals("Givenname:") && sourcePiece.equals("|")) )) {
                  totalRelatedNames++;
                  //System.out.println(newName);
                  sb.append(newName);
                  sb.append("\n");
               }
               else{
//                  System.out.println(newName.substring(0,newName.indexOf('|')));
               }
            }
         }
         if(totalRelatedNames > current + 29)
            over30Names++;
         logger.info("done" + title);

         // three variables must be set to post correctly
         edit.setPostVariable("related",sb.toString());
         edit.setPostVariable("wpSummary","automated edit to remove rare names");
         edit.setPostVariable("wpTextbox1",edit.readVariable(TEXTBOX1_PATTERN));
         edit.doPost();
      }
   }

   public static void main(String[] args) throws IOException
   {
      if (args.length <= 2) {
         System.out.println("Usage: <CommonNames file> <Prefix to do> <NameFile to process> <host> <password>");
      }
      else {
         NameEditor surnames = new NameEditor();
         surnames.LoadNames(args[0]);
         surnames.setPrefix(args[1]);
         surnames.openFile(args[2]); 
         surnames.editPages(args[3], args[4]);
         surnames.close();
         System.out.println(surnames.getAvg());
         System.out.println(surnames.Over30Names());
      }
   }
}
