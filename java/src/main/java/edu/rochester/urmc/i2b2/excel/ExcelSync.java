/**
 * Copyright 2015 , University of Rochester Medical Center
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
 * @author png (phillip_ng@urmc.rochester.edu)
 */

package edu.rochester.urmc.i2b2.excel;

import edu.rochester.urmc.i2b2.SQLQueryJob;
import edu.rochester.urmc.util.AggrAssemblerOnePass;
import edu.rochester.urmc.util.HashMapFunctions;
import edu.rochester.urmc.util.SQLUtilities;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.util.WorkbookUtil;

/**
 * This class is the execution side of the Excel Exporter. It is designed to be extremely fast, by first pulling all data
 * into a EAV model in the data database, which then it then does a select, sorted by patient and date, through a 
 * one pass aggregator, which does the pivoting operations upon generating the final sheet.
 * 
 * @author png
 */
public class ExcelSync extends SQLQueryJob {
    
    private static final Logger logger = LogManager.getLogger(ExcelSync.class);
    
    public String crcSchema = "";
    public String ontSchema = "";
    
    public String resulttype = "ENC";
    
    private String session_id = "";
    
    HashMap localSettings = null;
    
    HashMap[] patients = {};
    
    HashMap[] fields = {};
    
    int chunkSize = 500;
    
    public void run() {

        try{
            
            chunkSize = Integer.parseInt(settings.getProperty("EXCELCHUNKSIZE", "500"));
            
            this.updateStatus("Running", 1 );
            
            session_id = project.get("PARAMS").toString();
            
            localSettings = SQLUtilities.getTableHashedArray(datadb, "SELECT SESSIONID,USERNAME,PROJECTID,PROJECTCODE,RESULT_INSTANCE_ID,PATIENT_LIST_DESC,STATUS,DATECOMPLETED,OPTIONS FROM PROJECTS_XLS_EXP_JOBS WHERE SESSIONID='"+session_id+"'")[0];
            
            HashMap[] disp = {localSettings};
            log( HashMapFunctions.logHashMapArrayToTable("Was requested to process ", disp) );
            
            if( localSettings.get("RESULT_INSTANCE_ID") == null ){
                throw new IllegalArgumentException("You must first select a patient or encounter set.") ;
            }

            //the first thing you need to do is to mark the job as started so that he monitor doesn't restart the silly thing.
            this.updateStatus("Running" ,1);
            
            SQLUtilities.execSQL(datadb, "UPDATE PROJECTS_XLS_EXP_JOBS SET lastmost_sheet=empty_blob() WHERE SESSIONID='"+session_id+"'");    
            SQLUtilities.execSQL(datadb, "DELETE FROM PROJECTS_XLS_EAV WHERE SESSIONID='"+session_id+"'");    
            
            //some housekeeping. see if you can identify the ont, crc from the hive.
            ontSchema = "" + SQLUtilities.getTableHashedArray(datadb, "SELECT C_DB_FULLSCHEMA FROM I2B2HIVE.ONT_DB_LOOKUP WHERE REPLACE(UPPER(TRIM(C_PROJECT_PATH)),'/','')=UPPER(TRIM('"+localSettings.get("PROJECTCODE")+"'))")[0].get("C_DB_FULLSCHEMA");
            crcSchema = "" + SQLUtilities.getTableHashedArray(datadb, "SELECT C_DB_FULLSCHEMA FROM I2B2HIVE.CRC_DB_LOOKUP WHERE REPLACE(UPPER(TRIM(C_PROJECT_PATH)),'/','')=UPPER(TRIM('"+localSettings.get("PROJECTCODE")+"'))")[0].get("C_DB_FULLSCHEMA");
            
            //ok. So first you want to grab the concepts mapped to (by meta) and the field they want mapped, along with the rule.
            log("schemae found:" + ontSchema + " & " + crcSchema );
            
            patients = generatePatientList();
            
            this.updateStatus("Running", 10 );
            
            normalizeFields();
            
            log("Precalculation complete." );
            
            generateEAVData( );
            
            XSSFWorkbook hwb = generateSheet( );
            
            saveSheetToServer( hwb );
            
            SQLUtilities.execSQL(datadb, "DELETE FROM PROJECTS_XLS_EAV WHERE SESSIONID='"+session_id+"'");    
            
            this.updateStatus("Complete", 100 );
            

        } catch (Exception ex){

            logError( ex );


        } 
    }
    
