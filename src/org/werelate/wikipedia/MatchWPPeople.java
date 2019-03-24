package org.werelate.wikipedia;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.HttpClient;
import org.apache.log4j.Logger;
import org.werelate.utils.Util;

import java.io.*;
import java.net.URLEncoder;

import com.hp.hpl.sparta.Parser;
import com.hp.hpl.sparta.Document;
import com.hp.hpl.sparta.ParseException;
import com.hp.hpl.sparta.Element;

public class MatchWPPeople
{
   private static final Logger logger = Logger.getLogger("org.werelate.wikipedia");

   private static final float MATCH_THRESHOLD = 2.75f;

   private static final float HIGH_MATCH_THRESHOLD = 2.95f;

   private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
           
   public static float getMatchScore(HttpClient client, String title, String given, String surname, String birthDate, String deathDate) {
      GetMethod m = null;
      float matchScore = 0.0f;
      try
      {
         String query = "PersonGivenname:\""+given+"\" PersonSurname:\""+surname+"\" PersonBirthDate:\""+birthDate+"\" PersonDeathDate:\""+deathDate+"\"";
         String url = "https://index.werelate.org/solr/search";
         m = new GetMethod(url);
         NameValuePair[] nvp = new NameValuePair[5];
         nvp[0] = new NameValuePair("fl", "TitleStored,score");
         nvp[1] = new NameValuePair("rows", "1");
         nvp[2] = new NameValuePair("wt", "xml");
         nvp[3] = new NameValuePair("hl", "false");
         nvp[4] = new NameValuePair("q", query);
         m.setQueryString(nvp);
         client.executeMethod(m);
         String response = m.getResponseBodyAsString();
         Document doc = Parser.parse("response", new StringReader(response));
         String maxScore = doc.xpathSelectElement("/response/result").getAttribute("maxScore");
         matchScore = Float.parseFloat(maxScore);
      } catch (IOException e)
      {
         logger.warn("Error matching: "+title+" : "+e.getMessage());
      } catch (ParseException e)
      {
         logger.warn("Error parsing response: "+title+" : "+e.getMessage());
      } catch (NumberFormatException e) {
         logger.warn("Error parsing max score: "+title);
      } finally {
         m.releaseConnection();
      }

      return matchScore;
   }
   
   public static String getDate(String stdDate) {
      StringBuilder buf = new StringBuilder();
      if (stdDate.length() > 6) {
         buf.append(stdDate.substring(6));
         buf.append(" ");
      }
      if (stdDate.length() > 4) {
         buf.append(MONTHS[Integer.parseInt(stdDate.substring(4, 6))-1]);
         buf.append(" ");
      }
      buf.append(stdDate.substring(0, Math.min(stdDate.length(), 4)));
      
      return buf.toString();
   }

   // args[0] = filename with titles to match
   // args[1] = html filename to write for low-matches
   // args[2] = html filename to write for high-matches
   public static void main(String[] args) throws IOException
   {
      HttpClient client = new HttpClient();
      client.getParams().setParameter("http.protocol.content-charset", "UTF-8");
      client.getParams().setParameter("http.socket.timeout", 600000);
      client.getParams().setParameter("http.connection.timeout", 600000);

      // for each person in the file
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      PrintWriter outLow = new PrintWriter(args[1]);
      PrintWriter outHigh = new PrintWriter(args[2]);
      outLow.println("<html><head></head><body><ul>");
      outHigh.println("<html><head></head><body><ul>");
      int cnt = 0;
      while (in.ready()) {
         // match the person
         String line = in.readLine();
         String[] fields = line.split("\\|");
         if (fields.length >= 4) {
            // if the match is above threshold
            String birthDate = "";
            String deathDate = "";
            try {
               birthDate = getDate(fields[3]);
               if (fields.length >= 5) deathDate = getDate(fields[4]);
            }
            catch (NumberFormatException e) {
               logger.warn("Error parsing date for line="+line);
            }
            float matchScore = getMatchScore(client, fields[0], fields[1], fields[2], birthDate, deathDate);
            // write a search string, a wp link, and a source-wikipedia template to the output file
            String title = URLEncoder.encode(fields[0].replace(' ','_'), "UTF-8");
            String given = URLEncoder.encode(fields[1], "UTF-8");
            String surname = URLEncoder.encode(fields[2], "UTF-8");
            birthDate = URLEncoder.encode(birthDate, "UTF-8");
            deathDate = URLEncoder.encode(deathDate, "UTF-8");
            String htmlTitle = Util.encodeXML(fields[0]);
            PrintWriter out = (matchScore > MATCH_THRESHOLD ? outHigh : outLow);
            out.println("<li><a href=\"https://www.werelate.org/wiki/Special:Search?ns=Person&g="+given+"&s="+surname+"&bd="+birthDate+"&dd="+deathDate+"\">search</a>"+
                    " <a href=\"https://en.wikipedia.org/wiki/"+title+"\">wikipedia</a> {{source-wikipedia|"+htmlTitle+"}}</li>");
            out.flush();
         }
         else {
            logger.warn("Invalid line="+line);
         }
         cnt++;
         Util.sleep(100);
      }
      in.close();
      outLow.println("</ul></body></html>");
      outHigh.println("</ul></body></html>");
      outLow.close();
      outHigh.close();
   }
}
