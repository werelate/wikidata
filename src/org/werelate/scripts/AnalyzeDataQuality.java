/*
 * Copyright 2012 Foundation for On-Line Genealogy, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.werelate.scripts;

import org.werelate.parser.StructuredDataParser;
import org.werelate.parser.WikiReader;
import org.werelate.utils.Util;
import org.werelate.util.SharedUtils;
import org.werelate.dq.PersonDQAnalysis;
import org.werelate.dq.FamilyDQAnalysis;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.*;
import java.sql.Connection;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import nu.xom.ParsingException;
import nu.xom.Elements;
import nu.xom.Element;

/**
 * This class analyzes pages and derives some information that is written to SQL for Data Quality reporting in the wiki
 * User: DataAnalyst
 * Date: Mar 2021
 */
public class AnalyzeDataQuality extends StructuredDataParser {
   private Connection sqlCon;
   private static int jobId = 0, round = 0, rows = 0, issueRows = 0, actionRows = 0;
   private static SimpleDateFormat cachedtf = new SimpleDateFormat("yyyyMMddkkmmss");
   private static SimpleDateFormat logdtf = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
   private static SimpleDateFormat ymdf = new SimpleDateFormat("yyyy-MM-dd");
   private static Calendar startTime = Calendar.getInstance();
   private static int thisYear = startTime.get(Calendar.YEAR);

   // Assumptions for year calculations
   private static final int USUAL_LONGEST_LIFE = FamilyDQAnalysis.USUAL_LONGEST_LIFE;
   private static final int MIN_MARRIAGE_AGE = FamilyDQAnalysis.MIN_MARRIAGE_AGE;
   private static final int MAX_MARRIAGE_AGE = FamilyDQAnalysis.MAX_MARRIAGE_AGE;
   private static final int USUAL_YOUNGEST_FATHER = FamilyDQAnalysis.USUAL_YOUNGEST_FATHER;
   private static final int USUAL_YOUNGEST_MOTHER = FamilyDQAnalysis.USUAL_YOUNGEST_MOTHER;
   private static final int USUAL_OLDEST_FATHER = FamilyDQAnalysis.USUAL_OLDEST_FATHER;
   private static final int USUAL_OLDEST_MOTHER = FamilyDQAnalysis.USUAL_OLDEST_MOTHER;
   private static final int MAX_AFTER_PARENT_MARRIAGE = FamilyDQAnalysis.MAX_AFTER_PARENT_MARRIAGE;
   private static final int MAX_SPOUSE_GAP = FamilyDQAnalysis.MAX_SPOUSE_GAP;
   private static final int MAX_SIBLING_GAP = FamilyDQAnalysis.MAX_SIBLING_GAP;
   
   // Templates for addressing issues
   private static HashMap<String, String> aTemplates = new HashMap<String, String>();
   private static HashMap<String, String> dTemplates = new HashMap<String, String>();
   private static Pattern dPat = Pattern.compile("\\{\\{DeferredIssues.*\\}\\}");
   private static Pattern uPat = Pattern.compile("\\{[^\\|]*\\|[^\\|]*\\|(?:\\[\\[User:)?([^\\]\\|\\}]+)");  // gets user name from template

