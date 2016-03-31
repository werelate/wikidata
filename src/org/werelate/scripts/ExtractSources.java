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

    PrintWriter out;

    public ExtractSources(PrintWriter out) {
        this.out = out;
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
            if (!title.isEmpty()) {
                if (buf.length() > 0) {
                    buf.append("|");
                }
                buf.append(title+"~"+location);
            }
        }
        if (buf.length() == 0) {
            String title = clean(getField(root, "repository_name"), true);
            if (!title.isEmpty()) {
                String addr = clean(getField(root, "repository_addr"), true);
                if (!addr.isEmpty()) {
                    title = title + " " + addr;
                }
                String location = clean(getField(root, "url"), true);
                if (!location.isEmpty()) {
                    location = clean(getField(root, "call_number"), true);
                }
                buf.append(title+"~"+location);
            }
        }
        return buf.toString();
    }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      if (title.startsWith("Source:")) {
         title = title.substring("Source:".length());
         Matcher m = Util.REDIRECT_PATTERN.matcher(text.trim());
         if (!m.lookingAt()) {
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
   }


   // Generate list of sources
   // args array: 0=pages.xml 1=sources.tsv
   public static void main(String[] args)
           throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      ExtractSources self = new ExtractSources(new PrintWriter(args[1]));
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.close();
   }
}
