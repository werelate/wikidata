package org.werelate.places;

import org.werelate.utils.Util;

import java.util.Set;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Collections;

public class Place implements Comparable {
   private String title;
   private String preferredName;
   private String parentTitle;
   private String fromYear;
   private String toYear;
   private String type;
   private String latitude;
   private String longitude;
   private String gettyId;
   private String fhlcId;
   private String wikipediaTitle;
   private String text;
   private Set<PreviousParent> previousParents;
   private Set<VariantName> variantNames;
   private Set<SeeAlsoPlace> seeAlsoPlaces;
   private Set<ContainedPlace> containedPlaces;
   private PlaceStandard standard;

   /**
    * Convert a "X, Y, Z" or "Place:X, Y, Z" place name into a "Z/Y/X" place name
    * @param placeName
    * @return String
    */
   public static String reverseLevels(String placeName) {
      if (placeName.startsWith("Place:")) {
         placeName = placeName.substring("Place:".length());
      }
      String[] levels = placeName.split(",");
      StringBuffer buf = new StringBuffer();
      for (int i = levels.length-1; i >= 0; i--) {
         buf.append(levels[i].trim());
         if (i > 0) {
            buf.append("/");
         }
      }
      return buf.toString();
   }

   public Place() {
      previousParents = Collections.emptySet();
      variantNames = Collections.emptySet();
      seeAlsoPlaces = Collections.emptySet();
      containedPlaces = Collections.emptySet();
      title = null;
      preferredName = "";
      parentTitle = null;
      fromYear = null;
      latitude = null;
      longitude = null;
      gettyId = null;
      fhlcId = null;
      wikipediaTitle = null;
      text = null;
      standard = null;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      if (standard != null) {
         standard.updateTitleOfPlace(this, this.title, title);
      }
      this.title = title;
   }

   public String getPreferredName() {
      return preferredName;
   }

   public void setPreferredName(String preferredName) {
      if (standard != null) {
         standard.updatePreferredNameOfPlace(this, this.preferredName, preferredName);
      }
      this.preferredName = preferredName;
   }

   public String getParentTitle() {
      return parentTitle;
   }

   public void setParentTitle(String parent) {
      this.parentTitle = (parent == null ? null : parent.trim());
   }

   public String getFromYear() {
      return fromYear;
   }

   public void setFromYear(String fromYear) {
      this.fromYear = fromYear;
   }

   public String getToYear() {
      return toYear;
   }

   public void setToYear(String toYear) {
      this.toYear = toYear;
   }

   /**
    * Returns a set of PreviousParent's
    * @return String
    */
   public Set<PreviousParent> getPreviousParents() {
      return previousParents;
   }

   public void addPreviousParent(String parentTitle, String fromYear, String toYear) {
      if (previousParents.size() == 0) {
         previousParents = new TreeSet<PreviousParent>(new Comparator() {
            public int compare(Object o, Object o1) {
               return ((PreviousParent)o).getParentTitle().compareToIgnoreCase(((PreviousParent)o1).getParentTitle());
            }
         });
      }
      previousParents.add(new PreviousParent(parentTitle, fromYear, toYear));
   }

   /**
    * Returns a set of VariantName's
    * @return String
    */
   public Set<VariantName> getVariantNames() {
      return variantNames;
   }

   /**
    * Add a variant name even if it is the same as the preferred name
    * You must call setPreferredName before calling this function.
    * @param name
    * @param source
    */
   public void addVariantName(String name, String source) {
      if (!Util.isEmpty(name)) { // && !preferredName.equalsIgnoreCase(name)) {
         if (standard != null) {
            standard.addVariantNameToPlace(this, name);
         }
         if (variantNames.size() == 0) {
            variantNames = new TreeSet<VariantName>(new Comparator() {
               public int compare(Object o, Object o1) {
                  int c = ((VariantName)o).getName().compareToIgnoreCase(((VariantName)o1).getName());
                  if (c == 0) {
                     String s = ((VariantName)o).getSource();
                     String s1 = ((VariantName)o1).getSource();
                     if (s == null && s1 == null) {
                        c = 0;
                     }
                     else if (s == null) {
                        c = -1;
                     }
                     else if (s1 == null) {
                        c = 1;
                     }
                     else {
                        c = s.compareToIgnoreCase(s1);
                     }
                  }
                  return c;
               }
            });
         }
         variantNames.add(new VariantName(name, source));
      }
   }

