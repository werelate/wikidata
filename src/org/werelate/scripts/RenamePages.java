package org.werelate.scripts;

import org.werelate.editor.PageEditor;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;
import java.net.URLEncoder;

public class RenamePages
{
   private static Logger logger = Logger.getLogger("org.werelate.scripts");

   private static final Pattern OLD_TITLE = Pattern.compile("<input [^>]*?name=\"wpOldTitle\"[^>]*?value=\"(.*?)\"[^/]*/>",Pattern.DOTALL);

   private PageEditor editor;
   private String prefix;
   public int cnt;

   public RenamePages(String host, String password, String prefix) {
      logger.info("host="+host+" password="+password+" prefix="+prefix);
      editor = new PageEditor(host, password);
      this.prefix = prefix;
      cnt = 0;
   }

   public void rename(String oldTitle, String newTitle) throws UnsupportedEncodingException
   {
      try {
         editor.doGet("Special:Movepage",false,"target="+URLEncoder.encode(prefix+oldTitle, "UTF-8"));
      }
      catch (RuntimeException e) {
         logger.warn("Error reading: "+oldTitle+" => "+ e);
         return;
      }

      try {
         editor.setPostVariable("wpOldTitle", editor.readVariable(OLD_TITLE));
         editor.setPostVariable("wpNewTitle", prefix+newTitle);
         editor.setPostVariable("wpReason", "rename to standard page title format");
         editor.setPostVariable("wpMove", "Rename page");
         editor.doPost();
      }
      catch (RuntimeException e) {
         logger.warn("Error renaming: "+oldTitle+" => "+e);
         return;
      }

      String contents = editor.getContents();
      if (contents.contains("<h2>Deletion required</h2>")) {
         logger.warn("Target exists: "+oldTitle+" => " + newTitle);
      }
      else if (contents.contains("This action cannot be performed on this page")) {
         logger.warn("Page not found: "+oldTitle);
      }
      else if (contents.contains("Error: could not submit form")) {
         logger.warn("Error renaming page: "+oldTitle);
      }
      else if (++cnt % 100 == 0) {
         logger.info(cnt+" : "+oldTitle);
      }
   }

   // 0=titles to rename 1=host 2=password 3=prefix (optional)
   public static void main(String[] args) throws IOException
   {
      RenamePages rp = new RenamePages(args[1], args[2], args.length > 3 ? args[3] : "");

      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while(in.ready()){
         String line = in.readLine();
         String[] fields = line.split("\\|");
         String oldTitle = fields[0];
         String newTitle = fields[1];

         rp.rename(oldTitle, newTitle);
      }
      in.close();
      System.out.println("Renamed "+rp.cnt);
   }
}
