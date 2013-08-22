package org.werelate.scripts;

import org.werelate.utils.CountsCollector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

public class AnalyzeAuthors
{
   private static final Pattern AUTHOR_PATTERN = Pattern.compile("^((a|al|d|da|du|de|di|do|dal|der|del|den|dem|des|dell|dela|dalla|della|delle|dello|delos|el|fitz|l|le|la|lo|los|m|mac|mc|o|san|saint|st|ste|st\\.|ste\\.|t|te|ten|ter|van|ver|von|vom|vor|vander) )*[^ ]+,", Pattern.CASE_INSENSITIVE); //|société|society|societies|association|daughters");
   private static final Pattern PLACE_PATTERN = Pattern.compile("^([^,. &']+ ){1,2}\\([^,\\)]+\\)(\\.|$)");
   private static final String[] PLACES =
   {"Alabama.", "Alaska.", "Arizona.", "Arkansas.", "California.", "Colorado.", "Connecticut.", "Delaware.", "District of Columbia.",
    "Florida.", "Georgia.", "Hawaii.", "Idaho.", "Illinois.", "Indiana.", "Iowa.", "Kansas.", "Kentucky.", "Louisiana.", "Maine.",
    "Maryland.", "Massachusetts.", "Michigan.", "Minnesota.", "Mississippi.", "Missouri.", "Montana.", "Nebraska.", "Nevada.",
    "New Hampshire.", "New Jersey.", "New Mexico.", "New York.", "North Carolina.", "North Dakota.", "Ohio.", "Oklahoma.",
    "Oregon.", "Pennsylvania.", "Rhode Island.", "South Carolina.", "South Dakota.", "Tennessee.", "Texas.", "Utah.", "Vermont.",
    "Virginia.", "Washington.", "West Virginia.", "Wisconsin.", "Wyoming.",
    "United States.", "Great Britain.", "España.", "Danmark.", "Norge.", "Sicilia.", "Japan.", "Deutschland.", "France.", "Jamaica.",
    "New South Wales.", "Iglesia Católica.", "Ceylon.", "Sverige.", "Idrija.", "New South Wales.", "Íslandi.", "Filipinas.", "Suomi.",
    "Belgique.", "Tasmania.", "Nederland.", "Ontario.", "Cuba.", "New Brunswick.", "Armenia.", "Colombia.", "Confederate States of America.",
    "México.", "Österreich.", "Православная церковь.", "Православная консистория.", "Советский Союз.",
    "Ecuador.", "Gelre.", "Bergen op Zoom (Noord-Brabant).", "Rio Grande do Sul (Brasil).", "Brasil.",
   };
   private static final Pattern NON_AUTHOR_PATTERN = Pattern.compile("\\b("+ 
   "Census|Office|Church|Notary|Dirección|Notaría|Department|Records|Court|Notariaat|National archives|"+ 
    "Département|Administration|Survey|Notaría|Censo|Notariat|Préfecture|Judiciaire|Notariat|"+
    "Département|County|Council|Library of Congress|Amtsgericht|Königliche|Preußische|Regierung|Governador|"+ 
    "Konsulat|Clerk|Catholic|Civil|Bezirksamt|Gobierno|Lunds stift|Gemeentearchief|Innere Verwaltung|"+ 
    "Gemeentearchief|Commissioners|Secretariat|Justitieele|Registrar|Distriktsamt|Stadtrath|Stadtarchiv|Rijksarchief|"+ 
    "Landsarkivet|Archives|Archive|Library|Archivo|Registreringssentral|Arkhiv|Landsarkivet|Catholique|"+
    "Consulado|Vital Statistics|Gemeentelijke|Archiefdienst|Arquivo|Stadtrat|Ministerio|Guberniya|Evangelische|Kirche|"+ 
    "Evangelisches|Kirchenbuchamt|Standesamt|Kerk|Borough|Orthodox Consistory|Museum|Conseil|Municipal|"+
    "Stadtamt|Parish|Statisztikai|Hivatal|Landgericht|Católica|Baptist|Cemetery|Evangélikus|Rzymsko-katolicki|"+ 
    "kyrkan|Chapel|Folkekirke|Cattolica|Katólikus|Iglesia|Católica|Kirkko|Katholieke|Parrocchia|"+ 
    "Rimokatolička|Református|Methodist|National|Jüdische|Wesleyan|Katolícka|Katolícka|"+ 
    "Katolicki|Réformée|Kirke|Kirkinn|Calvinistic|Evangélikus|Protestan|Baptist|"+ 
    "Wesleyan|Batista|Evanjelická|Kirik|Evangeelne|Jewish|Congregation|Presbyterian|Evangélica|"+
    "Luterana|Reformovaná|Lutheran|Congregation|Katolik|Ewangelicki|Episcopal"+
    ")\\b", Pattern.CASE_INSENSITIVE);

   public static boolean isGovernmentChurchAuthor(String line) {
      for (String place : PLACES) {
         if (line.startsWith(place)) {
            return true;
         }
      }
      Matcher m = NON_AUTHOR_PATTERN.matcher(line);
      if (m.find()) return true;

      m = PLACE_PATTERN.matcher(line);
      if (m.find()) return true;

      return false;
   }

   public static boolean isHumanOrganizationAuthor(String line) {
      Matcher m = AUTHOR_PATTERN.matcher(line);
      return (m.find() || line.equals("Anonymous") || line.equals("Kratz Indexing (Salt Lake City)") ||
              line.indexOf("(Firm)") >= 0 || line.indexOf("Daughters") >= 0  ||
              ((line.indexOf("Genealogical") >= 0 || line.indexOf("Society") >= 0) &&
               !(line.indexOf("Church of Jesus Christ of Latter-day Saints") >= 0 || line.indexOf("Genealogical Society of Utah") >= 0)));
   }

   // 0=authors, 1=non-author-phrases 2=cutoff 3=authors-out 4=non-authors-out 5=unknown-authors-out 6=unknown-words-out
   public static void main(String[] args)
           throws IOException
   {
      CountsCollector ccAuthors = new CountsCollector();
      CountsCollector ccNonAuthors = new CountsCollector();
      CountsCollector ccUnknown = new CountsCollector();
      CountsCollector ccUnknownWords = new CountsCollector();

      Set<String> nonAuthorPhrases = new HashSet<String>();
      BufferedReader napReader = new BufferedReader(new FileReader(args[1]));
      while (napReader.ready()) {
         nonAuthorPhrases.add(napReader.readLine().toLowerCase());
      }
      napReader.close();

      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      while (in.ready()) {
         boolean foundAuthor = false;
         boolean foundNonAuthor = false;
         String line = in.readLine().trim();
         if (isHumanOrganizationAuthor(line)) {
            foundAuthor = true;
         }
         else if (isGovernmentChurchAuthor(line)) {
            foundNonAuthor = true;
         }
         if (foundAuthor) {
            ccAuthors.add(line);
         }
         else if (foundNonAuthor) {
            ccNonAuthors.add(line);
         }
         else {
            ccUnknown.add(line);
            String[] words = line.split("\\s+");
            for (String word : words) {
               ccUnknownWords.add(word);
            }
         }
      }
      in.close();

      int cutoff = Integer.parseInt(args[2]);
      ccAuthors.writeSorted(false, cutoff, new PrintWriter(args[3]));
      ccNonAuthors.writeSorted(false, cutoff, new PrintWriter(args[4]));
      ccUnknown.writeSorted(false, cutoff, new PrintWriter(args[5]));
      ccUnknownWords.writeSorted(false, cutoff, new PrintWriter(args[6]));
   }
}
