package org.werelate.source;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.werelate.utils.Util;

import java.util.Set;
import java.util.HashSet;
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

public class FhlcPlaceId{
   private Hashtable<String, String> fhlcPlaces; 
   private Hashtable<String, Set<String>> secFhlcPlaces; 
   private static final Pattern PLACEGROUP = Pattern.compile("<place\\s*hierarchy\\s*=\\s*\"([^\"]*)\">.*?(<.*?)</place>");
   private static final Pattern NAMES = Pattern.compile("<name[^>]*>([^<]*)</name>");
   private static final Pattern IDNUM = Pattern.compile("<id[^>]*>([^<]*)</id>");
   private static final Pattern HIERARCHY = Pattern.compile("([^/]+)/?");
   public FhlcPlaceId() {
      super();
      fhlcPlaces = new Hashtable<String,String>();
      secFhlcPlaces = new Hashtable<String,Set<String>>();

   }

   public void writeFhlcPlaceMap(String outputFileName) throws IOException{
      File fhlcFile = new File(outputFileName);
      FileWriter writer = new FileWriter(fhlcFile);
      Iterator<String> HashIter = fhlcPlaces.keySet().iterator();
      while(HashIter.hasNext()){
         String key = HashIter.next();
         if(key.equals("United States")){
            writer.write("United States|337\n");
         }
         else{
            writer.write(key);
            writer.write('|');
            writer.write(fhlcPlaces.get(key));
            writer.write('\n');
         }
      }
      writer.close();
   }

   public void writeSecondFhlcPlaceMap(String secondaryOutputFileName) throws IOException{
      File fhlcFile = new File(secondaryOutputFileName);
      FileWriter writer = new FileWriter(fhlcFile);
      Iterator<String> HashIter = secFhlcPlaces.keySet().iterator();
      while(HashIter.hasNext()){
         String key = HashIter.next();
         Iterator<String> idNumIter = secFhlcPlaces.get(key).iterator();
         while(idNumIter.hasNext()){
            writer.write(key);
            writer.write('|');
            writer.write(idNumIter.next());
            writer.write('\n');
         }
      }
      writer.close();

   }

   public void parsePlaceFile(String file) throws IOException{
      File notesFile = new File(file);
      if(!notesFile.exists()){
         System.out.println(file + "does not exist");
         return;
      }
      BufferedReader readIn = new BufferedReader(new FileReader(notesFile)); 
      StringBuilder sb = new StringBuilder("");
      while(readIn.ready())
         sb.append(Util.translateHtmlCharacterEntities(readIn.readLine()));
      String fileText = sb.toString();
      Matcher m = PLACEGROUP.matcher(fileText);
      while(m.find()){
         String hierarchy = m.group(1).trim();
         String text = m.group(2).trim();
         Matcher name = NAMES.matcher(text);
         Matcher id = IDNUM.matcher(text);
         String idNum = "";
         if(id.find())
            idNum = id.group(1);
         while(name.find()){
            String placeName = Util.unencodeXML(name.group(1));
            String place = constructPlace(hierarchy, placeName);
            if(fhlcPlaces.containsKey(place)){
               Matcher nameDouble = (Pattern.compile("<name[^>]*preferred[^>]*>"+placeName+"</name>")).matcher(text);
               if(nameDouble.find()){
                  Set<String> idNumbers = new HashSet<String>();
                  if(secFhlcPlaces.containsKey(place))
                     idNumbers = secFhlcPlaces.get(place);
                  idNumbers.add(idNum);
                  secFhlcPlaces.put((place),idNumbers);
                  fhlcPlaces.put((place),idNum);
               }
               else{
                  Set<String> idNumbers = new HashSet<String>();
                  if(secFhlcPlaces.containsKey(place))
                     idNumbers = secFhlcPlaces.get(place);
                  idNumbers.add(idNum);
                  secFhlcPlaces.put((place),idNumbers);
               }
            }
            else{
                  fhlcPlaces.put(Util.translateHtmlCharacterEntities(place),idNum);
            }
         }
      }
   }

   public String constructPlace(String hierarchy, String place){
      Matcher m = HIERARCHY.matcher(hierarchy);
      StringBuilder sb = new StringBuilder("");
      if(m.find()){

         String location = m.group(1);//these are virtual locations and are not in the fhlc data
         if(!location.equals("United States") && !location.equals("Canada")){
            sb.insert(0,location);
         }
      }
      while(m.find()){
         String location = m.group(1);
         if(sb.length() != 0)
            sb.insert(0,", ");
         sb.insert(0,location);
      }
      if(sb.length() != 0)
         sb.insert(0,", ");
      sb.insert(0,place);
      return sb.toString();
   }

   public static String pad(int num , int length){
      String temp = "00000000000" + num;
      return temp.substring(temp.length() - length);

   }

   public static void main(String[] args) throws IOException
   {
      if (args.length <= 2) {
         System.out.println("Usage: <input directory> <output file(preferred)> <secondary output>");
      }
      else {
         FhlcPlaceId fhlc = new FhlcPlaceId();
         int ones = 472;
         for(int i = 0;i<ones;i++){
            fhlc.parsePlaceFile(args[0] + pad(i,3) +  ".xml");
         }
        fhlc.parsePlaceFile(args[0] + "873035.xml");
        fhlc.parsePlaceFile(args[0] + "912474.xml");
        fhlc.parsePlaceFile(args[0] + "989359.xml");
         fhlc.writeFhlcPlaceMap(args[1]);
         fhlc.writeSecondFhlcPlaceMap(args[2]);

      }
   }
}
