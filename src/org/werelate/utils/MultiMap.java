package org.werelate.utils;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

public class MultiMap<T, R> {
   private Map<T, Set<R>> t2r = new HashMap<T, Set<R>>();

   public boolean containsKey(T key) {
      return t2r.containsKey(key);
   }

   public int size() {
      return t2r.size();
   }

   public void put(T key, R value) {
      Set<R> values;
      if (t2r.containsKey(key)) {
         values = t2r.get(key);
      } else {
         values = new HashSet<R>();
         t2r.put(key, values);
      }
      values.add(value);
   }

   public void put(T key, Set<R> value)
   {
      t2r.put(key, value);
   }

   public Set<R> get(T key) {
      return t2r.get(key);
   }

   public boolean remove(T key, R value) {
      if (t2r.containsKey(key)) {
         Set<R> rSet = t2r.get(key);
         return rSet.remove(value);
      } else {
         return false;
      }
   }

   public boolean removeAll(T key)
   {
      if (t2r.containsKey(key)) {
         t2r.remove(key);
         return true;
      }
      return false;
   }

   public Set<T> keySet() {
      return t2r.keySet();
   }

   /**
    *
    * @param a2b
    * @param b2c
    * @return a2c consolidated multimap
    */
   public static <T,R,S> MultiMap<T, S>  consolidateMap(MultiMap<T,R> a2b, MultiMap<R, S> b2c) {
      MultiMap<T,S> rval = new MultiMap<T, S>();
      for (T key : a2b.keySet()) {
         for (R key2 : a2b.get(key)) {
            if (b2c.containsKey(key2)) {
               for (S value : b2c.get(key2))
               {
                  rval.put(key, value);
               }
            }
         }
      }
      return rval;
   }
}