    /**
     * This method takes all of the selected concepts from the mapping and then performs a normalization to look up data
     * from the list in TABLE_ACCESS in metadata schema, and then another lookup to see what data is in the table for 
     * the details of what to extract.
     * @throws Exception 
     */
    private void normalizeFields() throws Exception {
        
        fields = SQLUtilities.getTableHashedArray(datadb,"" +
            "SELECT PROJECTS_XLS_EXP_FIELDS.* " + 
            "FROM PROJECTS_XLS_EXP_FIELDS WHERE" + 
            " PROJECTID='"+localSettings.get("PROJECTID") + "' AND "+
            " USERNAME='"+localSettings.get("USERNAME") + "' "+
            "ORDER BY SYSID "
        );
        
        HashMap[] allmeta = SQLUtilities.getTableHashedArray(datadb, "SELECT C_TABLE_CD, C_TABLE_NAME, C_FULLNAME FROM "+ontSchema+".TABLE_ACCESS ");
        
        for( HashMap fld: fields ){     
              
            String ontmapping = "I2B2";
            String conceptpth = (""+fld.get("CONCEPT_PATH")).replaceAll("\\\\\\\\", "\\\\");
            for( HashMap mappings : allmeta){   
                if( conceptpth.startsWith(("\\"+mappings.get("C_TABLE_CD"))) ){
                    ontmapping = ""+mappings.get("C_TABLE_NAME");
                    conceptpth = conceptpth.replaceAll(("\\\\"+mappings.get("C_TABLE_CD")),"");
                    break;
                }
            }
            HashMap[] meta = SQLUtilities.getTableHashedArray(datadb,
                "SELECT DISTINCT C_FACTTABLECOLUMN,C_TABLENAME,C_COLUMNNAME,C_COLUMNDATATYPE,C_OPERATOR,C_DIMCODE " + 
                "FROM "+ontSchema+"."+ontmapping+" WHERE C_FULLNAME='"+conceptpth.replaceAll("'", "''")+"'"
            );
            if( meta.length == 0 ){
                logger.info("I got no metadata for " + conceptpth.replaceAll("'", "''") );
            } else {
                fld.putAll(meta[0]);
            } //if there is something to the metadata.  

            //set the default to be the last value.
            if( fld.get("AGGR") == null ){
                fld.put("AGGR","VLAST");
            }                         
        } //for all fields.
    }
    
    /**
     * This loads up the entire patient list and returns it. This is the patient list or encounter list requested to 
     * process.
     * @return
     * @throws Exception 
     */
    private HashMap[] generatePatientList() throws Exception{
        
        HashMap[] types = SQLUtilities.getTableHashedArray(datadb, "SELECT RESULT_TYPE_ID FROM "+crcSchema+".QT_QUERY_RESULT_INSTANCE WHERE RESULT_INSTANCE_ID='"+localSettings.get("RESULT_INSTANCE_ID")+"'");
        if( types.length > 0 ){
            resulttype = "" + types[0].get("RESULT_TYPE_ID");
        } else {
            resulttype = "PTSET NOT SELECTED";
        }
        
        String patientinfo = "";
        HashMap[] patients = {};
        if( "1".equals( resulttype ) ){

            resulttype = "QT_PATIENT_SET_COLLECTION"; 
            patientinfo = "SELECT PATIENT_NUM FROM "+crcSchema+".QT_PATIENT_SET_COLLECTION WHERE RESULT_INSTANCE_ID='"+localSettings.get("RESULT_INSTANCE_ID")+"'"; 

        } else if ( "2".equals( resulttype ) ){

            resulttype = "QT_PATIENT_ENC_COLLECTION";                 
            patientinfo = "SELECT E.PATIENT_NUM, E.ENCOUNTER_NUM, START_DATE AS STARTING, NVL(END_DATE,START_DATE)+1 AS ENDING " +
                "FROM "+crcSchema+".VISIT_DIMENSION V, "+crcSchema+".QT_PATIENT_ENC_COLLECTION E " +
                "WHERE V.ENCOUNTER_NUM=E.ENCOUNTER_NUM  AND RESULT_INSTANCE_ID='"+localSettings.get("RESULT_INSTANCE_ID")+"' " +
                "ORDER BY E.PATIENT_NUM, START_DATE ";                 

        } else {
            throw new IllegalArgumentException("Do not know how to process a " + resulttype + " type answer, it's not a patient or encounter set? ");
        }
        return SQLUtilities.getTableHashedArray(datadb,patientinfo);
    }
    
