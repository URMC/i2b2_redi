/**
 * Copyright 2016 , University of Rochester Medical Center
 * 
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 * @author cculbertson1 (curtis_culbertson@urmc.rochester.edu)
 */
package edu.rochester.urmc.i2b2.DataDictionary;

import edu.rochester.urmc.i2b2.SQLQueryJob;
import edu.rochester.urmc.util.SQLUtilities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


/**
 * This class is the main class that controls the identification, correction
 * and aggregation of data found in i2b2.
 * 
 * @author cculbertson1
 */
public class DataDictionaryController extends SQLQueryJob {
    
    /**
     * Datetime formatted as single string
     */
    SimpleDateFormat DT_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    /**
     * Date formatted as single string
     */
    SimpleDateFormat SDT_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /**
     * Datetime in human readable format
     */
    SimpleDateFormat HUMAN_DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Date in human readable format
     */
    SimpleDateFormat HUMAN_DATE = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Time in human readable format
     */
    SimpleDateFormat HUMAN_TIME = new SimpleDateFormat("HH:mm:ss");
    
    // Change this to move between using CCULBERTSON or i2b2 schemata

    /**
     * Schema for the ontology
     */
    public String ontSchema = "I2B2METADATA";

    /**
     * Schema for the data
     */
    public String crcSchema = "I2B2DEMODATA";

    /**
     * Schema for the aggregates
     */
    public String aggSchema = "I2B2AGGREGATES";
    
    //The number of threads to run concurrently

    /**
     * Maximum number of threads running in parallel
     */
    private final int maxThreads = 7;
    
    /**
     * First collection date to process
     */
    public Date beginDate = null;

    /**
     * Last collection date to process
     */
    public Date endDate = null;

    /**
     * Previous progress
     */
    public Date lastDateProcessed = null;

    /**
     * Count of updated observations
     */
    public int numUpdated = 0;

    /**
     * Count of processed observations
     */
    public int numProcessed = 0;
    
    /**
     * All demographic combos
     */
    public HashMap[] comboLookup;
    
    // All of the combo variables to be updated by threads

    /**
     * Array of dates for each combo
     */
    public Date[] earliestColDate;

    /**
     * Array of dates for each combo
     */
    public Date[] latestColDate;

    /**
     * Array of counts for each combo
     */
    public int[] conceptCount;

    /**
     * Array of counts for each combo
     */
    public int[] patientCount;

    /**
     * Array of values for each combo
     */
    public BigDecimal[] minValue;

    /**
     * Array of values for each combo
     */
    public BigDecimal[] maxValue;

    /**
     * Array of values for each combo
     */
    public BigDecimal[] average;

    /**
     * Array of values for each combo
     */
    public BigDecimal[] stddev;

    /**
     * Array of counts for each combo
     */
    public int[] abnormalTotal;

    /**
     * Array of counts for each combo
     */
    public int[] abnormalLow;

    /**
     * Array of counts for each combo
     */
    public int[] abnormalHigh;

    /**
     * Array of counts for each combo
     */
    public int[] abnormalOther;
    
    /**
     * Running total of concept dates processed
     */
    public int totalConceptDatesProcessed = 0;
     
