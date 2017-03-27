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

package edu.rochester.urmc.i2b2.redcap;

import edu.rochester.urmc.i2b2.SQLQueryJobLogOnce;
import edu.rochester.urmc.util.DateConverter;
import edu.rochester.urmc.util.HashMapFunctions;
import edu.rochester.urmc.util.SQLUtilities;
import java.io.BufferedWriter;
import java.security.MessageDigest;

import java.sql.Connection;

import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * This class is the main class that controls the generation of the EAV files out of i2b2. 
 * 
 * It merges together the identified list, as present in the control database, and then outputs the data into the 
 * data generated database for later processing.
 * 
 * In its operation, 
 *  - it starts out by extracting the patient list, 
 *  - generates out the metadata in one pass so that the patient processing threads do not need to do that work
 *  - Spools out 5 worker threads to extract out the data from i2b2 and generate data for those patients in one shot.
 *  - It then monitors the generation of the five threads, creating them in each successive run until completion.
 *  - Then lastly it generates an excel preview in a tabbed native excel format.
 * 
 * @author png
 */
public class RedcapSync extends SQLQueryJobLogOnce {
    
    public HashSet allarms = new HashSet();
    
    
    public String crcSchema = "";
    public String ontSchema = "";
    
    protected boolean testmode = false;
    
    public HashMap[] allmeta ={};
    
    public HashMap[] allevents = { };
    
    public HashMap[] allfields = {};
    
    public HashMap[] masterfields = {};
    
    public HashMap testfields = null;
    
    //When doing debugging output, this is the list of variables that constitute useful output.
    List USEFUL_DISPLAY = Arrays.asList("UNIQUE_EVENT_NAME","FIELD_NAME","AGGR_TYPE","ORDNUNG","VALUE","C_DIMCODE","MODIFIER","FILTERED");

    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
    
    public void run(){
       
        
        try{
            this.updateStatus("Running",0);
            
            cleanup();
            
            generateMetaData();
            
            log("this project("+project_id+") has " + allarms  + " arms. ");
            
            this.updateStatus("Running",1);
            
            createAndMonitorThreads();
            
            if( !testmode ){
                //now save data to excel.
                log( "Excel Preview Generating...");
                new ExcelPreviewGenerator( this , project ).generateFile();
                log( "Excel Preview Generated.");
            }
            

            if( !isCancelled() ){
                this.updateStatus("Completed");
            } else {
                this.updateStatus("Cancelled");
            }



        } catch (Exception ex){

            logError( ex );


        } 
    }  
        
    /**
     * This method cleans up from any previous EAV file generation
     * @throws Exception - if the cleanup fails
     */
    private void cleanup() throws Exception {
        //first you want to make an output file to store the data/results/errors to be returned. Clear existing
        //records as to not contaminate the output. If you'r in test mode, which is a single patient 
        //test of the system, only clear out just your patient data.
        int starting = 0;
        try{
            starting = Integer.parseInt(project.get("PARAMS").toString());
        } catch( Exception ex ){}
        
        if( !testmode ){
            if( starting == 0 ){
                SQLUtilities.execSQL(datadb, "DELETE FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID ='"+project_id+"'"); 
            }
        } else {
            SQLUtilities.execSQL(datadb, "DELETE FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID ='"+project_id+"' AND PATIENT_NUM='"+testfields.get("TESTPATIENT")+"' AND VAR='"+testfields.get("TESTFIELD")+"'"); 
        }
    }
    