    /**
     * This method performs the mass loading of data for all patients/variables into the intermediate EAV data model
     * for further breakdown. 
     * @throws Exception 
     */
    private void generateEAVData() throws Exception{
        int counter = 0;
        for( HashMap control : fields ){

            counter ++;    

            if( isCancelled() ){ break; }

            //now loop around until you either have an answer with modifier or an answer without modifier. 
            //there is no possibility for there to be a parent containing concept that has children unexpectedly.
            boolean isfirst = true;
            boolean loopagain = false;

            do {
                
                this.updateStatus("Running", 10 + (int)(80.0 * ((0.0 + counter)/(0.00001 + fields.length)))  );

                String sql = generateSQL(control, isfirst );
                int patientcount = 0;
                HashSet previousprocessed = new HashSet();
                do{

                    String currbunch = generateBatchSelect(control,patientcount,previousprocessed);

                    log("Step " + counter + " of " + fields.length + " " + control.get("CONCEPT_NAME") + " Patients " + patientcount + " of " + patients.length );
                    String finalsql = "INSERT INTO PROJECTS_XLS_EAV ( SESSIONID, PATIENTNUM, LEVEL_CD, STARTING, ENDING, CONCEPTS, VAL, DATES )  \n" + 
                          "SELECT '"+session_id+"', PATIENT_NUM, '"+control.get("SYSID")+"', START_DATE,  END_DATE,  DESCR, DATA, UPDATED \n" + 
                          "FROM ( \n" + sql + " " + currbunch +"\n)";

                    //if there is something to do...
                    if( control.get("C_TABLENAME") != null ){
                        //System.err.println(finalsql);
                        SQLUtilities.execSQL(datadb, finalsql);
                    }
                    
                    patientcount = "".equals(currbunch) ? patients.length + 1 : patientcount + chunkSize;
                    
                } while ( patientcount < patients.length && !isCancelled() );

                Object itemsfound=SQLUtilities.getTableHashedArray(datadb, "SELECT COUNT(*) AS COUNTER FROM PROJECTS_XLS_EAV WHERE SESSIONID='"+session_id+"' AND LEVEL_CD='"+control.get("SYSID")+"'")[0].get("COUNTER");
                log("Step " + counter + " of " + fields.length + " " + control.get("CONCEPT_NAME") + " found " + itemsfound + " data elements to filter on ");


                if( isfirst ){
                    isfirst = false;
                    loopagain = "0".equals(""+itemsfound);
                    if( loopagain ){
                        log("Step " + counter + " of " + fields.length + " " + control.get("CONCEPT_NAME") + " trying again with modifiers off" );
                    }
                } else {
                    //if not first ( so second time ) do not reloop.
                    loopagain = false;
                }

            } while ( loopagain && !isCancelled());
            
        } //while there is still fields to process.
        
        this.updateStatus("Running", 10 + (int)(80.0 * ((0.0 + counter)/(0.00001 + fields.length)))  );
        
    }
    
