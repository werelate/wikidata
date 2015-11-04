package org.werelate.scripts;

/**
 * User: dallan
 * Date: 10/23/15
 */

import org.werelate.utils.Util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateSourceMatches {

   static class GedcomSource {
      String gedcomId;
      String srcKey;
      String author;
      String title;
      String abbrev;

      GedcomSource(String gedcomId, String srcKey, String author, String title, String abbrev) {
         this.gedcomId = gedcomId;
         this.srcKey = srcKey;
         this.author = author;
         this.title = title;
         this.abbrev = abbrev;
      }
   }

   static class GedcomKeyTitle {
      String gedcomId;
      String srcKey;
      String title;

      GedcomKeyTitle(String gedcomId, String srcKey, String title) {
         this.gedcomId = gedcomId;
         this.srcKey = srcKey;
         this.title = title;
      }
   }

   static class GedcomUser {
      String gedcomId;
      String userId;

      GedcomUser(String gedcomId, String userId) {
         this.gedcomId = gedcomId;
         this.userId = userId;
      }
   }

   private static final String[][] ABBREVS = {
      {"Alabama", "AL"},
      {"Alaska", "AK"},
      {"Arizona", "AZ"},
      {"Arkansas", "AR"},
      {"California", "CA"},
      {"Colorado", "CO"},
      {"Connecticut", "CT"},
      {"Delaware", "DE"},
      {"District of Columbia", "DC", "D.C."},
      {"Florida", "FL"},
      {"Georgia", "GA"},
      {"Hawaii", "HI"},
      {"Idaho", "ID"},
      {"Illinois", "IL"},
      {"Indiana", "IN"},
      {"Iowa", "IA"},
      {"Kansas", "KS"},
      {"Kentucky", "KY"},
      {"Louisiana", "LA"},
      {"Maine", "ME"},
      {"Maryland", "MD"},
      {"Massachusetts", "MA"},
      {"Michigan", "MI"},
      {"Minnesota", "MN"},
      {"Mississippi", "MS"},
      {"Missouri", "MO"},
      {"Montana", "MT"},
      {"Nebraska", "NE"},
      {"Nevada", "NV"},
      {"New Hampshire", "NH", "N.H."},
      {"New Jersey", "NJ", "N.J."},
      {"New Mexico", "NM", "N.M."},
      {"New York", "NY", "N.Y."},
      {"North Carolina", "NC", "N.C."},
      {"North Dakota", "ND", "N.D."},
      {"Ohio", "OH"},
      {"Oklahoma", "OK"},
      {"Oregon", "OR"},
      {"Pennsylvania", "PA"},
      {"Rhode Island", "RI", "R.I."},
      {"South Carolina", "SC", "S.C."},
      {"South Dakota", "SD", "S.D."},
      {"Tennessee", "TN"},
      {"Texas", "TX"},
      {"Utah", "UT"},
      {"Vermont", "VT"},
      {"Virginia", "VA"},
      {"Washington", "WA"},
      {"West Virginia", "WV", "WVa", "W.V.", "W.Va."},
      {"Wisconsin", "WI"},
      {"Wyoming", "WY"},
      {"United States", "US", "U.S.", "U. S.", "USA", "U.S.A."},
      {"Township", "Twp"},
      {"County", "Co"},
           {""}
   };

   static class PatternReplacement {
      Pattern pattern;
      String replacement;
      PatternReplacement(Pattern pattern, String replacement) {
         this.pattern = pattern;
         this.replacement = replacement;
      }
   }
   private static final List<PatternReplacement> ABBREV_REPLACEMENTS = new ArrayList<PatternReplacement>();

   static {
      for (String[] abbrev : ABBREVS) {
         String replacement = abbrev[0];
         for (int j = 1; j < abbrev.length; j++) {
            String p = "\\b"+abbrev[j].replace(".", "\\.");
            if (!p.endsWith(".")) {
               p += "\\b";
            }
            Pattern pattern = Pattern.compile(p);
            ABBREV_REPLACEMENTS.add(new PatternReplacement(pattern, replacement));
         }
      }
   }

   private static final String[] CUT_WORDS = {
           "accessed",
           "http://search.ancestry.com/",
           "http://www.ancestry.com/search"
   };

   private static final String[] STOP_WORDS = {
           "church of jesus christ of latter day saints", "familysearch", "ancestry com",
           "a", "an", "and", "are", "as", "at", "be", "by", "et al", "for", "from", "has", "he",
           "in", "is", "it", "its", "of", "on", "or", "that", "the", "to", "was", "were", "will", "with",
           "comp", "compiler", "ed", "editor", "editor in charge", "editor in chief",
           "com", "http", "https", "org", "url", "web", "website", "www",
           "county", "township",
           "available", "ca", "database", "digital", "i0", "images", "inc", "online"   // ca=circa
   };
   private static final Pattern STOP_WORDS_PATTERN;

   static {
      StringBuffer buf = new StringBuffer();
      for (String word : STOP_WORDS) {
         if (buf.length() > 0) {
            buf.append("|");
         }
         buf.append(word);
      }
      STOP_WORDS_PATTERN = Pattern.compile("\\b("+buf.toString()+")\\b");
   }

   static String clean(String s) {
      // convert Abbrevs
      for (PatternReplacement pr : ABBREV_REPLACEMENTS) {
         s = pr.pattern.matcher(s).replaceAll(pr.replacement);
      }

      // cut after cut-words
      for (String cut : CUT_WORDS) {
         int pos = s.lastIndexOf(cut);
         if (pos > 0) {
            s = s.substring(0, pos);
         }
      }

      // romanize
      s = Util.romanize(s);
      // remove all single-letters except a A I
      s = s.replaceAll("\\b[b-zB-HJ-Z]\\b", "");
      // lowercase
      s = s.toLowerCase();
      // remove 's convert all non-alpha-numeric to space; convert mutliple spaces to a single space
      s = s.replaceAll("'", "").replaceAll("[^a-z0-9]", " ").replaceAll("\\s+", " ");
      // handle find a grave
      s = s.replaceAll("\\bfind a grave\\b", "findagrave");
      // remove stopwords
      s = STOP_WORDS_PATTERN.matcher(s).replaceAll("");
      // remove all spaces
      s = s.replaceAll("\\s+", "");

      return s;
   }

   static String truncate(String s, int len) {
      if (s.length() < len) {
         return s;
      }
      return s.substring(0, len);
   }

   static String getGedcomSourceKey(String gedcomId, String srcKey) {
      return gedcomId + "|" + srcKey;
   }

   static void write(PrintWriter out, String userId, String source, String sourceType, String title) {
      out.printf("%s\t%s\t%s\t%s\n", userId, source, sourceType, title);
   }

   // 0=gedcom_sources.tsv 1=gedcom_key_title.tsv 2=gedcom_user.tsv 3=gedcom_source_matches.tsv (out)
   public static void main(String[] args) throws IOException {

      // gedcom_sources: gedcom_id, src_key, author, title, abbrev
      System.out.println("Read gedcom_sources");
      HashMap<String,GedcomSource> gedcomSources = new HashMap<String,GedcomSource>();
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\t");
         if (fields.length > 3) {
            GedcomSource gs = new GedcomSource(fields[0], fields[1], fields[2], fields[3], fields.length > 4 ? fields[4] : "");
            gedcomSources.put(getGedcomSourceKey(gs.gedcomId, gs.srcKey), gs);
         }
         else {
            System.out.println("gedcom_sources invalid line = "+line);
         }
      }
      in.close();

      // gedcom_user: gedcom_id, user_id
      System.out.println("Read gedcom_user");
      HashMap<String,GedcomUser> gedcomUsers = new HashMap<String,GedcomUser>();
      in = new BufferedReader(new FileReader(args[2]));
      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\t");
         if (fields.length > 1) {
            GedcomUser gu = new GedcomUser(fields[0], fields[1]);
            gedcomUsers.put(gu.gedcomId, gu);
         }
         else {
            System.out.println("gedcom_user invalid line = "+line);
         }
      }
      in.close();

      // write user_id, source, source_type (AT, AA, T, A), title
      PrintWriter out = new PrintWriter(new FileWriter(args[3]));

      // gedcom_key_title: gedcom_id, src_key, page_title
      System.out.println("Read gedcom_key_title");
      in = new BufferedReader(new FileReader(args[1]));
      while (in.ready()) {
         String[] fields = in.readLine().split("\t");
         GedcomKeyTitle gkt = new GedcomKeyTitle(fields[0], fields[1], fields[2]);

         // get user_id
         String userId = "0";
         GedcomUser gu = gedcomUsers.get(gkt.gedcomId);
         if (gu != null) {
            userId = gu.userId;
         }

         // get gedcomSource
         GedcomSource gs = gedcomSources.get(getGedcomSourceKey(gkt.gedcomId, gkt.srcKey));
         if (gs == null) {
            System.out.println("Gedcom source not found "+ gkt.gedcomId +" " + gkt.srcKey);
            continue;
         }

         String author = clean(gs.author);
         String title = clean(gs.title);
         String abbrev = clean(gs.abbrev);
         String pageTitle = gkt.title.replace(' ', '_');

         if (author.length() > 0) {
            if (title.length() > 0) {
               write(out, userId, truncate(author + title, 255), "AT", pageTitle);
            }
            if (abbrev.length() > 0) {
               write(out, userId, truncate(author + abbrev, 255), "AA", pageTitle);
            }
         }
         if (title.length() > 0) {
            write(out, userId, truncate(title, 255), "T", pageTitle);
         }
         if (abbrev.length() > 0) {
            write(out, userId, truncate(abbrev, 255), "A", pageTitle);
         }
      }
      in.close();

      out.close();
   }
}
