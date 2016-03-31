package org.werelate.scripts;

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExtractPersonsFamilies extends StructuredDataParser {
   PrintWriter personsOut;
   PrintWriter familiesOut;

   public ExtractPersonsFamilies(String personsPath, String familiesPath) throws IOException {
       personsOut = new PrintWriter(new FileWriter(personsPath));
       familiesOut = new PrintWriter(new FileWriter(familiesPath));
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Person:") || title.startsWith("Family:")) {
          if (title.startsWith("Person:") || title.startsWith("Family:")) {
             String[] split = splitStructuredWikiText(title.startsWith("Person:") ? "person" : "family", text);
             String structuredData = split[0];
             if (!Util.isEmpty(structuredData)) {
                Element root = parseText(structuredData).getRootElement();
                if (title.startsWith("Person:")) {
                    outputPerson(pageId, title, root);
                }
                 else {
                    outputFamily(pageId, title, root);
                }
             }
          }
      }
   }

    public void outputPerson(int pageId, String title, Element root) {
        // get name
        String given = null;
        String surname = null;
        Elements names = root.getChildElements("name");
        for (int i = 0; i < names.size(); i++) {
            Element name = names.get(i);
            given = name.getAttributeValue("given");
            surname = name.getAttributeValue("surname");
            break;
        }

        // get birth date and place
        String birthDate = null;
        String birthPlace = null;
        String deathDate = null;
        String deathPlace = null;
        Elements eventFacts = root.getChildElements("event_fact");
        for (int i = 0; i < eventFacts.size(); i++) {
            Element eventFact = eventFacts.get(i);
            if ("Birth".equals(eventFact.getAttributeValue("type"))) {
                birthDate = eventFact.getAttributeValue("date");
                birthPlace = getStandard(eventFact.getAttributeValue("place"));
                break;
            }
            if ("Death".equals(eventFact.getAttributeValue("type"))) {
                deathDate = eventFact.getAttributeValue("date");
                deathPlace = getStandard(eventFact.getAttributeValue("place"));
                break;
            }
        }

        // write person
        if (!Util.isEmpty(given) && !Util.isEmpty(surname)) {
            if (Util.isEmpty(birthDate)) {
                birthDate = deathDate;
            }
            if (Util.isEmpty(birthPlace)) {
                birthPlace = deathPlace;
            }
            if (Util.isEmpty(birthDate)) {
                birthDate = "";
            }
            if (Util.isEmpty(birthPlace)) {
                birthPlace = "";
            }
            personsOut.println(pageId + "\t" + title + "\t" + Util.romanize(given) + "\t" + Util.romanize(surname) + "\t" + birthDate + "\t" + birthPlace);
        }
    }

    public void outputFamily(int pageId, String title, Element root) {
        // get husband
        String husbandTitle = null;
        Elements parents = root.getChildElements("husband");
        for (int i = 0; i < parents.size(); i++) {
            Element parent = parents.get(i);
            husbandTitle = parent.getAttributeValue("title");
            break;
        }

        // get wife
        String wifeTitle = null;
        parents = root.getChildElements("wife");
        for (int i = 0; i < parents.size(); i++) {
            Element parent = parents.get(i);
            wifeTitle = parent.getAttributeValue("title");
            break;
        }

        // get children
        List<String> childTitles = new ArrayList<String>();
        Elements children = root.getChildElements("child");
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            String childTitle = child.getAttributeValue("title");
            if (!Util.isEmpty(childTitle)) {
                childTitles.add(childTitle);
            }
        }

        // write family
        if (Util.isEmpty(husbandTitle)) {
            husbandTitle = "";
        }
        if (Util.isEmpty(wifeTitle)) {
            wifeTitle = "";
        }
        familiesOut.println(pageId + "\t" + title + "\t" + husbandTitle + "\t" + wifeTitle + "\t" + Util.join("\t", childTitles));
    }

   private String getStandard(String text) {
       if (text == null) {
           return null;
       }
       int pos = text.indexOf('|');
       if (pos > 0) {
           return text.substring(0, pos);
       }
       return text;
   }

   public void close()
   {
      personsOut.close();
       familiesOut.close();
   }

   // Generate list of persons(id, title, given, surname, bdate, bplace) families(husbandids, wifeids, childids)
   // 0=pages.xml 1=persons 2=families
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      ExtractPersonsFamilies self = new ExtractPersonsFamilies(args[1], args[2]);
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.close();
   }
}
