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
package edu.rochester.urmc.i2b2;

import edu.rochester.urmc.util.DateConverter;
import edu.rochester.urmc.util.HashMapFunctions;
import edu.rochester.urmc.util.SQLUtilities;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class loads data from our clinical data repository (not Clarity) into a brand newly created data mart.
 * Since this is custom to URMC, this has been left in non-generic format so that it may serve as a basis for
 * implementation in other institutions.
 * The load is broken up into 9 distinct chunks, which were designed to execute in a previous life in PHP with very
 * short returns.
 * @author png
 */
public class DatamartLoader extends SQLQueryJob {

    private static final Logger logger = LogManager.getLogger(DatamartLoader.class);
    Connection out = null;
    boolean isfat = false;
    String ontSchema = "";
    String crcSchema = "";
    String projectTitle = "";
    HashMap localSettings = null;

    String url = "";
    String driver =  "";
    String username = "";
    String password = "";
    String driverjar =  "";


    //List of columns not to re-add to the study's metadata tables. This is because they already exist in the
    //demographic structure already and would add no benefit.
    private static List<String> ignored = Arrays.asList(("PATIENT_NUM,VITAL_STATUS_CD,SEX_CD,"
        + "AGE_IN_YEARS_NUM,LANGUAGE_CD,RACE_CD,ETHNICITY_CD,MARITAL_STATUS_CD,RELIGION_CD,"
        + "STATECITYZIP_PATH,INCOME_CD,PATIENT_BLOB,UPDATE_DATE,DOWNLOAD_DATE,IMPORT_DATE,"
        + "SOURCESYSTEM_CD,UPLOAD_ID").split(","));

    public void run(){

        this.updateStatus("Running",1);
        try{

            localSettings = SQLUtilities.getTableHashedArray(cntldb, "SELECT PROJECTTITLE,PHI, CONCEPTS_SELECTED, GENERATION_SQL FROM PROJECTS WHERE PROJECTID='"+project_id+"'")[0];

            url = settings.getProperty("DATAMARTCREATEURL","jdbc:oracle:thin:@i2b202:1521:i2b2");
            driver = settings.getProperty("DATAMARTCREATEDRIVER","oracle.jdbc.OracleDriver" );
            username = settings.getProperty("DATAMARTCREATEUSER","i2b2datacreationuser" );
            password = settings.getProperty("DATAMARTCREATEPASS","" );

            //some housekeeping. see if you can identify the ont, crc from the hive.
            ontSchema = "" + SQLUtilities.getTableHashedArray(datadb, "SELECT C_DB_FULLSCHEMA FROM I2B2HIVE.ONT_DB_LOOKUP WHERE UPPER(TRIM(C_PROJECT_PATH))=UPPER(TRIM('"+project_code+"/'))")[0].get("C_DB_FULLSCHEMA");
            crcSchema = "" + SQLUtilities.getTableHashedArray(datadb, "SELECT C_DB_FULLSCHEMA FROM I2B2HIVE.CRC_DB_LOOKUP WHERE UPPER(TRIM(C_PROJECT_PATH))=UPPER(TRIM('/"+project_code+"/'))")[0].get("C_DB_FULLSCHEMA");

            projectTitle = localSettings.get("PROJECTTITLE").toString().replaceAll("'", "''");

            //ok. So first you want to grab the concepts mapped to (by meta) and the field they want mapped, along with the rule.
            log("schemae found:" + ontSchema + " & " + crcSchema );

            out = java.sql.DriverManager.getConnection(url, username, password);

            for( Object line : new SQLUtilities( out, "select table_name from ALL_TABLES WHERE table_name='OBSERVATION_FACT' AND UPPER(OWNER)=UPPER('"+crcSchema+"')" ) ){
                isfat = true; //there is a OBS fact table locally.
            }

            if( isfat ){
                //we used to create full i2b2 CRC and literal copies of i2b2 datamarts. We instead are using a thin
                //data model instead, utilizing synonyms and views to replicate a full datamart will partitioning
                //each studies' data to its own schemae.
                log( "Automated FAT client datamarts are no longer supported.");
            } else {

                if( !isCancelled() ){
                    updateStatus("Running",2);
                    chunk1();
                    updateStatus("Running",10);
                }
                if( !isCancelled() ){
                    chunk2();
                    updateStatus("Running",20);
                }
                if( !isCancelled() ){
                    chunk3();
                    updateStatus("Running",30);
                }
                if( !isCancelled() ){
                    chunk4();
                    updateStatus("Running",40);
                }
                if( !isCancelled() ){
                    chunk5();
                    updateStatus("Running",50);
                }
                if( !isCancelled() ){
                    chunk6();
                    updateStatus("Running",60);
                }
                if( !isCancelled() ){
                    chunk7();
                    updateStatus("Running",70);
                }
                if( !isCancelled() ){
                    chunk8();
                    updateStatus("Running",80);
                }
                if( !isCancelled() ){
                    chunk9();
                    updateStatus("Running",90);
                }
                log( "Datamart structure created, running ETLs");
                if( !isCancelled() ){
                    chunk10();
                    updateStatus("Running",95);
                }
            }
            updateStatus("Complete");

        } catch (Exception ex ){
            logError(ex);
            this.updateStatus("Error");
        } finally {
            try{
                out.close();
            } catch ( Exception ex ){
                ex.printStackTrace();
            }
        }
    }

