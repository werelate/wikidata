package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.source.Source;
import org.werelate.utils.Util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.ParsingException;
import nu.xom.Element;
import nu.xom.Elements;

// select pl_title, count(*) as cnt from pagelinks where pl_namespace = 104 and
// exists (select * from page where page_namespace = 104 and page_title = pl_title and page_is_redirect = 0) and
// exists (select * from page where page_id = pl_from and (page_namespace = 108 or page_namespace = 110))
// group by pl_title

public class ExtractSources extends StructuredDataParser
{
    private Map<String,String> redirectMap;
    private Map<String,Integer> linkedSourcesMap;
    PrintWriter out;

    public ExtractSources(PrintWriter out) {
        this.out = out;
        redirectMap = new HashMap<String,String>();
        linkedSourcesMap = new HashMap<String, Integer>();
    }

    public void close() {
        this.out.close();
    }


    public String clean(String s, boolean arr) {
        if (s == null) {
            return "";
        }
        s = s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        if (arr) {
            s = s.replace('|', ' ').replace('~', ' ');
        }
        return s.trim();
    }

    public String getField(Element root, String fieldName) {
        String value = "";
        Element elm = root.getFirstChildElement(fieldName);
        if (elm != null && elm.getValue() != null) {
            value = clean(elm.getValue(), false);
        }
        // just in case
        return value;
    }

    public String getFields(Element root, String fieldName) {
        StringBuilder buf = new StringBuilder();
        Elements elms = root.getChildElements(fieldName);
        for (int i = 0; i < elms.size(); i++) {
            Element elm = elms.get(i);
            String value = clean(elm.getValue(), true);
            if (!value.isEmpty()) {
                if (buf.length() > 0) {
                    buf.append("|");
                }
                buf.append(value);
            }
        }
        return buf.toString();
    }

    public String getRepository(Element root) {
        StringBuilder buf = new StringBuilder();
        Elements elms = root.getChildElements("repository");
        for (int i = 0; i < elms.size(); i++) {
            Element elm = elms.get(i);
            String title = clean(elm.getAttributeValue("title"), true);
            String location = clean(elm.getAttributeValue("source_location"), true);
            if (!title.isEmpty() || !location.isEmpty()) {
                if (buf.length() > 0) {
                    buf.append("|");
                }
                buf.append(title+"~"+location);
            }
        }
        if (buf.length() == 0) {
            String title = clean(getField(root, "repository_name"), true);
            String location = clean(getField(root, "url"), true);
            if (location.isEmpty()) {
                location = clean(getField(root, "call_number"), true);
            }
            if (!title.isEmpty() || !location.isEmpty()) {
                String addr = clean(getField(root, "repository_addr"), true);
                if (!addr.isEmpty()) {
                    title = title + " " + addr;
                }
                buf.append(title+"~"+location);
            }
        }
        return buf.toString();
    }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      Matcher m = Util.REDIRECT_PATTERN.matcher(text.trim());
      if (title.startsWith("Source:")) {
         title = title.substring("Source:".length());
         if (m.lookingAt()) {
             String target = Util.cleanRedirTarget(m.group(1));
             if (target.startsWith("Source:")) {
                target = target.substring("Source:".length()).trim();
                redirectMap.put(title, target);
             }
         }
         else {
            String[] split = splitStructuredWikiText("source", text);
            String structuredData = split[0];
            if (!Util.isEmpty(structuredData)) {
               Element root = parseText(structuredData).getRootElement();

                // get author, source_title, subtitle, publisher, date_issued, place_issued, series_name, volumes, pages, references, sourceType, place, subject, fromYear, toYear, repository
                String author = getFields(root, "author");
                String sourceTitle = getField(root, "source_title");
                String subtitle = getField(root, "subtitle");
                String publisher = getField(root, "publisher");
                if (publisher.isEmpty()) {
                    publisher = getField(root, "publication_info");
                }
                String dateIssued = getField(root, "date_issued");
                String placeIssued = getField(root, "place_issued");
                String seriesName = getField(root, "series_name");
                String volumes = getField(root, "volumes");
                String pages = getField(root, "pages");
                String references = getField(root, "references");
                String sourceType = getField(root, "source_type");
                String place = getFields(root, "place");
                String subject = getFields(root, "subject");
                if (subject.isEmpty()) {
                    subject = getField(root, "source_category");
                }
                String fromYear = getField(root, "from_year");
                String toYear = getField(root, "to_year");
                String repository = getRepository(root);

                out.printf("%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                        pageId, title, author, sourceTitle, subtitle, publisher, dateIssued, placeIssued, seriesName, volumes, pages, references,
                        sourceType, place, subject, fromYear, toYear, repository);
            }
         }
      }
      else if (title.startsWith("Person:") || title.startsWith("Family:")) {
         if (!m.lookingAt()) {
            String[] split = splitStructuredWikiText(title.startsWith("Person:") ? "person" : "family", text);
            String structuredData = split[0];
            if (!Util.isEmpty(structuredData)) {
               Element root = parseText(structuredData).getRootElement();
               Elements eventFacts = root.getChildElements("source_citation");
               for (int i = 0; i < eventFacts.size(); i++) {
                  Element eventFact = eventFacts.get(i);
                  String source = eventFact.getAttributeValue("title");
                  if (source != null && source.startsWith("Source:")) {
                     source = source.substring("Source:".length()).trim();
                     int pos = source.indexOf('|');
                     if (pos >= 0) {
                        source = source.substring(0, pos);
                     }
                     if (source.length() > 0) {
                       if (linkedSourcesMap.containsKey(source)) {
                          linkedSourcesMap.put(source, linkedSourcesMap.get(source) + 1);
                       } else {
                          linkedSourcesMap.put(source, 1);
                       }
                     }
                  }
               }
            }
         }
      }
   }

    private String getFinalRedirectTarget(String title, int max) {
       int i = 0;
       while (redirectMap.containsKey(title)) {
          // avoid infinite redirect loops
          if (++i > max) {
             return null;
          }
          title = redirectMap.get(title);
       }
       return title;
    }


    private void addCountsToRedirectTargets() {
       for (String title : redirectMap.keySet()) {
          String target = getFinalRedirectTarget(title, 10);
          if (target != null) {
             Integer count = linkedSourcesMap.containsKey(title) ? linkedSourcesMap.get(title) : 0;
             linkedSourcesMap.put(target, (linkedSourcesMap.containsKey(target) ? linkedSourcesMap.get(target) : 0) + count);
          }
       }
    }

   // Generate list of sources
   // args array: 0=pages.xml 1=sources.tsv 2=source_counts.tsv
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      // write sources.tsv
      PrintWriter pw = new PrintWriter(args[1], "UTF-8");
      ExtractSources self = new ExtractSources(pw);
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.close();

      // write source_counts.tsv
      self.addCountsToRedirectTargets();
      pw = new PrintWriter(args[2], "UTF-8");
      for (String source : self.linkedSourcesMap.keySet()) {
          int count = self.linkedSourcesMap.get(source);
          pw.printf("%s\t%d\n", source, count);
      }
      pw.close();
   }
}