    /**
     * This selects out for each variable a small chunk, so that the UI gets to show a meaningful progress bar for each
     * variable - assume you have a patient set of 12M, you wouldn't want all 12M to run all at once (hours long) with
     * no indication of where it is in the process. This therefore breaks it down into somewhat smaller chunks.
     * @param control - what variable are we parsing currently?
     * @param patientcount - current patient count( since this is done iteratively, controlled externally
     * @param previousprocessed - list of previously tackled patients, thus we do not duplicate the patient in the query
     * @return WHERE Statement for a select of a specific group.
     */
    private String generateBatchSelect( HashMap control, int patientcount, HashSet previousprocessed ){
        //for better progress bar notifications, only do small chunks.
        String currbunch = "";
        if( control.get("C_TABLENAME") != null ){
            //we only want to do chunks of 100 at a time...

            
            //do not use this code as it takes forever for it to figure out what 500 patients looks like.
            //currbunch = " AND RS.PATIENT_NUM IN ( " +
            //    "SELECT PATIENT_NUM FROM (" +
            //    "   SELECT ROWNUM AS RN, PATIENT_NUM FROM (" +
            //    "       SELECT PATIENT_NUM FROM "+crcSchema+"."+resulttype+" " +
            //    "       WHERE RESULT_INSTANCE_ID='"+localSettings.get("RESULT_INSTANCE_ID")+"' " +
            //    "       ORDER BY PATIENT_NUM" +
            //    "   )" +
            //    ") " +
            //    "WHERE RN BETWEEN "+patientcount+" AND " + (patientcount+500) +" )";
            currbunch += "AND O.PATIENT_NUM IN ('-1'";
            for( int i = 0 ; i < chunkSize && i+patientcount <  patients.length; i++ ){
                if( previousprocessed.add(patients[patientcount + i].get("PATIENT_NUM")) ){
                    currbunch += ",'"+patients[patientcount + i].get("PATIENT_NUM")+"'";    
                }
            }
            currbunch += ")";
            

        } else if(control.get("C_TABLENAME") == null ){
            //to exit loop.
            log("Skipped " + control.get("CONCEPT_NAME") );
            
        } 
        return currbunch;
    }
    