    public String[] getNotificationTos(){
        String ans = "phillip_ng@urmc.rochester.edu";
        if( project.get("LAST_UI_TOUCHED") != null && System.currentTimeMillis() - ((Date) project.get("LAST_UI_TOUCHED")).getTime() > 10000 ){
            ans += ",adam_tatro@urmc.rochester.edu,david_pinto@urmc.rochester.edu";
        }
        return ans.split(",");
    }

    /**
     * Clears the datamart if previously built.
     * @throws Exception
     */
    private void chunk1() throws Exception {
        log( "Truncating Data from database" );
        SQLUtilities.execSQL(out, "TRUNCATE TABLE " + crcSchema + ".VISIT_DIMENSION");
        SQLUtilities.execSQL(out, "TRUNCATE TABLE " + crcSchema + ".PATIENT_DIMENSION");
        SQLUtilities.execSQL(out, "TRUNCATE TABLE " + crcSchema + ".CONCEPT_DIMENSION");
        SQLUtilities.execSQL(out, "TRUNCATE TABLE " + crcSchema + ".MODIFIER_DIMENSION");

        log( "Thin Datamart - clearing study specific data");
        SQLUtilities.execSQL(out, "DELETE FROM i2b2demodata.observation_fact where concept_cd like 'URMC:"+project_id+"%'");


    }

    /**
     * Loads up patient dimension from public datamart. Note at this point the info exists from query. This was
     * Generated from pick list from the studies setup page, or it's created from an uploaded list, referenced here by
     * database link. The re-identification has already been performed to lookup the "correct" i2b2 patient_num from
     * the web side.
     * @throws Exception
     */
    private void chunk2() throws Exception {
        String sql = "SELECT PID FROM TRIALS.ENROLLED_PATIENT@CDW WHERE PROJECTID='" + project_id +"'";
        if( localSettings.get("GENERATION_SQL") != null && localSettings.get("GENERATION_SQL").toString().trim().length() > 0 ){
            sql = localSettings.get("GENERATION_SQL").toString();
        }

        //CRI-437 - create queries from queries outside of CDW/i2b2 as source of data.
        if( sql.startsWith("CLARITY:") ){

            //create a new connection to the server.
            String driver = settings.getProperty("CLARITYDRIVER");
            String url = settings.getProperty("CLARITYURL");
            String user = settings.getProperty("CLARITYUSERNAME");
            String pass = settings.getProperty("CLARITYPASSWORD");
            Class.forName(driver);
            Connection in = java.sql.DriverManager.getConnection(url, user , pass);

            boolean truncated = false;
            int counter = 0 ;
            log("Executing query : " + sql.substring(8));
            for( Object line : new SQLUtilities(in, sql.substring(8)) ){
                HashMap i = (HashMap) line;

                String sqlvars = "PROJECTID";
                String sqlvals = "'"+project_id+"'";

                if( i.containsKey("PATIENT_NUM") && i.containsKey("DOB_DATE") ){

                    if( counter++ % 200 == 0 ){
                        log("Adding external data, patient " + counter );
                    }

                    if( !truncated ){
                        SQLUtilities.execSQL(cntldb, "DELETE FROM ENROLLED_PATIENT_XLS_DATA WHERE PROJECTID='"+project_id+"' ");
                        SQLUtilities.execSQL(cntldb, "DELETE FROM ENROLLED_PATIENT WHERE PROJECTID='"+project_id+"' ");
                        truncated = true;
                    }

                    //get next ID #.
                    String sysid = "" + SQLUtilities.getTableArray(cntldb, "SELECT SYSIDS.NEXTVAL FROM DUAL")[0][0];
                    sqlvars += "," + "SYSID";
                    sqlvals += "," + sysid;

                    /*
                    ENROLLED_PATIENT
                    ------------------------------
                    MRN            VARCHAR2(12 BYTE)
                    ADMIT_DATE     VARCHAR2(12 BYTE)   //unused
                    DISCHARGE_DATE VARCHAR2(12 BYTE)   //unused
                    PROJECTID      NUMBER              //needed
                    STUDYID        VARCHAR2(15 BYTE)   //meaningful
                    UPDATED        DATE
                    SYSID          NUMBER              //not null.
                    PID            NUMBER              //needed
                    DOB_DATE       DATE                //needed
                    ENROLLED       DATE                //hoped
                    ARM            VARCHAR2(25 BYTE)
                    */


                    if( i.containsKey("ENROLLED") ){
                        sqlvars += ",ENROLLED";
                        sqlvals += ",TO_DATE('"+ DateConverter.format( i.get("ENROLLED"),"YYYYMMdd") +"','YYYYMMDD')";
                        i.remove("ENROLLED");
                    }
                    if( i.containsKey("DOB_DATE") ){
                        sqlvars += ",DOB_DATE";
                        sqlvals += ",TO_DATE('"+ DateConverter.format( i.get("DOB_DATE"),"YYYYMMdd") +"','YYYYMMDD')";
                        i.remove("DOB_DATE");
                    }
                    if( i.containsKey("UPDATED") ){
                        sqlvars += ",UPDATED";
                        sqlvals += ",TO_DATE('"+ DateConverter.format( i.get("UPDATED"),"YYYYMMdd") +"','YYYYMMDD')";
                        i.remove("UPDATED");
                    } else {
                        sqlvars += ",UPDATED";
                        sqlvals += ",SYSDATE";
                    }
                    if( i.containsKey("STUDYID") ){
                        sqlvars += ",STUDYID";
                        sqlvals += ",'"+ i.get("STUDYID").toString().replaceAll("'", "''")+"'";
                        i.remove("STUDYID");
                    }
                    if( i.containsKey("ARM") ){
                        sqlvars += ",ARM";
                        sqlvals += ",'"+ i.get("ARM").toString().replaceAll("'", "''")+"'";
                        i.remove("ARM");
                    }
                    if( i.containsKey("PATIENT_NUM") ){
                        sqlvars += ",PID";
                        sqlvals += ",'"+ i.get("PATIENT_NUM").toString().replaceAll("'", "''")+"'";
                        i.remove("PATIENT_NUM");
                    }
                    SQLUtilities.execSQL(cntldb, "INSERT INTO ENROLLED_PATIENT ("+sqlvars+") VALUES ("+sqlvals+") ");

                    /*
                    ENROLLED_PATIENT_XLS_DATA
                    ------------------------------
                    PROJECTID      NUMBER(38,0)
                    EXTSYSID       NUMBER(38,0)
                    COLUMNID       VARCHAR2(255 BYTE)
                    VAL            VARCHAR2(255 BYTE)
                    */

                    for( Object key : i.keySet() ){
                        SQLUtilities.execSQL(cntldb, "INSERT INTO ENROLLED_PATIENT_XLS_DATA (PROJECTID,EXTSYSID,COLUMNID,VAL) VALUES "
                            + "("+project_id+"," + sysid + ",'" + key.toString().replaceAll("'", "''")+ "','"+ i.get(key).toString().replaceAll("'", "''")+"')"
                        );
                    }

                } else {
                    throw new IllegalArgumentException( "The query " + sql.substring(8) + " does not have a PATIENT_NUM or DOB_DATE column, we have: " + i);
                }
            }
            //reset the SQL so that the following can populate normally.
            sql = "SELECT PID FROM TRIALS.ENROLLED_PATIENT@CDW WHERE PROJECTID='" + project_id +"'";
            in.close();
        }

        sql = "INSERT INTO " + crcSchema + ".PATIENT_DIMENSION( PATIENT_NUM,VITAL_STATUS_CD,BIRTH_DATE,DEATH_DATE,SEX_CD,AGE_IN_YEARS_NUM,LANGUAGE_CD,RACE_CD,ETHNICITY_CD,MARITAL_STATUS_CD,\n" +
            "		RELIGION_CD,ZIP_CD,STATECITYZIP_PATH,INCOME_CD,UPDATE_DATE,DOWNLOAD_DATE,IMPORT_DATE,SOURCESYSTEM_CD, UPLOAD_ID )\n" +
            "SELECT PATIENT_NUM,VITAL_STATUS_CD,BIRTH_DATE,DEATH_DATE,SEX_CD,AGE_IN_YEARS_NUM,LANGUAGE_CD,RACE_CD,ETHNICITY_CD,MARITAL_STATUS_CD,\n" +
            "		RELIGION_CD,ZIP_CD,STATECITYZIP_PATH,INCOME_CD,UPDATE_DATE,DOWNLOAD_DATE,IMPORT_DATE,SOURCESYSTEM_CD, UPLOAD_ID\n" +
            "FROM i2b2DEMODATA.PATIENT_DIMENSION\n" +
            "WHERE PATIENT_NUM IN ( "+sql+"	)";
        System.out.println("DataMart creation pt load SQL is: " + sql );

        log( "Loading Patient List.");
        SQLUtilities.execSQL(out, sql);

        log( HashMapFunctions.logHashMapArrayToTable ("Loaded:",
            SQLUtilities.getTableHashedArray(out, "SELECT COUNT(*) AS NUMBER_PTS FROM "+ crcSchema + ".PATIENT_DIMENSION"))
        );


    }