   // For round 1
   private static String[] rowValue = new String[1000];
   private static String[] actionRowValue = new String[1000];
   private static String[] issueRowValue = new String[1000];
   // For subsequent rounds
   private static int[] pageId = new int[1000];
   private static String[] pageTitle = new String[1000];
   private static Integer[] earliestBirth = new Integer[1000];
   private static Integer[] latestBirth = new Integer[1000];
   private static Integer[] latestDeath = new Integer[1000];
   private static String[] parentPage = new String[1000];
   private static int[] latestRoundId = new int[1000];
   private static String[] birthCalc = new String[1000];
   
   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment)
           throws IOException, ParsingException {

      if (!text.startsWith("#REDIRECT")) {  // Ignore redirected pages
         String[] splitTitle = title.split(":",2);
         if (splitTitle[0].equals("Person") || splitTitle[0].equals("Family")) {
            int ns = splitTitle[0].equals("Person") ? 108 : 110;
            String[] split = splitStructuredWikiText(splitTitle[0].equals("Person") ? "person" : "family", text);
            String structuredData = split[0];

            // Variables used in round 1 and written to the db for use in later rounds
            Integer earliestBirth = null, latestBirth = null, latestDeath = null;
            Integer earliestMarriage = null, latestMarriage = null;
            String parentPage = null, husbandPage = null, wifePage = null;
            short diedYoungInd = 0, famousInd = 0, ancientInd = 0;
            String birthCalc = "";
            
            // Variables NOT written to the db - only used in round 1
            Integer earliestDeath = null;

            if (!Util.isEmpty(structuredData)) {
               Element root = parseText(structuredData).getRootElement();
               Elements elms;
               Element elm;

               // Identify issues and gather information required for further processing.

               // Person page
               if (ns == 108) {
                  // Determine dates and issues
                  PersonDQAnalysis personDQAnalysis = new PersonDQAnalysis(root, splitTitle[1]);

                  // Note that earliest and latest birth years can be changed in later rounds, based on dates of family members.
                  // Earliest and latest death years are based solely on the info on this Person page.
                  earliestBirth = personDQAnalysis.getEarliestBirth();
                  latestBirth = personDQAnalysis.getLatestBirth();
                  earliestDeath = personDQAnalysis.getEarliestDeath();
                  latestDeath = personDQAnalysis.getLatestDeath();
                  diedYoungInd = personDQAnalysis.getDiedYoungInd();
            
                  // Write issues to the db
                  String[][] issues = personDQAnalysis.getIssues();
                  for (int k=0; issues[k][0] != null; k++) {
                     createIssue(issues[k][0], issues[k][1], (issues[k][2].equals("Person") ? 108 : 110), SharedUtils.SqlTitle(issues[k][3]));
                  }
                  
                  // Track whether the person is famous (allows living person) or has the ancient template (required if born before 700 AD)
                  if (text.contains("{{FamousLivingPersonException") || text.contains("{{Wikidata|Q")) {
                     famousInd = 1;
                  }
                  if (text.contains("{{Wr-too-far-back}}")) {
                     ancientInd = 1;
                  }

                  // Get page title of parents for subsequent rounds
                  elms = root.getChildElements("child_of_family");
                  if (elms.size() > 0) {
                     elm = elms.get(0);
                     parentPage = SharedUtils.SqlTitle(elm.getAttributeValue("title"));
                  }
               }

               // Family page
               else {
                  // Determine dates and issues
                  FamilyDQAnalysis familyDQAnalysis = new FamilyDQAnalysis(root, splitTitle[1], "all");

                  // Note that earliest and latest marriage years are based solely on the info on this family page and not adjusted in subsequent rounds.
                  earliestMarriage = familyDQAnalysis.getEarliestMarriage();
                  latestMarriage = familyDQAnalysis.getLatestMarriage();
            
                  // Write issues to the db
                  String[][] issues = familyDQAnalysis.getIssues();
                  for (int k=0; issues[k][0] != null; k++) {
                     createIssue(issues[k][0], issues[k][1], (issues[k][2].equals("Person") ? 108 : 110), SharedUtils.SqlTitle(issues[k][3]));
                  }                              

                  // Get page titles of husband and wife for subsequent rounds
                  elms = root.getChildElements("husband");
                  if (elms.size() > 0) {
                     elm = elms.get(0);
                     husbandPage = SharedUtils.SqlTitle(elm.getAttributeValue("title"));
                  }
                  elms = root.getChildElements("wife");
                  if (elms.size() > 0) {
                     elm = elms.get(0);
                     wifePage = SharedUtils.SqlTitle(elm.getAttributeValue("title"));
                  }
               }
            }

            // Prepare line for writing to the database
            rowValue[rows++] = " (" + jobId + "," + pageId + "," + ns + ",\"" + SharedUtils.SqlTitle(splitTitle[1]) +
                      "\"," + earliestBirth + "," + latestBirth + "," + 
                     latestDeath + "," + earliestMarriage + "," + latestMarriage + "," +
                     (parentPage==null ? "null" : "\"" + parentPage + "\"") + "," + 
                     (husbandPage==null ? "null" : "\"" + husbandPage + "\"") + "," + 
                     (wifePage==null ? "null" : "\"" + wifePage + "\"") + "," + 
                     diedYoungInd + "," + famousInd + "," + ancientInd + ",\"" + 
                     username + "\",\"" + birthCalc + "\")";
            if (rows==500) {
               insertRows();
            }
            
            /* For each anomaly template, concatenate user info from each occurrence of the template */
            for (String template : aTemplates.keySet()) {
               trackUserAction(text, pageId, ns, splitTitle[1], "Anomaly", template, aTemplates.get(template));
            }
            /* Check for and handle deferral actions for this page */
            trackDeferralRequest(text, pageId, ns, splitTitle[1]);
         }

         /* Check for templates on talk pages as well (where they are expected to be placed) */
         if (splitTitle[0].equals("Person talk") || splitTitle[0].equals("Family talk")) {
            int ns = splitTitle[0].equals("Person talk") ? 109 : 111;

            /* For each anomaly template, concatenate user info from each occurrence of the template */
            for (String template : aTemplates.keySet()) {
               trackUserAction(text, pageId, ns, splitTitle[1], "Anomaly", template, aTemplates.get(template));
            }
            /* Check for and handle deferral actions for this page */
            trackDeferralRequest(text, pageId, ns, splitTitle[1]);
         }   
      }
   }

   /* Prepare an issue for writing to the database (and write if array is full). */
   private void createIssue(String cat, String desc, int namespace, String title) {
      String issueString = " (" + jobId + "," + namespace + ",\"" + title + "\",\"" + cat + "\",\"" + desc + "\")";

      // Check for a duplicate issue (this can happen for a child with multiple sets of parents). If so, ignore.
      // Check first in the table of issues not yet written to the database.
      for (int j=0; j<issueRows; j++) {
         if (issueRowValue[j].equals(issueString)) {
            return;
         }
      }
      // Check next for issues already written to the database.
      String query = "SELECT count(*) AS count FROM dq_issue_capture" +
      " WHERE dqi_job_id = " + jobId + " AND dqi_namespace = " + namespace + " AND dqi_title = \"" + title + "\"" +
      " AND dqi_issue_desc = \"" + desc + "\";";
      try (Statement stmt = sqlCon.createStatement()) {
         try (ResultSet rs = stmt.executeQuery(query)) {
            rs.next();
            if (rs.getInt("count") > 0) {
               return;
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }

      issueRowValue[issueRows++] = issueString;
      if (issueRows==1000) {
         insertIssueRows();
      }
   }

   private void insertRows() {
      String sql = "INSERT INTO dq_page_analysis (dq_job_id, dq_page_id, dq_namespace, dq_title, " +
                   "dq_earliest_birth_year, dq_latest_birth_year, " +
                   "dq_latest_death_year, dq_earliest_marriage_year, dq_latest_marriage_year, " +
                   "dq_parent_page, dq_husband_page, dq_wife_page, " +
                   "dq_died_young_ind, dq_famous_ind, dq_ancient_ind, " +
                   "dq_last_user, dq_birth_calc) VALUES";
      for (int i=0; i<rows; i++) {
         sql += rowValue[i];
         if (i<(rows-1)) {
            sql += ", ";
         }
         else {
            sql += ";";
         }
      }
      try (PreparedStatement stmt = sqlCon.prepareStatement(sql)) {
         stmt.executeUpdate();
         commitSql();
      } catch (SQLException e) {
         rollbackSql();
         e.printStackTrace();
      }
      rows = 0; 
   }

   private void insertIssueRows() {
      String sql = "INSERT INTO dq_issue_capture (dqi_job_id, dqi_namespace, dqi_title, dqi_category, dqi_issue_desc) VALUES";
      if (issueRows>0) {
         for (int i=0; i<issueRows; i++) {
            sql += issueRowValue[i];
            if (i<(issueRows-1)) {
               sql += ", ";
            }
            else {
               sql += ";";
            }
         }
         try (PreparedStatement stmt = sqlCon.prepareStatement(sql)) {
            stmt.executeUpdate();
            commitSql();
         } catch (SQLException e) {
            rollbackSql();
            e.printStackTrace();
         } 
      issueRows = 0;
      }
   }

   private void trackUserAction(String text, int pageId, int ns, String title, String type, String template, String desc) {
      boolean action = false;
      String aUser = "";
      Pattern tPat = Pattern.compile("\\{\\{" + template + ".*\\}\\}");
      Matcher tMat = tPat.matcher(text);
      while (tMat.find()) {
         action = true;             // Track user action whether or not user is identified
         Matcher uMat = uPat.matcher(tMat.group());
         if (uMat.find()) {
            aUser += (aUser == "" ? "" : ", ") + uMat.group(1);
         }
      }
      if (action) {
         actionRowValue[actionRows++] = " (" + jobId + "," + pageId + "," + ns + ",\"" + SharedUtils.SqlTitle(title) + "\",\"" + type + "\",\"" + 
               desc + "\",\"" + (aUser=="" ? "unidentified" : aUser) + "\")";
      }
      if (actionRows==1000) {
         insertActionRows();
      }
   }

   private void trackDeferralRequest(String text, int pageId, int ns, String title) {
      String dUser = "";
      Matcher dMat = dPat.matcher(text);
      while (dMat.find()) {
         Matcher uMat = uPat.matcher(dMat.group());
         if (uMat.find()) {
            dUser += (dUser == "" ? "|" : "") + uMat.group(1) + "|";         
         }
      }
      if (!dUser.equals("")) {         // Track deferral request only if user(s) is identified
         actionRowValue[actionRows++] = " (" + jobId + "," + pageId + "," + ns + ",\"" + SharedUtils.SqlTitle(title) + 
               "\",\"Page\",\"Deferral\",\"" + dUser + "\")";

      }
      if (actionRows==1000) {
         insertActionRows();
      }
   }

   private void insertActionRows() {
      String sql = "INSERT INTO dq_action (dqa_job_id, dqa_page_id, dqa_namespace, dqa_title, dqa_type, dqa_desc, dqa_action_by) VALUES";
      if (actionRows>0) {
         for (int i=0; i<actionRows; i++) {
            sql += actionRowValue[i];
            if (i<(actionRows-1)) {
               sql += ", ";
            }
            else {
               sql += ";";
            }
         }
         try (PreparedStatement stmt = sqlCon.prepareStatement(sql)) {
            stmt.executeUpdate();
            commitSql();
         } catch (SQLException e) {
            rollbackSql();
            e.printStackTrace();
         } 
      actionRows = 0;
      }
   }

   private void nextRound() {
      int limit = 500;
      int processedRows = 0;
      int updatedRows = 0;
      String query;

      System.out.print("Processing round " + round);

      /* Process rows without a birth year or where there is a significant gap 
         between earliest and latest birth year. */
      query = "SELECT dq_page_id, dq_title, dq_earliest_birth_year," +
              " dq_latest_birth_year, dq_latest_death_year, dq_parent_page, dq_birth_calc" + 
              " FROM dq_page_analysis" +
              " WHERE dq_job_id = " + jobId + " AND dq_namespace = 108 AND dq_page_id > ?" +
              " AND (dq_latest_birth_year is null OR (dq_latest_birth_year - dq_earliest_birth_year) > 10)" +
              " ORDER BY dq_page_id LIMIT " + limit + ";";
    
      try (PreparedStatement stmt = sqlCon.prepareStatement(query)) {
         int startPageId = 0, rows = 0;

         // Read rows with inadequate data, a batch of records at a time, and save in a set of arrays
         do {
            stmt.setInt(1,startPageId);
            try (ResultSet rs = stmt.executeQuery()) {
               rows=0;
               while (rs.next()) {
                  pageId[rows] = rs.getInt("dq_page_id");
                  pageTitle[rows] = SharedUtils.SqlTitle(rs.getString("dq_title"));
                  earliestBirth[rows] = getInteger(rs, "dq_earliest_birth_year");
                  latestBirth[rows] = getInteger(rs, "dq_latest_birth_year");
                  latestDeath[rows] = getInteger(rs, "dq_latest_death_year");
                  parentPage[rows] = SharedUtils.SqlTitle(rs.getString("dq_parent_page"));
                  birthCalc[rows] = rs.getString("dq_birth_calc");
                  latestRoundId[rows] = 0;  // reset for this group of records
                  rows++;
               }
               processedRows += rows;

               // Derive dates and update the database for this batch of records.
               if (rows > 0) {            // This check only required for small test files
                  deriveDates(rows);
                  updatedRows += updateRows(rows);
                  insertIssueRows();      
               }

               if (processedRows % 10000 == 0) {
                  System.out.print(".");
               }
               
               // Get startPageId for next batch
               if (rows>0) {
                  startPageId = pageId[rows-1];
               }
            } catch (SQLException e) {
               e.printStackTrace();
            }
         }
         while (rows==limit); // Loop only if last result set was full

      System.out.println();

      } catch (SQLException e) {
         e.printStackTrace();
      }
      Calendar roundTime = Calendar.getInstance();
      logger.info("Job #" + jobId + " round " + round + " ended at " + logdtf.format(roundTime.getTime()) + " and processed " + 
            processedRows + " rows and updated " + updatedRows + " rows");
      System.out.println("Job #" + jobId + " round " + round + " ended at " + logdtf.format(roundTime.getTime()) + " and processed " + 
            processedRows + " rows and updated " + updatedRows + " rows");
   }

   // Derive dates for the current batch of Persons based on dates of children, spouses and parents.
   private void deriveDates(int size) {
      // Create selection criteria for children/spouses and parents for the entire batch.
      String parSel = "", childSel = "";
      for (int i=0; i<size; i++) {
         childSel += (childSel.equals("") ? "" : ", ");
         childSel += "\"" + pageTitle[i] + "\"";
         if (parentPage[i] != null) {
            parSel += (parSel.equals("") ? "" : ", ");
            parSel += "\"" + parentPage[i] + "\"";
         }
      }

      // Get info for children (start with families where a person in the batch is the husband or wife)
      String childQuery =  "SELECT \"father\" as parent," + 
                           " f.dq_husband_page AS dq_title," +
                           " c.dq_title AS child_dq_title," +
                           " c.dq_earliest_birth_year AS child_dq_earliest_birth_year," +
                           " c.dq_latest_birth_year AS child_dq_latest_birth_year" +
                           " FROM dq_page_analysis f USE INDEX (dq_husband_page) " +
                           " INNER JOIN dq_page_analysis c USE INDEX (dq_parent_page) ON f.dq_title = c.dq_parent_page" +
                           " WHERE f.dq_job_id = " + jobId + " AND f.dq_namespace = 110 " +
                           " AND c.dq_job_id = " + jobId + " AND c.dq_namespace = 108 " +
                           " AND f.dq_husband_page IN ( " + childSel + " ) " +
                           " UNION " +
                           " SELECT \"mother\" as parent," +  
                           " f.dq_wife_page AS dq_title," +
                           " c.dq_title AS child_dq_title," +
                           " c.dq_earliest_birth_year AS child_dq_earliest_birth_year," +
                           " c.dq_latest_birth_year AS child_dq_latest_birth_year" +
                           " FROM dq_page_analysis f USE INDEX (dq_wife_page)" +
                           " INNER JOIN dq_page_analysis c USE INDEX (dq_parent_page) ON f.dq_title = c.dq_parent_page" +
                           " WHERE f.dq_job_id = " + jobId + " AND f.dq_namespace = 110 " +
                           " AND c.dq_job_id = " + jobId + " AND c.dq_namespace = 108 " +
                           " AND f.dq_wife_page IN ( " + childSel + " );";
      try (PreparedStatement stmt = sqlCon.prepareStatement(childQuery)) {
         try (ResultSet rs = stmt.executeQuery()) {
            while(rs.next()) {
               String selfPageTitle = SharedUtils.SqlTitle(rs.getString("dq_title"));
               String cPageTitle = SharedUtils.SqlTitle(rs.getString("child_dq_title"));
               String parent = rs.getString("parent");
               Integer cEarliestBirth = getInteger(rs, "child_dq_earliest_birth_year");
               Integer cLatestBirth = getInteger(rs, "child_dq_latest_birth_year");

               for (int i=0; i<size; i++) {
                  if (selfPageTitle.equals(pageTitle[i])) {
                     /* Update calc of earliest and latest birth years */
                     if (parent.equals("mother") && cEarliestBirth!=null && 
                           (earliestBirth[i]==null || (cEarliestBirth - USUAL_OLDEST_MOTHER) > earliestBirth[i])) {
                        earliestBirth[i] = cEarliestBirth - USUAL_OLDEST_MOTHER;
                        setBirthCalc(i, "child", cPageTitle);
                     }
                     if (parent.equals("father") && cEarliestBirth!=null && 
                           (earliestBirth[i]==null || (cEarliestBirth - USUAL_OLDEST_FATHER) > earliestBirth[i])) {
                        earliestBirth[i] = cEarliestBirth - USUAL_OLDEST_FATHER;
                        setBirthCalc(i,"child", cPageTitle);
                     }
                     if (parent.equals("mother") && cLatestBirth!=null && 
                           (latestBirth[i]==null || (cLatestBirth - USUAL_YOUNGEST_MOTHER) < latestBirth[i])) {
                        latestBirth[i] = cLatestBirth - USUAL_YOUNGEST_MOTHER;
                        setBirthCalc(i,"child", cPageTitle);
                     }
                     if (parent.equals("father") && cLatestBirth!=null && 
                           (latestBirth[i]==null || (cLatestBirth - USUAL_YOUNGEST_FATHER) < latestBirth[i])) {
                        latestBirth[i] = cLatestBirth - USUAL_YOUNGEST_FATHER;
                        setBirthCalc(i,"child", cPageTitle);
                     }
                  }
               }
            }   
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }

      // Get own marriage date and info for spouses
      String spouseQuery = "SELECT f.dq_page_id AS family_page_id," +
                           " f.dq_title AS family_title," +
                           " \"Husband\" AS role," +
                           " f.dq_husband_page AS dq_title," +
                           " s.dq_title AS spouse_dq_title," +
                           " f.dq_earliest_marriage_year AS dq_earliest_marriage_year," +
                           " f.dq_latest_marriage_year AS dq_latest_marriage_year," +
                           " s.dq_earliest_birth_year AS spouse_dq_earliest_birth_year," +
                           " s.dq_latest_birth_year AS spouse_dq_latest_birth_year" +
                           " FROM dq_page_analysis f USE INDEX (dq_husband_page)" +
                           " LEFT OUTER JOIN dq_page_analysis s USE INDEX (dq_title) ON f.dq_wife_page = s.dq_title" +
                           " AND s.dq_job_id = " + jobId + " AND s.dq_namespace = 108 " +
                           " WHERE f.dq_job_id = " + jobId + " AND f.dq_namespace = 110 " +
                           " AND f.dq_husband_page IN ( " + childSel + " ) " +
                           " UNION " +
                           " SELECT f.dq_page_id AS family_page_id," + 
                           " f.dq_title AS family_title," +
                           " \"Wife\" AS role," +
                           " f.dq_wife_page AS dq_title," +
                           " s.dq_title AS spouse_dq_title," +
                           " f.dq_earliest_marriage_year AS dq_earliest_marriage_year," +
                           " f.dq_latest_marriage_year AS dq_latest_marriage_year," +
                           " s.dq_earliest_birth_year AS spouse_dq_earliest_birth_year," +
                           " s.dq_latest_birth_year AS spouse_dq_latest_birth_year" +
                           " FROM dq_page_analysis f USE INDEX (dq_wife_page)" +
                           " LEFT OUTER JOIN dq_page_analysis s USE INDEX (dq_title) ON f.dq_husband_page = s.dq_title" +
                           " AND s.dq_job_id = " + jobId + " AND s.dq_namespace = 108 " +
                           " WHERE f.dq_job_id = " + jobId + " AND f.dq_namespace = 110 " +
                           " AND f.dq_wife_page IN ( " + childSel + " );";
      
      try (PreparedStatement stmt = sqlCon.prepareStatement(spouseQuery)) {
         try (ResultSet rs = stmt.executeQuery()) {
            while(rs.next()) {
               int familyPageId = rs.getInt("family_page_id");  /* check to see if still needed after refactoring */
               String familyTitle = SharedUtils.SqlTitle(rs.getString("family_title"));
               String selfRole = SharedUtils.SqlTitle(rs.getString("role"));
               String selfPageTitle = SharedUtils.SqlTitle(rs.getString("dq_title"));
               String spousePageTitle = SharedUtils.SqlTitle(rs.getString("spouse_dq_title"));
               Integer earliestMarriageYear = getInteger(rs, "dq_earliest_marriage_year");
               Integer latestMarriageYear = getInteger(rs, "dq_latest_marriage_year");
               Integer sEarliestBirth = getInteger(rs, "spouse_dq_earliest_birth_year");
               Integer sLatestBirth = getInteger(rs, "spouse_dq_latest_birth_year");
               for (int i=0; i<size; i++) {
                  if (selfPageTitle.equals(pageTitle[i])) {

                     /* Update calc of earliest and latest birth years */
                     if (earliestMarriageYear!=null && 
                           (earliestBirth[i]==null || (earliestMarriageYear - MAX_MARRIAGE_AGE) > earliestBirth[i])) {
                        earliestBirth[i] = earliestMarriageYear - MAX_MARRIAGE_AGE;
                        if (latestBirth[i]==null) {
                           latestBirth[i] = earliestBirth[i] + USUAL_LONGEST_LIFE; // somewhat arbitrary but needs a value if earliest set
                        }
                        setBirthCalc(i,"own marriage", "");
                     }
                     if (latestMarriageYear!=null && (latestBirth[i]==null || 
                           (latestMarriageYear - MIN_MARRIAGE_AGE) < latestBirth[i])) {
                        latestBirth[i] = latestMarriageYear - MIN_MARRIAGE_AGE;
                        if (earliestBirth[i]==null) {
                           earliestBirth[i] = latestBirth[i] - USUAL_LONGEST_LIFE;  // somewhat arbitrary but needs a value if latest set
                        }
                        setBirthCalc(i,"own marriage", "");
                     }
                     if (sEarliestBirth!=null && (earliestBirth[i]==null || 
                           (sEarliestBirth - MAX_SPOUSE_GAP) > earliestBirth[i])) {
                        earliestBirth[i] = sEarliestBirth - MAX_SPOUSE_GAP;
                        setBirthCalc(i,"spouse", spousePageTitle);
                     }
                     if (sLatestBirth!=null && (latestBirth[i]==null || 
                           (sLatestBirth + MAX_SPOUSE_GAP) < latestBirth[i])) {
                        latestBirth[i] = sLatestBirth + MAX_SPOUSE_GAP;
                        setBirthCalc(i,"spouse", spousePageTitle);
                     }
                  }
               }
            }   
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }

      // Get info for parents
      if (!parSel.equals("")) {
         String parQuery = "SELECT f.dq_title AS dq_parent_page," +
                           " f.dq_earliest_marriage_year AS dq_earliest_marriage_year," +
                           " f.dq_latest_marriage_year AS dq_latest_marriage_year," +
                           " f.dq_husband_page AS father_page," +
                           " f.dq_wife_page AS mother_page," +
                           " h.dq_earliest_birth_year AS father_dq_earliest_birth_year," +
                           " h.dq_latest_birth_year AS father_dq_latest_birth_year," +
                           " h.dq_latest_death_year AS father_dq_latest_death_year," +
                           " w.dq_earliest_birth_year AS mother_dq_earliest_birth_year," +
                           " w.dq_latest_birth_year AS mother_dq_latest_birth_year," +
                           " w.dq_latest_death_year AS mother_dq_latest_death_year" +
                           " FROM dq_page_analysis f USE INDEX (dq_title)" +
                           " LEFT OUTER JOIN dq_page_analysis h USE INDEX (dq_title)" + 
                           " ON f.dq_husband_page = h.dq_title AND h.dq_job_id = " + jobId + " AND h.dq_namespace = 108" +
                           " LEFT OUTER JOIN dq_page_analysis w USE INDEX (dq_title)" +
                           " ON f.dq_wife_page = w.dq_title AND w.dq_job_id = " + jobId + " AND w.dq_namespace = 108" +
                           " WHERE f.dq_job_id = " + jobId + " AND f.dq_title IN ( " + parSel + 
                           " ) AND f.dq_namespace = 110;";
         try (PreparedStatement stmt = sqlCon.prepareStatement(parQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
               while(rs.next()) {
                  String parPageTitle = SharedUtils.SqlTitle(rs.getString("dq_parent_page"));
                  String fPageTitle = SharedUtils.SqlTitle(rs.getString("father_page"));
                  String mPageTitle = SharedUtils.SqlTitle(rs.getString("mother_page"));
                  Integer parEarliestMarriageYear = getInteger(rs, "dq_earliest_marriage_year");
                  Integer parLatestMarriageYear = getInteger(rs, "dq_latest_marriage_year");
                  Integer fEarliestBirthYear = getInteger(rs, "father_dq_earliest_birth_year");
                  Integer fLatestBirthYear = getInteger(rs, "father_dq_latest_birth_year");
                  Integer fLatestDeathYear = getInteger(rs, "father_dq_latest_death_year");
                  Integer mEarliestBirthYear = getInteger(rs, "mother_dq_earliest_birth_year");
                  Integer mLatestBirthYear = getInteger(rs, "mother_dq_latest_birth_year");
                  Integer mLatestDeathYear = getInteger(rs, "mother_dq_latest_death_year");

                  for (int i=0; i<size; i++) {
                     if (parPageTitle.equals(parentPage[i])) {
                        /* Update calc of earliest and latest birth years */
                        if (parEarliestMarriageYear!=null && 
                              (earliestBirth[i]==null || parEarliestMarriageYear > earliestBirth[i])) {
                           earliestBirth[i] = parEarliestMarriageYear;
                           if (latestBirth[i]==null) {
                              latestBirth[i] = earliestBirth[i] + USUAL_LONGEST_LIFE;  // somewhat arbitrary but needs a value if earliest set
                           }
                           setBirthCalc(i,"parent's marriage","");
                        }
                        if (parLatestMarriageYear!=null && 
                              (latestBirth[i]==null || (parLatestMarriageYear + MAX_AFTER_PARENT_MARRIAGE) < latestBirth[i])) {
                           latestBirth[i] = parLatestMarriageYear + MAX_AFTER_PARENT_MARRIAGE;
                           if (earliestBirth[i]==null) {
                              earliestBirth[i] = latestBirth[i] - USUAL_LONGEST_LIFE;  // somewhat arbitrary but needs a value if latest set
                           }
                           setBirthCalc(i,"parent's marriage","");
                        }
                        if (mEarliestBirthYear!=null && (earliestBirth[i]==null ||
                              (mEarliestBirthYear + USUAL_YOUNGEST_MOTHER) > earliestBirth[i])) {
                           earliestBirth[i] = mEarliestBirthYear + USUAL_YOUNGEST_MOTHER;
                           setBirthCalc(i,"mother's birth", mPageTitle);
                        }
                        if (mLatestBirthYear!=null && (latestBirth[i]==null || 
                              (mLatestBirthYear + USUAL_OLDEST_MOTHER) < latestBirth[i])) {
                           latestBirth[i] = mLatestBirthYear + USUAL_OLDEST_MOTHER;
                           setBirthCalc(i,"mother's birth", mPageTitle);
                        }
                        if (fEarliestBirthYear!=null && (earliestBirth[i]==null || 
                              (fEarliestBirthYear + USUAL_YOUNGEST_FATHER) > earliestBirth[i])) {
                           earliestBirth[i] = fEarliestBirthYear + USUAL_YOUNGEST_FATHER;
                           setBirthCalc(i,"father's birth", fPageTitle);
                        }
                        if (fLatestBirthYear!=null && (latestBirth[i]==null || 
                              (fLatestBirthYear + USUAL_OLDEST_FATHER) < latestBirth[i])) {
                           latestBirth[i] = fLatestBirthYear + USUAL_OLDEST_FATHER;
                           setBirthCalc(i,"father's birth", fPageTitle);
                        }
                        if (mLatestDeathYear!=null && (latestBirth[i]==null || mLatestDeathYear < latestBirth[i])) {
                           latestBirth[i] = mLatestDeathYear;
                           setBirthCalc(i,"mother's death","");
                        }
                        if (fLatestDeathYear!=null && (latestBirth[i]==null || (fLatestDeathYear + 1) < latestBirth[i])) {
                           latestBirth[i] = fLatestDeathYear + 1;
                           setBirthCalc(i,"father's death","");
                        }
                     }
                  }
               }
            } catch (SQLException e) {
               e.printStackTrace();
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      }

      // Get info for siblings (in case there isn't a page for either mother or father)
      if (!parSel.equals("")) {
         String sibQuery = "SELECT s.dq_parent_page AS sibling_dq_parent_page," +
                           " s.dq_title AS sibling_dq_title," +
                           " s.dq_earliest_birth_year AS sibling_dq_earliest_birth_year," +
                           " s.dq_latest_birth_year AS sibling_dq_latest_birth_year" +
                           " FROM dq_page_analysis s USE INDEX (dq_parent_page)" +
                           " WHERE s.dq_job_id = " + jobId + " AND s.dq_parent_page IN ( " + parSel + 
                           " ) AND s.dq_namespace = 108;";
         try (PreparedStatement stmt = sqlCon.prepareStatement(sibQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
               while(rs.next()) {
                  String parPageTitle = SharedUtils.SqlTitle(rs.getString("sibling_dq_parent_page"));
                  String sibPageTitle = SharedUtils.SqlTitle(rs.getString("sibling_dq_title"));
                  Integer sEarliestBirthYear = getInteger(rs, "sibling_dq_earliest_birth_year");
                  Integer sLatestBirthYear = getInteger(rs, "sibling_dq_latest_birth_year");

                  for (int i=0; i<size; i++) {
                     if (parPageTitle.equals(parentPage[i])) {
                        if (sEarliestBirthYear != null && 
                                 (earliestBirth[i]==null || (sEarliestBirthYear - MAX_SIBLING_GAP) > earliestBirth[i])) {
                           earliestBirth[i] = sEarliestBirthYear - MAX_SIBLING_GAP;
                           setBirthCalc(i,"sibling",sibPageTitle);
                        }
                        if (sLatestBirthYear != null && 
                                 (latestBirth[i]==null || (sLatestBirthYear + MAX_SIBLING_GAP) < latestBirth[i])) {
                           latestBirth[i] = sLatestBirthYear + MAX_SIBLING_GAP;
                           setBirthCalc(i,"sibling",sibPageTitle);
                        }
                     }
                  }
               }
            } catch (SQLException e) {
               e.printStackTrace();
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      }
   }

   /* Add the calculation to the tracking string. */
   private void setBirthCalc(int i, String check, String pageTitle) {
      birthCalc[i] += (birthCalc[i].equals("") ? "" : "; ") + check;
      birthCalc[i] += pageTitle.equals("") ? " " : (": <" + pageTitle + "> ");
      birthCalc[i] += earliestBirth[i] + "," + latestBirth[i];
      latestRoundId[i] = round;
   }
   
   private int updateRows(int size) {
      int updatedRows = 0;

      String sql = "UPDATE dq_page_analysis " +
                   "SET dq_earliest_birth_year = ? " +
                   ", dq_latest_birth_year = ? " +
                   ", dq_birth_calc = ? " +
                   "WHERE dq_job_id = " + jobId + 
                   " AND dq_page_id = ? ;";
      try (PreparedStatement stmt = sqlCon.prepareStatement(sql)) {
         for (int i=0; i<size; i++) {
            if (latestRoundId[i] == round) {
               stmt.setInt(1,earliestBirth[i]);
               stmt.setInt(2,latestBirth[i]);
               stmt.setString(3,birthCalc[i]);
               stmt.setInt(4,pageId[i]);
               stmt.executeUpdate();
               updatedRows++;
            }
         }
         commitSql();
      } catch (SQLException e) {
         rollbackSql();
         e.printStackTrace();
      } 
      return updatedRows;
   }

   /* This function both:
      - associates the verification information with each issue and 
      - copies issues to the dq_issue table in the desired order for display on the wiki
        - this not only helps with performance, but also ensures that scrolling works correctly when titles include special characters */
   private void updateVerifiedBy() {
      String ver = "INSERT INTO dq_issue (dqi_job_id, dqi_page_id, dqi_category, dqi_issue_desc, dqi_verified_by) " +
      "SELECT dqi_job_id, dq_page_id, dqi_category, dqi_issue_desc, dqa_action_by " +
      "FROM dq_issue_capture " +
      "INNER JOIN dq_page_analysis ON dqi_job_id = dq_job_id AND dqi_namespace = dq_namespace and dqi_title = dq_title " +
      "LEFT OUTER JOIN " +
      "(SELECT p.dqa_job_id, p.dqa_page_id, p.dqa_desc, CONCAT(p.dqa_action_by, \", \", t.dqa_action_by) AS dqa_action_by " +
         "FROM dq_action p INNER JOIN dq_action t ON p.dqa_job_id = t.dqa_job_id AND p.dqa_namespace IN (108,110) " +
         "AND p.dqa_namespace = t.dqa_namespace-1 AND p.dqa_title = t.dqa_title AND p.dqa_desc = t.dqa_desc " +
         "WHERE p.dqa_action_by <> \"unidentified\" and t.dqa_action_by <> \"unidentified\" " +
         "AND p.dqa_type = \"Anomaly\" " +
      "UNION " +
      "SELECT p.dqa_job_id, p.dqa_page_id, p.dqa_desc, p.dqa_action_by " +
         "FROM dq_action p LEFT OUTER JOIN dq_action t ON p.dqa_job_id = t.dqa_job_id AND p.dqa_namespace = t.dqa_namespace-1 " +
         "AND p.dqa_title = t.dqa_title AND p.dqa_desc = t.dqa_desc " +
         "WHERE p.dqa_namespace IN (108,110) AND t.dqa_page_id IS NULL OR t.dqa_action_by = \"unidentified\" " +
         "AND p.dqa_type = \"Anomaly\" " +
      "UNION " +
      "SELECT t.dqa_job_id, a.dq_page_id, t.dqa_desc, t.dqa_action_by " +
         "FROM dq_action t INNER JOIN dq_page_analysis a ON t.dqa_job_id = a.dq_job_id AND t.dqa_namespace = a.dq_namespace+1 " +
         "AND t.dqa_title = a.dq_title " +
         "LEFT OUTER JOIN dq_action p ON p.dqa_job_id = t.dqa_job_id AND p.dqa_namespace = t.dqa_namespace-1 " +
         "AND p.dqa_title = t.dqa_title AND p.dqa_desc = t.dqa_desc " +
         "WHERE t.dqa_namespace IN (109,111) AND p.dqa_page_id IS NULL OR p.dqa_action_by = \"unidentified\" " +
         "AND t.dqa_type = \"Anomaly\") b " + 
      "ON dqi_job_id = dqa_job_id AND dq_page_id = dqa_page_id AND dqi_category = \"Anomaly\" AND dqi_issue_desc = dqa_desc " +
      "ORDER BY dq_namespace, " +
         "CASE dq_namespace WHEN 108 THEN CONCAT(" +
            "SUBSTRING(dq_title, POSITION('_' IN dq_title)+1, LENGTH(dq_title)-POSITION('_' IN dq_title)-LENGTH(SUBSTRING_INDEX(dq_title,'_',-1))-1)," + // surname
            "',_', SUBSTRING_INDEX(dq_title, '_', 1)," + // given
            "'_', SUBSTRING_INDEX(dq_title, '_', -1)) " + // number
         "ELSE CONCAT(" +
            "SUBSTRING(dq_title, POSITION('_' IN dq_title)+1, POSITION('_and_' IN dq_title)-POSITION('_' IN dq_title)-1)," + // husband surnam
            "',_', SUBSTRING_INDEX(dq_title, '_', 1), '_and_'," + // husband given
            "SUBSTRING(SUBSTRING(dq_title, POSITION('_and_' IN dq_title)+5, LENGTH(dq_title)), " +
	            "POSITION('_' IN SUBSTRING(dq_title, POSITION('_and_' IN dq_title)+5, LENGTH(dq_title)))+1, " +
               "LENGTH(SUBSTRING(dq_title, POSITION('_and_' IN dq_title)+5, LENGTH(dq_title))) - " +
               "POSITION('_' IN SUBSTRING(dq_title, POSITION('_and_' IN dq_title)+5, LENGTH(dq_title))) - " +
               "LENGTH(SUBSTRING_INDEX(dq_title, '_', -1))-1)," + // wife surname
            "',_', SUBSTRING_INDEX(SUBSTRING(dq_title, POSITION('_and_' IN dq_title)+5, LENGTH(dq_title)),'_', 1)," + // wife given 
            "'_', SUBSTRING_INDEX(dq_title, '_', -1)) " + // number
         "END, dqi_issue_desc;";

      try (Statement stmt = sqlCon.createStatement()) {
         stmt.executeUpdate(ver);
         commitSql();
      } catch (SQLException e) {
         e.printStackTrace();
      }
   }

   private void updateDeferredBy() {
      String def = "UPDATE dq_page_analysis, " +
      "(SELECT p.dqa_job_id, p.dqa_page_id, CONCAT(p.dqa_action_by, \"|\", t.dqa_action_by) AS dqa_action_by " +
         "FROM dq_action p INNER JOIN dq_action t ON p.dqa_job_id = t.dqa_job_id AND p.dqa_namespace IN (108,110) " +
         "AND p.dqa_namespace = t.dqa_namespace-1 AND p.dqa_title = t.dqa_title AND p.dqa_desc = t.dqa_desc " +
         "WHERE p.dqa_type = \"Page\" AND p.dqa_desc = \"Deferral\" " +
      "UNION " +
      "SELECT p.dqa_job_id, p.dqa_page_id, p.dqa_action_by " +
         "FROM dq_action p LEFT OUTER JOIN dq_action t ON p.dqa_job_id = t.dqa_job_id AND p.dqa_namespace = t.dqa_namespace-1 " +
         "AND p.dqa_title = t.dqa_title AND p.dqa_desc = t.dqa_desc " +
         "WHERE p.dqa_namespace IN (108,110) AND t.dqa_page_id IS NULL " +
         "AND p.dqa_type = \"Page\" AND p.dqa_desc = \"Deferral\" " +
      "UNION " +
      "SELECT t.dqa_job_id, a.dq_page_id, t.dqa_action_by " +
         "FROM dq_action t INNER JOIN dq_page_analysis a ON t.dqa_job_id = a.dq_job_id AND t.dqa_namespace = a.dq_namespace+1 " +
         "AND t.dqa_title = a.dq_title " +
         "LEFT OUTER JOIN dq_action p ON p.dqa_job_id = t.dqa_job_id AND p.dqa_namespace = t.dqa_namespace-1 " +
         "AND p.dqa_title = t.dqa_title AND p.dqa_desc = t.dqa_desc " +
         "WHERE t.dqa_namespace IN (109,111) AND p.dqa_page_id IS NULL " +
         "AND t.dqa_type = \"Page\" AND t.dqa_desc = \"Deferral\") b " +
      "SET dq_viewed_by = dqa_action_by " +
      "WHERE dq_job_id = dqa_job_id AND dq_page_id = dqa_page_id;";

      try (Statement stmt = sqlCon.createStatement()) {
         // Update dq_viewed_by column of dq_page table
         stmt.executeUpdate(def);
         commitSql();
      } catch (SQLException e) {
         e.printStackTrace();
      }
   }

   private void copyIssues() {
      ResultSet rs;
      Calendar yesterday = Calendar.getInstance();
      yesterday.add(Calendar.DAY_OF_MONTH, -1);
      String effDate = ymdf.format(yesterday.getTime());
      Integer livingTh = thisYear - USUAL_LONGEST_LIFE;

      // First delete rows for all but last 2 runs
      purgeIssues();

      /* Statement to copy pages with issues to dq_page table */
      String copy = "INSERT INTO dq_page SELECT * FROM dq_page_analysis " +
                    "WHERE dq_job_id = " + jobId + 
                     " AND (dq_namespace = 108 " +
                         "AND (dq_earliest_birth_year > dq_latest_birth_year " +
                            "OR ((dq_latest_birth_year IS NULL OR dq_latest_birth_year > " + livingTh + ") " + 
                               "AND dq_latest_death_year IS NULL)) " +
                        "OR (dq_namespace, dq_title) IN " +
                           "(SELECT dqi_namespace, dqi_title FROM dq_issue_capture WHERE dqi_job_id = " + jobId + ")) " +
                    "ORDER BY dq_page_id;";  

      /* Statistics queries */ 
      String total = "SELECT dq_namespace, COUNT(*) AS count FROM dq_page_analysis " +
                     "WHERE dq_job_id = " + jobId + 
                     " GROUP BY dq_namespace;";
      String living = "SELECT COUNT(*) AS count FROM dq_page " +
                      "WHERE dq_job_id = " + jobId + " AND dq_namespace = 108 " +
                      "AND dq_latest_birth_year > " + livingTh + " " + 
                      "AND dq_latest_death_year IS NULL AND dq_famous_ind = 0 AND dq_died_young_ind = 0 " +
                      "AND dq_title <> \"Unknown_(27824)\";";        // 1 possibly living person deemed not needing to be fixed or deleted
      String probLiving = "SELECT COUNT(*) AS count FROM dq_page " +
                      "WHERE dq_job_id = " + jobId + " AND dq_namespace = 108 " +
                      "AND dq_earliest_birth_year > " + livingTh + 
                      " AND dq_latest_death_year IS NULL AND dq_famous_ind = 0 AND dq_died_young_ind = 0 " +
                      "AND dq_title <> \"Unknown_(27824)\";";        // 1 possibly living person deemed not needing to be fixed or deleted
      String noDate = "SELECT COUNT(*) AS count FROM dq_page " +
                      "WHERE dq_job_id = " + jobId + " AND dq_namespace = 108 " +
                      "AND dq_latest_birth_year IS NULL AND dq_latest_death_year IS NULL " + 
                      "AND dq_famous_ind = 0 AND dq_died_young_ind = 0;";
      String chron = "SELECT COUNT(*) AS count FROM dq_page " +
                     "WHERE dq_job_id = " + jobId + " AND dq_namespace = 108 " +
                     "AND dq_latest_birth_year < dq_earliest_birth_year;";                      
      String issue = "SELECT dqi_category, dqi_issue_desc, COUNT(*) AS count FROM dq_issue " +
                     "WHERE dqi_job_id = " + jobId + 
                     " AND dqi_verified_by is null" +    // don't count verified anomalies
                     " GROUP BY dqi_category, dqi_issue_desc ORDER BY dqi_category, dqi_issue_desc;";
      String impact = "SELECT dq_namespace, COUNT(*) AS count FROM " +
                     "(SELECT DISTINCT dq_namespace, dqi_page_id FROM dq_issue " +
                     "INNER JOIN dq_page ON dqi_page_id = dq_page_id AND dqi_job_id = dq_job_id " +
                     "WHERE dqi_job_id = " + jobId + " AND dqi_category IN ('Anomaly', 'Error') AND dqi_verified_by IS NULL) d " +
                     "GROUP BY dq_namespace;";

      /* Statement to write statistics to the stats table */               
      String stats = "INSERT INTO dq_stats (dqs_job_id, dqs_date, dqs_category, dqs_issue_desc, dqs_count) " +
                     "VALUES(" + jobId + ", ? , ? , ? , ? );"; 

      try (Statement stmt = sqlCon.createStatement()) {
         // Copy pages with issues to dq_page
         stmt.executeUpdate(copy);
         commitSql();

         // Output counts of pages and issues
         rs = stmt.executeQuery(total);
         while (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,"Growth");
               insertStmt.setString(3,getInteger(rs,"dq_namespace")==108 ? "Person pages" : "Family pages");
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();

         rs = stmt.executeQuery(living);
         if (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,"Living");
               insertStmt.setString(3,"Potentially living");
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();

         rs = stmt.executeQuery(probLiving);
         if (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,"Living");
               insertStmt.setString(3,"Considered living");
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();

         rs = stmt.executeQuery(noDate);
         if (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,"Incomplete");
               insertStmt.setString(3,"No dates within a few generations");
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();

         rs = stmt.executeQuery(chron);
         if (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,"Relationship");
               insertStmt.setString(3,"Inter-generational chronological issue");
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();

         rs = stmt.executeQuery(issue);
         while (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,rs.getString("dqi_category"));
               insertStmt.setString(3,rs.getString("dqi_issue_desc"));
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();

         rs = stmt.executeQuery(impact);
         while (rs.next()) {
            try (PreparedStatement insertStmt = sqlCon.prepareStatement(stats)) {
               insertStmt.setString(1,effDate);
               insertStmt.setString(2,"Impact");
               insertStmt.setString(3,getInteger(rs,"dq_namespace")==108 ? "Person pages with error/anomaly" : "Family pages with error/anomaly");
               insertStmt.setInt(4,rs.getInt("count"));
               insertStmt.executeUpdate();
               commitSql();
            } catch (SQLException e) {
               rollbackSql();
               e.printStackTrace();
            }
         }
         rs.close();
      } catch (SQLException e) {
         e.printStackTrace();
      }

   }

   private void purgeAnalysis() {
      String purge = "TRUNCATE dq_page_analysis;";
      try (Statement truncate = sqlCon.createStatement()) {
         truncate.executeUpdate(purge);
      } catch (SQLException e) {
         e.printStackTrace();
      }
      purge = "TRUNCATE dq_issue_capture;";
      try (Statement truncate = sqlCon.createStatement()) {
         truncate.executeUpdate(purge);
      } catch (SQLException e) {
         e.printStackTrace();
      }
      purge = "TRUNCATE dq_action;";
      try (Statement truncate = sqlCon.createStatement()) {
         truncate.executeUpdate(purge);
      } catch (SQLException e) {
         e.printStackTrace();
      }
   }

   private void purgeIssues() {
      int[] oldJobId = new int[20];
      String check = "SELECT DISTINCT dq_job_id FROM dq_page ORDER BY dq_job_id;";
      String purge = "DELETE FROM dq_page WHERE dq_job_id = ? ;";
      String purgeIssues = "DELETE FROM dq_issue WHERE dqi_job_id = ? ;";
      
      System.out.print("Purging issues");
      try (PreparedStatement stmt = sqlCon.prepareStatement(check);
           PreparedStatement delete = sqlCon.prepareStatement(purge);
           PreparedStatement delIss = sqlCon.prepareStatement(purgeIssues)) {
         int i=0;
         try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               oldJobId[i++] = rs.getInt("dq_job_id");
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
         // Delete rows for all but last 2 job_id's
         for (int j=0; j<i-2; j++) {
            try {
               delete.setInt(1,oldJobId[j]);
               delete.executeUpdate();
               delIss.setInt(1,oldJobId[j]);
               delIss.executeUpdate();
               commitSql();
               System.out.print(".");
            } catch (SQLException e) {
               e.printStackTrace();
            }
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }
      System.out.println();
   }

   /* This function drops secondary indexes on dq_page_analysis to improve performance during initial population in round 1. */
   private void dropIndexes() {
      String drop;
      String[] index = getIndexes();
      for (int i=0; index[i] != null; i++) {
         if (!index[i].equals("PRIMARY")) {
            try (Statement stmt = sqlCon.createStatement()) {
               drop = "DROP INDEX " + index[i] + " ON dq_page_analysis;";
               stmt.executeUpdate(drop);
            } catch (SQLException e) {
               e.printStackTrace();
            }
         }
      } 
   }

   private void createIndexes() {
      String[][] indexStmt = new String[2][4];
      indexStmt[0][0] = "dq_title";
      indexStmt[0][1] = "dq_parent_page";
      indexStmt[0][2] = "dq_husband_page";
      indexStmt[0][3] = "dq_wife_page";
      indexStmt[1][0] = "CREATE UNIQUE INDEX dq_title ON dq_page_analysis (dq_title, dq_job_id, dq_namespace);";
      indexStmt[1][1] = "CREATE INDEX dq_parent_page ON dq_page_analysis (dq_parent_page, dq_job_id, dq_namespace);";
      indexStmt[1][2] = "CREATE INDEX dq_husband_page ON dq_page_analysis (dq_husband_page, dq_job_id, dq_namespace);";
      indexStmt[1][3] = "CREATE INDEX dq_wife_page ON dq_page_analysis (dq_wife_page, dq_job_id, dq_namespace);";

      String[] index = getIndexes();
      if (index[3] == null) {
         System.out.print("Creating indexes");
      }
      for (int i=0; i<4; i++) {
         boolean indexExists = false;
         for (int j=0; index[j] != null; j++) {
            if (index[j].equals(indexStmt[0][i])) {
               indexExists = true;
               break;
            }
         }
         if (!indexExists) {
            try (Statement stmt = sqlCon.createStatement()) {
               stmt.executeUpdate(indexStmt[1][i]);
               System.out.print(".");
            } catch (SQLException e) {
               e.printStackTrace();
            }
         }
      }
      if (index[3] == null) {
         System.out.println();
      }
   }

   private String[] getIndexes() {
      String[] index = new String[20];
      String query = "SELECT index_name FROM information_schema.statistics " +
                     "WHERE table_schema = \"wikidb\" AND table_name = \"dq_page_analysis\" " +
                     "AND seq_in_index = 1;";
      try (Statement stmt = sqlCon.createStatement()) {
         try (ResultSet rs = stmt.executeQuery(query)) {
            for (int i=0; rs.next(); i++) {
               index[i] = rs.getString("index_name");
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }
      return index;
   }

   private boolean setJobId() {
      String analysisJob = "SELECT dq_job_id FROM dq_page_analysis ORDER BY dq_job_id DESC LIMIT 1;";
      String issuesJob = "SELECT dq_job_id FROM dq_page ORDER BY dq_job_id DESC LIMIT 1;";
      String issueJob = "SELECT dqi_job_id FROM dq_issue ORDER BY dqi_job_id DESC LIMIT 1;";
      String statsJob = "SELECT dqs_job_id FROM dq_stats ORDER BY dqs_job_id DESC LIMIT 1;";
      ResultSet rs;
      try (Statement stmt = sqlCon.createStatement()) {

         /* If extending a job, jobId is set to last jobId in dq_page_analysis
            and there cannot be any records in dq_page or dq_stats for this or a later job. */
         if (round>1) {
            rs = stmt.executeQuery(analysisJob);
            if (rs.next()) {
               jobId = rs.getInt("dq_job_id");
            }
            else {
               logger.error("Cannot extend a job because dq_page_analysis is empty");
               System.out.println("Cannot extend a job because dq_page_analysis is empty");
               return false;
            }
            rs.close();
            rs = stmt.executeQuery(issuesJob);
            if (rs.next()) {
               int lastJobId = rs.getInt("dq_job_id");
               if (jobId <= lastJobId) {
                  logger.error("Cannot extend job " + jobId + "; there are records in dq_page (and maybe dq_stats) for this or a later job");
                  System.out.println("Cannot extend job " + jobId + "; there are records in dq_page (and maybe dq_stats) for this or a later job");
                  return false;
               }
            }
            rs.close();
            rs = stmt.executeQuery(statsJob);
            if (rs.next()) {
               int lastJobId = rs.getInt("dqs_job_id");
               if (jobId <= lastJobId) {
                  logger.error("Cannot extend job " + jobId + "; there are records in dq_stats for this or a later job");
                  System.out.println("Cannot extend job " + jobId + "; there are records in dq_stats for this or a later job");
                  return false;
               }
            }
            rs.close();
         }

         /* If starting at first round, jobId is 1 higher than last jobId in dq_issue. */
         else {
            rs = stmt.executeQuery(issueJob);
            if (rs.next()) {
               jobId = rs.getInt("dqi_job_id")+1;
            }
            else {
               jobId = 1;
            }
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }
      return true;
   }

   private void writeCacheTime(String cacheTime) {
      String select = "SELECT COUNT(*) AS count FROM querycache_info WHERE qci_type = 'AnalyzeDataQuality';";
      String insert = "INSERT INTO querycache_info (qci_type, qci_timestamp) " +
            "VALUES ('AnalyzeDataQuality', '" + cacheTime + "');";
      String update = "UPDATE querycache_info SET qci_timestamp = '" +
            cacheTime + "' WHERE qci_type = 'AnalyzeDataQuality';";
      try (Statement stmt = sqlCon.createStatement()) {
         ResultSet rs = stmt.executeQuery(select);
         if (rs.next()) {
            if (rs.getInt("count") > 0) {
               stmt.executeUpdate(update);
            }
            else {
               stmt.executeUpdate(insert);
            }
            commitSql();
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }
   }

   private void openSqlConnection(String dbHost, String userName, String password) {
      try {
         Class.forName("com.mysql.jdbc.Driver").newInstance();
         sqlCon = DriverManager.getConnection("jdbc:mysql://" + dbHost + 
                  "/wikidb?useTimezone=true&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&user=" + 
                  userName + "&password=" + password);
         sqlCon.setAutoCommit(false);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private void commitSql() {
      try {
         sqlCon.commit();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private void rollbackSql() {
      try {
         sqlCon.rollback();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private void closeSqlConnection() {
      try {
         sqlCon.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   // Utility copied from www.java2s.com copyright 2016 under Apache License
   public static Integer getInteger(final ResultSet rs, final String field) throws SQLException {
      if (rs.getObject(field) == null) {
         return null;
      }
      return Integer.valueOf(rs.getInt(field));
   }

   // Analyze pages, determine earliest and latest birth year if missing and report errors and anomalies
   // args array: 0=pages.xml 1=databasehost 2=username 3=password 4=startround 5=endround
   public static void main(String[] args)
           throws IOException, ParsingException
   {

      // Initialize
      AnalyzeDataQuality self = new AnalyzeDataQuality();
      
      // Templates that verify anomalies
      aTemplates.put("UnusuallyYoungWife", FamilyDQAnalysis.YOUNG_SPOUSE[1].replace("<role>", "Wife"));
      aTemplates.put("UnusuallyYoungHusband", FamilyDQAnalysis.YOUNG_SPOUSE[1].replace("<role>", "Husband"));
      aTemplates.put("UnusuallyOldWife", FamilyDQAnalysis.OLD_SPOUSE[1].replace("<role>", "Wife"));
      aTemplates.put("UnusuallyOldHusband", FamilyDQAnalysis.OLD_SPOUSE[1].replace("<role>", "Husband"));
      aTemplates.put("BirthBeforeParentsMarriage", FamilyDQAnalysis.BEF_MARR[1]);
      aTemplates.put("UnusuallyYoungMother", FamilyDQAnalysis.YOUNG_MOTHER[1]);
      aTemplates.put("UnusuallyYoungFather", FamilyDQAnalysis.YOUNG_FATHER[1]);
      aTemplates.put("BirthLongAfterParentsMarriage", FamilyDQAnalysis.LONG_AFT_MARR[1]);
      aTemplates.put("UnusuallyOldMother", FamilyDQAnalysis.OLD_MOTHER[1]);
      aTemplates.put("UnusuallyOldFather", FamilyDQAnalysis.OLD_FATHER[1]);
      aTemplates.put("BaptismAfterMothersDeath", FamilyDQAnalysis.CHR_DEAD_MOTHER[1]);
      aTemplates.put("BaptismWellAfterFathersDeath", FamilyDQAnalysis.CHR_DEAD_FATHER[1]);

      // By default, go from round 1 to round 4, but user can override (reduce rounds or extend a previous job by adding rounds)
      round = 1;
      if (args.length > 4) {
         round = Integer.parseInt(args[4]);
      }
      int endRound = 4;
      if (args.length > 5) {
         endRound = Integer.parseInt(args[5]);
      }
      self.openSqlConnection(args[1], args[2], args[3]);
      if (self.setJobId()) {
         logger.info("Job #" + jobId + " started at " + logdtf.format(startTime.getTime()));
         System.out.println("Job #" + jobId + " started at " + logdtf.format(startTime.getTime()));

         // Initial round - create rows in dq_page_analysis table (after truncating the table)
         if (round == 1) {
            WikiReader wikiReader = new WikiReader();
            wikiReader.setSkipRedirects(true);
            wikiReader.addWikiPageParser(self);
            InputStream in = new FileInputStream(args[0]);

            self.dropIndexes();
            self.purgeAnalysis();
            try { 
               wikiReader.read(in);
            } catch (ParsingException e) {
               e.printStackTrace();
            } finally {
               self.insertRows(); // Last set of rows
               self.insertIssueRows();
               self.insertActionRows();
               in.close();
            }
            self.createIndexes();
            self.updateVerifiedBy();
            self.updateDeferredBy();

            Calendar roundTime = Calendar.getInstance();
            logger.info("Job #" + jobId + " round 1 ended at " + logdtf.format(roundTime.getTime()));
            System.out.println("Job #" + jobId + " round 1 ended at " + logdtf.format(roundTime.getTime()));
            round++;
         }

         // Subsequent rounds to derive dates based on dates in immediate family. Create indexes if missing.
         while (round <= endRound) {
            self.createIndexes();
            self.nextRound();
            round++;
         }

         // Copy rows indicating an issue to the dq_page table, and output counts of issue types and other statistics.
         self.copyIssues();

         Calendar endTime = Calendar.getInstance();
         logger.info("Job #" + jobId + " ended at " + logdtf.format(endTime.getTime()));
         System.out.println("Job #" + jobId + " ended at " + logdtf.format(endTime.getTime()));
         self.writeCacheTime(cachedtf.format(startTime.getTime()));
      }
      else {
         logger.error("Job failed");
         System.out.println("Job failed");
      }
      self.closeSqlConnection();
      
      System.exit(0);     // to prevent problems with cleaning up threads (from use of SQL)
   }
}
