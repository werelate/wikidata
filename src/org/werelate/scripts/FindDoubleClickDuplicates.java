package org.werelate.scripts;

import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class FindDoubleClickDuplicates  extends StructuredDataParser {
   private static Logger logger = Logger.getLogger("org.werelate.redirect");
   private static final int WINDOW_SIZE = 3;

   private List<String> titleQueue;
   private List<Integer> pageIdQueue;
   private PrintWriter out;
   private PreparedStatement ps;

   public FindDoubleClickDuplicates(PrintWriter out, String userName, String password) throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
      super();
      titleQueue = new LinkedList<String>();
      pageIdQueue = new LinkedList<Integer>();
      this.out = out;
      Class.forName("com.mysql.jdbc.Driver").newInstance();
      Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/wikidb", userName, password);
      ps = conn.prepareStatement("select count(*) from revision where rev_page = ?");
   }

   private int getRevCount(int pageId) throws SQLException {
      ps.clearParameters();
      ps.setInt(1, pageId);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
         return rs.getInt(1);
      }
      rs.close();
      return 0;
   }

   private boolean matches(String t1, int p1, String t2, int p2) {
      int l1 = t1.lastIndexOf('(');
      int l2 = t2.lastIndexOf('(');
      int r1 = t1.lastIndexOf(')');
      int r2 = t2.lastIndexOf(')');
      boolean titlesMatch = false;
      try {
         titlesMatch = (l1 > 0 &&
              l1 == l2 &&
              r1 > l1+1 &&
              r2 > l2+1 &&
              t1.substring(0, l1).equals(t2.substring(0, l2)) &&
              Integer.parseInt(t1.substring(l1+1, r1))+1 == Integer.parseInt(t2.substring(l2+1, r2)));
      }
      catch (NumberFormatException e) {
         // ignore
      }

      // one of the pages must have just 1 revision
      boolean editedOnce = false;
      if (titlesMatch) {
         try {
            editedOnce = (getRevCount(p1) == 1 || getRevCount(p2) == 1);
         } catch (SQLException e) {
            e.printStackTrace();
         }
      }
      return titlesMatch && editedOnce;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if ((title.startsWith("Person:") || title.startsWith("Family:")) && !comment.equals("gedcom upload")) {
         // does title match any in titleQueue?
         for (int i = 0; i < titleQueue.size(); i++) {
            String oldTitle = titleQueue.get(i);
            int oldPageId = pageIdQueue.get(i);
            if (matches(oldTitle, oldPageId, title, pageId)) {
               out.println("[[" + oldTitle + "]] [[" + title + "]]<br>");
               break;
            }
         }
         // add this to titleQueue
         titleQueue.add(0, title);
         pageIdQueue.add(0, pageId);
         // remove last from queues
         if (titleQueue.size() > WINDOW_SIZE) {
            titleQueue.remove(WINDOW_SIZE);
            pageIdQueue.remove(WINDOW_SIZE);
         }
      }
   }



   public static void main(String[] args) throws IOException, ParsingException, ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException {

      if (args.length != 4) {
         System.out.println("Usage: <pages.xml file> <file to write duplicates to> userName, password");
      }
      else {
         PrintWriter out = new PrintWriter(args[1]);
         FindDoubleClickDuplicates self = new FindDoubleClickDuplicates(out, args[2], args[3]);
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(true);
         wikiReader.addWikiPageParser(self);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         out.close();
      }
   }
}
