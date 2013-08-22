package org.werelate.scripts;

import nu.xom.*;
import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GenerateSitemap extends StructuredDataParser {
   private static final int MAX_URLS = 25000;
   private static final String WERELATE_ORG = "www.werelate.org";
   private static final String PATH_PREFIX = "/wiki/";
   private static final String SITEMAP_PREFIX = "/sitemap/";
   private static final String XMLNS = "http://www.sitemaps.org/schemas/sitemap/0.9";

   private static final String[] INDEXED_NAMESPACES_ARRAY = {
           "Person", "Portal", // article
           "Family", "Transcript",
           "Source", "Repository", "Surname" };
   private static final Set<String> INDEXED_NAMESPACES = new HashSet<String>(Arrays.asList(INDEXED_NAMESPACES_ARRAY));

   private File outputDir;
   private List<String> titles;
   private int numSitemaps = 0;

   public GenerateSitemap(String outputDir) {
      this.outputDir = new File(outputDir);
      this.titles = new ArrayList<String>(MAX_URLS);
   }

   private void writeSitemap() {
      // write titles
      Element urlset = new Element("urlset", XMLNS);
      for (String titleTimestamp : titles) {
         String[] fields = titleTimestamp.split("\\|");
         if (fields.length != 3) {
            logger.warn("Invalid titleTimestamp="+titleTimestamp);
            continue;
         }
         String title = fields[0];
         String timestamp = fields[1];
         int length = Integer.parseInt(fields[2]);
         try {
            Element url = new Element("url", XMLNS);
            Element loc = new Element("loc", XMLNS);
            URI uri = new URI("http", WERELATE_ORG, PATH_PREFIX + title.replace(' ','_'), null);
            loc.appendChild(uri.toASCIIString());
            url.appendChild(loc);
            Element lastmod = new Element("lastmod", XMLNS);
            lastmod.appendChild(timestamp);
            url.appendChild(lastmod);
            Element changefreq = new Element("changefreq", XMLNS);
            changefreq.appendChild(title.equals("Main Page") || title.startsWith("Portal:") ? "weekly" : "yearly");
            url.appendChild(changefreq);
            Element priority = new Element("priority", XMLNS);
            String[] namespaceTitle = Util.splitNamespaceTitle(title);
            String ns = namespaceTitle[0];
            double level = 0.1;
            double lenBoost = (double)length / 10000.0;
            if (title.equals("Main Page") || ns.equals("Portal")) {
               level = 1.0;
            }
            else if (ns.length() == 0 || ns.equals("Person")) {
               level = Math.min(0.5 + lenBoost, 0.9);
            }
            else if (ns.equals("Family") || ns.equals("Transcript")) {
               level = Math.min(0.3 + lenBoost, 0.7);
            }
            else { // source, repo, surname
               level = Math.min(0.1 + lenBoost, 0.5);
            }
            priority.appendChild(Double.toString(Math.round(level*100)/100.0));
            url.appendChild(priority);
            urlset.appendChild(url);
         } catch (URISyntaxException e) {
            logger.warn("Invalid title="+title);
         }
      }

      Document doc = new Document(urlset);

      File file = null;
      try {
         file = new File(outputDir,"map"+numSitemaps+".xml");
         Serializer serializer = new Serializer(new FileOutputStream(file), "UTF-8");
         serializer.write(doc);
      } catch (IOException e) {
         logger.warn("IO exception writing "+file);
      }
      numSitemaps++;
   }

   private void writeSitemapIndex() {
      if (titles.size() > 0) {
         writeSitemap();
      }
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
      String timestamp = format.format(new Date()).replaceFirst("(\\d\\d)(\\d\\d)$", "$1:$2");

      Element sitemapindex = new Element("sitemapindex", XMLNS);
      for (int i = 0; i < numSitemaps; i++) {
         try {
            Element sitemap = new Element("sitemap", XMLNS);
            Element loc = new Element("loc", XMLNS);
            URI uri = new URI("http", WERELATE_ORG, SITEMAP_PREFIX+"map"+i+".xml.gz", null);
            loc.appendChild(uri.toASCIIString());
            sitemap.appendChild(loc);
            Element lastmod = new Element("lastmod", XMLNS);
            lastmod.appendChild(timestamp);
            sitemap.appendChild(lastmod);
            sitemapindex.appendChild(sitemap);
         } catch (URISyntaxException e) {
            logger.warn("Error writing sitemap file="+e.getMessage());
         }
      }

      Document doc = new Document(sitemapindex);

      File file = null;
      try {
         file = new File(outputDir,"index.xml");
         Serializer serializer = new Serializer(new FileOutputStream(file), "UTF-8");
         serializer.write(doc);
      } catch (IOException e) {
         logger.warn("IO exception writing "+file);
      }
   }

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException
   {
      String[] namespaceTitle = Util.splitNamespaceTitle(title);
      if ((namespaceTitle[0].length() == 0 || INDEXED_NAMESPACES.contains(namespaceTitle[0])) &&
          text.indexOf("{{wikipedia-notice") < 0) { // google doesn't like copied content
         titles.add(title+"|"+timestamp+"|"+text.length());
         if (titles.size() == MAX_URLS) {
            writeSitemap();
            titles.clear();
         }
      }
   }

   // args[0] = pages.xml
   // args[1] = sitemap directory
   public static void main(String[] args) throws IOException, ParsingException
   {
      WikiReader wikiReader = new WikiReader();
      wikiReader.setSkipRedirects(true);
      GenerateSitemap self = new GenerateSitemap(args[1]);
      wikiReader.addWikiPageParser(self);
      InputStream in = new FileInputStream(args[0]);
      wikiReader.read(in);
      in.close();
      self.writeSitemapIndex();
   }
}
