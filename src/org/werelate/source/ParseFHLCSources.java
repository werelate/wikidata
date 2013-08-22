package org.werelate.source;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;

import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

public class ParseFHLCSources{
   private FileWriter writer;
   private Hashtable<String, Set<Pair>> fhlcSources; 
   private Hashtable<String, String> fhlcPlaceToNum;
   private static final Pattern TITLENO = Pattern.compile("www\\.familysearch\\.org.*?titleno=([0123456789]+)");
   private static final Pattern PLACEID = Pattern.compile("^([^|]+)\\|([0-9]+)$");
   private static final Pattern PAIR = Pattern.compile("<([a-zA-Z0-9]*\\s*)>([^<]*)</\\1"); 
   private static final Pattern SUBJECTPLACE = Pattern.compile("([^- ]([^ ]+|(?: (?=[^-])))+)");
   private static final Pattern HTMLPAIR = Pattern.compile("<tr[^>]*>.*?<td[^>]*>(<[^>]*>|\\s)*([A-Za-z0-9 (),.;:-]*)(<[^>]*>|\\s)*?</td[^>]*>.*?<td[^>]*>  (<[^>]*>|\\s)*(.*?)</td"); 
//   private static final Pattern MULTIPLEPAIR = Pattern.compile("([^<]+)\\s*(<.*?>)*");
   private static final Pattern MULTIPLEPAIR = Pattern.compile("\\s*(<a[^>]*>)?([^<]+)(</a>([^<]*))?((<[^>]*>)+)");
   private static final Pattern PLACEHIERARCHY = Pattern.compile("([^,]+)(, )?");
   public ParseFHLCSources() {
      super();
      fhlcPlaceToNum = new Hashtable<String,String>();
      fhlcSources = new Hashtable<String,Set<Pair>>();
   }
   public void loadFhlcPlaceHash(String fhlcPlaceFile) throws IOException{
      File placeFile = new File(fhlcPlaceFile);
      String fhlcPlace;
      String idnum;
      if(!placeFile.exists()){
         System.out.println(fhlcPlaceFile + " does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(placeFile));
      while(readIn.ready()){
         String currentLine = readIn.readLine();
         Matcher m = PLACEID.matcher(currentLine);
         if(m.find()){
            fhlcPlace = m.group(1);
            idnum = m.group(2);
            fhlcPlaceToNum.put(Util.romanize(fhlcPlace),idnum);
            fhlcPlaceToNum.put(fhlcPlace,idnum);//if they are the same they will overwrite
         }
         else{
            System.out.println("********************** error in fhlc map file *******************************" + currentLine);
         }

      }
      readIn.close();
   }




   public void parseXmlFile(String file) throws IOException{
      File xmlFile = new File(file);
      if(!xmlFile.exists()){
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(xmlFile)); 
      StringBuilder sb = new StringBuilder("");
      while(readIn.ready())
         sb.append(Util.translateHtmlCharacterEntities(readIn.readLine()));
      String fileText = sb.toString();
      Matcher m = TITLENO.matcher(fileText);
      String titleNumber = "";
      if(m.find()){
         titleNumber = m.group(1).trim();
      }

      HashSet<Pair> values = new HashSet<Pair>();
      m = PAIR.matcher(fileText);
      while(m.find()){
         String name = m.group(1).trim();
         String value = Util.unencodeXML(m.group(2)).trim();
         if(name.equals("place")){
            value = confirmIsFhlcPlace(value);
            if(!value.equals("")){
               Pair newPair = new Pair(name,value);
               values.add(newPair);
            }
         }
         else{
            Pair newPair = new Pair(name,value);
//System.out.println("xml "+ titleNumber+" : " + name + " = " + value);
            values.add(newPair);
         }
      }
      if(fhlcSources.containsKey(titleNumber)){
         values.addAll(fhlcSources.get(titleNumber));
      }
      fhlcSources.put(titleNumber,values);
      readIn.close();
   }

   public String confirmIsFhlcPlace(String place){

      while(!fhlcPlaceToNum.containsKey(place)){
         int comma = place.indexOf(',');
         if(comma < 0 || comma >= place.length() -2)
            return "";
         place = place.substring(comma +2);
      }
      return place;
   }


   public void parseDetailsFile(String file) throws IOException{
      File notesFile = new File(file);
      if(!notesFile.exists()){
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(notesFile)); 
      StringBuilder sb = new StringBuilder("");
      String detailsFileText;
      String fhlcSourceId = "";
      Matcher titleNumMatcher;
      Matcher htmlPairMatcher;
      Matcher multiplePairMatcher;
      Matcher subjectPlaceMatcher;
      HashSet<Pair> dataPairSet;
      String pairTitle;
      while(readIn.ready()){
         sb.append(readIn.readLine());
      }

      detailsFileText = sb.toString();
      titleNumMatcher = TITLENO.matcher(detailsFileText);
      if(titleNumMatcher.find()){
         fhlcSourceId = titleNumMatcher.group(1).trim();
      }
      dataPairSet = new HashSet<Pair>();
      htmlPairMatcher = HTMLPAIR.matcher(detailsFileText);
      while(htmlPairMatcher.find()){//do I need to call the translateHtmlCharacterEntities?
         pairTitle = Util.translateHtmlCharacterEntities(htmlPairMatcher.group(2).trim());
         multiplePairMatcher = MULTIPLEPAIR.matcher(Util.translateHtmlCharacterEntities(htmlPairMatcher.group(5)));
         while(multiplePairMatcher.find()){
            Pair newPair = new Pair(pairTitle,multiplePairMatcher.group(2).trim());
            if (newPair.getValue().length() == 0) {
               continue;
            }
//            System.out.println("html "+ fhlcSourceId+" : " + newPair.getName() + " = " + newPair.getValue());
            if(pairTitle.equals("Authors")){
               String extra = multiplePairMatcher.group(4);
               if (extra != null) {
                  if (extra.contains("Main Author")) {
                     newPair.setName("Main Author");
                  }
                  else if (extra.contains("Added Author")) {
                     newPair.setName("Added Author");
                  }
                  else if (extra.contains("Subject")) {
                     continue;
                  }
               }
               else {
                  System.out.println("null extra "+ fhlcSourceId+" : " + newPair.getName() + " = " + newPair.getValue());
               }
               dataPairSet.add(newPair);
            }
            else{
               if(pairTitle.equals("Subjects")){
                  subjectPlaceMatcher = SUBJECTPLACE.matcher(newPair.getValue());

                  while(subjectPlaceMatcher.find()){
                     String possiblePl = subjectPlaceMatcher.group(1).trim();
                     if(possiblePl.contains("Eastern Europe")){
                       dataPairSet.add(new Pair("place","Belarus")); 
                       dataPairSet.add(new Pair("place","Czech Republic")); 
                       dataPairSet.add(new Pair("place","Moldova")); 
                       dataPairSet.add(new Pair("place","Hungary")); 
                       dataPairSet.add(new Pair("place","Slovakia")); 
                       dataPairSet.add(new Pair("place","Ukraine")); 
                       dataPairSet.add(new Pair("place","Romania")); 
                       dataPairSet.add(new Pair("place","Bulgaria")); 
                       dataPairSet.add(new Pair("place","Poland")); 
                       dataPairSet.add(new Pair("place","Russia")); 
                     }
                     if(possiblePl.contains("Southern States") || possiblePl.contains("Confederate States")){
                       dataPairSet.add(new Pair("place","Virginia"));
                       dataPairSet.add(new Pair("place","South Carolina")); 
                       dataPairSet.add(new Pair("place","Missouri")); 
                       dataPairSet.add(new Pair("place","Mississippi")); 
                       dataPairSet.add(new Pair("place","Texas")); 
                       dataPairSet.add(new Pair("place","Florida")); 
                       dataPairSet.add(new Pair("place","Alabama")); 
                       dataPairSet.add(new Pair("place","North Carolina")); 
                       dataPairSet.add(new Pair("place","Tennessee"));
                       dataPairSet.add(new Pair("place","Florida")); 
                       dataPairSet.add(new Pair("place","Alabama")); 
                       dataPairSet.add(new Pair("place","North Carolina")); 
                       dataPairSet.add(new Pair("place","Kentucky"));
                       dataPairSet.add(new Pair("place","Louisiana")); 
                       dataPairSet.add(new Pair("place","Georgia")); 
                       dataPairSet.add(new Pair("place","Arkansas")); 
                       dataPairSet.add(new Pair("place","West Virginia"));
                     }
                     if(possiblePl.contains("Baltic States")){
                       dataPairSet.add(new Pair("place","Estonia")); 
                       dataPairSet.add(new Pair("place","Lithuania")); 
                       dataPairSet.add(new Pair("place","Latvia")); 
                     }
                     if(possiblePl.contains("United States, West")){
                       dataPairSet.add(new Pair("place","Oregon")); 
                       dataPairSet.add(new Pair("place","Idaho")); 
                       dataPairSet.add(new Pair("place","Colorado")); 
                       dataPairSet.add(new Pair("place","Arizona")); 
                       dataPairSet.add(new Pair("place","Utah")); 
                       dataPairSet.add(new Pair("place","Wyoming")); 
                       dataPairSet.add(new Pair("place","Montana")); 
                       dataPairSet.add(new Pair("place","Washington")); 
                       dataPairSet.add(new Pair("place","Nevada")); 
                       dataPairSet.add(new Pair("place","California"));
                       dataPairSet.add(new Pair("place","New Mexico")); 
                     }
                     if(possiblePl.contains("New England")){
                       dataPairSet.add(new Pair("place","Delaware"));
                       dataPairSet.add(new Pair("place","Virginia")); 
                       dataPairSet.add(new Pair("place","Rhode Island")); 
                       dataPairSet.add(new Pair("place","Connecticut")); 
                       dataPairSet.add(new Pair("place","Vermont")); 
                       dataPairSet.add(new Pair("place","New Hampshire")); 
                       dataPairSet.add(new Pair("place","District of Columbia")); 
                       dataPairSet.add(new Pair("place","New York")); 
                       dataPairSet.add(new Pair("place","Massachusetts"));
                       dataPairSet.add(new Pair("place","Maryland")); 
                       dataPairSet.add(new Pair("place","New Jersey")); 
                       dataPairSet.add(new Pair("place","Pennsylvania")); 
                       dataPairSet.add(new Pair("place","Maine"));
                       dataPairSet.add(new Pair("place","West Virginia")); 
                     }     

                           String hold = confirmIsFhlcPlace(possiblePl); 
                           if(fhlcPlaceToNum.containsKey(hold)){
                              dataPairSet.add(new Pair("place",hold));
                           }
                           String possiblePl2 = LocationSwitcher(possiblePl);
                           hold = confirmIsFhlcPlace(possiblePl2);
                           if(fhlcPlaceToNum.containsKey(hold)){
                              dataPairSet.add(new Pair("place",hold));
                           }

                    
                   
                           //this way it check both with and without romanization
                           possiblePl = Util.romanize(possiblePl);
                           hold = confirmIsFhlcPlace(possiblePl); 
                           if(fhlcPlaceToNum.containsKey(hold)){
                              dataPairSet.add(new Pair("place",hold));
                           }
                           possiblePl = LocationSwitcher(possiblePl);
                           hold = confirmIsFhlcPlace(possiblePl);
                           if(fhlcPlaceToNum.containsKey(hold)){
                              dataPairSet.add(new Pair("place",hold));
                           }
                  }  
               }
               dataPairSet.add(newPair); 
            }  
         }  
      }            
      if(fhlcSources.containsKey(fhlcSourceId)){
         dataPairSet.addAll(fhlcSources.get(fhlcSourceId));
      }
      fhlcSources.put(fhlcSourceId,dataPairSet);
      readIn.close();
   }

   public String LocationSwitcher(String possiblePl){
      Matcher hierarchy = PLACEHIERARCHY.matcher(possiblePl);
      StringBuilder resultPlace = new StringBuilder("");
      if(hierarchy.find())
         resultPlace.append(hierarchy.group(1));
      while(hierarchy.find()){
         resultPlace.insert(0,hierarchy.group(1) +  ", ");
      }
      return resultPlace.toString();
   }

   public void writeXmlResultFile(String filename)throws IOException{
      Iterator<String> MyIter;
      Iterator<Pair> PairIter;
      Set<Pair> PairSet;
      writer = new FileWriter(filename);
      writer.write("<sources>\n");
      MyIter = fhlcSources.keySet().iterator();
      while(MyIter.hasNext()){ 
         String titleNo = (String)MyIter.next();
         PairSet = fhlcSources.get(titleNo);
         PairIter = PairSet.iterator();
         writer.write(" <source id=\"" + titleNo + "\">\n");
         while(PairIter.hasNext())
            writer.write("    " + ((Pair)PairIter.next()).toXmlString());
         writer.write(" </source>\n");
      }
      writer.write("</sources>");
      writer.close();
      fhlcSources = new Hashtable<String,Set<Pair>>();
   }

   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }

   public static void main(String[] args) throws IOException
   {
      int million = 121;
      int thousand = 99;
      int ones = 99;
      if (args.length <= 2) {
         System.out.println("Usage: <input directory> <output directory> <fhlc placeMap>");
      }
      else {
         ParseFHLCSources fhlc = new ParseFHLCSources();
         fhlc.loadFhlcPlaceHash(args[2]);
         for(int i = 000;i<million + 1;i++){
            for(int j = 0;j<thousand + 1;j++){
               for(int k = 00;k<ones + 1;k++){
                  fhlc.parseDetailsFile(args[0] + pad(i,3) + '/' + pad(j,2) + '/' + pad(i,3) + pad(j,2) + pad(k,2)+ "-details.html");
                  fhlc.parseXmlFile(args[0] + pad(i,3) + '/' + pad(j,2) + '/' + pad(i,3) + pad(j,2) + pad(k,2)+ "-details.html.xml");  
               }
               fhlc.writeXmlResultFile(args[1] + pad(i,3) +pad(j,2) + "[00-99].xml");
            }
         }
      }
   }
}