    private void generateMetaData() throws Exception {

        //some housekeeping. see if you can identify the ont, crc from the hive.
        ontSchema = "" + SQLUtilities.getTableHashedArray(datadb, "SELECT C_DB_FULLSCHEMA FROM I2B2HIVE.ONT_DB_LOOKUP WHERE UPPER(TRIM(C_PROJECT_PATH))=UPPER(TRIM('"+project_code+"/'))")[0].get("C_DB_FULLSCHEMA");
        crcSchema = "" + SQLUtilities.getTableHashedArray(datadb, "SELECT C_DB_FULLSCHEMA FROM I2B2HIVE.CRC_DB_LOOKUP WHERE UPPER(TRIM(C_PROJECT_PATH))=UPPER(TRIM('/"+project_code+"/'))")[0].get("C_DB_FULLSCHEMA");

        //ok. So first you want to grab the concepts mapped to (by meta) and the field they want mapped, along with the rule.
        log("schemae found:" + ontSchema + " & " + crcSchema );


        //locate all ontologies.
        allmeta = SQLUtilities.getTableHashedArray(datadb,
            "SELECT C_TABLE_CD, C_TABLE_NAME, C_FULLNAME FROM "+ontSchema+".TABLE_ACCESS "
        );

        String where = "";
        if( testmode ){
            where += "AND MAP.FIELD_NAME = '"+testfields.get("TESTFIELD").toString().replaceAll("'", "''")+"'";
            log("Testing Clauses: " + where );                
        }
        
        //load all forms for all events and arms. for longitundinal studies this will be populated. For other studies, 
        // this will be sized 1, because of the default "$" event and arm that is added to the schema by the web part.
        //look up all of the fields that need to be mapped, this way only the items that actually get mapped are brought in.
        //we are ordering by field and desired order of operations - for shortcircuit analysis.
        allevents = SQLUtilities.getTableHashedArray( cntldb,"" +
            "SELECT DISTINCT EVT.ARM, UNIQUE_EVENT_NAME, FLDS.*, DAY_OFFSET-OFFSET_MIN AS STARTING, "
              + "DAY_OFFSET+OFFSET_MAX AS ENDING, MAP.AGGR_TYPE, MAP.OPTIONS, MAP.CONCEPTPATH, MAP.VALUE, "
              + "MAP.ORDNUNG , MAP.MODIFIER, MAP.FILTERED " +
            "FROM PROJECTS_REDCAP_FIELDS FLDS, "
              + "PROJECTS_REDCAP_EVENTS EVT, "
              + "PROJECTS_REDCAP_FORMS FRMS, "
              + "PROJECTS_REDCAP_FIELDS_MAPPING MAP " +
            
            "WHERE " +
            "   MAP.FIELD_NAME=FLDS.FIELD_NAME AND " + 
            "   MAP.PROJECTID=FLDS.PROJECTID AND " + 
            "   MAP.FORM_NAME=FLDS.FORM_NAME AND " + 
            "   FRMS.FORM     =FLDS.FORM_NAME AND " + 
            "   FRMS.PROJECTID=FLDS.PROJECTID AND " + 
            " EVT.PROJECTID         = FRMS.PROJECTID AND " +                  
            " EVT.ARM               = FRMS.ARM AND " +
            " EVT.UNIQUE_EVENT_NAME = FRMS.EVENT AND "  +

            " FRMS.PROJECTID=" + project_id + " " +                                                                 

            where +

            "ORDER BY EVT.ARM, FLDS.FORM_NAME,FLDS.FIELD_NAME, EVT.UNIQUE_EVENT_NAME,  MAP.ORDNUNG"
        );


        //let's go lookup the appropriate data.
        for( HashMap fld : allevents ){

            allarms.add(fld.get("ARM"));

            //this resolves the concept path with all of the possible mappings in found from querying TABLE_ACCESS
            //from the metadata table. 
            String ontmapping = "I2B2";
            String conceptpth = (""+fld.get("CONCEPTPATH")).replaceAll("\\\\\\\\", "\\\\");
            for( HashMap mappings : allmeta){   
                if( conceptpth.startsWith(("\\"+mappings.get("C_TABLE_CD"))) ){
                    ontmapping = ""+mappings.get("C_TABLE_NAME");
                    conceptpth = conceptpth.replaceAll(("\\\\"+mappings.get("C_TABLE_CD")),"");
                    break;
                }
            }
            

            //iterate through all of the items that are usable, if they are invalid, let's mark the arm as 
            //"INVALID ENTRY" so that it skips over it elsewhere in the code.
            if( fld.get("VALUE") != null && fld.get("SELECT_CHOICES_OR_CALCULATIONS") != null ){
                boolean inChoiceList = false;
                for( String choice : fld.get("SELECT_CHOICES_OR_CALCULATIONS").toString().split("\\\\n|\\|") ){
                    if( choice.contains(",") ){
                        if( inChoiceList = choice.split(",")[0].toLowerCase().trim().equals( 
                                fld.get("VALUE").toString().trim().toLowerCase() 
                            ) 
                        ){
                            break;
                        }
                    }
                }
                if( !inChoiceList ){
                    fld.put("ARM","INVALID ENTRY"); 
                    if( testmode ){
                        HashMap[] dis2 = { fld };
                        log(
                            HashMapFunctions.logHashMapArrayToTable(
                                "This entry has been hidden as it is not a valid mapping based on the current choices : " + 
                                fld.get("SELECT_CHOICES_OR_CALCULATIONS") ,
                                dis2 , 
                                USEFUL_DISPLAY
                            )
                        );   
                    }
                }
            }
            
            HashMap[] meta = SQLUtilities.getTableHashedArray(datadb,
                "SELECT DISTINCT C_FACTTABLECOLUMN,C_TABLENAME,C_COLUMNNAME,C_COLUMNDATATYPE,C_OPERATOR,C_DIMCODE " + 
                "FROM "+ontSchema+"."+ontmapping+" WHERE C_FULLNAME='"+conceptpth.replaceAll("'", "''")+"'"
            );
            if( meta.length > 0 ){
                fld.putAll(meta[0]);
                adjustOptions( fld );
            } else {
                log("concept " + conceptpth + " is not mapped to anything. ");
            }

            //System.out.println("" + fld);
        } //for all fields.
        
        
        if( testmode ){
            log(HashMapFunctions.logHashMapArrayToTable("Looking For : " , allevents, USEFUL_DISPLAY ));        
        }
        
    }
    
