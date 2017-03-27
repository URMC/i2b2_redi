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

import edu.rochester.urmc.util.SQLUtilities;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * This class is responsible for a singular observation date for a concept,
 * identifying and correcting value types and tallying aggregate information.
 * @author cculbertson1
 */
public class DataDictionaryThread extends Thread{
    
    SimpleDateFormat DT_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    SimpleDateFormat SDT_FORMAT = new SimpleDateFormat("yyyyMMdd");
    SimpleDateFormat HUMAN_DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat HUMAN_DATE = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat HUMAN_TIME = new SimpleDateFormat("HH:mm:ss");
    
    DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    DataDictionaryController parent;
    String conceptCode;
    String conceptUnitsCode;
    Date collectionDate;
    int numProcessed;
    int numUpdated;
    
    // All of the combo variables to be updated by threads
    public int[] conceptCount;
    public Date[] earliestColDate;
    public Date[] latestColDate;
    public int[] patientCount;
    public BigDecimal[] minValue;
    public BigDecimal[] maxValue;
    public BigDecimal[] average;
    public BigDecimal[] stddev;
    public int[] abnormalTotal;
    public int[] abnormalLow;
    public int[] abnormalHigh;
    public int[] abnormalOther;

    boolean relative_warning = false;
    
    public DataDictionaryThread( DataDictionaryController parent, String conceptCode, String conceptUnitsCode, Date collectionDate ) {
        super();
        this.parent = parent;
        this.conceptCode = conceptCode;
        this.conceptUnitsCode = conceptUnitsCode;
        this.collectionDate = collectionDate;
        this.numProcessed = 0;
        this.numUpdated = 0;
        
        conceptCount = new int[parent.comboLookup.length];
        earliestColDate = new Date[parent.comboLookup.length];
        latestColDate = new Date[parent.comboLookup.length];
        patientCount = new int[parent.comboLookup.length];
        minValue = new BigDecimal[parent.comboLookup.length];
        maxValue = new BigDecimal[parent.comboLookup.length];
        average = new BigDecimal[parent.comboLookup.length];
        stddev = new BigDecimal[parent.comboLookup.length];
        abnormalTotal = new int[parent.comboLookup.length];
        abnormalLow = new int[parent.comboLookup.length];
        abnormalHigh = new int[parent.comboLookup.length];
        abnormalOther = new int[parent.comboLookup.length];
    }
    public void run(){        
        try {
            //System.out.println(HUMAN_DATETIME.format(new Date().getTime()) + ": Thread Started : " + this.getId() + " : " + conceptCode + " " + HUMAN_DATE.format(collectionDate));
            
            HashMap[] observations = SQLUtilities.getTableHashedArray(parent.datadb,
                    "SELECT "
                            + " o.ENCOUNTER_NUM "
                            + ",o.PATIENT_NUM "
                            + ",o.VALTYPE_CD "
                            + ",o.TVAL_CHAR "
                            + ",o.NVAL_NUM "
                            + ",o.VALUEFLAG_CD "
                            + ",o.UNITS_CD "
                            + ",o.START_DATE "
                            + ",o.IMPORT_DATE "
                            + ",o.UPLOAD_ID "
                            + ",(CASE " 
                                + "WHEN FLOOR((O.START_DATE-P.BIRTH_DATE)/365.25) >= 18 THEN '18+' "
                                + "WHEN FLOOR((O.START_DATE-P.BIRTH_DATE)/365.25) < 18 THEN '0-17' "
                            + "END) AS AGE_BRACKET "
                            + ",p.SEX_CD "
                            + ",p.RACE_CD "
                            + ",p.ETHNICITY_CD " +
                    "FROM " 
                            + parent.crcSchema + ".OBSERVATION_FACT o " 
                            + "JOIN I2B2DEMODATA.PATIENT_DIMENSION p "
                            + "   ON o.PATIENT_NUM = p.PATIENT_NUM " +
                    "WHERE "
                            + "o.CONCEPT_CD = '" + conceptCode + "' "
                            + "AND o.UNITS_CD = '" + conceptUnitsCode + "' "
                            + "AND o.START_DATE BETWEEN TO_DATE('" + SDT_FORMAT.format(collectionDate) + "000000','yyyymmddhh24miss') " 
                            + "AND TO_DATE('" + SDT_FORMAT.format(collectionDate) + "235959','yyyymmddhh24miss') ");
            
            try (PreparedStatement updateObsFactStmt = parent.datadb.prepareStatement(
                "UPDATE " + parent.crcSchema + ".OBSERVATION_FACT " +
                "SET "
                        + " VALTYPE_CD = ? "
                        + ",TVAL_CHAR = ? "
                        + ",NVAL_NUM = ? "
                        + ",UPDATE_DATE = SYSDATE " +
                "WHERE "
                        + "CONCEPT_CD = ? "
                        + "AND UNITS_CD = ?" 
                        + "AND ENCOUNTER_NUM = ? " 
                        + "AND PATIENT_NUM = ? "
                        + "AND START_DATE = TO_DATE(?,'YYYYMMDDHH24MISS') "
                        + "AND IMPORT_DATE = TO_DATE(?,'YYYYMMDDHH24MISS') "
                        + "AND UPLOAD_ID = ? ")) {
                // Iterate over observations
                for (HashMap observation : observations) {
                    String encounterNum = observation.get("ENCOUNTER_NUM").toString();
                    String patientNum = observation.get("PATIENT_NUM").toString();
                    String valTypeCd = observation.get("VALTYPE_CD").toString();
                    String tValChar = observation.get("TVAL_CHAR").toString();
                    String nValNum = observation.get("NVAL_NUM") != null ? observation.get("NVAL_NUM").toString() : "";
                    String valueFlagCode = observation.get("VALUEFLAG_CD") != null ? observation.get("VALUEFLAG_CD").toString() : "";
                    String startDate = DT_FORMAT.format(observation.get("START_DATE"));
                    String importDate = DT_FORMAT.format(observation.get("IMPORT_DATE"));
                    String uploadID = observation.get("UPLOAD_ID").toString();
                    String ageBracket = observation.get("AGE_BRACKET").toString();
                    String sexCode = observation.get("SEX_CD").toString();
                    List<String> raceCodes = Arrays.asList(observation.get("RACE_CD").toString().split(",",-1));
                    List<String> ethnicityCodes = Arrays.asList(observation.get("ETHNICITY_CD").toString().split(",",-1));
                    
                    // Add new variables so we can determine if anything important changes
                    String newValTypeCd = valTypeCd;
                    String newTValChar = tValChar;
                    String newNValNum = nValNum;
                    
                    // Regex on tValChar to see if it is mostly numeric
                    String inequality_pattern_string = "^\\s*(<|<=|>|>=|<>|!=)\\s*[-+]{0,1}\\s*(\\d+\\.{0,1}\\d*)\\s*$"; // Number has an inequality prefix
                    String ratio_pattern_string = "^\\s*(\\d+)\\s*([:\\/\\\\]{1})\\s*(\\d+)\\s*$"; // Number is a ratio e.g. blood pressure
                    
                    Pattern inequality_pattern = Pattern.compile(inequality_pattern_string);
                    Pattern ratio_pattern = Pattern.compile(ratio_pattern_string);
                    
                    Matcher inequality_match = inequality_pattern.matcher(tValChar);
                    Matcher ratio_match = ratio_pattern.matcher(tValChar);
                    
                    Boolean inequality_match_found = inequality_match.find();
                    Boolean ratio_match_found = ratio_match.find();
                    
                    //
                    // Determine value type
                    //
                    
                    if(NumberUtils.isNumber(nValNum)) {
                        // nValNum is purely numeric
                        newValTypeCd = "N";
                    } else if (NumberUtils.isNumber(tValChar)) {
                        // tValChar is actually a pure numeric
                        newValTypeCd = "N";
                        newNValNum = tValChar;
                        newTValChar = "E";
                    } else if(inequality_match_found && NumberUtils.isNumber(inequality_match.group(2))) {
                        // tValChar is an inequality
                        newValTypeCd = "N";
                        
                        // Parse the inequality operator and set the alpha equivalent
                        switch(inequality_match.group(1)) {
                            case "<>": newTValChar = "NE";
                            break;
                            
                            case "!=": newTValChar = "NE";
                            break;
                            
                            case "<": newTValChar = "L";
                            break;
                            
                            case "<=": newTValChar = "LE";
                            break;
                            
                            case ">": newTValChar = "G";
                            break;
                            
                            case ">=": newTValChar = "GE";
                            break;
                        }
                        
                        newNValNum = inequality_match.group(2); // Giving nValNum the value of the inequality boundary for querying purposes
                    } else if(ratio_match_found && NumberUtils.isNumber(ratio_match.group(1)) && NumberUtils.isNumber(ratio_match.group(3))) {
                        // tValChar is a ratio
                        newValTypeCd = "T"; // Keeping as T for now
                    } else if(nValNum.isEmpty() && tValChar.length() <= 255) {
                        // tValChar is no more than 255 bytes of text
                        newValTypeCd = "T";
                    } else if(nValNum.isEmpty() && tValChar.length() > 255){
                        // tValChar is a blob > 255 bytes of data
                        newValTypeCd = "B";
                    }
                    // Set parameters
                    // |-- ONLY DO THIS IF SOMETHING HAS CHANGED
                    // v
                    if(!newValTypeCd.equals(valTypeCd) || !newTValChar.equals(tValChar) || !newNValNum.equals(nValNum)) {
                        updateObsFactStmt.setString(1, newValTypeCd);
                        updateObsFactStmt.setString(2, newTValChar);
                        
                        if(newNValNum.isEmpty()) { // Handle null nValNum when type is T
                            updateObsFactStmt.setBigDecimal(3, null);
                        } else {
                            updateObsFactStmt.setBigDecimal(3, new BigDecimal(newNValNum));
                        }
                        
                        updateObsFactStmt.setString(4, conceptCode);
                        updateObsFactStmt.setString(5, conceptUnitsCode);
                        updateObsFactStmt.setLong(6, Long.parseLong(encounterNum));
                        updateObsFactStmt.setLong(7, Long.parseLong(patientNum));
                        updateObsFactStmt.setString(8, startDate);
                        updateObsFactStmt.setString(9, importDate);
                        updateObsFactStmt.setLong(10, Long.parseLong(uploadID));
                        
                        updateObsFactStmt.addBatch();
                        valTypeCd = newValTypeCd;
                        tValChar = newTValChar;
                        nValNum = newNValNum;
                        numUpdated++;
                    }
                    // ^
                    // |-- ONLY DO THIS IF SOMETHING HAS CHANGED
                    
                    // Update tracking values for numeric data
                    if(valTypeCd.equals("N") && tValChar.equals("E")) {                        
                        for(HashMap combo : parent.comboLookup) {
                            int comboID = Integer.parseInt(combo.get("COMBO_ID").toString());
                            BigDecimal value = new BigDecimal(nValNum);                            
                            boolean raceMatch = false;
                            boolean ethnicityMatch = false;
                            
                            // Compare patient's listed races until there is a match
                            for(String raceCode : raceCodes) {
                                if(combo.get("RACE_CD").toString().contains(raceCode)) {
                                    raceMatch = true;
                                    break;
                                }
                            }
                            
                            // Compare patient's listed ethnicities until there is a match
                            for(String ethnicityCode : ethnicityCodes) {
                                if(combo.get("ETHNICITY_CD").toString().contains(ethnicityCode)) {
                                    ethnicityMatch = true;
                                    break;
                                }
                            }

                            // For each combo, if the observation qualifies set values appropriately
                            if(     (combo.get("SEX_CD").toString().equals(sexCode) || combo.get("SEX_CD").toString().equals("@")) &&
                                    (combo.get("AGE_BRACKET").toString().equals(ageBracket) || combo.get("AGE_BRACKET").toString().equals("@")) &&
                                    (raceMatch || combo.get("RACE_CD").toString().equals("@")) &&
                                    (ethnicityMatch || combo.get("ETHNICITY_CD").toString().equals("@")
                                    )
                            ) {
                                /**
                                 * These values don't depend on how many observations 
                                 * have already been processed, they are simple increments
                                 */
                                conceptCount[comboID] += 1;
                                switch(valueFlagCode) {
                                    case "H":
                                        abnormalTotal[comboID] += 1;
                                        abnormalHigh[comboID] += 1;
                                        break;
                                    case "L":
                                        abnormalTotal[comboID] += 1;
                                        abnormalLow[comboID] += 1;
                                        break;
                                    case "A":
                                        abnormalTotal[comboID] += 1;
                                        abnormalOther[comboID] += 1;
                                        break;
                                }
                                
                                // If this is the first observation we're aggregating just set the values
                                if(conceptCount[comboID] == 1) {
                                    latestColDate[comboID] = collectionDate;
                                    earliestColDate[comboID] = collectionDate;
                                    minValue[comboID] = value;
                                    maxValue[comboID] = value;
                                    stddev[comboID] = BigDecimal.ZERO;
                                    average[comboID] = value;
                                // Otherwise incrementally update them
                                } else {
                                    BigDecimal n = BigDecimal.valueOf(conceptCount[comboID]);
                                    BigDecimal mn_1 = average[comboID];
                                    BigDecimal sn_1 = stddev[comboID];
                                    BigDecimal n_1 = BigDecimal.valueOf(conceptCount[comboID]-1);
                                    
                                    if(collectionDate.before(earliestColDate[comboID])) {
                                        earliestColDate[comboID] = collectionDate;
                                    }
                                    
                                    if(collectionDate.after(latestColDate[comboID])) {
                                        latestColDate[comboID] = collectionDate;
                                    }
                                    
                                    if(value.compareTo(minValue[comboID]) == -1) {
                                        minValue[comboID] = value;
                                    }
                                    
                                    if(value.compareTo(maxValue[comboID]) == 1) {
                                        maxValue[comboID] = value;
                                    }
                                    
                                    BigDecimal m_combined = mn_1.add(value.subtract(mn_1).divide(n, 5, RoundingMode.HALF_EVEN));
                                    
                                    // Break up the calculation into multiple terms to improve readability
                                    BigDecimal a = n_1.multiply(sn_1.pow(2));
                                    BigDecimal b = value.subtract(mn_1);
                                    BigDecimal c = value.subtract(m_combined);                                    
                                    
                                    BigDecimal s2 = (a.add(b.multiply(c))).divide(n, 5, RoundingMode.HALF_EVEN);
                                    
                                    average[comboID] = m_combined;
                                    stddev[comboID] = BigDecimal.valueOf(StrictMath.sqrt(s2.doubleValue()));
                                }
                            }
                        }
                    }
                    
                    numProcessed++;
                } // End of observations

                if(numUpdated > 0) {
                    updateObsFactStmt.executeBatch();
                }
                
                parent.updateValues(conceptCount,earliestColDate,latestColDate,minValue,maxValue,average,stddev,abnormalTotal,abnormalLow,abnormalHigh,abnormalOther);

                parent.incrementNumProcessed(numProcessed);
                parent.incrementNumUpdated(numUpdated);

                parent.setLastDateProcessed(collectionDate);
            }
 
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }
}