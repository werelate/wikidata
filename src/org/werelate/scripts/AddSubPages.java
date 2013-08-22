package org.werelate.scripts;

import org.apache.log4j.Logger;
import org.werelate.editor.PageEditor;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class AddSubPages {
   private static Logger logger = Logger.getLogger("org.werelate.scripts");

   private PageEditor editor;
   private String title;

   public AddSubPages(String title, String host, String password) {
      logger.info("host="+host+" password="+password+" title="+title);
      editor = new PageEditor(host, password);
      this.title = title;
   }

   public void add(String subTitle, String text) {
      String wrTitle = this.title+"/"+subTitle;
      editor.doGet(wrTitle, true, "xml=1");
      String wrText = editor.readVariable(PageEditor.TEXTBOX1_PATTERN, true).trim();
      if (wrText.length() > 0) {
         System.out.println("Skip existing "+wrTitle);
      }
      else {
         System.out.println("Adding "+wrTitle);
         editor.setPostVariable("wpTextbox1", text);
         editor.setPostVariable("wpSummary", "initial load");
         editor.setPostVariable("xml", "1");
         editor.doPost();
      }
   }

   private static String readFile(File f) throws IOException {
     FileInputStream stream = new FileInputStream(f);
     try {
       FileChannel fc = stream.getChannel();
       MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
       /* Instead of using default, pass in a decoder. */
       return Charset.defaultCharset().decode(bb).toString();
     }
     finally {
       stream.close();
     }
   }

   // 0=title 1=host 2=password 4=directory containing subpages to add 5=max pages to add
   public static void main(String[] args) throws IOException, InterruptedException {
      if (args.length < 4) {
         System.out.println("Usage: title, host, password, directory");
         System.exit(1);
      }
      int max = -1;
      if (args.length > 4) {
         max = Integer.parseInt(args[4]);
      }
      AddSubPages self = new AddSubPages(args[0], args[1], args[2]);
      File dir = new File(args[3]);
      for (File file : dir.listFiles()) {
         if (max-- == 0) break;
         self.add(file.getName(), readFile(file));
         Thread.sleep(1000);
      }
   }
}
