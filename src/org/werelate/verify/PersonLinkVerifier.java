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

public class PersonLinkVerifier extends StructuredDataParser {
   // family collections
   private static final int SPOUSES = 0;
   private static final int CHILDREN = 1;
   private static final int FAMILY_IMAGES = 2;
   // person collections
   private static final int PARENT_FAMILIES = 0;
   private static final int SPOUSE_FAMILIES = 1;
   private static final int PERSON_IMAGES = 2;
   // image collections
   private static final int PEOPLE = 0;
   private static final int FAMILIES = 1;

   private static class CollectionArray {
      public Collection<String>[] collections;
      public CollectionArray(Collection<String> one, Collection<String> two) {
         collections = new ArrayList[2];
         collections[0] = one;
         collections[1] = two;
      }
      public CollectionArray(Collection<String> one, Collection<String> two, Collection<String> three) {
         collections = new ArrayList[3];
         collections[0] = one;
         collections[1] = two;
         collections[2] = three;
      }
   }

   private static Logger logger = Logger.getLogger("org.werelate.verify");
   private static final Pattern REDIRECT_PATTERN = Pattern.compile("\\s*#redirect\\s*\\[\\[(.+?)\\]\\]", Pattern.CASE_INSENSITIVE);
   private Map<String,CollectionArray> familyLinks = new HashMap<String,CollectionArray>(100000);
   private Map<String,CollectionArray> personLinks = new HashMap<String,CollectionArray>(100000);
   private Map<String,CollectionArray> imageLinks = new HashMap<String,CollectionArray>(1000);
   private Collection<String> redirects = new HashSet<String>(10000);
   private int totalProblems;

   public PersonLinkVerifier() {
      super();
   }

   private String formatLink(String namespace, String title) {
      try
      {
         return "<a href=\"https://www.werelate.org/wiki/"+namespace+":"+URLEncoder.encode(title, "UTF-8")+"\">"+Util.encodeXML(namespace+":"+title)+"</a>";
      } catch (UnsupportedEncodingException e)
      {
         throw new RuntimeException(e.getMessage());
      }
   }

   private void checkBackLinks(String nsA, Map<String,CollectionArray> aToBLinks, int collPosA,
                               String nsB, Map<String,CollectionArray> bToALinks, int collPosB, PrintWriter out) {
      for (String a : aToBLinks.keySet()) {
         Collection<String> bTitles = aToBLinks.get(a).collections[collPosA];
         if (bTitles != null) {
            for (String b : bTitles) {
               CollectionArray ca = bToALinks.get(b);
               if (ca != null) {
                  Collection<String> aTitles = ca.collections[collPosB];
                  if (aTitles == null || !aTitles.contains(a)) {
                     out.println("<li>"+formatLink(nsA, a) + " links to " + formatLink(nsB, b) + " without a backlink"+"</li>");
                     totalProblems++;
                  }
               }
               else if (redirects.contains(nsB+":"+b)) {
                  out.println("<li>"+formatLink(nsA, a) + " links to " + formatLink(nsB, b) + " that is a redirect"+"</li>");
                  totalProblems++;
               }
               else {
                  // out.println(formatLink(nsB, b) + " is not created yet");
               }
            }
         }
      }
   }

   public void verify(String filename) throws IOException
   {
      totalProblems = 0;
      PrintWriter out = new PrintWriter(new FileWriter(filename));
      DateFormat dateFormat = new SimpleDateFormat("d MMM yyyy");
      Date date = new Date();
      out.println("<html><head><title>Person problems report</title></head><body><h2>Problems report for people dated "+dateFormat.format(date)+"</h2><ul>");

      checkBackLinks("Person", personLinks, PARENT_FAMILIES, "Family", familyLinks, CHILDREN, out);
      checkBackLinks("Person", personLinks, SPOUSE_FAMILIES, "Family", familyLinks, SPOUSES, out);
      checkBackLinks("Person", personLinks, PERSON_IMAGES, "Image", imageLinks, PEOPLE, out);

      checkBackLinks("Family", familyLinks, CHILDREN, "Person", personLinks, PARENT_FAMILIES, out);
      checkBackLinks("Family", familyLinks, SPOUSES, "Person", personLinks, SPOUSE_FAMILIES, out);
      checkBackLinks("Family", familyLinks, FAMILY_IMAGES, "Image", imageLinks, FAMILIES, out);

      checkBackLinks("Image", imageLinks, PEOPLE, "Person", personLinks, PERSON_IMAGES, out);
      checkBackLinks("Image", imageLinks, FAMILIES, "Family", familyLinks, FAMILY_IMAGES, out);

      out.println("</ul></body></html>");
      out.close();
   }

