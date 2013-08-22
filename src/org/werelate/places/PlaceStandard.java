package org.werelate.places;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;
import org.werelate.utils.Util;

public class PlaceStandard {
   private static final Logger logger = Logger.getLogger(PlaceStandard.class);
   private static final int MAX_REDIRECTS = 10;

   // if you add a new type, you must add it to WikiPage, PlaceStandard and StandardMerger
   private static final Pattern TYPE_WORDS = Pattern.compile("\\b(?:" +
           "arrondissement|borough|canton|census area|city|council|county|department|département|diocese|diocèse|district|" +
           "krai|metropolitan|municipal|municipality|oblast|prefecture|province|region|région|regional|regione|republic|state|territoire|voivodship" +
           ")\\b");
   private static final Pattern[] ABBREV_PATTERNS = {
      Pattern.compile("\\s(?:of|&)\\s"),    // noise words to remove
      Pattern.compile("\\bste?\\.?\\s"),
      Pattern.compile("\\bsainte\\s"),
   };
   private static final String[] ABBREV_WORDS = {   // correspond to ABBREV_PATTERNS
      " ",
      "saint",
      "saint",
   };
   private static final int MAX_DEPTH = 6;

   private boolean indexNames;
   private Map<String,Place> titleToPlace;      // need to romanize because I messed up when writing the fhlc.xml files and romanized the titles
                                                //todo but do I still need to romanize?
   private Map prefNameToPlaces;
   private Map variantNameToPlaces;
   private Map<String,String> redirect;

   private static String standardize(String name) {
      name = name.toLowerCase().replace('-', ' ');

      // remove type words
      Matcher m = TYPE_WORDS.matcher(name);
      String shortName = m.replaceAll(" ");

      // remove noise words and expand abbreviations
      for (int i = 0; i < ABBREV_PATTERNS.length; i++) {
         m = ABBREV_PATTERNS[i].matcher(shortName);
         shortName = m.replaceAll(ABBREV_WORDS[i]);
      }

      // set name to original name if there is nothing left
      shortName = shortName.trim();
      if (shortName.length() > 0) {
         name = shortName;
      }

      // romanize the name
      name = Util.romanize(name);

      // remove all spacing
      return name.replaceAll("\\s+","");
   }

   public static String standardizeTitle(String title) {
      return Util.romanize(title.replace('_', ' '));
   }

   public PlaceStandard() {
      this(true);
   }

   public PlaceStandard(boolean indexNames) {
      init(indexNames);
   }

   private void init(boolean indexNames) {
      this.indexNames = indexNames;
      titleToPlace = new HashMap<String,Place>();
      redirect = new HashMap<String,String>();
      if (indexNames) {
         prefNameToPlaces = new HashMap();
         variantNameToPlaces = new HashMap();
      }
      else {
         prefNameToPlaces = null;
         variantNameToPlaces = null;
      }
   }

   public void clear() {
      Iterator pi = getPlaces().iterator();
      while (pi.hasNext()) {
         Place p = (Place)pi.next();
         p.setStandard(null);
      }
      init(this.indexNames);
   }

   public boolean addPlace(Place place) {
      if (titleToPlace.get(Util.romanize(place.getTitle())) != null) {
         logger.warn("Place already exists - not adding: " + place.getTitle());
         return false;
      }
      updateTitleOfPlace(place, null, place.getTitle());
      updatePreferredNameOfPlace(place, null, place.getPreferredName());
      Iterator i = place.getVariantNames().iterator();
      while (i.hasNext()) {
         Place.VariantName variantName = (Place.VariantName)i.next();
         addVariantNameToPlace(place, variantName.getName());
      }
      place.setStandard(this);
      return true;
   }

   public void addRedirect(String oldTitle, String newTitle) {
      redirect.put(oldTitle, newTitle);
   }

   public void removePlace(Place place) {
      updateTitleOfPlace(place, place.getTitle(), null);
      updatePreferredNameOfPlace(place, place.getPreferredName(), null);
      Iterator i = place.getVariantNames().iterator();
      while (i.hasNext()) {
         Place.VariantName variantName = (Place.VariantName)i.next();
         removeVariantNameFromPlace(place, variantName.getName());
      }
      place.setStandard(null);
   }

   void updateTitleOfPlace(Place place, String oldTitle, String newTitle) {
      if (oldTitle != null) {
         titleToPlace.remove(standardizeTitle(oldTitle));
      }
      if (newTitle != null) {
         titleToPlace.put(standardizeTitle(newTitle), place);
      }
   }

