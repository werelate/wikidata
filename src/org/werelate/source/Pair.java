package org.werelate.source;

import org.werelate.utils.Util;

public class Pair {
   private String name;
   private String value;
   public Pair(String newName, String newValue){
   name = newName;
   value = newValue;
   }

   public String getName(){
      return name;
   }
   public String getValue(){
      return value;
   }

   public void setName(String newName){
      name = newName;
   }
   
   public void SetValue(String newValue){
      value = newValue;
   }
   
   public String toXmlString(){
   return ("<" + name + ">" + Util.encodeXML(value) + "</" + name + ">\n");
   }

   public String toString(){
      return ("title = " + name + " value= "+ value);
   }

}