    public void run(){
        this.updateStatus("Running",1);
        try{
            Date runStart = new Date();
            HUMAN_DATE.setTimeZone(TimeZone.getTimeZone("EST"));
            
            comboLookup = SQLUtilities.getTableHashedArray(datadb,
                "SELECT "
                    + " COMBO_ID "
                    + ",COMBO_DESCRIPTION "
                    + ",SEX_CD "
                    + ",AGE_BRACKET "
                    + ",RACE_CD "
                    + ",ETHNICITY_CD " +
                "FROM "
                    + aggSchema + ".COMBO_DIMENSION " +
                "ORDER BY "
                    + "COMBO_ID");
            
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Adding new concepts");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": Adding new concepts");
            insertAggFactConcepts();
            
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Creating and monitoring threads");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": Creating and monitoring threads");
            
            createAndMonitorThreads();
            
            double runtime = (new Date().getTime() - runStart.getTime());
            long conceptDatesPerHour = Math.round(totalConceptDatesProcessed/(runtime/3600000.0));
            
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Total run time: " + runtime + " ms.");
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": This is a rate of concept " + conceptDatesPerHour + " dates per hour.");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": Total run time: " + runtime + " ms.");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": This is a rate of concept " + conceptDatesPerHour + " dates per hour.");
            
            this.updateStatus("Complete");
        } catch (Exception ex ){
            logError(ex);
            this.updateStatus("Error");
        }
    }
    
    /**
     * Adds new rows to AGGREGATE_FACT. Each concept will have rows for each
     * demographic combo represented in COMBO_DIMENSION.
     * 
     * @throws Exception
     */
    private void insertAggFactConcepts() throws Exception {
        HashMap[] newConcepts = SQLUtilities.getTableHashedArray(datadb,
                "SELECT "
                        + " CONCEPT_CD "
                        + ",UNITS_CD " +
                "FROM (" +
                "    SELECT "
                        + " DISTINCT O.CONCEPT_CD AS CONCEPT_CD "
                        + ",O.UNITS_CD " +
                "    FROM "
                        + crcSchema + ".OBSERVATION_FACT O " 
                        + "LEFT JOIN " + aggSchema + ".AGGREGATE_FACT A "
                            + "ON O.CONCEPT_CD = A.CONCEPT_CD " +
                "    WHERE "
                        + "A.CONCEPT_CD IS NULL "
                        + "AND O.CONCEPT_CD LIKE 'URMC|LABS:CHEM:%')" +
                "ORDER BY CONCEPT_CD");
        
        BigDecimal maxConceptID = SQLUtilities.getMaxValue(datadb,aggSchema,"AGGREGATE_FACT","CONCEPT_ID");
        int nextConceptID = maxConceptID != null ? maxConceptID.intValue()+1 : 0;
        
        try (PreparedStatement insertAggregateFactStmt = datadb.prepareStatement(
                "INSERT INTO " + aggSchema + ".AGGREGATE_FACT (CONCEPT_ID, CONCEPT_CD, COMBO_ID, UNITS_CD) " +
                "VALUES(?,?,?,?)")) {
            for(HashMap newConcept : newConcepts) {
                for(HashMap combo : comboLookup) {
                    insertAggregateFactStmt.setBigDecimal(1, new BigDecimal(nextConceptID));
                    insertAggregateFactStmt.setString(2, newConcept.get("CONCEPT_CD").toString());
                    insertAggregateFactStmt.setBigDecimal(3, (BigDecimal) combo.get("COMBO_ID"));
                    insertAggregateFactStmt.setString(4, newConcept.get("UNITS_CD").toString());
                    insertAggregateFactStmt.addBatch();
                }
                nextConceptID++;
            }
            
            int numNewConceptCombos = insertAggregateFactStmt.executeBatch().length;
            
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": New concept codes: " + newConcepts.length + " New concept/combo rows: " + numNewConceptCombos);
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": New concept codes: " + newConcepts.length + " New concept/combo rows: " + numNewConceptCombos);
        }  
    }
    
    /**
     * Simple function to add days to a date. Use negative days to subtract.
     * 
     * @param date the date to add to
     * @param days how many days to add
     * @return a Date object with the added days
     */
    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }
    
    /**
     * This method creates a processing thread for each concept in 
     * observation_fact, and this shepherds 5 threads at a time through until 
     * completion or rescheduling. This maintains a separate reschedule flag 
     * from the normal SQL job because we would like to know when the threads 
     * finish gracefully when a cancel has occurred.
     * 
     * @throws Exception - failure for any reason.
     */
    private void createAndMonitorThreads() throws Exception {
        boolean timeIsUp = false;
        
        /**
        * We're going to create concept/date processing threads, 7 of them 
        * always, and start them separately. This thread will check on each 
        * thread every second, and unless it was asked to be rescheduled, it 
        * will check whether it was rescheduled at the beginning of creating a 
        * new processing thread. 
        */
        int counter = 0; 
        int total = 0;

        // Get parameters
        String originalParams = project.get("PARAMS").toString();
        String[] params = originalParams.split(",",-1);
        String beginDateParam = params[0].isEmpty() ? "20110301000000" : params[0];

        /**
        * If no endDate parameter is specified it will run through the
        * current date.
        */
        String endDateParam = params[1].isEmpty() ? DT_FORMAT.format(new Date()) : params[1];

        // Variables for tracking process
        beginDate = DT_FORMAT.parse(beginDateParam);
        endDate = DT_FORMAT.parse(endDateParam);

        System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Started processing");
        log(HUMAN_DATETIME.format(new Date().getTime()) + ": Started processing");

        /**
        * Get concept statuses
        * Including DISTINCT in query because we only want to count each
        * concept once.
        */
        HashMap[] concepts = SQLUtilities.getTableHashedArray(datadb,
                    "SELECT\n" +
                    "     DISTINCT CONCEPT_CD\n" +
                    "    ,CONCEPT_ID\n" +
                    "    ,UNITS_CD\n" +
                    "    ,PROCESSED_THROUGH\n" +
                    "FROM\n" +
                    "    " + aggSchema + ".AGGREGATE_FACT\n" +
                    "WHERE\n" +
                    "    TRUNC(TO_DATE('" + endDateParam + "','yyyymmddhh24miss')) - TRUNC(PROCESSED_THROUGH) > 0\n" +
                    "    OR PROCESSED_THROUGH IS NULL\n" +
                    "ORDER BY\n" +
                    "    CONCEPT_ID");

        /**
        * Establish a total number of concept/dates by iterating through concepts
        * and then iterating through processedThrough+1 to endDate. Running sum.
        * This lets us get the progress %.
        */
        for(HashMap concept : concepts) {
            Date processedThrough = (Date) concept.get("PROCESSED_THROUGH");
            
            for(Date dateCursor = processedThrough != null ? addDays(processedThrough,1) : beginDate; 
                    dateCursor.before(endDate) || dateCursor.equals(endDate); 
                    dateCursor = addDays(dateCursor,1)) {

                total++;
            }
        }

        /**
         * Only create threads for one concept at a time. This is so when 
         * threads move into different concepts the progress tracking doesn't
         * get too complicated. At worst the last thread of a concept waits for
         * the others to finish before moving on to a new concept.
         */
        for(HashMap concept : concepts) {
            LinkedList<Thread> workers = new LinkedList<Thread>();
            String conceptCode = concept.get("CONCEPT_CD").toString();
            String unitsCode = concept.get("UNITS_CD").toString();
            Date processedThrough = (Date) concept.get("PROCESSED_THROUGH");
            
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Analyzing " + conceptCode + "(" + unitsCode + ").");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": Analyzing " + conceptCode + "(" + unitsCode + ").");            
            
            /**
            * Get the previously established values so we can merge results. 
            * There should be a row for each combo.
            */            
            HashMap[] conceptAggregates = SQLUtilities.getTableHashedArray(datadb,
                    "SELECT "
                        + " COMBO_ID "
                        + ",CONCEPT_CD "
                        + ",UNITS_CD "
                        + ",PROCESSED_THROUGH "
                        + ",EARLIEST_COLDATE "
                        + ",LATEST_COLDATE "
                        + ",CONCEPT_COUNT "
                        + ",PATIENT_COUNT "
                        + ",MIN_VALUE "
                        + ",MAX_VALUE "
                        + ",AVERAGE "
                        + ",STDDEV "
                        + ",ABNORMAL_TOTAL "
                        + ",ABNORMAL_LOW "
                        + ",ABNORMAL_HIGH "
                        + ",ABNORMAL_OTHER "
                +   "FROM " + aggSchema + ".AGGREGATE_FACT "
                +   "WHERE "
                        + "CONCEPT_CD = '" + concept.get("CONCEPT_CD").toString() + "' "
                        + "AND UNITS_CD = '" + concept.get("UNITS_CD").toString() + "' "
                +   "ORDER BY CONCEPT_ID, COMBO_ID");

            // Reinitialize tracking variables for this concept
            lastDateProcessed = processedThrough != null ? processedThrough : addDays(beginDate,-1);
            numProcessed = 0;
            numUpdated = 0;
            
            earliestColDate = new Date[comboLookup.length];
            latestColDate = new Date[comboLookup.length];
            conceptCount = new int[comboLookup.length];
            patientCount = new int[comboLookup.length];
            minValue = new BigDecimal[comboLookup.length];
            maxValue = new BigDecimal[comboLookup.length];
            average = new BigDecimal[comboLookup.length];
            stddev = new BigDecimal[comboLookup.length];
            abnormalTotal = new int[comboLookup.length];
            abnormalLow = new int[comboLookup.length];
            abnormalHigh = new int[comboLookup.length];
            abnormalOther = new int[comboLookup.length];
            
            // Initialize values to their stored state
            for(HashMap conceptAggregate : conceptAggregates) {
                int comboID = Integer.parseInt(conceptAggregate.get("COMBO_ID").toString());
                
                if(conceptAggregate.get("EARLIEST_COLDATE") != null) {
                    earliestColDate[comboID] = HUMAN_DATETIME.parse(conceptAggregate.get("EARLIEST_COLDATE").toString());
                }
                
                if(conceptAggregate.get("LATEST_COLDATE") != null) {
                    latestColDate[comboID] = HUMAN_DATETIME.parse(conceptAggregate.get("LATEST_COLDATE").toString());
                }
                
                if(conceptAggregate.get("CONCEPT_COUNT") != null) {
                    conceptCount[comboID] = Integer.parseInt(conceptAggregate.get("CONCEPT_COUNT").toString());
                }
                
                // Distinct patient counts can't be incremented, so set to 0 and requery entire dataset
                patientCount[comboID] = 0;
                
                if(conceptAggregate.get("MIN_VALUE") != null) {
                    minValue[comboID] = new BigDecimal(conceptAggregate.get("MIN_VALUE").toString());
                }
                
                if(conceptAggregate.get("MAX_VALUE") != null) {
                    maxValue[comboID] =  new BigDecimal(conceptAggregate.get("MAX_VALUE").toString());
                }
                
                if(conceptAggregate.get("AVERAGE") != null) {
                    average[comboID] =  new BigDecimal(conceptAggregate.get("AVERAGE").toString());
                }
                
                if(conceptAggregate.get("STDDEV") != null) {
                    stddev[comboID] =  new BigDecimal(conceptAggregate.get("STDDEV").toString());
                }
                
                if(conceptAggregate.get("ABNORMAL_TOTAL") != null) {
                    abnormalTotal[comboID] = Integer.parseInt(conceptAggregate.get("ABNORMAL_TOTAL").toString());
                }
                
                if(conceptAggregate.get("ABNORMAL_LOW") != null) {
                    abnormalLow[comboID] = Integer.parseInt(conceptAggregate.get("ABNORMAL_LOW").toString());
                }
                
                if(conceptAggregate.get("ABNORMAL_HIGH") != null) {
                    abnormalHigh[comboID] = Integer.parseInt(conceptAggregate.get("ABNORMAL_HIGH").toString());
                }
                
                if(conceptAggregate.get("ABNORMAL_OTHER") != null) {
                    abnormalOther[comboID] = Integer.parseInt(conceptAggregate.get("ABNORMAL_OTHER").toString());
                }
            }
            
            // Build list of threads, one for each collection date in the concept that hasn't been identified
            for(Date dateCursor = processedThrough != null ? addDays(processedThrough,1) : beginDate; 
                    dateCursor.before(endDate) || dateCursor.equals(endDate); 
                    dateCursor = addDays(dateCursor,1)) {
                workers.add(new DataDictionaryThread(this, conceptCode, unitsCode, dateCursor));
            }

            // Start and monitor threads
            while( workers.size() > 0 ) {
                boolean displayFinish = false;
                int numberAlive = 0;
                Iterator<Thread> i = workers.iterator();

                // Iterate over remaining threads
                while (i.hasNext()) {
                    Thread worker = i.next();
                    Thread.State state = worker.getState();
                    if( state == state.TERMINATED ){
                        i.remove();
                        counter++;
                        displayFinish = true;

                        // Log progress for roughly every 3 months of a concept processed
                        if( counter % 90 == 0 ) {
                            log("Finished " + (counter) + " concept dates");
                        }
                    } else if( state != state.NEW ) {
                        numberAlive ++;
                    // Remove the thread if we are in an unrunnable state and it is NEW (unstarted)
                    } else if( state == state.NEW && !isRunnable() ) {
                        i.remove();
                    }
                }

                // We have entered an unrunnable state for the first time
                if(!timeIsUp && !isRunnable() ){
                    log("-------------Time's up!------------");
                    timeIsUp = true;
                }

                if( displayFinish & timeIsUp){
                    log("Thread has finished, time is up. " + numberAlive + " still running.");
                }

                // Start threads until there is a full compliment as long as there are NEW threads and time is not up
                for( int news = numberAlive; !timeIsUp && news < this.maxThreads && news < workers.size() ; news++ ) {
                    // Search for a thread to start
                    for (Thread worker : workers ) {
                        if( worker.getState() == Thread.State.NEW ) {
                            worker.start();
                            break;
                        }
                    }
                } 
                sleep(2500);                
                this.updateStatus("Running",(int)((100.0*counter)/(0.01+total)));
                
            } //while there are threads (collection dates)
 
            // Update the fact for current concept
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Running statistics for " + conceptCode + "(" + unitsCode + ").");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": Running statistics for " + conceptCode + "(" + unitsCode + ").");
            updateAggFact(conceptCode,unitsCode);
            
            
            
            // Update the ontology with the concept's new statistics
            String conceptHeader = getConceptHeader(conceptCode, unitsCode);
            
            if(!conceptHeader.equals("~EMPTY~")) {
                System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Generating HTML for " + conceptCode + "(" + unitsCode + ").");
                log(HUMAN_DATETIME.format(new Date().getTime()) + ": Generating HTML for " + conceptCode + "(" + unitsCode + ").");

                updateOntologyStats(conceptCode,
                        conceptHeader + 
                        getConceptStatTable(conceptCode, unitsCode, "Overview","@","COMBO_DESCRIPTION") + 
                        getConceptStatTable(conceptCode, unitsCode, "Gender","SEX_CD","COMBO_DESCRIPTION") + 
                        getConceptStatTable(conceptCode, unitsCode, "Age x Gender","SEX_CD","AGE_BRACKET") + 
                        getConceptStatTable(conceptCode, unitsCode, "Age x Race","RACE_CD","AGE_BRACKET") + 
                        getConceptStatTable(conceptCode, unitsCode, "Ethnicity","ETHNIC_CD","COMBO_DESCRIPTION") + 
                        getConceptFooter());
            } else {
                System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": No entry in ontology for " + conceptCode + "(" + unitsCode + ").");
                log(HUMAN_DATETIME.format(new Date().getTime()) + ": No entry in ontology for " + conceptCode + "(" + unitsCode + ").");
            }
            
            System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Finished " + conceptCode + "(" + unitsCode + "). " + numProcessed + " lines processed, " + numUpdated + " observations modified.");
            log(HUMAN_DATETIME.format(new Date().getTime()) + ": Finished " + conceptCode + "(" + unitsCode + "). " + numProcessed + " lines processed, " + numUpdated + " observations modified.");
            
            // We do another check here so we don't needlessly queue up more 
            // threads for the remaining concepts if we just hit an unrunnable 
            // state.
            if(!isRunnable() && !timeIsUp) {
                log("-------------Time is up!------------");
                timeIsUp = true;
                System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": I should stop now: Concept");
                log(HUMAN_DATETIME.format(new Date().getTime()) + ": I should stop now: Concept");
                break;
            } else if(!isRunnable() && timeIsUp) {
                System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": I should stop now: Concept");
                log(HUMAN_DATETIME.format(new Date().getTime()) + ": I should stop now: Concept");
                break;
            }
        } // End of concept
        
        log("Done processing, "+ counter+ " concept dates processed.");
        totalConceptDatesProcessed += counter;
    }
    
    /**
     * Safely sets the last date processed to the supplied date, but only if
     * the date is greater than the current value. This is to account for
     * threads finishing out of order.
     *
     * @param date the date to set
     */
    public synchronized void setLastDateProcessed(Date date) {
        if(lastDateProcessed == null) {
            lastDateProcessed = date;
        }
        else if(date.after(lastDateProcessed)) {
            lastDateProcessed = date;
        }
    }
    
    /**
     * Safely increments numProcessed
     *
     * @param increment the value to increment by
     */
    public synchronized void incrementNumProcessed(int increment) {
        this.numProcessed += increment;
    }
    
    /**
     * Safely increments numUpdated
     *
     * @param increment the value to increment by
     */
    public synchronized void incrementNumUpdated(int increment) {
        this.numUpdated += increment;
    }
    
    /**
     * Updates AGGREGATE_FACT to note processing status of each concept
     * 
     * @param conceptCode the concept code
     * @param lastDateProcessed the last date that was processed
     * @throws Exception
     */
    private void updateAggFact(String conceptCode, String unitsCode) throws Exception {
        try (
                PreparedStatement updateAggFactStmt = datadb.prepareStatement(
                    "UPDATE " + aggSchema + ".AGGREGATE_FACT " +
                    "SET "
                            + " PROCESSED_THROUGH = TO_DATE(?,'yyyymmddhh24miss') "
                            + ",EARLIEST_COLDATE = TO_DATE(?,'yyyymmddhh24miss') "
                            + ",LATEST_COLDATE = TO_DATE(?,'yyyymmddhh24miss') "
                            + ",CONCEPT_COUNT = ? "
                            + ",PATIENT_COUNT = ? "
                            + ",MIN_VALUE = ? "
                            + ",MAX_VALUE = ? "
                            + ",AVERAGE = ? "
                            + ",STDDEV = ? "
                            + ",ABNORMAL_TOTAL = ? "
                            + ",ABNORMAL_LOW = ? "
                            + ",ABNORMAL_HIGH = ? "
                            + ",ABNORMAL_OTHER= ? " +
                    "WHERE "
                            + "CONCEPT_CD = ? "
                            + "AND COMBO_ID = ? "
                            + "AND UNITS_CD = ? ")
                ) {
            
            // Clear the temp patient table
            try(CallableStatement truncStat = datadb.prepareCall("{call " + aggSchema + ".TRUNC_TEMP_PAT_DIM()}")) {
                truncStat.executeQuery();
            }
            
            // Populate the temp patient table with this concept's patients
            SQLUtilities.execSQL(datadb,
                    "INSERT INTO " + aggSchema + ".TEMP_PATIENT_DIMENSION\n" +
                    "SELECT \n" +
                    "     DISTINCT P.PATIENT_NUM\n" +
                    "    ,P.SEX_CD\n" +
                    "    ,(CASE\n" +
                    "        WHEN FLOOR((O.START_DATE-P.BIRTH_DATE)/365.25) >= 18 THEN '18+'\n" +
                    "        WHEN FLOOR((O.START_DATE-P.BIRTH_DATE)/365.25) < 18 THEN '0-17'\n" +
                    "        END) AS AGE_BRACKET\n" +
                    "    ,P.RACE_CD\n" +
                    "    ,P.ETHNICITY_CD\n" +
                    "FROM \n" +
                    "    " + crcSchema + ".OBSERVATION_FACT O\n" +
                    "    JOIN I2B2DEMODATA.PATIENT_DIMENSION P\n" +
                    "        ON O.PATIENT_NUM = P.PATIENT_NUM\n" +
                    "WHERE \n" +
                    "    CONCEPT_CD = '" + conceptCode + "'\n" +
                    "    AND UNITS_CD = '" + unitsCode + "'\n" +
                    "    AND START_DATE BETWEEN TO_DATE('20110301000000','yyyymmddHH24miss') AND TO_DATE('" + SDT_FORMAT.format(lastDateProcessed) + "235959','yyyymmddHH24miss')\n" +
                    "    AND VALTYPE_CD = 'N'\n" +
                    "    AND TVAL_CHAR = 'E'\n" +
                    "ORDER BY PATIENT_NUM");

            for(HashMap combo : comboLookup) {
                int comboID = Integer.parseInt(combo.get("COMBO_ID").toString());
                String comboDesc = combo.get("COMBO_DESCRIPTION").toString();
                
                
                if(conceptCount[comboID] > 0) {
                    List<String> raceCodes = Arrays.asList(combo.get("RACE_CD").toString().split(",",-1));
                    List<String> ethnicityCodes = Arrays.asList(combo.get("ETHNICITY_CD").toString().split(",",-1));          
                    
                    // Build the patient count query for this demographic combination
                    String patCountSQL = 
                            "SELECT\n" +
                            "     COUNT(*) AS PATIENT_COUNT\n" +
                            "FROM\n" +
                            "    " + aggSchema + ".TEMP_PATIENT_DIMENSION\n";
                    
                    // Add qualifiers based on demographic combo
                    switch(comboDesc) {
                        case "Overview":
                            break;
                        case "Gender":
                            patCountSQL +=
                                    "WHERE\n" +
                                    "    SEX_CD = '" + combo.get("SEX_CD").toString() + "'\n";
                            break;
                        case "Age x Gender":
                            patCountSQL +=
                                    "WHERE\n" +
                                    "    AGE_BRACKET = '" + combo.get("AGE_BRACKET").toString() + "'\n" +
                                    "    AND SEX_CD = '" + combo.get("SEX_CD").toString() + "'\n";
                            break;
                        case "Age x Race":
                            patCountSQL +=
                                    "WHERE\n" +
                                    "    AGE_BRACKET = '" + combo.get("AGE_BRACKET").toString() + "'\n" +
                                    "    AND ";
                            
                            // Iterate over possible race codes and add individually to avoid oversized REGEXP
                            for(int i = 0; i < raceCodes.size(); i++) {
                                if(raceCodes.size() == 1) {                                   
                                    patCountSQL +=
                                            "    REGEXP_LIKE(RACE_CD,'" + raceCodes.get(i) + "')\n";                                    
                                } else if(i == 0) {                                    
                                    patCountSQL +=
                                            "    (REGEXP_LIKE(RACE_CD,'" + raceCodes.get(i) + "')\n";
                                } else if(i == raceCodes.size()-1) {
                                    patCountSQL +=
                                            "    OR REGEXP_LIKE(RACE_CD,'" + raceCodes.get(i) + "'))\n";
                                } else {
                                    patCountSQL +=
                                            "    OR REGEXP_LIKE(RACE_CD,'" + raceCodes.get(i) + "')\n";
                                }
                            }
                            break;
                        case "Ethnicity":
                            patCountSQL +=
                                    "WHERE\n" +
                                    "    ";
                            
                            // Iterate over possible ethnicity codes and add individually to avoid oversized REGEXP
                            for(int i = 0; i < ethnicityCodes.size(); i++) {
                                if(ethnicityCodes.size() == 1) {
                                    patCountSQL +=
                                            "    REGEXP_LIKE(ETHNICITY_CD,'" + ethnicityCodes.get(i) + "')\n";
                                } else if(i == 0) {                                    
                                    patCountSQL +=
                                            "    (REGEXP_LIKE(ETHNICITY_CD,'" + ethnicityCodes.get(i) + "')\n";
                                } else if(i == ethnicityCodes.size()-1) {
                                    patCountSQL +=
                                            "    OR REGEXP_LIKE(ETHNICITY_CD,'" + ethnicityCodes.get(i) + "'))\n";
                                } else {
                                    patCountSQL +=
                                            "    OR REGEXP_LIKE(ETHNICITY_CD,'" + ethnicityCodes.get(i) + "')\n";
                                }
                            }
                            break;
                    }
                    
                    // Get the patient count
                    HashMap patCount = SQLUtilities.getHashedTable(datadb,
                            patCountSQL).getFirst();
                    
                    patientCount[comboID] = patCount.get("PATIENT_COUNT") != null ? Integer.parseInt(patCount.get("PATIENT_COUNT").toString()) : 0;

                    updateAggFactStmt.setString(1, DT_FORMAT.format(lastDateProcessed));
                    updateAggFactStmt.setString(2, DT_FORMAT.format(earliestColDate[comboID]));
                    updateAggFactStmt.setString(3, DT_FORMAT.format(latestColDate[comboID]));
                    updateAggFactStmt.setInt(4, conceptCount[comboID]);
                    updateAggFactStmt.setInt(5, patientCount[comboID]);
                    updateAggFactStmt.setBigDecimal(6, minValue[comboID]);
                    updateAggFactStmt.setBigDecimal(7, maxValue[comboID]);
                    updateAggFactStmt.setBigDecimal(8, average[comboID]);                
                    updateAggFactStmt.setBigDecimal(9, stddev[comboID]);
                    updateAggFactStmt.setInt(10, abnormalTotal[comboID]);
                    updateAggFactStmt.setInt(11, abnormalLow[comboID]);
                    updateAggFactStmt.setInt(12, abnormalHigh[comboID]);
                    updateAggFactStmt.setInt(13, abnormalOther[comboID]);
                    updateAggFactStmt.setString(14, conceptCode);
                    updateAggFactStmt.setInt(15, comboID);
                    updateAggFactStmt.setString(16, unitsCode);
                    updateAggFactStmt.addBatch();
                }
                else {
                    updateAggFactStmt.setString(1, DT_FORMAT.format(lastDateProcessed));
                    updateAggFactStmt.setNull(2, Types.DATE);
                    updateAggFactStmt.setNull(3, Types.DATE);
                    updateAggFactStmt.setNull(4, Types.NUMERIC);
                    updateAggFactStmt.setNull(5, Types.NUMERIC);
                    updateAggFactStmt.setNull(6, Types.NUMERIC);
                    updateAggFactStmt.setNull(7, Types.NUMERIC);
                    updateAggFactStmt.setNull(8, Types.NUMERIC);
                    updateAggFactStmt.setNull(9, Types.NUMERIC);
                    updateAggFactStmt.setNull(10, Types.NUMERIC);
                    updateAggFactStmt.setNull(11, Types.NUMERIC);
                    updateAggFactStmt.setNull(12, Types.NUMERIC);
                    updateAggFactStmt.setNull(13, Types.NUMERIC);
                    updateAggFactStmt.setString(14, conceptCode);
                    updateAggFactStmt.setInt(15, comboID);
                    updateAggFactStmt.setString(16, unitsCode);
                    updateAggFactStmt.addBatch();
                }
            }
            
            updateAggFactStmt.executeBatch();
        }
    }
    
    /**
     * Combines previously recorded statistics with
     * statistics gathered by the calling thread. Thread
     * safety is maintained by using the synchronized
     * key word.
     * 
     * @param threadConceptCount
     * @param threadAverage
     * @param threadEarliestColDate
     * @param threadAbnormalHigh
     * @param threadMaxValue
     * @param threadLatestColDate
     * @param threadMinValue
     * @param threadAbnormalLow
     * @param threadStddev
     * @param threadAbnormalTotal
     * @param threadAbnormalOther
     */
    public synchronized void updateValues(
            int[] threadConceptCount,
            Date[] threadEarliestColDate,
            Date[] threadLatestColDate,
            BigDecimal[] threadMinValue,
            BigDecimal[] threadMaxValue,
            BigDecimal[] threadAverage,
            BigDecimal[] threadStddev,
            int[] threadAbnormalTotal,
            int[] threadAbnormalLow,
            int[] threadAbnormalHigh,
            int[] threadAbnormalOther) {
        for(HashMap combo : comboLookup) {
            int comboID = Integer.parseInt(combo.get("COMBO_ID").toString());
            
            // Only attempt to set values if there are new values
            if(threadConceptCount[comboID] > 0) {                
                BigDecimal n_prev = new BigDecimal(conceptCount[comboID]); // previous n
                BigDecimal m_prev = average[comboID]; // previous mean
                BigDecimal s_prev = stddev[comboID]; // previous standard deviation
                BigDecimal n_thread = new BigDecimal(threadConceptCount[comboID]); // thread n
                BigDecimal m_thread = threadAverage[comboID]; // thread mean
                BigDecimal s_thread = threadStddev[comboID]; // thread standard deviation

                // Make the easy increments on primitives (initialized to 0 so no special check needed)
                conceptCount[comboID] += threadConceptCount[comboID];
                abnormalTotal[comboID] += threadAbnormalTotal[comboID];
                abnormalHigh[comboID] += threadAbnormalHigh[comboID];
                abnormalLow[comboID] += threadAbnormalLow[comboID];
                abnormalOther[comboID] += threadAbnormalOther[comboID];
                
                // If this is the first date with observations ever processed just set the values
                if(n_prev.intValue() == 0) {
                    earliestColDate[comboID] = threadEarliestColDate[comboID];
                    latestColDate[comboID] = threadLatestColDate[comboID];
                    minValue[comboID] = threadMinValue[comboID];
                    maxValue[comboID] = threadMaxValue[comboID];
                    average[comboID] = threadAverage[comboID];
                    stddev[comboID] = threadStddev[comboID];
                // Otherwise apply updates normally
                } else {
                    if(threadEarliestColDate[comboID].before(earliestColDate[comboID])) {
                        earliestColDate[comboID] = threadEarliestColDate[comboID];
                    }

                    if(threadLatestColDate[comboID].after(latestColDate[comboID])) {
                        latestColDate[comboID] = threadLatestColDate[comboID];
                    }

                    if(threadMinValue[comboID].compareTo(minValue[comboID]) == -1) {
                        minValue[comboID] = threadMinValue[comboID];
                    }

                    if(threadMaxValue[comboID].compareTo(maxValue[comboID]) == 1) {
                        maxValue[comboID] = threadMaxValue[comboID];
                    }

                    // Combined 
                    average[comboID] = n_prev.multiply(m_prev).add(n_thread.multiply(m_thread)).divide(n_prev.add(n_thread),5,RoundingMode.HALF_EVEN);

                    BigDecimal m_combined = average[comboID];
                    
                    // Break up the calculation into multiple terms to improve readability
                    BigDecimal a = (m_prev.subtract(m_combined)).pow(2);
                    BigDecimal b = (s_prev.pow(2)).add(a);
                    BigDecimal c = (m_thread.subtract(m_combined)).pow(2);
                    BigDecimal d = (s_thread.pow(2)).add(c);
                    BigDecimal numerator = (n_prev.multiply(b)).add(n_thread.multiply(d));
                    BigDecimal denominator = n_prev.add(n_thread);
                    
                    BigDecimal s2_combined = numerator.divide(denominator, 5, RoundingMode.HALF_EVEN);
                    
                    stddev[comboID] = BigDecimal.valueOf(StrictMath.sqrt(s2_combined.doubleValue())).divide(BigDecimal.ONE, 5, RoundingMode.HALF_EVEN);
                }
            }
        }
    }
    
    /**
     * Generate the HTML document header for ontology statistics
     * 
     * @param conceptCode The concept to generate info for
     * @param unitsCode The units of the concept
     * @return a string containing the opening HTML content
     * @throws Exception
     */
    private String getConceptHeader(String conceptCode, String unitsCode) throws Exception {
        LinkedList<HashMap> hm = SQLUtilities.getHashedTable(datadb,
                "SELECT\n" +
                "     I.C_FULLNAME\n" +
                "    ,I.C_NAME\n" +
                "FROM\n" +
                "    " + aggSchema + ".AGGREGATE_FACT A\n" +
                "    JOIN " + ontSchema + ".I2B2 I\n" +
                "        ON A.CONCEPT_CD = I.C_BASECODE\n" +
                "    JOIN " + aggSchema + ".COMBO_DIMENSION C\n" +
                "        ON A.COMBO_ID = C.COMBO_ID\n" +
                "WHERE\n" +
                "    A.CONCEPT_CD = '" + conceptCode + "'\n" +
                "    AND A.UNITS_CD = '" + unitsCode + "'\n" +
                "    AND A.COMBO_ID = 0");
        
        if(!hm.isEmpty()) {        
            return   
                    "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "<meta charset=\"utf-8\">\n" +
                    "<title>" + hm.getFirst().get("C_NAME").toString() + "</title>\n" +
                    "<style>\n" +
                    "table,th,td {\n" +
                    "  border:1px solid black;\n" +
                    "  border-collapse:collapse;\n" +
                    "}\n" +
                    "th,td {\n" +
                    "  padding:5px;\n" +
                    "  font-family:Arial,sans-serif;\n" +
                    "}\n" +
                    "th {\n" +
                    "  text-align:left;\n" +
                    "  background-color:gainsboro;\n" +
                    "}\n" +
                    ".null {\n" +
                    "  text-align:center;\n" +
                    "}\n" +
                    "</style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<h1>" + hm.getFirst().get("C_FULLNAME").toString().substring(6) + "</h1>\n" +
                    "<h2>\n" +
                    "Name: " + hm.getFirst().get("C_NAME").toString() + "<br>\n" +
                    "Code: " + conceptCode + "<br>\n" +
                    "Units: " + unitsCode + "\n" +
                    "</h2>\n";
        } else {
            return "~EMPTY~";
        }
    }
    
    /**
     * Generate the closing tags at the end of the HTML string
     * 
     * @return a string comprised of the closing HTML tags
     */
    private String getConceptFooter() {
        return
                "</body>\n" +
                "</html>\n";
    }
    
    /**
     * Generate a table or tables for a demographic combo
     * 
     * @param conceptCode The concept code to grab statistics for
     * @param unitsCode The concept's units
     * @param combo The demographic combo being generated
     * @param majorDemo The demographic with the larger number of values.
     *                  Represented as additional columns. If solo, use '@'.
     * @param minorDemo The demographic with the smaller number of values.
     *                  Represented as additional tables. If solo, use
     *                  COMBO_DESCRIPTION.
     * @return An string containing well-formed HTML
     * @throws Exception
     */
    private String getConceptStatTable(String conceptCode, String unitsCode, String combo, String majorDemo, String minorDemo) throws Exception {
        String htmlString = "";
        String headerText = "";
        
        // Set header text for compound demographics
        if(majorDemo.equals("SEX_CD")) {
                headerText = "Gender";
        } else if(majorDemo.equals("RACE_CD")) {
                headerText = "Race";
        }
        
        htmlString +=
                "<h2>\n" +
                "Category: " + combo + "<br>\n" +
                "</h2>\n";        

        String demoQuery =
                "SELECT\n" +
                "     DISTINCT C." + minorDemo + "\n" +
                "FROM\n" +
                "    " + aggSchema + ".COMBO_DIMENSION C\n" +
                "WHERE\n" +
                "    C.COMBO_DESCRIPTION = '" + combo + "'\n";
        
        // Iterate through each minor demographic
        for(HashMap demo : SQLUtilities.getHashedTable(datadb, demoQuery))
        {
            String demoValue = demo.get(minorDemo) != null ? demo.get(minorDemo).toString() : "@";
            
            // Initialize each row
            String headerRow =              "  <th>" + headerText + "</th>\n";
            String earliestColDateRow =     "  <th>Earliest Date</th>\n";
            String latestColDateRow =       "  <th>Latest Date</th>\n";
            String observationCountRow =    "  <th>Observation Count</th>\n";
            String patientCountRow =        "  <th>Patient Count</th>\n";
            String minValueRow =            "  <th>Min Value</th>\n";
            String maxValueRow =            "  <th>Max Value</th>\n";
            String averageRow =             "  <th>Mean</th>\n";
            String stddevRow =              "  <th>StdDev</th>\n";
            String abnormalTotalRow =       "  <th>Abnormal Count (Total)</th>\n";
            String abnormalLowRow =         "  <th>Abnormal Count (Low)</th>\n";
            String abnormalHighRow =        "  <th>Abnormal Count (High)</th>\n";
            String abnormalOtherRow =       "  <th>Abnormal Count (Other)</th>\n";
            
            
            switch(combo) {
                case "Overview":
                case "Gender":
                case "Ethnicity":
                    break;
                case "Age x Gender":
                    htmlString +=
                            "<h3>Age Bracket: " + demoValue + "</h3>\n";
                    break;
                case "Age x Race":
                    htmlString +=
                            "<h3>Age Bracket: " + demoValue + "</h3>\n";
                    break;
            }
            
            String aggregateQuery =
                    "SELECT\n" +
                    "     DISTINCT A.COMBO_ID\n" +
                    "    ,C.SEX_CD\n" +
                    "    ,C.AGE_BRACKET\n" +
                    "    ,C.RACE_CD\n" +
                    "    ,C.ETHNICITY_CD\n" +
                    "    ,A.EARLIEST_COLDATE\n" +
                    "    ,A.LATEST_COLDATE\n" +
                    "    ,A.CONCEPT_COUNT\n" +
                    "    ,A.PATIENT_COUNT\n" +
                    "    ,A.MIN_VALUE\n" +
                    "    ,A.MAX_VALUE\n" +
                    "    ,A.AVERAGE\n" +
                    "    ,A.STDDEV\n" +
                    "    ,A.ABNORMAL_TOTAL\n" +
                    "    ,A.ABNORMAL_LOW\n" +
                    "    ,A.ABNORMAL_HIGH\n" +
                    "    ,A.ABNORMAL_OTHER\n" +
                    "FROM\n" +
                    "    " + aggSchema + ".AGGREGATE_FACT A\n" +
                    "    JOIN " + ontSchema + ".I2B2 I\n" +
                    "        ON A.CONCEPT_CD = I.C_BASECODE\n" +
                    "    JOIN " + aggSchema + ".COMBO_DIMENSION C\n" +
                    "        ON A.COMBO_ID = C.COMBO_ID\n" +
                    "WHERE\n" +
                    "    A.CONCEPT_CD = '" + conceptCode + "'\n" +
                    "    AND A.UNITS_CD = '" + unitsCode + "'\n" +
                    "    AND C.COMBO_DESCRIPTION = '" + combo + "'\n" +
                    "    AND C." + minorDemo + " = '" + demoValue + "'\n" +
                    "ORDER BY\n" +
                    "    A.COMBO_ID\n";
            
            // Iterate through aggregate columns
            for(HashMap aggregate : SQLUtilities.getHashedTable(datadb, aggregateQuery)) {

                String headerCol = "";
                String gender = "";
                String ageBracket = aggregate.get("AGE_BRACKET") != null ? aggregate.get("AGE_BRACKET").toString() : "";
                String race = "";
                String ethnicity = "";

                // Set friendly gender
                if(aggregate.get("SEX_CD") != null) {
                    switch(aggregate.get("SEX_CD").toString()) {
                        case "M":
                            gender = "Male";
                            break;
                        case "F":
                            gender = "Female";
                            break;
                        case "I":
                            gender = "Intersex";
                            break;
                    }
                }

                // Set friendly race
                if(aggregate.get("RACE_CD") != null) {
                    switch(aggregate.get("RACE_CD").toString()) {
                        case "~AMERICAN_INDIAN_OR_ALASKA_NATIVE~":
                            race = "American Indian or Alaska Native";
                            break;
                        case "~ASIAN~,~ASIAN_INDIAN~,~BANGLADESHI~,~BHUTANESE~,~BURMESE~,~CAMBODIAN~,~CHINESE~,~FILIPINO~,~HMONG~,~INDONESIAN~,~IWO_JIMAN~,~JAPANESE~,~KOREAN~,~LAOTIAN~,~MADAGASCAR~,~MALAYSIAN~,~MALDIVIAN~,~NEPALESE~,~OKINAWAN~,~PAKISTANI~,~SINGAPOREAN~,~SRI_LANKAN~,~TAIWANESE~,~THAI~,~VIETNAMESE~":
                            race = "Asian";
                            break;
                        case "~BLACK_OR_AFRICAN-AMERICAN~":
                            race = "Black or African-American";
                            break;
                        case "~NATIVE_HAWAIIAN_OR_PACIFIC_ISLANDER~,~CAROLINIAN~,~CHAMORRO~,~CHUUKESE~,~FIJIAN~,~GUAMANIAN_OR_CHAMORRO~,~GUAMANIAN~,~KIRIBATI~,~KOSRAEAN~,~MARIANA_ISLANDER~,~MARSHALLESE~,~MELANESIAN~,~MICRONESIAN~,~NATIVE_HAWAIIAN~,~NEW_HEBRIDES~,~OTHER_PACIFIC_ISLANDER~,~PALAUAN~,~PAPUA_NEW_GUINEAN~,~POHNPEIAN~,~POLYNESIAN~,~SAIPANESE~,~SAMOAN~,~SOLOMON_ISLANDER~,~TAHITIAN~,~TOKELAUAN~,~TONGAN~,~YAPESE~":
                            race = "Native Hawaiian or Pacific Islander";
                            break;
                        case "~OTHER~":
                            race = "Other";
                            break;
                        case "~PATIENT_DECLINED~":
                            race = "Patient Declined";
                            break;
                        case "~UNKNOWN~":
                            race = "Unknown";
                            break;
                        case "~WHITE~":
                            race = "White";
                            break;
                    }
                }

                // Set friendly ethnicity
                if(aggregate.get("ETHNICITY_CD") != null) {
                    switch(aggregate.get("ETHNICITY_CD").toString()) {
                        case "~HISPANIC_ORIGIN~,~ANDALUSIAN~,~ARGENTINEAN~,~ASTURIAN~,~BELEARIC_ISLANDER~,~BOLIVIAN~,~CANAL_ZONE~,~CANARIAN~,~CASTILLIAN~,~CATALONIAN~,~CENTRAL_AMERICAN_INDIAN~,~CENTRAL_AMERICAN~,~CHICANO~,~CHILEAN~,~COLOMBIAN~,~COSTA_RICAN~,~CRIOLLO~,~CUBAN~,~DOMINICAN~,~ECUADORIAN~,~GALLEGO~,~GUATEMALAN~,~HONDURAN~,~LATIN_AMERICAN~,~LA_RAZA~,~MEXICANO~,~MEXICAN_AMERICAN_INDIAN~,~MEXICAN_AMERICAN~,~MEXICAN~,~NICARAGUAN~,~PANAMANIAN~,~PARAGUAYAN~,~PERUVIAN~,~PUERTO_RICAN~,~SALVADORAN~,~SOUTH_AMERICAN_INDIAN~,~SOUTH_AMERICAN~,~SPANIARD~,~SPANISH_BASQUE~,~URUGUAYAN~,~VALENCIAN~,~VENEZUELAN~":
                            ethnicity = "Hispanic Origin";
                            break;
                        case "~NOT_HISPANIC_OR_LATINO~":
                            ethnicity = "Not Hispanic or Latino";
                            break;
                        case "~PATIENT_DECLINED~":
                            ethnicity = "Patient Declined";
                            break;
                        case "~UNKNOWN~":
                            ethnicity = "Unknown";
                            break;
                    }
                }

                switch(combo) {
                    case "Overview":
                        headerCol = " ";
                        break;
                    case "Gender":
                        headerCol = gender;
                        break;
                    case "Age x Gender":
                        headerCol = gender;
                        break;
                    case "Age x Race":
                        headerCol = race;
                        break;
                    case "Ethnicity":
                        headerCol = ethnicity;
                        break;
                }

                String earliestColDateCol = aggregate.get("EARLIEST_COLDATE")   != null ?   "  <td>" + aggregate.get("EARLIEST_COLDATE").toString().substring(0,10) : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String latestColDateCol = aggregate.get("LATEST_COLDATE")       != null ?   "  <td>" + aggregate.get("LATEST_COLDATE").toString().substring(0,10) : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String observationCountCol = aggregate.get("CONCEPT_COUNT")     != null ?   "  <td>" + aggregate.get("CONCEPT_COUNT").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String patientCountCol = aggregate.get("PATIENT_COUNT")         != null ?   "  <td>" + aggregate.get("PATIENT_COUNT").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String minValueCol = aggregate.get("MIN_VALUE")                 != null ?   "  <td>" + aggregate.get("MIN_VALUE").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String maxValueCol = aggregate.get("MAX_VALUE")                 != null ?   "  <td>" + aggregate.get("MAX_VALUE").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String meanCol = aggregate.get("AVERAGE")                       != null ?   "  <td>" + aggregate.get("AVERAGE").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String stddevCol = aggregate.get("STDDEV")                      != null ?   "  <td>" + aggregate.get("STDDEV").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String abnormalTotalCol = aggregate.get("ABNORMAL_TOTAL")       != null ?   "  <td>" + aggregate.get("ABNORMAL_TOTAL").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String abnormalLowCol = aggregate.get("ABNORMAL_LOW")           != null ?   "  <td>" + aggregate.get("ABNORMAL_LOW").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String abnormalHighCol = aggregate.get("ABNORMAL_HIGH")         != null ?   "  <td>" + aggregate.get("ABNORMAL_HIGH").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";
                String abnormalOtherCol = aggregate.get("ABNORMAL_OTHER")       != null ?   "  <td>" + aggregate.get("ABNORMAL_OTHER").toString() : 
                                                                                            "  <td class=\"null\">" + "&mdash;";

                headerRow +=                    "  <th>" + headerCol + "</th>\n";
                earliestColDateRow +=           earliestColDateCol   + "</td>\n";
                latestColDateRow +=             latestColDateCol     + "</td>\n";
                observationCountRow +=          observationCountCol  + "</td>\n";
                patientCountRow +=              patientCountCol      + "</td>\n";
                minValueRow +=                  minValueCol          + "</td>\n";
                maxValueRow +=                  maxValueCol          + "</td>\n";
                averageRow +=                   meanCol              + "</td>\n";
                stddevRow +=                    stddevCol            + "</td>\n";
                abnormalTotalRow +=             abnormalTotalCol     + "</td>\n";
                abnormalLowRow +=               abnormalLowCol       + "</td>\n";
                abnormalHighRow +=              abnormalHighCol      + "</td>\n";
                abnormalOtherRow +=             abnormalOtherCol     + "</td>\n";

            }

            htmlString +=
                    "<table>\n";
            
            // Only add a header row if we're not creating an overview table
            if(!combo.equals("Overview")) {
                htmlString +=
                        "<tr>\n" + headerRow            + "</tr>\n";
            }
                    
            htmlString +=
                    "<tr>\n" + earliestColDateRow   + "</tr>\n" +
                    "<tr>\n" + latestColDateRow     + "</tr>\n" +
                    "<tr>\n" + observationCountRow  + "</tr>\n" +
                    "<tr>\n" + patientCountRow      + "</tr>\n" +
                    "<tr>\n" + minValueRow          + "</tr>\n" +
                    "<tr>\n" + maxValueRow          + "</tr>\n" +
                    "<tr>\n" + averageRow           + "</tr>\n" +
                    "<tr>\n" + stddevRow            + "</tr>\n" +
                    "<tr>\n" + abnormalTotalRow     + "</tr>\n" +
                    "<tr>\n" + abnormalLowRow       + "</tr>\n" +
                    "<tr>\n" + abnormalHighRow      + "</tr>\n" +
                    "<tr>\n" + abnormalOtherRow     + "</tr>\n" +
                    "</table>\n" +
                    "<br>\n";
        }
        
        return htmlString;  
    }
    
    /**
     * Update the ontology with statistics for a specific concept code
     * 
     * @param conceptCode The concept to update
     * @param htmlString A well-formed HTML document string containing
     *                   statistics
     * @throws Exception
     */
    private void updateOntologyStats(String conceptCode, String htmlString) throws Exception {
        // Break htmlString into segments of 4000 characters or less 
        // (4000 characters is max allowed by Oracle SQL)
        List<String> chunks = new ArrayList<>();
        for(int i = 0; i < htmlString.length(); i += 4000) {
            chunks.add(htmlString.substring(i, Math.min(i + 4000, htmlString.length())));
        }
        
        String updateSQL =
                "UPDATE\n" + 
                "    " + ontSchema + ".I2B2\n" +
                "SET\n" +
                "     UPDATE_DATE = SYSDATE\n" +
                "    ,C_TOTALNUM = " + patientCount[0] +
                "    ,C_COMMENT = ";
        
        for(int i = 0; i < chunks.size(); i++) {
            if(i == chunks.size()-1) {
                updateSQL += "TO_CLOB('"+chunks.get(i)+"') ";
            } else {
                updateSQL += "TO_CLOB('"+chunks.get(i)+"') || ";
            }
        }        
        
        updateSQL +=
                "WHERE\n" +
                "    C_BASECODE = '" + conceptCode + "'\n";
        
        SQLUtilities.execSQL(datadb, updateSQL);
    }
}
