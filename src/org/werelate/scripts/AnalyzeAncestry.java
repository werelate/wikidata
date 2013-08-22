package org.werelate.scripts;

import org.werelate.utils.CountsCollector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class AnalyzeAncestry
{
   private static final Pattern FAMILY_PATTERN = Pattern.compile("^([^-]+?) family\\.?$", Pattern.CASE_INSENSITIVE);
   private static final Pattern COUNTY_STATE_PATTERN = Pattern.compile("^(.+?) \\(([A-Za-z:. ]+?)\\)$");
   private static final String[] US_STATES_ARRAY =
   {"Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "District of Columbia",
    "Florida", "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine",
    "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada",
    "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio", "Oklahoma",
    "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas", "Utah", "Vermont",
    "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming"
   };
   private static final Set<String> US_STATES = new HashSet<String>();
   static {
      for (String place : US_STATES_ARRAY) US_STATES.add(place);
   }

   private static final String[] COUNTRIES_ARRAY =
   {"Great Britain", "France", "Germany", "Italy", "Isle of Man", "Mexico", "Netherlands", "Philippines", "Sweden",
    "England", "Canada", "Ireland", "Scotland", "Wales", "Spain",
   };
   private static final Set<String> COUNTRIES = new HashSet<String>();
   static {
      for (String place : COUNTRIES_ARRAY) COUNTRIES.add(place);
   }

   private static final String[] NON_PLACES_ARRAY =
   {"Genealogy", "Social life and customs", "Colonies", "History", "Description and travel", "Geography",
    "Emigration and immigration", "Biography", "Politics and government", "Prairie Provinces", "Religion",
    "Church history",
   };
   private static final Set<String> NON_PLACES = new HashSet<String>();
   static {
      for (String place : NON_PLACES_ARRAY) NON_PLACES.add(place);
   }

   private static final Map<String,String> OTHER_PLACES = new HashMap<String,String>();
   static {
      OTHER_PLACES.put("United States", "United States");
      OTHER_PLACES.put("Washington, D.C.", "District of Columbia, United Sates");
      OTHER_PLACES.put("America", "United States");
      OTHER_PLACES.put("British Columbia", "British Columbia, Canada");
      OTHER_PLACES.put("Manitoba", "Manitoba, Canada");
      OTHER_PLACES.put("New Brunswick", "New Brunswick, Canada");
      OTHER_PLACES.put("Newfoundland", "Newfoundland, Canada");
      OTHER_PLACES.put("Nova Scotia", "Nova Scotia, Canada");
      OTHER_PLACES.put("Ontario", "Ontario, Canada");
      OTHER_PLACES.put("Prince Edward Island", "Prince Edward Island, Canada");
      OTHER_PLACES.put("Québec", "Québec, Canada");
      OTHER_PLACES.put("Quebec", "Québec, Canada");
      OTHER_PLACES.put("Saint Pierre and Miquelon", "Saint Pierre and Miquelon, Canada");
      OTHER_PLACES.put("Yukon Territory", "Yukon Territory, Canada");
      OTHER_PLACES.put("Confederate States of America", "United States");
   }

   public static String getFamilySubject(String subject) {
      Matcher m = FAMILY_PATTERN.matcher(subject);
      if (m.find()) {
         return m.group(1);
      }
      return null;
   }

   public static String getPlaceSubject(String subject) {
      String[] fields = subject.split("\\s+--\\s+");
      for (int i = 0; i < fields.length; i++) {
         if (fields[i].endsWith(".")) fields[i] = fields[i].substring(0, fields[i].length()-1);
         if (fields[i].equals("New York (State)")) fields[i] = "New York";
      }
      for (int i = 0; i < fields.length; i++) {
         if (US_STATES.contains(fields[i])) {
            if (fields.length >= i+2 && (fields[i+1].endsWith(" County") || fields[i+1].endsWith(" Parish"))) {
               if (fields.length >= i+3) {
                  return fields[i+2]+", "+fields[i+1].substring(0, fields[i+1].length()-" County".length())+", "+fields[i]+", United States";
//                  return fields[i]+", "+fields[i+1].substring(0, fields[i+1].length()-" County".length())+", "+fields[i+2];
               }
               else {
                  return fields[i+1].substring(0, fields[i+1].length()-" County".length())+", "+fields[i]+", United States";
//                  return fields[i]+", "+fields[i+1].substring(0, fields[i+1].length()-" County".length());
               }
            }
            else {
               return fields[i];
            }
         }
         else if (COUNTRIES.contains(fields[i])) {
            if (fields.length >= i+2 && !NON_PLACES.contains(fields[i+1])) {
               if (fields.length >= i+3) {
                  return fields[i+2]+", "+fields[i+1]+", "+fields[i];
//                  return fields[i]+", "+fields[i+1]+", "+fields[i+2];
               }
               else {
                  return fields[i+1]+", "+fields[i];
//                  return fields[i]+", "+fields[i+1];
               }
            }
            else {
               return fields[i];
            }
         }
         String otherPlace = OTHER_PLACES.get(fields[i]);
         if (otherPlace != null) {
            return otherPlace;
         }
         Matcher m = COUNTY_STATE_PATTERN.matcher(fields[i]);
         if (m.find()) {
            String county = m.group(1).trim();
            String state = m.group(2).trim();
            if (state.indexOf(':') >= 0) state = state.substring(0, state.indexOf(':')).trim();
            if (NON_PLACES.contains(county) || county.indexOf("Church") >= 0) {
               // skip it; it doesn't cover the entire state and hopefully another subject will get it
            }
            else {
               return county+", "+state;
//               return state+", "+county;
            }
         }
      }
      return null;
   }

   private static Map<String,String> CATEGORY_MAP = new HashMap<String,String>();
   static {
      CATEGORY_MAP.put("History", "History");
      CATEGORY_MAP.put("Church", "Church records");
      CATEGORY_MAP.put("Biography", "Biography");
      CATEGORY_MAP.put("War", "Military records");
      CATEGORY_MAP.put("1861-1865", "Military records");
      CATEGORY_MAP.put("Army", "Military records");
      CATEGORY_MAP.put("Revolution", "Military records");
      CATEGORY_MAP.put("Epitaphs", "Cemetery records");
      CATEGORY_MAP.put("Cemeteries", "Cemetery records");
      CATEGORY_MAP.put("Presbyterian", "Church records");
      CATEGORY_MAP.put("Catholic", "Church records");
      CATEGORY_MAP.put("Methodist", "Church records");
      CATEGORY_MAP.put("Congregational", "Church records");
      CATEGORY_MAP.put("Directories", "Directory records");
      CATEGORY_MAP.put(" births", "Vital records");
      CATEGORY_MAP.put(" marriages", "Vital records");
      CATEGORY_MAP.put(" deaths", "Vital records");
   };

   public static String getCategory(String subject) {
      for (String catWord : CATEGORY_MAP.keySet()) {
         if (subject.contains(catWord+" ") || subject.contains(catWord+".")) {
            return CATEGORY_MAP.get(catWord);
         }
      }
      return null;
   }

   // 0=ancestry_data 1=place_subject_out 2=family_subject_out 3=subject_words_out
   public static void main(String[] args)
           throws IOException
   {
      BufferedReader in = new BufferedReader(new FileReader(args[0]));
      PrintWriter placeSubjectOut = new PrintWriter(args[1]);
      PrintWriter familySubjectOut = new PrintWriter(args[2]);
      PrintWriter subjectWordsOut = new PrintWriter(args[3]);
      CountsCollector ccPlaceSubject = new CountsCollector();
      CountsCollector ccFamilySubject = new CountsCollector();
      CountsCollector ccSubjectWords = new CountsCollector();

      while (in.ready()) {
         String line = in.readLine();
         String[] fields = line.split("\\|");
         if (fields.length < 5 || fields.length > 6) {
            System.out.println("malformed line="+line+" num fields="+fields.length);
         }
         else {
            String subjectText = (fields.length == 6 ? fields[5] : "");
            if (subjectText.length() > 0) {
               String[] subjects = subjectText.split("\\~");
               for (String subject : subjects) {
                  String family = getFamilySubject(subject);
                  if (family != null) {
//                     ccFamilySubject.add(family);
                  }
                  else {
                     String place = getPlaceSubject(subject);
                     if (place != null) {
                        ccPlaceSubject.add(place);
                     }
                  }
                  String category = getCategory(subject);
//                  if (category != null) {
//                     ccSubjectWords.add(subject);
//                  }
               }
            }
         }
      }

//      ccPlaceSubject.writeSorted(true, 0, placeSubjectOut);
//      ccFamilySubject.writeSorted(false, 1, familySubjectOut);
//      ccSubjectWords.writeSorted(false, 10, subjectWordsOut);

      for (String place : ccPlaceSubject.getKeys()) {
         placeSubjectOut.println(place);
      }

      in.close();
      placeSubjectOut.close();
      familySubjectOut.close();
      subjectWordsOut.close();
   }
}