    /**
     * Loads the distinct list of visits from the existing public side, based on the list loaded in step 2.
     * @throws Exception
     */
    private void chunk3() throws Exception {

        String sql = "";
        if( localSettings.get("GENERATION_SQL") != null && localSettings.get("GENERATION_SQL").toString().trim().length() > 0 ){
            sql = localSettings.get("GENERATION_SQL").toString();
        }
        //CRI-364 - use encounter set if chosen.
        if( sql.toLowerCase().indexOf("qt_patient_enc_collection") > 0 ){
            log( "Loading Visit List - Encounter Set");
            SQLUtilities.execSQL(out, "INSERT INTO " + crcSchema + ".VISIT_DIMENSION\n" +
                "SELECT * FROM i2b2DEMODATA.VISIT_DIMENSION\n" +
                "WHERE ENCOUNTER_NUM IN (\n" +
                    sql.replaceAll("SELECT PATIENT_NUM FROM", "SELECT ENCOUNTER_NUM FROM") +
                ")"
            );
        } else {
            log( "Loading Visit List - Standard");
            SQLUtilities.execSQL(out, "INSERT INTO " + crcSchema + ".VISIT_DIMENSION\n" +
                "SELECT * FROM i2b2DEMODATA.VISIT_DIMENSION\n" +
                "WHERE PATIENT_NUM IN (\n" +
                "	SELECT PATIENT_NUM FROM " + crcSchema + ".PATIENT_DIMENSION\n" +
                ")"
            );
        }
        log( HashMapFunctions.logHashMapArrayToTable ("Loaded:",
            SQLUtilities.getTableHashedArray(out, "SELECT COUNT(*) AS NUM_VISITS FROM "+ crcSchema + ".VISIT_DIMENSION"))
        );
    }