    /**
     * For each variable, this is the SQL statement generated for each data type. There can be a maximum of two passes
     * performed on each select. If there is no data within the query, it will remove the modifiers on the non-first 
     * attempt to broaden the data search. 
     * @param control - the variable to extract.
     * @param isFirst - if this is false, it will remove the modifiers from the data pull. This is for things like meds
     *                  which might have no data since all meaningful data lives in a modifier.
     * @return 
     */
    private String generateSQL(HashMap control, boolean isFirst){
        String sql = (""+control.get("C_DIMCODE"));
        if( "IN".equalsIgnoreCase( ""+control.get("C_OPERATOR") ) ){    
            sql = "(" + sql + ")";
        } else if( "BETWEEN".equalsIgnoreCase( ""+control.get("C_OPERATOR") ) ){    
        } else if( "LIKE".equalsIgnoreCase( ""+control.get("C_OPERATOR") ) ){
            sql = "'" + sql + "%'";

        } else {
            if( "T".equals( control.get("C_COLUMNDATATYPE") ) ){
                sql = "'" + sql + "'";
            }
        }

        sql = control.get("C_COLUMNNAME") + " " + control.get("C_OPERATOR") + " " + sql;

        if( "PROVIDER_DIMENSION".equalsIgnoreCase(""+control.get("C_TABLENAME")) ){

            sql = "SELECT O.PATIENT_NUM, O.START_DATE, O.END_DATE, to_char(O.START_DATE,'YYYY-MM-DD HH24:MI:SS') AS UPDATED, P.NAME_CHAR AS DATA, P.PROVIDER_ID AS DESCR \n" + 
                  "FROM "+crcSchema+".OBSERVATION_FACT O, "+crcSchema+".PROVIDER_DIMENSION P \n" +
                  "WHERE P.PROVIDER_ID=O.PROVIDER_ID AND \n" +
                  sql ;

            //the second time skip over the modifier to allow children to populate if the parents are missing.
            if( isFirst ){
                if( control.get("MODIFIER") != null ){
                    sql += " AND MODIFIER_CD = '"+control.get("MODIFIER").toString().replaceAll("'", "''")+"' ";
                } else {
                    sql += " AND MODIFIER_CD = '@' ";
                }
            } 

        } else if( "concept_dimension".equalsIgnoreCase(""+control.get("C_TABLENAME")) ){

            sql = "SELECT O.PATIENT_NUM, O.START_DATE, O.END_DATE, to_char(O.START_DATE,'YYYY-MM-DD HH24:MI:SS') AS UPDATED, " +
                  "CASE WHEN NOT NVAL_NUM IS NULL THEN ''||NVAL_NUM " +
                  "     WHEN NOT TVAL_CHAR IS NULL THEN TVAL_CHAR " +
                  " ELSE NAME_CHAR END AS DATA," +
                  "O.CONCEPT_CD AS DESCR \n" + 
                  "FROM "+crcSchema+".OBSERVATION_FACT O, "+crcSchema+".CONCEPT_DIMENSION C \n" +
                  "WHERE C.CONCEPT_CD=O.CONCEPT_CD AND \n" + 
                  sql ;

            //the second time skip over the modifier to allow children to populate if the parents are missing.
            if( isFirst ){
                if( control.get("MODIFIER") != null ){
                    sql += " AND MODIFIER_CD = '"+control.get("MODIFIER").toString().replaceAll("'", "''")+"' ";
                } else {
                    sql += " AND MODIFIER_CD = '@' ";
                }
            }

        } else if( "visit_dimension".equalsIgnoreCase(""+control.get("C_TABLENAME")) ){

            sql = "SELECT O.PATIENT_NUM, O.START_DATE, O.END_DATE, to_char(O.START_DATE,'YYYY-MM-DD HH24:MI:SS') AS UPDATED, " + control.get("C_COLUMNNAME") + " AS DATA,'"+(""+control.get("CONCEPT_NAME")).replace('\'', '`')+"' AS DESCR \n" + 
                  "FROM "+crcSchema+".VISIT_DIMENSION O \n " +
                  "WHERE \n" + 
                   sql  ;

        }  else if( "Patient_dimension".equalsIgnoreCase(""+control.get("C_TABLENAME")) ){

            sql = "SELECT O.PATIENT_NUM, O.BIRTH_DATE AS START_DATE, O.DEATH_DATE AS END_DATE, to_char(O.UPDATE_DATE,'YYYY-MM-DD HH24:MI:SS') AS UPDATED, ''||" + control.get("C_COLUMNNAME") + " AS DATA,'"+(""+control.get("CONCEPT_NAME")).replace('\'', '`')+"' AS DESCR \n" + 
                  "FROM "+crcSchema+".PATIENT_DIMENSION O \n " +
                  "WHERE \n" + 
                   sql  ;

        }
        return sql;
    }
    
    private XSSFWorkbook generateSheet() throws Exception {
        log("Finished Data Calculating, now merging data for export" );
        int rows = 1;
        int cell = 0;
        XSSFWorkbook hwb = new XSSFWorkbook();
        XSSFSheet sheet = hwb.createSheet( WorkbookUtil.createSafeSheetName( localSettings.get("PROJECTCODE") + " Extract of " + localSettings.get("PATIENT_LIST_DESC")));
        sheet.createFreezePane( 0, 1, 0, 1 );
        XSSFRow rowhead = sheet.createRow((short) 0);
        rowhead.createCell(cell++).setCellValue("PATIENTNUM");
        for( HashMap fld : fields ){
            rowhead.createCell(cell++).setCellValue(""+fld.get("CONCEPT_NAME"));
        }

        SQLUtilities finalizer = new SQLUtilities( datadb, "SELECT * FROM PROJECTS_XLS_EAV WHERE SESSIONID='"+session_id+"' ORDER BY PATIENTNUM, STARTING"  );
        int counter = 0;
        Object currentpt = null;
        HashMap currentLine = null;
        LinkedList< AggrAssemblerOnePass > assemblers = new LinkedList();
        while( (currentLine = finalizer.getNextHashLine()) != null ){
            Object thispt = currentLine.get("PATIENTNUM");
            if( thispt == null || !thispt.equals(currentpt) ){

                //mark and complete all previous rows, clear out all choices, then add new assember(s).
                for( AggrAssemblerOnePass assembler: assemblers ){
                    serializeExcelRow( sheet, fields, assembler.serialize(), rows++ );
                    assembler.markComplete(); //this tells the pivot functions to do final data analysis.
                    if( ++counter % chunkSize == 0 ){
                        log("Aggregating Patients Data " + counter + " of " + patients.length );
                    }
                    this.updateStatus("Running", 90 + (int)(10.0 * ((0.0 + counter)/(0.00001 + patients.length)))  );
                }
                assemblers.clear();
                for( HashMap pt : patients ){
                    if( thispt.equals( pt.get("PATIENT_NUM") ) ){
                        assemblers.add( new AggrAssemblerOnePass (pt, fields) );
                    }
                }
                if( assemblers.size() > 0 ){
                    System.out.println(assemblers);
                }
                currentpt = thispt;     
            }
            for( AggrAssemblerOnePass assembler: assemblers ){
                assembler.process(currentLine);
            }
        }

        //finalize the last row (s).
        for( AggrAssemblerOnePass assembler: assemblers ){
            serializeExcelRow( sheet, fields, assembler.serialize(), rows++ );
            assembler.markComplete();
        }

        //go through all of the rows to put in the ids that dont exist.
        for( HashMap pt : patients ){
            if( !pt.containsKey( "DONE" ) ){
                serializeExcelRow( sheet, fields, pt, rows++ );        
            }
        }
        return hwb;
    }
    
