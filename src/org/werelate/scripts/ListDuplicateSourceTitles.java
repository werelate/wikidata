package org.werelate.scripts;

import org.werelate.utils.Util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListDuplicateSourceTitles
{

   private static class TitleData {
      public String pageTitle;
      public String author;
      public String place;
      public String title;
      public String dateIssued;
      public String subtitle;
      public String username;

      public TitleData(String pageTitle, String author, String place, String title, String dateIssued, String subtitle, String username) {
         this.pageTitle = pageTitle;
         this.author = author;
         this.place = place;
         this.title = title;
         this.dateIssued = dateIssued;
         this.subtitle = subtitle;
         this.username = username;
      }
   }

   private static Pattern CENSUS_PATTERN = Pattern.compile("^1\\d\\d0 U.S. Census Population Schedule$");
   private static final Pattern FHLID_PATTERN = Pattern.compile("\\(\\d+\\)$");

   public Set<String> oldTitles;
   public Set<String> newTitles;
   public Map<String,List<TitleData>> renames;
   public int dups;
   public int dupSets;

   public ListDuplicateSourceTitles() {
      oldTitles = new HashSet<String>();
      newTitles = new HashSet<String>();
      renames = new TreeMap<String,List<TitleData>>();
      dups = dupSets = 0;
   }

   public void addRename(String newTitle, TitleData td) {
      List<TitleData> tdl = renames.get(newTitle);
      if (tdl == null) {
         tdl = new ArrayList<TitleData>();
         renames.put(newTitle, tdl);
      }
      tdl.add(td);
   }

   public void loadRenames(String inFilename) throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(inFilename));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         if (fields.length < 2 || fields[0].length() == 0 || fields[1].length() == 0) {
            System.out.println("Invalid line:"+line);
            continue;
         }
         String newTitle = fields[0];
         String oldTitle = fields[1];
         TitleData td = new TitleData(fields[1],
                                      fields.length <= 2 ? "" : fields[2], fields.length <= 3 ? "" : fields[3], fields.length <= 4 ? "" : fields[4],
                                      fields.length <= 5 ? "" : fields[5], fields.length <= 6 ? "" : fields[6], fields.length <= 7 ? "" : fields[7]);
         oldTitles.add(oldTitle);
         newTitles.add(newTitle);
         addRename(newTitle, td);
      }
      in.close();
   }

   public void reduceDuplicates() {
      for (String newTitle : newTitles) {
         List<TitleData> tdl = renames.get(newTitle);
         Matcher m = CENSUS_PATTERN.matcher(newTitle);
         if (tdl.size() > 1 && !m.find()) {  // don't try to eliminate census duplicates
            String newTitlePostColon = null;
            int pos = newTitle.lastIndexOf(" : ");
            if (pos > 0) newTitlePostColon = newTitle.substring(pos+3);
            boolean addFhlid = true;
            boolean addDateIssued = true;
            Set<String> datesIssued = new HashSet<String>();
            Iterator<TitleData> iter = tdl.iterator();
            while (iter.hasNext()) {
               TitleData td = iter.next();
               m = FHLID_PATTERN.matcher(td.pageTitle);
               if (!m.find()) {
                  addFhlid = false;
               }
               // if there is a date issued, add that
               if (td.dateIssued.length() == 0 || newTitle.endsWith("("+td.dateIssued+")") || !datesIssued.add(td.dateIssued)) {
                  addDateIssued = false;
               }
            }
            iter = tdl.iterator();
            while (iter.hasNext()) {
               TitleData td = iter.next();
               String altTitle = null;
               m = FHLID_PATTERN.matcher(td.pageTitle);
               // if there is a unique date issued, add that
               if (addDateIssued && td.dateIssued.length() > 0 && !newTitle.endsWith("("+td.dateIssued+")")) {
                  altTitle = Util.prepareWikiTitle(newTitle +" ("+td.dateIssued+")", Util.MAX_TITLE_LEN+10); // allow room for the date issued
               }
               // if each page title ends with an FHLID, add it back
               else if (addFhlid && m.find() && !newTitle.endsWith(m.group(0))) {
                  altTitle = Util.prepareWikiTitle(newTitle+" "+m.group(0), Util.MAX_TITLE_LEN+10); // allow room for the fhlid
               }
               // if there's a subtitle, add that
               else if (newTitle.length() < Util.MAX_TITLE_LEN && td.subtitle.length() > 0 &&
                        !(newTitlePostColon != null && td.subtitle.toLowerCase().contains(newTitlePostColon.toLowerCase()))) { // don't add if it looks like it's been added before
                  altTitle = Util.prepareWikiTitle(newTitle +" : "+Util.capitalizeTitleCase(td.subtitle));
               }

               // if we've found an alternate title for this source, move it
               if (altTitle != null && !newTitle.equals(altTitle)) {
                  iter.remove();
                  addRename(altTitle, td);
               }
            }

            if (tdl.size() > 1) {
               // try again with fhl# and date issued
               addFhlid = true;
               addDateIssued = true;
               datesIssued.clear();
               iter = tdl.iterator();
               while (iter.hasNext()) {
                  TitleData td = iter.next();
                  m = FHLID_PATTERN.matcher(td.pageTitle);
                  if (!m.find()) {
                     addFhlid = false;
                  }
                  // if there is a date issued, add that
                  if (td.dateIssued.length() == 0 || newTitle.endsWith("("+td.dateIssued+")") || !datesIssued.add(td.dateIssued)) {
                     addDateIssued = false;
                  }
               }

               iter = tdl.iterator();
               while (iter.hasNext()) {
                  TitleData td = iter.next();
                  String altTitle = null;
                  m = FHLID_PATTERN.matcher(td.pageTitle);
                  // if there is a unique date issued, add that
                  if (addDateIssued && td.dateIssued.length() > 0 && !newTitle.endsWith("("+td.dateIssued+")")) {
                     altTitle = Util.prepareWikiTitle(newTitle +" ("+td.dateIssued+")", Util.MAX_TITLE_LEN+10); // allow room for the date issued
                  }
                  // if each page title ends with an FHLID, add it back
                  else if (addFhlid && m.find() && !newTitle.endsWith(m.group(0))) {
                     altTitle = Util.prepareWikiTitle(newTitle+" "+m.group(0), Util.MAX_TITLE_LEN+10); // allow room for the fhlid
                  }

                  // if we've found an alternate title for this source, move it
                  if (altTitle != null && !newTitle.equals(altTitle)) {
                     iter.remove();
                     addRename(altTitle, td);
                  }
               }
            }
         }
      }
   }

   public String getLineOut(String newTitle, TitleData td) {
      return newTitle+"|"+td.pageTitle+"|"+td.author+"|"+td.place+"|"+td.title+"|"+
                        td.dateIssued+"|"+td.subtitle+"|"+td.username;
   }

   private void printDups(PrintWriter pwHtml, PrintWriter pwTxt, String newTitle, List<TitleData> tdl) {
      StringBuilder buf = new StringBuilder();
      buf.append(newTitle);

      pwHtml.println(newTitle+"<ul>");
      for (TitleData td : tdl) {
         pwHtml.println("<li><a href=\"http://www.werelate.org/wiki/Source:"+Util.wikiUrlEncoder(td.pageTitle)+"\">"+Util.encodeXML(td.pageTitle)+"</a>");
         buf.append("|");
         buf.append(td.pageTitle);
      }
      pwHtml.println("</ul>");

      pwTxt.println(buf.toString());
   }

   public void writeRenames(String allFilename, String goodFilename, String dupHtmlFilename, String dupTxtFilename, String existsHtmlFilename, String existsTxtFilename) throws UnsupportedEncodingException, FileNotFoundException
   {
      PrintWriter all = new PrintWriter(allFilename);
      PrintWriter good = new PrintWriter(goodFilename);
      PrintWriter dupHtml = new PrintWriter(dupHtmlFilename);
      dupHtml.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>");
      PrintWriter dupTxt = new PrintWriter(dupTxtFilename);
      PrintWriter existsHtml = new PrintWriter(existsHtmlFilename);
      dupHtml.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>");
      PrintWriter existsTxt = new PrintWriter(existsTxtFilename);

      for (String newTitle : renames.keySet()) {
         List<TitleData> tdl = renames.get(newTitle);
         if (tdl.size() > 0) {            // might have gotten emptied out by reduceDuplicates
            TitleData td0 = tdl.get(0);

            if (tdl.size() > 1) {
               printDups(dupHtml, dupTxt, newTitle, tdl);
               dupSets++;
               dups+= tdl.size();
            }
            else if (!newTitle.equals(td0.pageTitle) && oldTitles.contains(newTitle)) {
               existsHtml.println("<li><a href=\"http://www.werelate.org/wiki/Source:"+Util.wikiUrlEncoder(td0.pageTitle)+"\">"+Util.encodeXML(td0.pageTitle)+"</a>");
               existsHtml.println("<br><a href=\"http://www.werelate.org/wiki/Source:"+Util.wikiUrlEncoder(newTitle)+"\">"+Util.encodeXML(newTitle)+"</a>");
               existsTxt.println(td0.pageTitle+"|"+newTitle);
            }
            else if (!newTitle.equals(td0.pageTitle)) {
               good.println(td0.pageTitle+"|"+newTitle);
            }

            for (TitleData td : tdl) {
               all.println(getLineOut(newTitle, td));
            }
         }
      }
      all.close();
      good.close();
      dupHtml.println("</body></html>");
      dupHtml.close();
      dupTxt.close();
      existsHtml.println("</body></html>");
      existsHtml.close();
      existsTxt.close();
   }

   // 0=source_renames in 1=all_renames.txt 2=good.txt 3=duplicates.html 4=duplicates.txt 5 exists.html 6=exists.txt
   public static void main(String[] args) throws IOException
   {
      ListDuplicateSourceTitles ldst = new ListDuplicateSourceTitles();
      ldst.loadRenames(args[0]);
      ldst.reduceDuplicates();
      ldst.writeRenames(args[1], args[2], args[3], args[4], args[5], args[6]);
      System.out.println("Dup Sets="+ldst.dupSets);
      System.out.println("Duplicates="+ldst.dups);
   }
}