    /**
     * This copies over the modifier dimension and concept_dimension from production. Note this is run fresh because
     * any data that has been run or uploaded
     * @throws Exception
     */
    private void chunk4() throws Exception {
        log( "Loading Modifiers");
        SQLUtilities.execSQL(out, "INSERT INTO " + crcSchema + ".MODIFIER_DIMENSION\n" +
                "SELECT * FROM i2b2DEMODATA.MODIFIER_DIMENSION"
        );
        log( "Loading Concepts");
        SQLUtilities.execSQL(out,
            "INSERT INTO " + crcSchema + ".CONCEPT_DIMENSION\n" +
            "SELECT * FROM i2b2DEMODATA.CONCEPT_DIMENSION "
        );
    }

    /**
     * This populates observation_fact with just a study id, so it is essentially making one row with the study id in it
     * so it's a patient selector list on the left. This was needed in the first revision of the thin datamart model.
     * @throws Exception
     */
    private void chunk5() throws Exception {
        log( "Populating With Study IDS");
        SQLUtilities.execSQL(out,
            "INSERT INTO I2B2DEMODATA.OBSERVATION_FACT( ENCOUNTER_NUM, PATIENT_NUM, CONCEPT_CD, PROVIDER_ID, START_DATE, MODIFIER_CD, INSTANCE_NUM, VALTYPE_CD, TVAL_CHAR, END_DATE, UPDATE_DATE, DOWNLOAD_DATE, IMPORT_DATE, SOURCESYSTEM_CD )\n" +
            "SELECT -PATIENT_NUM, PATIENT_NUM, 'URMC:"+project_id+"', '@', NVL(BIRTH_DATE,TO_DATE('19000101','YYYYMMDD')), '@', '1', 'T', 'In study', TO_DATE('22000101','YYYYMMDD'), SYSDATE, SYSDATE, SYSDATE, 'XLS'\n" +
            "FROM " + crcSchema + ".PATIENT_DIMENSION "
        );
    }

    /**
     * This removes out any of the concepts they had before mapped out if they uploaded an excel document. This will
     * repopulate the obs fact with the latest and hopefully greatest uploaded excel spreadsheet. This doesn't copy any
     * excel data, just the columns and metadata.
     *
     * @throws Exception
     */
    private void chunk6() throws Exception {
        log( "Deleting project custom concept data");
        SQLUtilities.execSQL(out,
            "DELETE FROM " + crcSchema + ".CONCEPT_DIMENSION WHERE CONCEPT_PATH LIKE '\\i2b2\\Excel Data\\%'"
        );
        SQLUtilities.execSQL(out,
            "DELETE FROM " + crcSchema + ".CONCEPT_DIMENSION WHERE CONCEPT_PATH LIKE '\\i2b2\\" + projectTitle + "\\%'"
        );
        SQLUtilities.execSQL(out,
            "TRUNCATE TABLE " + crcSchema + ".TABLE_ACCESS"
        );
        SQLUtilities.execSQL(out,
            "INSERT INTO " + crcSchema + ".TABLE_ACCESS SELECT * FROM i2b2metadata.TABLE_ACCESS WHERE C_TABLE_CD IN ( '"+
                localSettings.get("CONCEPTS_SELECTED").toString().replaceAll("'", "''").replaceAll(",", "','")+"') "
        );
        //unset any hidden fields so they can query.
        SQLUtilities.execSQL(out,
            "UPDATE " + crcSchema + ".TABLE_ACCESS SET C_PROTECTED_ACCESS='N'"
        );
        //Add a space so that this shows up at the top of the selection box on the left in the display name.
        SQLUtilities.execSQL(out,
            "INSERT INTO " + ontSchema + ".TABLE_ACCESS( "
                + " C_TABLE_CD, C_TABLE_NAME,  C_PROTECTED_ACCESS,  C_HLEVEL,  C_FULLNAME,  C_NAME,  C_SYNONYM_CD,  "
                + "C_VISUALATTRIBUTES, C_FACTTABLECOLUMN, C_DIMTABLENAME, C_COLUMNNAME, C_COLUMNDATATYPE, "
                + "C_OPERATOR, C_DIMCODE, C_TOOLTIP"
            + ") VALUES ( "
                + "'i2b2_EXCEL', 'EXCEL',     'N',       '1', '\\i2b2\\" + projectTitle + "\\', "
                + "' " + projectTitle + " Data', 'N', 'FA', 'concept_cd', 'concept_dimension', "
                + "'concept_path', 'T', 'LIKE', '\\i2b2\\" + projectTitle + "\\', "
                + "' " + projectTitle + " Project Data')"
        );

        boolean existsXLSTable = false;
        for( Object line : new SQLUtilities( out,
            "select table_name from ALL_TABLES WHERE table_name='EXCEL' AND UPPER(OWNER)=UPPER('"+ontSchema+"')"
        ) ){
            existsXLSTable = true; //there is a OBS fact table locally.
        }

        //create basic structure for the external data if it doesn't exist.
        if( !existsXLSTable ){
            log( "Creating Excel Schema");
            SQLUtilities.execSQL(out, "CREATE TABLE "+ontSchema+".EXCEL AS SELECT * FROM i2b2metadata.i2b2 where 0=1" );
        }

        log( "Creating End User Uploaded Schema");
        SQLUtilities.execSQL(out, "TRUNCATE TABLE " + ontSchema + ".EXCEL" );

        SQLUtilities.execSQL(out,
            "INSERT INTO " + ontSchema + ".EXCEL( C_HLEVEL, C_FULLNAME, C_NAME, C_SYNONYM_CD, C_VISUALATTRIBUTES, C_FACTTABLECOLUMN, "
                + " C_TABLENAME, C_COLUMNNAME, C_COLUMNDATATYPE, C_OPERATOR, C_DIMCODE, C_TOOLTIP, M_APPLIED_PATH, "
                + " UPDATE_DATE, DOWNLOAD_DATE, IMPORT_DATE, SOURCESYSTEM_CD"
            + ") VALUES ( "
                + "'1', '\\i2b2\\" + projectTitle + "\\', ' " + projectTitle + " Data', 'N', 'LA', 'concept_cd', "
                + "'concept_dimension', 'concept_path', 'T', 'LIKE', '\\i2b2\\" + projectTitle + "\\', "
                + "'Data from " + projectTitle + " excel', '@', SYSDATE, SYSDATE, SYSDATE, 'XLS')"
        );
        SQLUtilities.execSQL(out,
            "INSERT INTO " + crcSchema + ".CONCEPT_DIMENSION( CONCEPT_PATH,CONCEPT_CD,NAME_CHAR, UPDATE_DATE, DOWNLOAD_DATE, IMPORT_DATE, SOURCESYSTEM_CD ) VALUES ("
            + "'\\i2b2\\" + projectTitle + "\\', 'URMC:"+project_id+"','In this study', SYSDATE, SYSDATE, SYSDATE, 'XLS')"
        );

        //add the user added excel data into the database. Note, the columns studyid, arm and enrolled are in the
        //both the enrolled_patients and in the EAV data as well. Thus we're excluding them.
        log( "Creating End User Uploaded Columns");
        for( Object line : new SQLUtilities( cntldb, "SELECT DISTINCT COLUMNID FROM ENROLLED_PATIENT_XLS_DATA WHERE PROJECTID='"+project_id+"' AND NOT COLUMNID IN ('ENROLLED','STUDYID','ARM')" ) ){
            HashMap row = (HashMap) line;
            String column = row.get("COLUMNID").toString().replaceAll("'", "''");
            addColumn( column );
        }
        addColumn( "ENROLLED" );
        addColumn( "STUDYID" );
        addColumn( "ARM" );
    }