   void updatePreferredNameOfPlace(Place place, String oldName, String newName) {
      if (oldName != null) {
         if (indexNames) {
            Set places = (Set)prefNameToPlaces.get(standardize(oldName));
            places.remove(place);
         }
      }
      if (newName != null) {
         if (indexNames) {
            Set places = (Set)prefNameToPlaces.get(standardize(newName));
            if (places == null) {
               places = new HashSet();
               prefNameToPlaces.put(standardize(newName), places);
            }
            places.add(place);
         }
      }
   }

   void addVariantNameToPlace(Place place, String name) {
      if (indexNames) {
         Set places = (Set)variantNameToPlaces.get(standardize(name));
         if (places == null) {
            places = new HashSet();
            variantNameToPlaces.put(standardize(name), places);
         }
         places.add(place);
      }
   }

   private void removeVariantNameFromPlace(Place place, String name) {
      if (indexNames) {
         Set places = (Set)variantNameToPlaces.get(standardize(name));
         places.remove(place);
      }
   }

   public boolean isRedirect(String placeTitle) {
      return redirect.get(placeTitle) != null;
   }

   public Place getPlace(String placeTitle) {
      Place p = titleToPlace.get(standardizeTitle(placeTitle));
      if (p == null) {
         // maybe it's been redirected
         String savePlaceTitle = placeTitle;
         placeTitle = redirect.get(placeTitle);
         int c = 0;
         while (placeTitle != null) {
            if (c++ > MAX_REDIRECTS) {
               logger.error("Redirect loop: " + placeTitle);
               break;
            }
            p = titleToPlace.get(standardizeTitle(placeTitle));
            if (p != null) {
               break;
            }
            placeTitle = redirect.get(placeTitle);
         }
//         logger.info("saveTitle=" + savePlaceTitle + " return=" + (placeTitle == null ? "" : placeTitle));
      }
      return p;
   }

   public Set getPlacesWithPrefName(String prefName) {
      if (indexNames) {
         return (Set)prefNameToPlaces.get(standardize(prefName));
      }
      throw new IllegalStateException("names not indexed");
   }

   public Set getPlacesWithVariantName(String variantName) {
      if (indexNames) {
         return (Set)variantNameToPlaces.get(standardize(variantName));
      }
      throw new IllegalStateException("names not indexed");
   }

   public Collection<Place> getPlaces() {
      return getPlaces(false);
   }

   public int getPlaceLevel(String placeTitle) {
      int level = -1;
      Place p = getPlace(placeTitle);
      while (p != null) {
         level++;
         if (level > MAX_DEPTH) {
            logger.error("looping on: " + placeTitle);
            break;
         }
         placeTitle = p.getParentTitle();
         // US states are at the same level as countries
         // note that this function may be called when we have just a minimal place standard (from PlaceStandardParse)
         // so we have access to only a few fields (e.g., we don't have type)
         if (Util.isEmpty(placeTitle) ||
             (level == 0 && placeTitle.equalsIgnoreCase("united states"))) {
            break;
         }
         p = getPlace(placeTitle);
      }
      return level;
   }

   /**
    * Return the "canonical" full name of the place, which is the preferred name of the place and its parents up the chain
    * @param placeTitle
    * @return String
    */
   public String getFullName(String placeTitle) {
      StringBuilder buf = new StringBuilder();
      Place p = getPlace(placeTitle);
      int cnt = 0;
      while (p != null) {
         if (cnt++ > MAX_DEPTH) {
            logger.error("looping on: " + placeTitle);
            break;
         }
         String prefName = p.getPreferredName();
         if (!Util.isEmpty(prefName)) {
            if (buf.length() > 0) {
               buf.append(", ");
            }
            buf.append(prefName);
         }
         placeTitle = p.getParentTitle();
         if (Util.isEmpty(placeTitle)) {
            break;
         }
         p = getPlace(placeTitle);
      }
      return buf.toString();
   }

   // returns titles of self and see-also places
   private Set<String> findSelfAndRelatedPlaces(String placeTitle) {
      HashSet<String> result = new HashSet<String>();
      Place p = getPlace(placeTitle);
      result.add(placeTitle);
      for (Place.SeeAlsoPlace seeAlsoPlace:p.getSeeAlsoPlaces()) {
         result.add(seeAlsoPlace.getPlaceTitle());
      }
      return result;
   }