   public int getTotalProblems() {
      return totalProblems;
   }

   private Collection<String> getRelatedLinks(Element root, String elmName, String attrName) {
      Elements links = root.getChildElements(elmName);
      if (links.size() == 0) {
         return null;
      }
      Collection<String> related = new ArrayList<String>(links.size());
      for (int i = 0; i < links.size(); i++) {
         Element link = links.get(i);
         String name = Util.translateHtmlCharacterEntities(link.getAttributeValue(attrName));
         related.add(name);
      }
      return related;
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (title.startsWith("Family:")) {
         String[] split = splitStructuredWikiText("family", text);
         String structuredData = split[0];
         String wikiText = split[1];

         if (!Util.isEmpty(structuredData)) {
            title = title.substring("Family:".length());
            Document doc = parseText(split[0]);
            Element root = doc.getRootElement();
            Collection<String> spouseLinks = getRelatedLinks(root, "husband", "title");
            Collection<String> otherSpouseLinks = getRelatedLinks(root, "wife", "title");
            if (spouseLinks == null) {
               spouseLinks = otherSpouseLinks;
            }
            else if (otherSpouseLinks != null) {
               spouseLinks.addAll(otherSpouseLinks);
            }
            familyLinks.put(title, new CollectionArray(spouseLinks,
                                                       getRelatedLinks(root, "child", "title"),
                                                       getRelatedLinks(root, "image", "filename")));
         }
         else {
            Matcher m = REDIRECT_PATTERN.matcher(wikiText);
            if (m.lookingAt()) {
               redirects.add(title);
            }
         }
      }
      else if (title.startsWith("Person:")) {
         String[] split = splitStructuredWikiText("person", text);
         String structuredData = split[0];
         String wikiText = split[1];

         if (!Util.isEmpty(structuredData)) {
            title = title.substring("Person:".length());
            Document doc = parseText(split[0]);
            Element root = doc.getRootElement();
            personLinks.put(title, new CollectionArray(getRelatedLinks(root, "child_of_family", "title"),
                                                       getRelatedLinks(root, "spouse_of_family", "title"),
                                                       getRelatedLinks(root, "image", "filename")));
         }
         else {
            Matcher m = REDIRECT_PATTERN.matcher(wikiText);
            if (m.lookingAt()) {
               redirects.add(title);
            }
         }
      }
      else if (title.startsWith("Image:")) {
         String[] split = splitStructuredWikiText("image_data", text);
         String structuredData = split[0];
         String wikiText = split[1];

         if (!Util.isEmpty(structuredData)) {
            title = title.substring("Image:".length());
            Document doc = parseText(split[0]);
            Element root = doc.getRootElement();
            imageLinks.put(title, new CollectionArray(getRelatedLinks(root, "person", "title"),
                                                      getRelatedLinks(root, "family", "title")));
         }
         else {
            Matcher m = REDIRECT_PATTERN.matcher(wikiText);
            if (m.lookingAt()) {
               redirects.add(title);
            }
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
         PersonLinkVerifier personVerifier = new PersonLinkVerifier();
         wikiReader.addWikiPageParser(personVerifier);
         InputStream in = new FileInputStream(args[0]);
         wikiReader.read(in);
         in.close();
         personVerifier.verify(args[1]);
      }
   }
}