    /**
     * This function adds a single concept to the study's datamart.
     * @param column
     * @throws Exception
     */
    private void addColumn( String column ) throws Exception {
        log( "Adding Column " + column );
        SQLUtilities.execSQL(out,
            "INSERT INTO " + ontSchema + ".EXCEL( C_HLEVEL, C_FULLNAME, C_NAME, C_SYNONYM_CD, C_VISUALATTRIBUTES, C_FACTTABLECOLUMN, C_TABLENAME, "
                + "C_COLUMNNAME, C_COLUMNDATATYPE, C_OPERATOR, C_DIMCODE, C_TOOLTIP, M_APPLIED_PATH, "
                + "UPDATE_DATE, DOWNLOAD_DATE, IMPORT_DATE, SOURCESYSTEM_CD ) VALUES ( '2', "
                + "'\\i2b2\\" + projectTitle + "\\" + column + "\\', '" + column + "', 'N', 'LA', 'concept_cd',"
                + " 'concept_dimension', 'concept_path', 'T', 'LIKE', '\\i2b2\\" + projectTitle + "\\" + column + "\\', "
                + "'Data from excel, column " + column + "', '@', SYSDATE, SYSDATE, SYSDATE, 'XLS' )"
        );
        SQLUtilities.execSQL(out,
            "INSERT INTO " + crcSchema + ".CONCEPT_DIMENSION( CONCEPT_PATH,CONCEPT_CD,NAME_CHAR, UPDATE_DATE, DOWNLOAD_DATE, "
                + "IMPORT_DATE, SOURCESYSTEM_CD ) VALUES ( '\\i2b2\\" + projectTitle + "\\" + column + "\\', "
                + "'URMC:"+project_id+":" + column + "','" + column + "', SYSDATE, SYSDATE, SYSDATE, 'XLS' ) "
        );
    }

