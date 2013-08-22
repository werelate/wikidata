package org.werelate.scripts;

import org.apache.log4j.Logger;
import org.werelate.editor.PageEditor;

import java.util.regex.Pattern;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URLEncoder;

public class DeletePages
{
   private static Logger logger = Logger.getLogger("org.werelate.scripts");

   private PageEditor editor;
   private String prefix;
   public int cnt;

   public DeletePages(String host, String password, String prefix) {
      logger.info("host="+host+" password="+password+" prefix="+prefix);
      editor = new PageEditor(host, password);
      this.prefix = prefix;
      cnt = 0;
   }

   public void delete(String title, String reason) throws UnsupportedEncodingException
   {
      try {
         editor.doGet(prefix+title, false, "action=delete");
      }
      catch (RuntimeException e) {
         logger.warn("Error reading: "+title+" => "+ e);
         return;
      }

      String contents = editor.getContents();
      if (contents.contains("<h1 class=\"firstHeading\">Internal error</h1>")) {
         logger.warn("Page not found: "+title);
      }
      else {
         editor.setPostVariable("wpReason", reason);
         editor.setPostVariable("wpConfirmB", "Delete page");
         editor.doPost("delete", null);

         contents = editor.getContents();
         if (!contents.contains("has been deleted")) {
            logger.warn("Error deleting page: "+title);
         }
         else if (++cnt % 100 == 0) {
            System.out.print(".");
         }
      }
   }

   // 0=titles to delete 1=host 2=password 3=reason 4=prefix (optional)
   public static void main(String[] args) throws IOException
   {
      DeletePages dp = new DeletePages(args[1], args[2], args.length > 4 ? args[4] : "");

      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while(in.ready()){
         String line = in.readLine();
         dp.delete(line, args[3]);
      }
      in.close();
      System.out.println("Deleted "+dp.cnt);
   }
}
