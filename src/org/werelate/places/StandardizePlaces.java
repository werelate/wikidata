package org.werelate.places;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.HttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.werelate.utils.Util;

import javax.xml.xpath.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.io.*;

public class StandardizePlaces
{
   private String placeServer;
   private HttpClient client;
   private DocumentBuilder db;
   private XPathExpression lstExpression;
   private XPathExpression queryExpression;
   private XPathExpression placeTitleExpression;
   private XPathExpression errorExpression;
   private Map<String,String> redirects;

   public StandardizePlaces(String placeServer) throws ParserConfigurationException, XPathExpressionException
   {
      this.placeServer = placeServer;
      resetClient();
      db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      XPathFactory xpf = XPathFactory.newInstance();
      XPath xpe = xpf.newXPath();
      lstExpression = xpe.compile("/response/arr/lst");
      queryExpression = xpe.compile("./str[@name='q']");
      placeTitleExpression = xpe.compile("./str[@name='PlaceTitle']");
      errorExpression = xpe.compile("./str[@name='error']");
      redirects = new HashMap<String,String>();
   }

   public void loadRedirects(String filename) throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         redirects.put(fields[0], fields[1]);
      }
      in.close();
   }

   public String getRedirTarget(String name) {
      while (redirects.get(name) != null) {
         name = redirects.get(name);
      }
      return name;
   }

   private void resetClient() {
      client = new HttpClient();
      client.getParams().setParameter("http.protocol.content-charset", "UTF-8");
      client.getParams().setParameter("http.socket.timeout", 600000);
      client.getParams().setParameter("http.connection.timeout", 600000);
   }

   // err may be null
   public Map<String,String> getStandardizedPlaceNames(Set<String> names, PrintWriter err) throws IOException, SAXException, XPathExpressionException
   {
      Map<String,String> result = new HashMap<String,String>();
      String query = Util.join("|", names);
      String url = "http://"+placeServer+"/placestandardize";
      PostMethod m = new PostMethod(url);
      NameValuePair[] nvp = new NameValuePair[2];
      nvp[0] = new NameValuePair("q", query);
      nvp[1] = new NameValuePair("wt", "xml");
      m.setRequestBody(nvp);
      HttpMethodParams params = new HttpMethodParams();
      params.setContentCharset("UTF-8");
      params.setHttpElementCharset("UTF-8");
      params.setParameter("http.protocol.content-charset", "UTF-8");
      m.setParams(params);
      m.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
      int numTries = 0;
      while (numTries++ < 3) {
         try {
            client.executeMethod(m);
            break;
         }
         catch (IOException e) {
            resetClient();
         }
      }
      String response = m.getResponseBodyAsString();
      Document doc = db.parse(new InputSource(new StringReader(response)));
      NodeList lstNodes = (NodeList) lstExpression.evaluate(doc, XPathConstants.NODESET);
      for (int i=0; i < lstNodes.getLength(); i++)
      {
         Node node = lstNodes.item(i);
         String q = (String) queryExpression.evaluate(node, XPathConstants.STRING);
         String placeTitle = (String)placeTitleExpression.evaluate(node, XPathConstants.STRING);
         String error = (String) errorExpression.evaluate(node, XPathConstants.STRING);
         if (err != null && !Util.isEmpty(error)) {
            err.println(q);
         }
         placeTitle = getRedirTarget(placeTitle);
         result.put(q,placeTitle);
      }
      return result;
   }

   // 0=places to standardize 1=place redirs 2=output 3=errors out
   public static void main(String[] args) throws IOException, ParserConfigurationException, XPathExpressionException, SAXException
   {
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      PrintWriter out = new PrintWriter(args[2]);
      PrintWriter err = new PrintWriter(args[3]);
      StandardizePlaces sp = new StandardizePlaces("index.werelate.org/solr/werelate");
      sp.loadRedirects(args[1]);
      Set<String> names = new HashSet<String>();

      while (in.ready()) {
         String line = in.readLine();
         names.add(line);
         if (names.size() >= 75 || !in.ready()) {
            Map<String,String> result = sp.getStandardizedPlaceNames(names, err);
            names.clear();
            for (Map.Entry entry : result.entrySet()) {
               out.println(entry.getKey()+"|"+entry.getValue());
            }
            out.flush();
         }
      }
      in.close();
      out.close();
      err.close();
   }
}
