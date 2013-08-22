package org.werelate.source;

import java.util.*;

public class Source {
   private Hashtable<String,List<String>> valueListsTable;
   private Hashtable<String,String> valuesTable;

   public Source(){
      valueListsTable = new Hashtable<String,List<String>>();
      valuesTable = new Hashtable<String,String>();
   }

   private boolean isMultiValueField(String name) {
      for (String fld : SourceUpdater.MULTI_VALUE_FIELDS) {
         if (name.equals(fld)) {
            return true;
         }
      }
      return false;
   }

   public void addValue(String name, String value){
      if(isMultiValueField(name)) {
         List<String> tempList = valueListsTable.get(name);
         if(tempList == null) {
            tempList = new ArrayList<String>(3);
            valueListsTable.put(name,tempList);
         }
         tempList.add(value);
         return;
      }
      valuesTable.put(name,value);
   }

   public Hashtable<String,List<String>> getMultiValues(){
      return valueListsTable;
   }

   public Hashtable<String,String> getSingleValues(){
      return valuesTable;
   }
   
}
