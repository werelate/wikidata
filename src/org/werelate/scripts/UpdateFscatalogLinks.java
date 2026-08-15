package org.werelate.scripts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.werelate.editor.PageEditor;
import org.werelate.utils.Util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Replace broken FamilySearch FHLC catalog links on Source pages with the {{fscatalog|N}} template.
 *
 * FamilySearch retired the old catalog URL scheme, e.g.:
 *   http://www.familysearch.org/Eng/Library/fhlcatalog/supermainframeset.asp?display=titledetails&titleno=######
 * Each such URL is replaced (in place) with:
 *   {{fscatalog|######}}
 * where ###### is the value of the URL's titleno parameter.
 *
 * A broken link can appear in two places on a Source page:
 *   1. the repository table, in a repository's source_location attribute, and
 *   2. the free-text body of the page.
 * Rather than round-tripping the structured edit form field-by-field (which risks dropping
 * fields that PageEditor cannot faithfully reproduce, such as multi-valued subjects or the
 * volumes field), we edit the page in raw-XML mode. Passing xml=1 to the editor tells the
 * structured-namespace code (StructuredData::renderEditFields / importEditData) to skip
 * splitting the page into edit fields and skip reconstructing it on save, so wpTextbox1 holds
 * the complete raw wikitext -- repository XML and body together. A single regex pass over that
 * text fixes both places at once and preserves everything else verbatim.
 */
public class UpdateFscatalogLinks {
   private static Logger logger = LogManager.getLogger("org.werelate.scripts");

   // Matches a broken FamilySearch FHLC catalog URL. Case-insensitive on scheme/host/path, and
   // consumes the whole query string up to the first whitespace or wikitext/HTML delimiter.
   // PageEditor.readVariable() HTML-unescapes the page text once before we see it, so an
   // ampersand written in the page body arrives as a plain '&', while an ampersand stored inside
   // the repository XML attribute arrives as the literal string "&amp;". Both are made of
   // characters this class accepts, so either form is matched.
   private static final Pattern FHLC_URL = Pattern.compile(
      "https?://(?:www\\.)?familysearch\\.org/eng/library/fhlcatalog/supermainframeset\\.asp\\?[^\\s\\[\\]{}|<>\"']*",
      Pattern.CASE_INSENSITIVE);

   // Extracts the catalog number from a matched URL, regardless of parameter order.
   private static final Pattern TITLENO = Pattern.compile("titleno=(\\d+)", Pattern.CASE_INSENSITIVE);

   private PageEditor editor;

   public UpdateFscatalogLinks(String host, String password) {
      editor = new PageEditor(host, password);
   }

   /**
    * Replace every broken FHLC catalog URL in the given text with {{fscatalog|<titleno>}}.
    * Returns the transformed text, or the original text unchanged when there are no matching
    * URLs (or a matched URL has no titleno parameter, in which case it is left alone and logged).
    */
   static String replaceLinks(String text) {
      if (text == null) {
         return null;
      }
      Matcher m = FHLC_URL.matcher(text);
      StringBuffer sb = new StringBuffer();
      while (m.find()) {
         String url = m.group();
         Matcher t = TITLENO.matcher(url);
         if (t.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("{{fscatalog|" + t.group(1) + "}}"));
         }
         else {
            // No catalog number to build a template from; leave the URL as-is.
            logger.warn("FHLC URL without titleno, leaving unchanged: " + url);
            m.appendReplacement(sb, Matcher.quoteReplacement(url));
         }
      }
      m.appendTail(sb);
      return sb.toString();
   }

   public void updatePage(String sourceTitle) {
      // Fetch the edit form in raw-XML mode so wpTextbox1 holds the complete raw wikitext.
      editor.doGet(sourceTitle, true, "xml=1");
      String text = editor.readVariable(PageEditor.TEXTBOX1_PATTERN, false);
      if (text == null) {
         logger.warn("No edit text found (skipping): " + sourceTitle);
         return;
      }
      String updated = replaceLinks(text);
      if (updated.equals(text)) {
         logger.info("No change: " + sourceTitle);
         return;
      }
      editor.setPostVariable("xml", "1"); // stay in raw-XML mode on save (no field reconstruction)
      editor.setPostVariable("wpTextbox1", updated);
      editor.setPostVariable("wpSummary", "replace broken FamilySearch catalog links with {{fscatalog}} template");
      editor.setPostVariable("wpMinoredit", "1");
      editor.doPost();
      logger.info("Updated: " + sourceTitle);
   }

   // 0=source_titles.txt (one Source page title per line, with or without the "Source:" prefix)
   // 1=host  2=agent password
   public static void main(String[] args) throws IOException {
      if (args.length < 3) {
         System.out.println("Usage: UpdateFscatalogLinks <source_titles.txt> <host> <password>");
         return;
      }
      UpdateFscatalogLinks updater = new UpdateFscatalogLinks(args[1], args[2]);
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      try {
         while (in.ready()) {
            String line = in.readLine();
            if (line == null) {
               break;
            }
            String title = line.trim();
            if (Util.isEmpty(title)) {
               continue;
            }
            if (!title.startsWith("Source:")) {
               title = "Source:" + title;
            }
            try {
               updater.updatePage(title);
            }
            catch (RuntimeException e) {
               logger.error("Failed: " + title + " -> " + e);
            }
         }
      }
      finally {
         in.close();
      }
   }
}
