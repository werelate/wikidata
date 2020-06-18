package org.werelate.verify;

import org.apache.log4j.Logger;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.net.URLEncoder;

import nu.xom.ParsingException;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class PlaceLinkVerifier extends StructuredDataParser {
   private static Logger logger = Logger.getLogger("org.werelate.verify");
   private static final Pattern REDIRECT_PATTERN = Pattern.compile("\\s*#redirect\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
   private Map<String,Collection<String>> containsLinks = new HashMap<String,Collection<String>>(100000);
   private Map<String,Collection<String>> isContainedLinks = new HashMap<String,Collection<String>>(100000);
   private Map<String,String> redirects = new HashMap<String,String>(10000);
   private int totalProblems;

   public PlaceLinkVerifier() {
      super();
   }

   private String redirect(String src){
      int redirs = 0;
      while (redirs < 5) {
         String target = redirects.get(src);
         if (target == null || "".equals(target)) {
            return src;
         }
         src = target;
         redirs++;
      }
      return "";
   }

   private String formatLink(String title) {
      try
      {
         return "<a href=\"https://www.werelate.org/wiki/Place:"+URLEncoder.encode(title, "UTF-8")+"\">"+Util.encodeXML(title)+"</a>";
      } catch (UnsupportedEncodingException e)
      {
         throw new RuntimeException(e.getMessage());
      }
   }

   public void verify(String filename) throws IOException
   {
      totalProblems = 0;

      // update isContainedLinks to point to redirect targets
      Collection<String> redirectedPlaces = new HashSet<String>();
      for (String place : isContainedLinks.keySet()) {
         Collection<String> superiorPlaces = isContainedLinks.get(place);
         for (String superiorPlace : superiorPlaces) {
            redirectedPlaces.add(redirect(superiorPlace));
         }
         superiorPlaces.clear();
         superiorPlaces.addAll(redirectedPlaces);
         redirectedPlaces.clear();
      }

      PrintWriter out = new PrintWriter(new FileWriter(filename));
      DateFormat dateFormat = new SimpleDateFormat("d MMM yyyy");
      Date date = new Date();
      out.println("<html><head><title>Place problems report</title></head><body><h2>Problems report for places dated "+dateFormat.format(date)+"</h2><ul>");

      //this check to make sure the everything in a places contains group points back to it as well
      Set<String> ContainsLinksKeySet = containsLinks.keySet();
      Iterator ContainsPlaceKeyIter = ContainsLinksKeySet.iterator();
      while(ContainsPlaceKeyIter.hasNext()){
         String OrigPlace = (String)ContainsPlaceKeyIter.next();
         Collection<String> InferiorPlaces = containsLinks.get(OrigPlace);
         Iterator InferiorPlaceIter = InferiorPlaces.iterator();

         while(InferiorPlaceIter.hasNext()){
            String InferiorPlace = (String)InferiorPlaceIter.next();
            if(isContainedLinks.get(InferiorPlace)!=null){
               if (!isContainedLinks.get(InferiorPlace).contains(OrigPlace)) {
                  totalProblems++;
                  out.println("<li>"+formatLink(OrigPlace)+" links to contained place "+formatLink(InferiorPlace)+" without a backlink"+"</li>");
               }
            }
            else{
               totalProblems++;
               out.println("<li>"+formatLink(OrigPlace)+" links to contained place "+formatLink(InferiorPlace)+" that is not found or is a redirect"+"</li>");
            }
         }
      }

      //this checks to make sure that everything a place is located in points back to it as well
      Set<String> IsContainedLinksKeySet = isContainedLinks.keySet();
      Iterator IsContainedPlaceKeyIter = IsContainedLinksKeySet.iterator();
      while(IsContainedPlaceKeyIter.hasNext()){
         String OrigPlace = (String)IsContainedPlaceKeyIter.next();
         Collection<String> SuperiorPlaces = isContainedLinks.get(OrigPlace);
         Iterator SuperPlaceIter = SuperiorPlaces.iterator();

         while(SuperPlaceIter.hasNext()){
            String SuperiorPlace = (String)SuperPlaceIter.next();
            if(containsLinks.get(SuperiorPlace) !=null){
               if(!containsLinks.get(SuperiorPlace).contains(OrigPlace)) {
                  totalProblems++;
                  out.println("<li>"+formatLink(OrigPlace)+" links to parent place "+formatLink(SuperiorPlace)+" without a backlink"+"</li>");
               }
            }
            else{
               totalProblems++;
               out.println("<li>"+formatLink(OrigPlace)+" links to parent place "+formatLink(SuperiorPlace)+" that is not found"+"</li>");
            }
         }
      }

      out.println("</ul></body></html>");
      out.close();
   }

   public int getTotalProblems() {
      return totalProblems;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (title.startsWith("Place:")) {
         title = title.substring("Place:".length());
         String[] split = splitStructuredWikiText("place", text);
         String structuredData = split[0];
         Matcher m = REDIRECT_PATTERN.matcher(text);

         if (m.lookingAt()) {
            String target = Util.cleanRedirTarget(m.group(1)).substring("Place:".length()).trim();
            redirects.put(title, target);
         }
         else if (!Util.isEmpty(structuredData)) {
            Document doc = parseText(split[0]);
            Element elm = doc.getRootElement();

            Elements prevParents = elm.getChildElements("contained_place");
            Collection<String> ContainsInSet = null;
            ContainsInSet = new HashSet<String>(prevParents.size());
            for (int i = 0; i < prevParents.size(); i++) {
               Element prevParent = prevParents.get(i);
               String place = Util.translateHtmlCharacterEntities(prevParent.getAttributeValue("place"));
               ContainsInSet.add(place);
            }
            containsLinks.put(title,ContainsInSet);

            prevParents = elm.getChildElements("also_located_in");
            Collection<String> IsContainedInSet = new ArrayList<String>(prevParents.size()+1);
            if(title.indexOf(',') != -1) IsContainedInSet.add(title.substring(title.indexOf(',')+1).trim());
            for (int i = 0; i < prevParents.size(); i++) {
               Element prevParent = prevParents.get(i);
               String place = Util.translateHtmlCharacterEntities(prevParent.getAttributeValue("place"));
               IsContainedInSet.add(place);
            }
            isContainedLinks.put(title,IsContainedInSet);
         }
      }
   }

   public static void main(String[] args) throws IOException, ParsingException
   {
      if (args.length < 2) {
         System.out.println("Usage: <pages.xml file> <problems.html file>");
      }
      else {
         WikiReader wikiReader = new WikiReader();
         wikiReader.setSkipRedirects(false);
         PlaceLinkVerifier placeVerifier = new PlaceLinkVerifier();
         wikiReader.addWikiPageParser(placeVerifier);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         placeVerifier.verify(args[1]);
      }
   }
}