    /**
     * Given a metadata field and mapping, this will apply any user selected time options to the stored data. 
     * This method mutates the item passed in.
     * @param fld 
     */
    private void adjustOptions( HashMap fld ){
        if( fld.get("OPTIONS") != null && !"".equals(fld.get("OPTIONS").toString().trim()) ){
            for( String item : fld.get("OPTIONS").toString().split(",") ){
                String pair[] = item.split("=",2);   
                if( pair.length > 1 ){
                    if( "before".equals(pair[0]) ){
                        //try to dateparseit.
                        try{ 
                            fld.put("STARTING", DateConverter.convertToDB(pair[1])); 
                        } catch (Exception ex){
                            //then try to number parse it.
                            try{  
                                fld.put("STARTING", "" + Float.parseFloat(pair[1]));
                            } catch (Exception ex1){
                                //otherwise, wtf?
                                log("option " + pair[1] + " is not a date nor a number?");                    
                            }
                        }
                        
                    }
                    if( "after".equals(pair[0]) ){
                        //try to dateparseit.
                        try{ 
                            fld.put("ENDING", DateConverter.convertToDB(pair[1])); 
                        } catch (Exception ex){
                            //then try to number parse it.
                            try{  
                                fld.put("ENDING", "" + Float.parseFloat(pair[1]));
                            } catch (Exception ex1){
                                //otherwise, wtf?
                                log("option " + pair[1] + " is not a date nor a number?");                    
                            }
                        }
                        
                    }
                }
            } //all options
        } //if options was defined
    } //adjustOptions

    
    /**
     * This method creates a processing thread for each patient in the research study, and this shepherds 5 threads at
     * a time through until completion or cancellation. This maintains a separate cancel flag from the normal SQL job
     * because we would like to know when the threads finish gracefully when a cancel has occurred.
     * @throws Exception - failure for any reason.
     */
    private void createAndMonitorThreads() throws Exception {
        
        boolean wasCancelled = false;
        //we're going to create patient processing threads, 5 of them always, and start them seperately. 
        //this thread will check on each thread every second, and unless it was ased to be cancelled, it will check for cancelledness at 
        //the beginning of creating a new processing thread. 
        int counter = 0; 
        int total = 0;
        
        int starting = 0;
        try{
            //CRI-417 - Job Restart not 100% correct at where it picks up.
            starting = Integer.parseInt(project.get("PARAMS").toString())-getMaxChildThreads();
            if( starting < 0 ){
                starting = 0;
            }
            log( "Starting point set to: " + starting );
        } catch (Exception ex){}
        
        LinkedList<Thread> workers = new LinkedList<Thread>();
        for( HashMap pt : SQLUtilities.getHashedTable(cntldb,
            "SELECT NVL(STUDYID,MRN) AS STUDY_ID, ENROLLED_PATIENT.* FROM ENROLLED_PATIENT " +
            "WHERE NOT PID IS NULL AND " + 
            "PROJECTID=" + project_id +" "+
            (!testmode ? "" : " AND PID='"+testfields.get("TESTPATIENT")+"' ") +
            "ORDER BY ENROLLED_PATIENT.SYSID" //AI-417 - the ordering must be more precise.
        )){
            if( total >= starting ){
                workers.add(new PatientProcessorThread( this, pt ));
            }
            total ++;
        }
        
        //now let's start and monitor the threads
        while( workers.size() > 0 ){
            boolean display_finish = false;
            int numberalive = 0;
            Iterator<Thread> i = workers.iterator();
            while (i.hasNext()) {
                Thread worker = i.next();
                Thread.State state = worker.getState();
                if( state == state.TERMINATED ){
                    i.remove();
                    System.gc();
                    counter++;
                    display_finish = true; 
                    if( counter % 50 == 0 ){
                        log("Finished " + (starting + counter) + " patients");
                    }
                } else if( state != state.NEW ){
                    numberalive ++;
                } else if( state == state.NEW && isCancelled() ){
                    i.remove();
                }
            }
            if(!wasCancelled && isCancelled() ){
                log("-------------Was Cancelled!------------");
                wasCancelled = true;
            }
            if( display_finish & wasCancelled){
                log("Thread has finished, export was cancelled." + numberalive + " still running..");
            }
            for( int news = numberalive; !wasCancelled && news < getMaxChildThreads() && news < workers.size() ; news++ ){
                //search for a thread to start.
                for (Thread worker : workers ){
                    if( worker.getState() == Thread.State.NEW ){
                        worker.start();
                        break;
                    }
                }
            } 
            sleep(2500);
            this.updateStatus("Running",(int)((100.0*counter)/(0.01+total)), ""+(starting + counter));
            
        } //while there are threads
        log("done processing, "+ (starting + counter)+ " patients processed." );
        
    } //createAndMonitorThreads()

    private int getMaxChildThreads(){
        
        int answer = 10;
        if( testmode ){
            answer = 1;
        }
        return answer;
    }
    
}
