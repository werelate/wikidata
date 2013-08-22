/**
 * Copyright (C) 2005-2006 Foundation for On-Line Genealogy (folg.org)

 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * See gpl.txt in the root project folder or
 * www.gnu.org/copyleft/gpl.html
 */
package org.werelate.scripts;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;
import org.werelate.editor.PageEditor;

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

public class RedirectEditor {
   private static Logger logger = Logger.getLogger("org.werelate.redirect");
   private static final Pattern REDIRECT_PATTERN1 = Pattern.compile("#redirect\\s*\\[\\[Family:(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
   private static final Pattern RELATEDBOX_PATTERN = Pattern.compile("<textarea[^>]*?related[^>]*?>(.*?)</textarea>", Pattern.DOTALL);
   private static final Pattern TEXTBOX1_PATTERN = Pattern.compile("<textarea[^>]*?wpTextbox1[^>]*?>(.*?)</textarea>", Pattern.DOTALL);

   private HashSet<String> CommonNames = new HashSet<String>(); 
   private BufferedReader readIn;
   public RedirectEditor() {
      super();
   }

   public void openFile(String file) throws IOException{
      readIn = new BufferedReader(new FileReader(file));
   }
   public void close() throws IOException {
      readIn.close();
   }

   public void editPages()throws IOException{
      PageEditor edit = new PageEditor("beta.werelate.org","password");
      Scanner scan;
      while(readIn.ready()){
         String title = readIn.readLine();
         String subText = "#REDIRECT[[" + title.substring(title.indexOf('=')+1)+ "]]";
         title = title.substring(0,title.indexOf('='));
         edit.doGet(title,true);
         logger.info(title + " page is done");
         // three variables must be set to post correctly
         //edit.setPostVariable("related",edit.readVariable(RELATEDBOX_PATTER));
         edit.setPostVariable("wpSummary","computer-edit to remove redirect chaining");
         edit.setPostVariable("wpTextbox1",subText);
         edit.doPost();
      }
   }


   public static void main(String[] args) throws IOException
   {

      if (args.length < 1) {
         System.out.println("Usage: <RedirectFile to use>");
      }
      else {
         RedirectEditor surnames = new RedirectEditor();
         surnames.openFile(args[0]); 
         surnames.editPages();
         surnames.close();
      }
   }
}