    /**
     * This copies the excel row by row, studyid, arm, and enrolled date to the observation data.
     * @throws Exception
     */
    private void chunk7() throws Exception {
        //excel data.
        SQLUtilities.execSQL(out,"INSERT INTO I2B2DEMODATA.OBSERVATION_FACT("
            + "			ENCOUNTER_NUM,"
            + "			PATIENT_NUM,"
            + "			CONCEPT_CD,"
            + "			PROVIDER_ID,"
            + "			START_DATE,"
            + "			MODIFIER_CD,"
            + "			INSTANCE_NUM,"
            + "			VALTYPE_CD,"
            + "			TVAL_CHAR,"
            + "			END_DATE,"
            + "			UPDATE_DATE,"
            + " 		DOWNLOAD_DATE,"
            + "			IMPORT_DATE,"
            + "			SOURCESYSTEM_CD)"
            + "		SELECT -PID, PID, 'URMC:"+project_id+":'||REPLACE(COLUMNID,'''',''''''),"
            + "			'@',"
            + "			NVL(DOB_DATE,TO_DATE('19000101','YYYYMMDD')),"
            + "			'@',"
            + "			'1',"
            + "			'T',"
            + "			VAL,"
            + "			TO_DATE('22000101','YYYYMMDD'),"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + " 		'XLS'"
            + "		FROM TRIALS.ENROLLED_PATIENT_XLS_DATA@CDW XLS, TRIALS.ENROLLED_PATIENT@CDW PT"
            + "		WHERE XLS.EXTSYSID = PT.SYSID AND NOT PID IS NULL AND PT.PROJECTID='"+project_id+"' "
            + "           AND NOT COLUMNID IN ('ENROLLED','STUDYID','ARM')"
        );

        //add the enrolled date.
        SQLUtilities.execSQL(out,"INSERT INTO I2B2DEMODATA.OBSERVATION_FACT("
            + "			ENCOUNTER_NUM,"
            + "			PATIENT_NUM,"
            + "			CONCEPT_CD,"
            + "			PROVIDER_ID,"
            + "			START_DATE,"
            + "			MODIFIER_CD,"
            + "			INSTANCE_NUM,"
            + "			VALTYPE_CD,"
            + "			TVAL_CHAR,"
            + "			END_DATE,"
            + "			UPDATE_DATE,"
            + " 		DOWNLOAD_DATE,"
            + "			IMPORT_DATE,"
            + "			SOURCESYSTEM_CD)"
            + "		SELECT -PID, PID, 'URMC:"+project_id+":ENROLLED',"
            + "			'@',"
            + "			NVL(DOB_DATE,TO_DATE('19000101','YYYYMMDD')),"
            + "			'@',"
            + "			'1',"
            + "			'T',"
            + "			TO_CHAR(ENROLLED,'MM/DD/YYYY HH24:MI'),"
            + "			TO_DATE('22000101','YYYYMMDD')," //if you see this comment and it is 2199AD, then I am very sorry for you.
            + "			SYSDATE,"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + " 		'XLS'"
            + "		FROM TRIALS.ENROLLED_PATIENT@CDW PT"
            + "		WHERE NOT PID IS NULL AND NOT ENROLLED IS NULL AND PT.PROJECTID='"+project_id+"'"
        );

        //add the study id date.
        SQLUtilities.execSQL(out,"INSERT INTO I2B2DEMODATA.OBSERVATION_FACT("
            + "			ENCOUNTER_NUM,"
            + "			PATIENT_NUM,"
            + "			CONCEPT_CD,"
            + "			PROVIDER_ID,"
            + "			START_DATE,"
            + "			MODIFIER_CD,"
            + "			INSTANCE_NUM,"
            + "			VALTYPE_CD,"
            + "			TVAL_CHAR,"
            + "			END_DATE,"
            + "			UPDATE_DATE,"
            + " 		DOWNLOAD_DATE,"
            + "			IMPORT_DATE,"
            + "			SOURCESYSTEM_CD)"
            + "		SELECT -PID, PID, 'URMC:"+project_id+":STUDYID',"
            + "			'@',"
            + "			NVL(DOB_DATE,TO_DATE('19000101','YYYYMMDD')),"
            + "			'@',"
            + "			'1',"
            + "			'T',"
            + "			STUDYID,"
            + "			TO_DATE('22000101','YYYYMMDD'),"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + " 		'XLS'"
            + "		FROM TRIALS.ENROLLED_PATIENT@CDW PT"
            + "		WHERE NOT PID IS NULL AND STUDYID IS NOT NULL AND PT.PROJECTID='"+project_id+"'"
        );

        //add the arm.
        SQLUtilities.execSQL(out,"INSERT INTO I2B2DEMODATA.OBSERVATION_FACT("
            + "			ENCOUNTER_NUM,"
            + "			PATIENT_NUM,"
            + "			CONCEPT_CD,"
            + "			PROVIDER_ID,"
            + "			START_DATE,"
            + "			MODIFIER_CD,"
            + "			INSTANCE_NUM,"
            + "			VALTYPE_CD,"
            + "			TVAL_CHAR,"
            + "			END_DATE,"
            + "			UPDATE_DATE,"
            + " 		DOWNLOAD_DATE,"
            + "			IMPORT_DATE,"
            + "			SOURCESYSTEM_CD)"
            + "		SELECT -PID, PID, 'URMC:"+project_id+":ARM',"
            + "			'@',"
            + "			NVL(DOB_DATE,TO_DATE('19000101','YYYYMMDD')),"
            + "			'@',"
            + "			'1',"
            + "			'T',"
            + "			ARM,"
            + "			TO_DATE('22000101','YYYYMMDD'),"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + "			SYSDATE,"
            + " 		'XLS'"
            + "		FROM TRIALS.ENROLLED_PATIENT@CDW PT"
            + "		WHERE NOT PID IS NULL AND ARM IS NOT NULL AND PT.PROJECTID='"+project_id+"'"
        );
    }