    /**
     * This creates a new connection to the database to upload a spreadsheet. This is required so that with Oracle, 
     * another query on the same connection does not accidentally cause the BLOB transaction to span commits, which
     * would fail.
     * 
     * @param hwb - the spreadsheet to save.
     * @throws SQLException - if there are database issues
     * @throws IOException - If for some reason, the data cannot be written to memory.
     */
    
    private void saveSheetToServer(XSSFWorkbook hwb) throws SQLException, IOException {
        
        File outputFolder = new File( settings.getProperty("TEMPORARY_FILES", "/data01/temp/"));
        File currSession = new File( outputFolder.getAbsolutePath() + "/" + project_id + "."+session_id+".xlsx");
        
        PreparedStatement pstmt = datadb.prepareStatement("update PROJECTS_XLS_EXP_JOBS SET lastmost_sheet= ? where SESSIONID = ?");
        log("Writing: " + currSession.getAbsolutePath());
        
        FileOutputStream output = new FileOutputStream(currSession);
        hwb.write( output );
        output.close();
        hwb.close();
        log("Wrote: " + currSession.getAbsolutePath() + " wrote " + currSession.length());
        
        FileInputStream in = new FileInputStream(currSession);

        // the cast to int is necessary because with JDBC 4 there is 
        // also a version of this method with a (int, long) 
        // but that is not implemented by Oracle
        pstmt.setBinaryStream(1, in, (int)currSession.length()); 

        pstmt.setInt(2, Integer.parseInt(session_id));  // set the PK value
        pstmt.executeUpdate();
        
        log("Excel File Saved To Database " );

    }
    
    /**
     * This method takes data passed into it and spews out one aggregated row; coloring the cell if the data is too long.
     * @param The POI Excel spreadsheet.
     * @param fields All of the columns of data you need to export
     * @param data The data in the spreadsheet row, post aggregation
     * @param rowd what row to dump data onto.
     * @throws Exception 
     */
    private void serializeExcelRow( XSSFSheet sheet, HashMap[] fields, HashMap data, int rowd ) throws Exception {
        int cell = 0;
        XSSFRow row = sheet.createRow(rowd);
        HashMap pt = data;
        if( data.containsKey("PATIENT") ){
            pt = (HashMap) data.get("PATIENT");
        }
        row.createCell(cell++).setCellValue( "" + pt.get("PATIENT_NUM") );    
        for( HashMap fld : fields ){
            Object out = data.get(fld);
            String writethis = out == null ? "" : out.toString();
            if( writethis.length() > 32767 ){
                writethis = writethis.toString().substring(0, 32700) + "\n\n!WARNING TOO LONG TRUNCATED!";
                row.createCell(cell).getCellStyle().setFillBackgroundColor(new HSSFColor.RED().getIndex());
            }
            row.createCell(cell).setCellValue( writethis );    
            
            cell++;
        }
    }
    
}