   /**
    * Sets the set of variant names to the specified set.
    * Any prior value for the variant name set is forgotten.
    * @param variantNames
    */
   public void setVariantNames(Set variantNames, String source) {
      if (this.variantNames.size() > 0) {
         this.variantNames.clear();
      }
      Iterator i = variantNames.iterator();
      while (i.hasNext()) {
         String variantName = (String)i.next();
         addVariantName(variantName, source);
      }
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getLatitude() {
      return latitude;
   }

   public void setLatitude(String latitude) {
      this.latitude = latitude;
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getLongitude() {
      return longitude;
   }

   public void setLongitude(String longitude) {
      this.longitude = longitude;
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getGettyId() {
      return gettyId;
   }

   public void setGettyId(String gettyId) {
      this.gettyId = gettyId;
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getFhlcId() {
      return fhlcId;
   }

   public void setFhlcId(String fhlcId) {
      this.fhlcId = fhlcId;
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getWikipediaTitle() {
      return wikipediaTitle;
   }

   public void setWikipediaTitle(String wikipediaTitle) {
      this.wikipediaTitle = wikipediaTitle;
   }

   /**
    * Returns null if not set
    * @return String
    */
   public String getText() {
      return text;
   }

   public void setText(String text) {
      this.text = text;
   }

   public Set<SeeAlsoPlace> getSeeAlsoPlaces() {
      return seeAlsoPlaces;
   }

   public void addSeeAlsoPlace(String placeTitle, String reason) {
      if (seeAlsoPlaces.size() == 0) {
         seeAlsoPlaces = new TreeSet<SeeAlsoPlace>(new Comparator() {
            public int compare(Object o, Object o1) {
               return ((SeeAlsoPlace)o).getPlaceTitle().compareToIgnoreCase(((SeeAlsoPlace)o1).getPlaceTitle());
            }
         });
      }
      seeAlsoPlaces.add(new SeeAlsoPlace(placeTitle, reason));
   }

   public Set<ContainedPlace> getContainedPlaces() {
      return containedPlaces;
   }

   public void addContainedPlace(String placeTitle, String type, boolean also) {
      if (containedPlaces.size() == 0) {
         containedPlaces = new TreeSet<ContainedPlace>(new Comparator() {
            public int compare(Object o, Object o1) {
               return ((ContainedPlace)o).getPlaceTitle().compareToIgnoreCase(((ContainedPlace)o1).getPlaceTitle());
            }
         });
      }
      containedPlaces.add(new ContainedPlace(placeTitle, type, also));
   }

   public void setStandard(PlaceStandard standard) {
      this.standard = standard;
   }

   public PlaceStandard getStandard() {
      return standard;
   }

   public void updateFrom(Place p) {
      if (Util.isEmpty(getPreferredName())) {
         setPreferredName(p.getPreferredName());
      }
      for (VariantName vn:p.getVariantNames()) {
         addVariantName(vn.getName(), vn.getSource());
      }
      if (Util.isEmpty(getFromYear())) {
         setFromYear(p.getFromYear());
      }
      if (Util.isEmpty(getToYear())) {
         setToYear(p.getToYear());
      }
      if (Util.isEmpty(getType())) {
         setType(p.getType());
      }
      if (Util.isEmpty(getLatitude())) {
         setLatitude(p.getLatitude());
      }
      if (Util.isEmpty(getLongitude())) {
         setLongitude(p.getLongitude());
      }
      if (Util.isEmpty(getGettyId())) {
         setGettyId(p.getGettyId());
      }
      if (Util.isEmpty(getFhlcId())) {
         setFhlcId(p.getFhlcId());
      }
      if (Util.isEmpty(getWikipediaTitle())) {
         setWikipediaTitle(p.getWikipediaTitle());
      }
      if (Util.isEmpty(getPreferredName())) {
         setPreferredName(p.getPreferredName());
      }
      if (Util.isEmpty(getText())) {
         setText(p.getText());
      }
   }

   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Place)) return false;

      final Place place = (Place) o;

      if (title != null ? !title.equals(place.title) : place.title != null) return false;

      return true;
   }

   public int hashCode() {
      return (title != null ? title.hashCode() : 0);
   }

   public int compareTo(Object o) {
      return title.compareTo(((Place) o).getTitle());
   }
   //-------------------------------

   public static class PreviousParent implements Comparable {
      private String parentTitle;
      private String fromYear;
      private String toYear;

      public PreviousParent(String parentTitle, String fromYear, String toYear) {
         this.parentTitle = parentTitle;
         this.fromYear = fromYear;
         this.toYear = toYear;
      }

      public String getParentTitle() {
         return parentTitle;
      }

      public void setParentTitle(String parentTitle) {
         this.parentTitle = (parentTitle == null ? null : parentTitle.trim());
      }

      public String getFromYear() {
         return fromYear;
      }

      public void setFromYear(String fromYear) {
         this.fromYear = fromYear;
      }

      public String getToYear() {
         return toYear;
      }

      public void setToYear(String toYear) {
         this.toYear = toYear;
      }

      public int compareTo(Object o) {
         if (!(o instanceof PreviousParent)) {
            throw new IllegalStateException("trying to compare instances of different classes");
         }
         return parentTitle.compareTo(((PreviousParent)o).parentTitle);
      }

      public boolean equals(Object o) {
         if (this == o) return true;
         if (!(o instanceof PreviousParent)) return false;

         return compareTo(o) == 0;
      }

      public int hashCode() {
         return parentTitle.hashCode();
      }
   }

   //-------------------------------

   public static class VariantName  implements Comparable {
      private String name;
      private String source;

      public VariantName(String name, String source) {
         this.name = name;
         this.source = source;
      }

      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = (name == null ? null : name.trim());
      }

      public String getSource() {
         return source;
      }

      public void setSource(String source) {
         this.source = source;
      }

      public int compareTo(Object o) {
         if (!(o instanceof VariantName)) {
            throw new IllegalStateException("trying to compare instances of different classes");
         }
         final VariantName variantName = (VariantName) o;
         int c = name.compareTo(variantName.name);
         if (c != 0) {
            return c;
         }
         else if (source != null && variantName.source != null) {
            return source.compareTo(variantName.source);
         }
         else if (source == null && variantName.source == null) {
            return 0;
         }
         else if (source != null) {
            return 1;
         }
         else {
            return -1;
         }
      }

      public boolean equals(Object o) {
         if (this == o) return true;
         if (!(o instanceof VariantName)) return false;

         return compareTo(o) == 0;
      }

      public int hashCode() {
         int result;
         result = name.hashCode();
         result = 29 * result + (source != null ? source.hashCode() : 0);
         return result;
      }
   }

   //-------------------------------

   public static class SeeAlsoPlace  implements Comparable {
      private String placeTitle;
      private String reason;

      public SeeAlsoPlace(String placeTitle, String reason) {
         this.placeTitle = placeTitle;
         this.reason = reason;
      }
      public String getPlaceTitle() {
         return placeTitle;
      }

      public void setPlaceTitle(String placeTitle) {
         this.placeTitle = (placeTitle == null ? null : placeTitle.trim());
      }

      public String getReason() {
         return reason;
      }

      public void setReason(String reason) {
         this.reason = reason;
      }

      public int compareTo(Object o) {
         if (!(o instanceof SeeAlsoPlace)) {
            throw new IllegalStateException("trying to compare instances of different classes");
         }
         return placeTitle.compareTo(((SeeAlsoPlace)o).placeTitle);
      }

      public boolean equals(Object o) {
         if (this == o) return true;
         if (!(o instanceof SeeAlsoPlace)) return false;

         return compareTo(o) == 0;
      }

      public int hashCode() {
         return placeTitle.hashCode();
      }
   }

   //-------------------------------

   public static class ContainedPlace  implements Comparable {
      private String placeTitle;
      private String type;
      private boolean also;

      public ContainedPlace(String placeTitle, String type, boolean also) {
         this.placeTitle = placeTitle;
         this.type = type;
         this.also = also;
      }

      public String getPlaceTitle() {
         return placeTitle;
      }

      public void setPlaceTitle(String placeTitle) {
         this.placeTitle = (placeTitle == null ? null : placeTitle.trim());
      }

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public boolean isAlso() {
         return also;
      }

      public void setAlso(boolean also) {
         this.also = also;
      }

      public int compareTo(Object o) {
         if (!(o instanceof ContainedPlace)) {
            throw new IllegalStateException("trying to compare instances of different classes");
         }
         return placeTitle.compareTo(((ContainedPlace)o).placeTitle);
      }

      public boolean equals(Object o) {
         if (this == o) return true;
         if (!(o instanceof ContainedPlace)) return false;

         return compareTo(o) == 0;
      }

      public int hashCode() {
         return placeTitle.hashCode();
      }
   }
}