    /**
     * This modifies the patient table to store the PHI for these identified patients in an identified datamart. This
     * also keeps the data out of the main warehouse observation_fact.
     * @throws Exception
     */
    private void chunk8() throws Exception {
        if( "Y".equals( localSettings.get("PHI") )) {
            log("Study is a PHI based study.");
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD FIRST_NAME    VARCHAR2(80)");
                log("Added Column FIRST_NAME");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD LAST_NAME     VARCHAR2(80)");
                log("Added Column LAST_NAME");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD MIDDLE_NAME   VARCHAR2(80)");
                log("Added Column MIDDLE_NAME");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD NAME_SUFFIX   VARCHAR2(10)");
                log("Added Column NAME_SUFFIX");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD NAME_PREFIX   VARCHAR2(10)");
                log("Added Column NAME_PREFIX");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD NAME_DEGREE   VARCHAR2(10)");
                log("Added Column NAME_DEGREE");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD SMHMRN        VARCHAR2(10)");
                log("Added Column SMHMRN");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD HHMRN         VARCHAR2(10)");
                log("Added Column HHMRN");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD ADDRESS_LINE1 VARCHAR2(80)");
                log("Added Column ADDRESS_LINE1");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD ADDRESS_LINE2 VARCHAR2(80)");
                log("Added Column ADDRESS_LINE2");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD ADDRESS_LINE3 VARCHAR2(80)");
                log("Added Column ADDRESS_LINE3");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD CITY          VARCHAR2(80)");
                log("Added Column CITY");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD STATE_CD      VARCHAR2(15)");
                log("Added Column STATE_CD");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD HOME_PHONE    VARCHAR2(20)");
                log("Added Column HOME_PHONE");
            } catch(Exception ex){}
            try{
                SQLUtilities.execSQL(out,"ALTER TABLE " + crcSchema + ".PATIENT_DIMENSION ADD WORK_PHONE    VARCHAR2(20)");
                log("Added Column WORK_PHONE");
            } catch(Exception ex){}

            SQLUtilities.execSQL(out, "UPDATE ("
                + "	SELECT"
                + "	PATIENT_DIMENSION.FIRST_NAME     AS O_FIRST_NAME    , PATIENT.FIRST_NAME     AS N_FIRST_NAME    ,"
                + "	PATIENT_DIMENSION.LAST_NAME      AS O_LAST_NAME     , PATIENT.LAST_NAME      AS N_LAST_NAME     ,"
                + "	PATIENT_DIMENSION.MIDDLE_NAME    AS O_MIDDLE_NAME   , PATIENT.MIDDLE_NAME    AS N_MIDDLE_NAME   ,"
                + "	PATIENT_DIMENSION.NAME_SUFFIX    AS O_NAME_SUFFIX   , PATIENT.NAME_SUFFIX    AS N_NAME_SUFFIX   ,"
                + "	PATIENT_DIMENSION.NAME_PREFIX    AS O_NAME_PREFIX   , PATIENT.NAME_PREFIX    AS N_NAME_PREFIX   ,"
                + "	PATIENT_DIMENSION.NAME_DEGREE    AS O_NAME_DEGREE   , PATIENT.NAME_DEGREE    AS N_NAME_DEGREE   ,"
                + "	PATIENT_DIMENSION.ADDRESS_LINE1  AS O_ADDRESS_LINE1 , PATIENT.ADDRESS_LINE1  AS N_ADDRESS_LINE1 ,"
                + "	PATIENT_DIMENSION.ADDRESS_LINE2  AS O_ADDRESS_LINE2 , PATIENT.ADDRESS_LINE2  AS N_ADDRESS_LINE2 ,"
                + "	PATIENT_DIMENSION.ADDRESS_LINE3  AS O_ADDRESS_LINE3 , PATIENT.ADDRESS_LINE3  AS N_ADDRESS_LINE3 ,"
                + "	PATIENT_DIMENSION.CITY           AS O_CITY          , PATIENT.CITY           AS N_CITY          ,"
                + "	PATIENT_DIMENSION.STATE_CD       AS O_STATE_CD      , PATIENT.STATE_CD       AS N_STATE_CD      ,"
                + "	PATIENT_DIMENSION.HOME_PHONE     AS O_HOME_PHONE    , PATIENT.HOME_PHONE     AS N_HOME_PHONE    ,"
                + "	PATIENT_DIMENSION.WORK_PHONE     AS O_WORK_PHONE    , PATIENT.WORK_PHONE     AS N_WORK_PHONE"
                + "	FROM CDWMGR.PATIENT@CDW INNER JOIN " + crcSchema + ".PATIENT_DIMENSION ON PATIENT_DIMENSION.UPLOAD_ID=PATIENT.PID"
                + ")"
            + "SET"
                + "	O_FIRST_NAME    =N_FIRST_NAME   ,"
                + "	O_LAST_NAME     =N_LAST_NAME    ,"
                + "	O_MIDDLE_NAME   =N_MIDDLE_NAME  ,"
                + "	O_NAME_SUFFIX   =N_NAME_SUFFIX  ,"
                + "	O_NAME_PREFIX   =N_NAME_PREFIX  ,"
                + "	O_NAME_DEGREE   =N_NAME_DEGREE  ,"
                + "	O_ADDRESS_LINE1 =N_ADDRESS_LINE1,"
                + "	O_ADDRESS_LINE2 =N_ADDRESS_LINE2,"
                + "	O_ADDRESS_LINE3 =N_ADDRESS_LINE3,"
                + "	O_CITY          =N_CITY         ,"
                + "	O_STATE_CD      =N_STATE_CD     ,"
                + "	O_HOME_PHONE    =N_HOME_PHONE   ,"
                + "	O_WORK_PHONE    =N_WORK_PHONE"
            );

            for( String i : ignored ){
                System.out.println(">>> "  + i );
            }

            //we only want to insert the columns not selectable...
            for( String col : SQLUtilities.getFieldNames(out, "SELECT * FROM " + crcSchema + ".PATIENT_DIMENSION WHERE ROWNUM < 2") ){

                if( !ignored.contains(col.toUpperCase().trim())){
                    if( col.toUpperCase().trim().endsWith("_DATE") ){
                        log( "Adding Date Row " + col );
                        SQLUtilities.execSQL(out, "INSERT INTO " + ontSchema + ".EXCEL( C_HLEVEL, C_FULLNAME, C_NAME, "
                            + "C_SYNONYM_CD, C_VISUALATTRIBUTES, C_FACTTABLECOLUMN, C_TABLENAME, C_COLUMNNAME, "
                            + "C_COLUMNDATATYPE, C_OPERATOR, C_DIMCODE, C_TOOLTIP, M_APPLIED_PATH, UPDATE_DATE, "
                            + "DOWNLOAD_DATE, IMPORT_DATE, SOURCESYSTEM_CD ) VALUES ( '2', "
                            + "'\\i2b2\\" + projectTitle + "\\" + col + "\\', '" + col + "', 'N', 'LA', 'patient_num', 'patient_dimension',"
                            + " 'TO_CHAR(" + col + ",''MM/DD/YYYY'')', 'T', '<>', '''''', 'Data from CDW, column " + col + "', '@', SYSDATE, SYSDATE, SYSDATE, 'XLS' ) "
                        );
                    } else {
                        log( "Adding Row " + col );
                        SQLUtilities.execSQL(out, "INSERT INTO " + ontSchema + ".EXCEL( C_HLEVEL, C_FULLNAME, C_NAME, "
                            + "C_SYNONYM_CD, C_VISUALATTRIBUTES, C_FACTTABLECOLUMN, C_TABLENAME, C_COLUMNNAME, "
                            + "C_COLUMNDATATYPE, C_OPERATOR, C_DIMCODE, C_TOOLTIP, M_APPLIED_PATH, UPDATE_DATE, "
                            + "DOWNLOAD_DATE, IMPORT_DATE, SOURCESYSTEM_CD ) VALUES ( '2', "
                            + "'\\i2b2\\" + projectTitle + "\\" + col + "\\', '" + col + "', 'N', 'LA', 'patient_num', 'patient_dimension',"
                            + " '" + col + "', 'T', '<>', '''''', 'Data from CDW, column " + col + "', '@', SYSDATE, SYSDATE, SYSDATE, 'XLS' ) "
                        );
                    }
                }
            }

        } else {
            log("Study is not a PHI based study.");
        }

    }

    /**
     * This method updates the table with the MRN fetched from the data warehouse.
     * @throws Exception
     */
    private void chunk9() throws Exception {
        log("Correcting MRNS");
        int counter= 0;
        for( Object i : new SQLUtilities(out,"SELECT i2b2.UPLOAD_ID , MRNUM, EXTAPP_CD "
            + "FROM CDWMGR.EXTPAT@CDW, " + crcSchema + ".PATIENT_DIMENSION i2b2 "
            + "WHERE (EXTPAT.LINK_PID = i2b2.UPLOAD_ID OR EXTPAT.PID = i2b2.UPLOAD_ID) AND EXTPAT.EXTAPP_CD IN ('HH','SMH')")
        ){
            if( counter++ % 1000 == 0 ){
                System.out.println("Correcting " + counter);
            }
            HashMap row = (HashMap) i;
            SQLUtilities.execSQL(out, "UPDATE " + crcSchema + ".PATIENT_DIMENSION "
                + "SET "+row.get("EXTAPP_CD")+"MRN='"+row.get("MRNUM")+"' "
                + "WHERE UPLOAD_ID='"+row.get("UPLOAD_ID")+"'"
            );
        }
    }

    /**
     * This method runs the ETLs that the end user has requested.
     */
    private void chunk10() throws Exception {
        log("Correcting MRNS");

        for( Object i : new SQLUtilities(out,"SELECT C_TABLE_CD, C_COMMENT FROM " + crcSchema + ".TABLE_ACCESS WHERE C_COMMENT IS NOT NULL")){
            if( !isCancelled() ){
                HashMap row = (HashMap) i;
                if( row.get("C_COMMENT") != null ){
                    for( String classes : row.get("C_COMMENT").toString().split(",") ){
                        log( "Running ETL: " + classes );

                        //clear the status so the inner classes don't explode.
                        updateStatus("Running",95,"");

                        SQLQueryJob microjob = createBabyJob(classes);
                        if( microjob != null && !isCancelled()){
                            microjob.run();
                        } else {
                            log( "<span color='red'>ETL Failed: " + classes + "</span>");
                        }

                        updateStatus("Running",95,row.get("C_COMMENT").toString());
                    }
                }
            }
        }


    }

    private SQLQueryJob createBabyJob( String job_type ){
        SQLQueryJob runthis = null;
        try{

            Class inner = Class.forName(job_type);
            if( inner.newInstance() instanceof SQLQueryJob ){
                runthis = (SQLQueryJob) inner.newInstance();
            }

        } catch( ClassNotFoundException ex1 ){
            try{
                Class inner = Class.forName("edu.rochester.urmc.i2b2." +job_type);
                if( inner.newInstance() instanceof SQLQueryJob ){
                    runthis = (SQLQueryJob) inner.newInstance();
                }
            } catch ( Exception ex2 ){
                log("Class not createable, try 2 " + ex2.getMessage() );
            }
        } catch ( Exception ex2 ){
            log("Class not createable " + ex2.getMessage());
        }
        if( runthis != null ){
            runthis.initialize(this.cntldb, this.datadb, this.project, this.settings);
        }
        return runthis;
    }
}
