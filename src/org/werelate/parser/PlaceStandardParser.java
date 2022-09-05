package org.werelate.parser;

import org.werelate.places.PlaceStandard;
import org.werelate.places.Place;
import org.werelate.utils.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import nu.xom.Element;
import nu.xom.Document;
import nu.xom.ParsingException;
import nu.xom.Elements;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PlaceStandardParser extends StructuredDataParser {
   private static Logger logger = LogManager.getLogger("org.werelate.places");

   public static final String NAME = "name";
   public static final String PLACE = "place";
   public static final String PREFERRED_NAME = "preferred_name";
   public static final String ALTERNATE_NAME = "alternate_name";
   public static final String SOURCE = "source";
   public static final String PLACE_TYPE = "type";
   public static final String LATITUDE = "latitude";
   public static final String LONGITUDE = "longitude";
   public static final String LOCATED_IN_TITLE = "located_in";
   public static final String FROM_YEAR = "from_year";
   public static final String TO_YEAR = "to_year";
   public static final String PREVIOUSLY_LOCATED_IN = "previously_located_in";
   public static final String ALSO_LOCATED_IN = "also_located_in";
   public static final String SEE_ALSO_PLACE = "see_also";
   public static final String REASON = "reason";
   public static final String CONTAINED_PLACE = "contained_place";
   public static final String PREVIOUS = "previous";

   private PlaceStandard placeStandard;
   private boolean includeText;

   public PlaceStandardParser() {
      this(false);
   }

   public PlaceStandardParser(boolean includeText) {
      super();
      placeStandard = new PlaceStandard(false);
      this.includeText = includeText;
   }

   private static final Pattern REDIRECT_PATTERN = Pattern.compile("#redirect\\s*\\[\\[Place:(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);

   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException {
      if (shouldIndex(title)) {
         String[] split = splitStructuredWikiText(getTagName(title), text);
         title = title.substring("Place:".length());
         String[] titlePieces = title.split(",", 2);

         // set absolute minimal place fields (need lat+long when indexing sources)
         Place p = new Place();
         p.setTitle(Util.translateHtmlCharacterEntities(title));

         if (!Util.isEmpty(split[0])) {
            Document doc = parseText(split[0]);
            Element elm = doc.getRootElement();

            String prefName = null;
            if (elm.getFirstChildElement(PREFERRED_NAME) != null) {
               prefName = Util.translateHtmlCharacterEntities(elm.getFirstChildElement(PREFERRED_NAME).getValue());
               logger.warn("Pref name not empty: " + title);
            }
            if (Util.isEmpty(prefName)) {
               prefName = titlePieces[0].trim();
            }
            p.setPreferredName(prefName);

            String locatedIn = null;
            if (elm.getFirstChildElement(LOCATED_IN_TITLE) != null) {
               locatedIn = Util.translateHtmlCharacterEntities(elm.getFirstChildElement(LOCATED_IN_TITLE).getValue());
               logger.warn("Located in not empty: " + title);
            }
            if (Util.isEmpty(locatedIn) && titlePieces.length > 1) {
               locatedIn = titlePieces[1].trim();
            }
            if (locatedIn != null) {
               p.setParentTitle(locatedIn);
            }

            if (elm.getFirstChildElement(PLACE_TYPE) != null) {
               p.setType(Util.translateHtmlCharacterEntities(elm.getFirstChildElement(PLACE_TYPE).getValue()));
            }

            if (elm.getFirstChildElement(LATITUDE) != null) {
               p.setLatitude(elm.getFirstChildElement(LATITUDE).getValue());
            }
            if (elm.getFirstChildElement(LONGITUDE) != null) {
               p.setLongitude(elm.getFirstChildElement(LONGITUDE).getValue());
            }
            if (elm.getFirstChildElement(FROM_YEAR) != null) {
               p.setFromYear(elm.getFirstChildElement(FROM_YEAR).getValue());
            }
            if (elm.getFirstChildElement(TO_YEAR) != null) {
               p.setToYear(elm.getFirstChildElement(TO_YEAR).getValue());
            }

            Elements prevParents = elm.getChildElements(ALSO_LOCATED_IN);
            for (int i = 0; i < prevParents.size(); i++) {
               Element prevParent = prevParents.get(i);
               p.addPreviousParent(Util.translateHtmlCharacterEntities(prevParent.getAttributeValue(PLACE)),
                                   Util.translateHtmlCharacterEntities(prevParent.getAttributeValue(FROM_YEAR)),
                                   Util.translateHtmlCharacterEntities(prevParent.getAttributeValue(TO_YEAR)));
            }

            Elements variantNames = elm.getChildElements(ALTERNATE_NAME);
            for (int i = 0; i < variantNames.size(); i++) {
               Element variantName = variantNames.get(i);
               p.addVariantName(Util.translateHtmlCharacterEntities(variantName.getAttributeValue(NAME)),
                                Util.translateHtmlCharacterEntities(variantName.getAttributeValue(SOURCE)));
            }

            Elements seeAlsoPlaces = elm.getChildElements(SEE_ALSO_PLACE);
            for (int i=0; i < seeAlsoPlaces.size(); i++)
            {
               Element seeAlso = seeAlsoPlaces.get(i);
               p.addSeeAlsoPlace(Util.translateHtmlCharacterEntities(seeAlso.getAttributeValue(PLACE)),
                     Util.translateHtmlCharacterEntities(seeAlso.getAttributeValue(REASON)));
            }


            if (this.includeText) {
               p.setText(split[1]);
            }
            placeStandard.addPlace(p);
         }
         else {
            Matcher m = REDIRECT_PATTERN.matcher(split[1]);
            if (m.lookingAt()) {
               String newTitle = Util.translateHtmlCharacterEntities(m.group(1));
//               logger.info("old=" + p.getName() + " new=" + newTitle);
               placeStandard.addRedirect(p.getTitle(), newTitle);
            }
         }
      }
   }

   public boolean shouldIndex(String title) {
      return (title.startsWith("Place:") || title.startsWith("place:"));
   }

   public String getTagName(String title) {
      return PLACE;
   }

   public PlaceStandard getPlaceStandard() {
      return placeStandard;
   }
}