   // returns titles of self and see-also places for titles in specified set
   private Set<String> findSelfAndRelatedPlaces(Set<String> s) {
      HashSet<String> result = new HashSet<String>();
      for(String placeTitle:s) {
         result.addAll(findSelfAndRelatedPlaces(placeTitle));
      }
      return result;
   }

   // returns titles of parent and all previous parents for titles in specified set
   private Set<String> findParentsAndPriorParents(Set<String> s) {
      Set<String> result = new HashSet<String>();
      for(String placeTitle:s) {
         Place p = getPlace(placeTitle);
         if (p != null) {
            if (!Util.isEmpty(p.getParentTitle())) {
               result.add(p.getParentTitle());
            }
            for (Place.PreviousParent prevParent:p.getPreviousParents()) {
               result.add(prevParent.getParentTitle());
            }
         }
      }
      return result;
   }

   public Set<String> computeNameClosure(String placeTitle, boolean addVariantPlaceNames, boolean addVariantAncestorNames) {
      Set<String> result = new HashSet<String>();
      Set<String> toTraverse = new HashSet<String>();
      if (!Util.isEmpty(placeTitle)) {
         toTraverse.add(placeTitle);
      }

      boolean isAncestor = false;
      int cnt = 0;
      while (!toTraverse.isEmpty()) {
         if (cnt++ > MAX_DEPTH) {
            logger.error("looping on: " + placeTitle);
            break;
         }
         // add preferred name (and variant names if allNames is true) to result
         for (String traverseTitle:toTraverse) {
            Place p = getPlace(traverseTitle);
            if (p != null) {
               result.add(p.getPreferredName());
               if ((!isAncestor && addVariantPlaceNames) || (isAncestor && addVariantAncestorNames)) {
                  for (Place.VariantName variantName:p.getVariantNames()) {
                     result.add(variantName.getName());
                  }
               }
            }
            else {
               result.add(traverseTitle);
               logger.debug("Place not found: " + traverseTitle);
            }
         }

         // get parents and previous parents
         isAncestor = true;
         toTraverse = findParentsAndPriorParents(toTraverse);
      }

      return result;
   }

   private String getSortKey(Place p) {
      StringBuffer buf = new StringBuffer();
      String parentTitle = p.getParentTitle();
      int cnt = 0;
      while (parentTitle != null && parentTitle.length() > 0) {
         if (cnt++ > MAX_DEPTH) {
            logger.error("looping on: " + p.getTitle());
            break;
         }
         Place parent = getPlace(parentTitle);
         if (parent == null) {
            logger.error("Parent page not found: " + parentTitle);
            break;
         }
         buf.insert(0, "|");
         buf.insert(0, parent.getPreferredName());
         parentTitle = parent.getParentTitle();
      }
      buf.append(p.getPreferredName());
      buf.append(" ");           // to ensure that each place has a unique sort key
      buf.append(p.getTitle());
      return buf.toString();
   }

   public Collection<Place> getPlaces(boolean sorted) {
      if (sorted) {
         TreeSet<Place> result = new TreeSet<Place>(new Comparator() {
            public int compare(Object o1, Object o2) {
               if (!(o1 instanceof Place) || !(o2 instanceof Place)) {
                  throw new IllegalStateException("trying to compare something other than places");
               }
               return getSortKey((Place)o1).compareTo(getSortKey((Place)o2));
             }
         });
         result.addAll(titleToPlace.values());
         return result;
      }
      else {
         return titleToPlace.values();
      }
   }

   public boolean isValid() {
      boolean valid = true;
      Iterator pi = getPlaces().iterator();
      while (pi.hasNext()) {
         Place place = (Place)pi.next();
         if (place.getTitle() == null || Util.romanize(place.getTitle()).length() == 0) {
            logger.error("empty title: " + place.getTitle());
         }
         if (place.getParentTitle() != null && place.getParentTitle().length() > 0 && getPlace(place.getParentTitle()) == null) {
            logger.error("parent missing: " + place.getParentTitle());
            valid = false;
         }
         Iterator sapi = place.getSeeAlsoPlaces().iterator();
         while (sapi.hasNext()) {
            Place.SeeAlsoPlace sap = (Place.SeeAlsoPlace)sapi.next();
            if (getPlace(sap.getPlaceTitle()) == null) {
               logger.error("see-also place missing: " + sap.getPlaceTitle());
               valid = false;
            }
         }
      }
      return valid;
   }
}
