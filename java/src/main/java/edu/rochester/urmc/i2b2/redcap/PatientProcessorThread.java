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


import edu.rochester.urmc.util.AggregateFx;
import edu.rochester.urmc.util.DateConverter;
import edu.rochester.urmc.util.HashMapFunctions;
import edu.rochester.urmc.util.REDCapFormatter;
import edu.rochester.urmc.util.SQLUtilities;
import java.util.Collection;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * This class is responsible for a singular patient, to locate and find the data specified by all of the filters
 * in the Sync controller class, and then push that final data to an EAV file.
 * @author png
 */
public class PatientProcessorThread extends Thread{

    static private int ids = 0;
    
    int id = 0;
    
    RedcapSync par = null;
    HashMap pt = null;
    Object arm = "";
    HashMap<String, HashMap> ans = new HashMap<String, HashMap>();
    
    boolean relative_warning = false;
    
    public PatientProcessorThread( RedcapSync parent, HashMap pt ) {
        super();
        id = ids ++;
        this.par=parent;
        this.pt=pt;
    }    
    public void run(){
        
        System.out.println( id + " Thread Started : " + pt);

        
        try{ 
            
            arm = pt.get("ARM");
            //see if you are in the right arm.
            if( par.allarms.size() > 1 && arm == null ){
                //probably many more issues abound. Better just kill this patient thread.
                throw new IllegalArgumentException( "There are multiple arms in this study, however this patient does not have an arm assigned." );
            } else if( arm == null ){
                //set the default arm if there isn't one set.
                for( Object defaults : par.allarms ) arm = defaults;
            }
            
            boolean armhasdata = false;
            for( HashMap data : par.allevents ){
                if( (""+arm).equalsIgnoreCase( ""+data.get("ARM")) ){
                    armhasdata = true; 
                    break;
                }
            }
            
            if( armhasdata ){
                
                cleanup();
            
                processObsFact();
                processEveryThingElse();
    
                saveDataToEAV();
                
            } else {
                throw new IllegalArgumentException( "This patient is in arm " + arm + " but there is no data mapped for that arm." );
            }
            
        } catch (Exception ex){
            ex.printStackTrace();
            par.log("Error - for patient "  + pt.get("STUDYID") + " : " + ex.getMessage() );
        }
 
        
    } 
    
    private void cleanup() {
        try{
            SQLUtilities.execSQL( par.datadb, 
                "DELETE FROM PROJECTS_REDCAP_OUTPUT_EAV "+
                "WHERE PROJECTID ='"+par.project_id+"' AND " +
                "STUDYID='"+pt.get("STUDYID")+"'"
            ); 
        }catch (Exception ex ){
            ex.printStackTrace();
        }
    }
    
    /**
     * This method, given a single field, and given the looked up data for all fields, will run the aggregation/pivot
     * functions and then marshal the result to an EAV file.
     * @param control - the database event/field mapping for a single field-event
     * @param obs_fact - all values found for that event/field
     * @throws Exception 
     */
    public void locateAndFinalizeRecord( HashMap control, HashMap[] obs_fact ) throws Exception {
       
        //now that you have the data, ask the datafinder to match up the data and to process it.
        HashMap[] limited = dataFinder(control,obs_fact);
        
        Object answer = runAggregateFx( control, limited );
        
        if( par.testmode ){
            HashMap[] desc = {control};
            
            par.log( 
                "<br>mapped to "+control.get("C_DIMCODE")+",<br>"+ 
                (control.get("VALUE")==null?"":("Value of "+control.get("VALUE")))+
                "<b> I found " + limited.length + " rows, result set to " + answer + "</b>"
            );        
        }
        
    }
    
    /**
     * This method saves all answers calculated, with all events, into the EAV file.
     * @throws Exception 
     */
    private void saveDataToEAV() throws Exception {
                
        for( HashMap event : ans.values() ){
            for( Object key: event.keySet() ){
                if( !par.testmode && !key.equals("STUDYID") && !key.equals("EVENT") ){
                    SQLUtilities.execSQL( par.datadb, 
                        "INSERT INTO PROJECTS_REDCAP_OUTPUT_EAV (SYSID, PROJECTID, PATIENT_NUM, STUDYID, EVENT_NAME, VAR, VALUE ) VALUES (" +
                            "SYSIDS.NEXTVAL, " + 
                            fix(par.project_id) + ", " + 
                            fix(pt.get("PID")) + ", " + 
                            fix(pt.get("STUDYID")) + ", " + 
                            fix(event.get("EVENT")) + ", " + 
                            fix(key) + ", " + 
                            fix(event.get(key)) +
                        ")"
                    );
                }
            }
        }
    }
    
    /**
     * Simple SQL escape.
     * @param in - data item inbound.
     * @return - slightly better escaped string. 
     */
    private String fix( Object in ){
        return in == null ? "null" : "'"+in.toString().replaceAll("'", "''")+"'";
    }
    
    /**
     * Given the data field, and all possibilities for that one event/field, run the pivoting/aggregation options on it.
     * @param control - the event/data field pair
     * @param limited - the reduced set of just the items that match all criterion.
     * @return - the representation of the data after applying pivoting.
     */
    private Object runAggregateFx( HashMap control, HashMap[] limited ){
        Object answer = null;
        try{
            AggregateFx functions = new AggregateFx();
            
            java.lang.reflect.Method method = functions.getClass().getMethod("aggr"+control.get("AGGR_TYPE"), HashMap[].class, HashMap.class );
            Object[] parameters ={limited, control};
            answer=method.invoke(functions, parameters);
            
            answer=REDCapFormatter.format(answer, control);
            
            //special calc for drop down and radio selections.
            if( "PRESENCE".equals(control.get("AGGR_TYPE")) ){
                if( "1".equals( answer ) ){
                    answer = setAnswer( 
                        "" + control.get("UNIQUE_EVENT_NAME"), 
                        "" + control.get("FIELD_NAME"), 
                        "" + control.get("VALUE"),  
                        "checkbox".equals(control.get("FIELD_TYPE"))
                    );
                }
            } else {
                answer = setAnswer( 
                    "" + control.get("UNIQUE_EVENT_NAME"), 
                    "" + control.get("FIELD_NAME"), 
                    "" + answer, 
                    false
                );
            }
        } catch (Exception ex){
        }
        return answer;
    }
    
    /**
     * This adds the answer to a particular event/variable combination.
     * @param event - what event does this map to 
     * @param variable - the variable you want to commit to cache
     * @param answer - what the answer is after pivoting
     * @param ischeckbox - whether or not this question is a checkbox field, due to the special REDCAP input format 
     * @returns whether or not the addition was successful.
     */
    private Object setAnswer( String event, String variable, String answer, boolean ischeckbox ){
        if( !ans.containsKey(event) || ans.get(event) == null ){
            HashMap addition = new HashMap();
            addition.put( "EVENT", event );
            addition.put( "STUDYID", pt.get("STUDYID"));
            ans.put(event, addition);
        } 
        if( ischeckbox ){
            /* https://redcapdev.urmc-sh.rochester.edu/redcap/api/help/
             * NOTE: When importing data in EAV type format, please be aware that checkbox fields must have their field_name listed as 
             * variable+"___"+optionCode and its value as either "0" or "1" (unchecked or checked, respectively). For example, for a 
             * checkbox field with variable name "icecream", it would be imported as EAV with the field_name as "icecream___4" having a 
             * value of "1" in order to set the option coded with "4" (which might be "Chocolate") as "checked".
             */
            variable += "___" + answer;
            answer="1";
        }
        
        //don't insert blanks or if the data already exists in the output.
        if( ischeckbox || answer != null && answer.toString().trim().length() > 0 && !ans.get(event).containsKey(variable) ){
            ans.get(event).put(variable, answer); 
        } 
        return ans.get(event).get(variable);
    }
    
    /**
     * This method attempts to locate the data, based on the question, and then returns the final array of data that
     * only represents the patient/event/variable. This also applies the logic indicated in the mappings as well as
     * any date logic, pick-list logic and special options.
     * @param control
     * @param data
     * @return
     * @throws Exception 
     */
    private HashMap[] dataFinder( HashMap control, HashMap[] data ) throws Exception {
        LinkedList<HashMap> ans = new LinkedList();
        
        //now inspect the starting and ending, calculate the times for this particular control row.
        Date enrollment = (Date) pt.get("ENROLLED");
        Date starting = (Date) DateConverter.convertToDB("1/1/1900");
        Date ending   = (Date) DateConverter.convertToDB("1/1/2100");
        
        
        Object startsetting = control.get("STARTING");
        Object endsetting = control.get("ENDING");
        Object modifier = control.get("MODIFIER");
                
        if( enrollment == null && startsetting != null && !(startsetting instanceof Date) ) {
            if( !relative_warning ){
                par.log("There are relative starting for field "+control.get("FIELD_NAME")+", however this patient does not have an enrollment date:" + pt );
                relative_warning = true;
            }
        } else if (startsetting instanceof Date){
            starting = (Date) startsetting;
        } else if( startsetting!= null ){
            Double adjustment = Double.parseDouble(startsetting.toString());
            starting = new Date( enrollment.getTime() + (long)(adjustment.doubleValue() * 86400000)  );
        }
        if( enrollment == null && endsetting != null && !(endsetting instanceof Date) ) {
            if( !relative_warning ){
                par.log("There are relative ending for field "+control.get("FIELD_NAME")+", however this patient does not have an enrollment date:" + pt );
                relative_warning = true;
            }
        } else if (endsetting instanceof Date){
            ending = (Date)endsetting;
        } else if( endsetting != null ){
            Double adjustment = Double.parseDouble(endsetting.toString());
            ending = new Date( enrollment.getTime() + (long)(adjustment.doubleValue() * 86400000)  );
        }
        
        if( par.testmode ){
            
            par.log("For variable " + control.get("FIELD_NAME") + " from the enrolled date of " + pt.get("ENROLLED") + " " +  
                control.get("STARTING") + " ( " + starting + ") to " + control.get("ENDING") + " (" + ending + "), "
                    + "the following results matched : "
            );
            
        }
        
        LinkedList<HashMap> display = new LinkedList();
        for( HashMap item : data ){
            //since we are looking for like, we can look just for startswith in the data coming back.
            if( item.get("CONCEPT_PATH") == null || (""+item.get("CONCEPT_PATH")).startsWith( ""+control.get("C_DIMCODE")) ){                       
                
                Date itemstart = ((Date) item.get("START_DATE"));
                Date itemend   = ((Date) item.get("END_DATE"));
                
                if( itemstart == null ){
                    itemstart = new Date(Long.MIN_VALUE);
                } 
                if( itemend == null ){
                    itemend = itemstart;
                }
                boolean within = false;
                
                //check dates, if they are within ranges specified
                within = (itemstart.compareTo(starting) > 0 && itemstart.compareTo(ending) < 0) ||
                        (itemend.compareTo(starting)   > 0 && itemend.compareTo(ending) < 0 ) ||
                        (itemstart.compareTo(starting) < 0 && itemend.compareTo(starting) > 0 ) ||
                        (itemstart.compareTo(ending  ) < 0 && itemend.compareTo(ending  ) > 0 );
                
                //System.out.println("Check: >> " + starting + " - " + itemstart + " - " + itemend + " -> " + ending + " = " + within + " for: " + item );
                
                //modifier codes need to be compared if it is selected. If not, ignore.
                within &= modifier == null || ( modifier != null && modifier.equals(item.get("MODIFIER_CD")));
                
                //grab the additional filter by elements from the tables and preprocess them to memory.
                String[] commandstack = {};
                if( control.get("FILTERED")!= null ){
                    commandstack = control.get("FILTERED").toString().trim().split("\n");
                }
                
                //if there is a command stack, then the data must be also filtered via the popup box.
                if( within && commandstack.length > 1 ){
                    within = filterPickList( item, commandstack );
                } // if there is a filtered modifier set.
                
                if( within ){
                    if( par.testmode ){
                        display.add(item);
                    }
                    ans.add(item);
                }
            }
        }
        
        if( par.testmode && display.size() > 0){
            HashMap[] disp = new HashMap[ display.size() ];
            disp = display.toArray(disp);
            par.log(HashMapFunctions.hashMapArrayToTable(disp));
        }
    
        return ans.toArray(new HashMap[ans.size()]);
    }
    
    /**
     * This takes an individual data point, and the data data from a pick list item to do the matching to see
     * if the data matches and should be included in the output.
     * @param item - one individual Observation Fact item matching this patient / event / variable.
     * @param commandstack - what options were set in the drop down boxes that it needs to match
     * @return whether or not the observation belongs.
     */
    private boolean filterPickList( HashMap item , String[] commandstack){
        boolean within = true;

        //note it should only get into here if the within variable has already been set to true.
        Object value = item.get("DATA");
        //IN / ENUMS
        if( "IN".equals(commandstack[0]) ){

            boolean test = false;
            for( int i = 1; !test && i < commandstack.length; i++ ){
                test = commandstack[i].equals(value);
            }
            within &= test;

        } else if( "VALUEFLAG_CD".equals(commandstack[0])){

            within &= commandstack[1].equals(value);

        //String operators are "LIKE[something]    
        } else if( value != null && commandstack[0].indexOf("LIKE") == 0 ){

            if( commandstack[0].equals("LIKE[contains]") ){
                within &= value.toString().indexOf(commandstack[1]) >= 0;
            } else if( commandstack[0].equals("LIKE[exact]") ){
                within &= commandstack[1].equals(value);
            } else if( commandstack[0].equals("LIKE[begin]") ){
                within &= value.toString().startsWith(commandstack[1]);
            } else if( commandstack[0].equals("LIKE[end]") ){
                within &= value.toString().endsWith(commandstack[1]);
            }

        //numbers are pretty much everything else. they're the only ones that support units.
        } else if( commandstack[0].indexOf("BETWEEN") == 0 ){

            Double valuenum = AggregateFx.val(value);
            Double before = AggregateFx.val(commandstack[1]);
            Double after = AggregateFx.val(commandstack[2]);

            //the answer must not be blank eh?
            within &= valuenum != null;
            if( before != null && after != null ){
                within &= before.compareTo(valuenum) <= 0 && after.compareTo(valuenum) >= 0;
            }

            if( commandstack.length >= 4 ){
                within &= commandstack[3].equals(item.get("UNITS_CD"));
            }


        } else {

            Double valuenum = AggregateFx.val(value);
            Double compare = AggregateFx.val(commandstack[1]);

            if( commandstack[0].equals("LE") ){
                within &= valuenum != null && valuenum.compareTo(compare) <= 0;
            } else if( commandstack[0].equals("LT") ){
                within &= valuenum != null && valuenum.compareTo(compare) < 0;
            } else if( commandstack[0].equals("EQ") ){
                within &= valuenum != null && valuenum.compareTo(compare) == 0;
            } else if( commandstack[0].equals("GT") ){
                within &= valuenum != null && valuenum.compareTo(compare) > 0;
            } else if( commandstack[0].equals("GE") ){
                within &= valuenum != null && valuenum.compareTo(compare) >= 0;
            }
            if( commandstack.length >= 3 ){
                within &= commandstack[2].equals(item.get("UNITS_CD"));
            }
        }
        return within;
    }
    public String toString(){
        return "PatientProcessorThread: " + id + ":"+ getState() + "->" + pt +"\n";
    }
    
    /**
     * There's only two major types of data, observation fact data, and "other". This method handles observation fact 
     * data. It assembles for all fields in memory, it generates a large SQL statement containing what data elements
     * to extract for all timepoints for a patient, for all observation facts in a grouping. Then it whittles the result 
     * by sending it to a finalizer step that reduces and picks the correct values for each patient-event-variable. Each
     * group is determined by the variables on a data form; hopefully they will be in some semblance of similar data, 
     * such as a form of lab values - so the same partitions are searched.
     * 
     * @throws Exception 
     */
    private void processObsFact() throws Exception {
        
        LinkedList<String> conceptProcessed = new LinkedList();
        LinkedList formsProcessed = new LinkedList();
        String concept_smash = "";
        for( int todoqueue = 1; todoqueue > 0 ; ){
            //in the try catch so a failure in a mapping does not bring down the house.
            try {
                
                //assemble the SQL where clauses.
                LinkedList<HashMap> inprocess = new LinkedList<HashMap>();
                todoqueue = 0;
                inprocess.clear();
                
                Object form = null;
                
                //we're going to query the database by each form - how many fields are you going to put on a form anyway?
                //since we are querying all items on the form in one go, no time in the query, we optimized the loading to 
                //be in that order so we are doing that one variable all at once.
                
                for( HashMap data : par.allevents ){
                    if( (""+arm).equalsIgnoreCase( ""+data.get("ARM")) ){
                        if( "LIKE".equalsIgnoreCase( ""+data.get("C_OPERATOR") ) && "CONCEPT_DIMENSION".equalsIgnoreCase( ""+data.get("C_TABLENAME") )){  
                            
                            //if we're hunting for a new form to do, see if we have done it yet, otherwise we have seen it before,
                            //leave the form flag set to null so that we skip over it and do not account for it.
                            if( form == null && !formsProcessed.contains(data.get("FORM_NAME"))){
                                form = data.get("FORM_NAME");
                                formsProcessed.add(form);
                            }
                            
                            //skip over the ones we've already done. ( should be null )
                            if( form != null ){
                                
                                if( form.equals(data.get("FORM_NAME")) ){
                                    String this_concept = "(";
                                    this_concept += "concept_path LIKE '" + (""+data.get("C_DIMCODE")).replaceAll("'","''") + "%' ";
                                    
                                    //since we will be recomparing the data at a later state, only the modifier is the bit that
                                    //really limits the data to a usable state.
                                    if( data.get("MODIFIER") != null && !"".equals(data.get("MODIFIER"))){
                                        this_concept += "AND MODIFIER_CD='"+data.get("MODIFIER").toString().replaceAll("'","''")+"' ";
                                    }
                                    this_concept += ") OR \n" ;
                                    inprocess.add( data );
                                    
                                    //no point adding the same bloody thing more than once - it's already accounted for.
                                    if(!conceptProcessed.contains(this_concept)){
                                        conceptProcessed.add( this_concept );
                                    }
                                } else {
                                    todoqueue ++;
                                }
                            }
                        }
                    }
                } // for all events in the preprocessed list of data.
                
                concept_smash = "";
                for( String concept : conceptProcessed ){
                    concept_smash += concept ;
                }
                concept_smash += "0=1";
                
                if( par.testmode ){
                    par.log( "Processing " + inprocess.size() + " items in this batch, there are " + todoqueue + " observations_fact calculations to go.");
                } 
                
                //pretty much all of the data is in the observation fact table, so assemble a large chunk of codes and query for that in one fell swoop. 
                HashMap[] obs_fact = SQLUtilities.getTableHashedArray(par.datadb,
                    "SELECT concept_path, MODIFIER_CD,UNITS_CD, VALUEFLAG_CD, START_DATE, END_DATE, NVL( (''||NVAL_NUM),NVL(TVAL_CHAR,NAME_CHAR)) AS DATA " + 
                    "FROM "+par.crcSchema+".OBSERVATION_FACT, "+par.crcSchema+".CONCEPT_DIMENSION " +
                    "WHERE PATIENT_NUM = " + pt.get("PID") + " AND " +
                    "CONCEPT_DIMENSION.CONCEPT_CD=OBSERVATION_FACT.CONCEPT_CD AND (" + concept_smash + ")" +
                    "ORDER BY OBSERVATION_FACT.START_DATE "
                );
                
                
                for( HashMap control : inprocess ){
                    
                    //shortcut the analysis, no point even looking.
                    if( obs_fact.length == 0 ) break;
                    locateAndFinalizeRecord( control , obs_fact );
                    
                }
                
                if( par.testmode ){
                    String id = ""+System.currentTimeMillis();
                    Collection keys = null;
                    par.log(HashMapFunctions.logHashMapArrayToTable("Observations For Concepts: " + obs_fact.length + "observations, "
                        + "<a href='javascript:void(0);' onClick='jQuery(\"#REDCap_"+id+"\").show();'>Show Raw Data</a>"
                        + "<div id=\"REDCap_"+id+"\" style='display:none'>", obs_fact, keys,  "</div>"));        
                }
                
                //force sleep between concepts so that the main thread has some processing time.
                Thread.sleep(1000);
                
            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("Bad SQL:" + concept_smash );
                par.log( "Encountered issue in obsfact, " + ex + ":"+ ex.getStackTrace()[0] );
            }
        } // while there's still things in the queue to do for observation_fact
        
    }
    /**
     * There's only two types of data, OBS fact, and smaller fast table joins with visit or patient information.
     * The latter is handled by this method. It looks up and performs the matches and sends it off to be finalized.
     * @throws Exception 
     */
    private void processEveryThingElse() throws Exception {
        
        //everything else really 
        for( HashMap control : par.allevents ){
            
            String sql = "";
            //in the try catch so a failure in a mapping does not bring down the house.
            try {
                    
                if( (""+arm).equalsIgnoreCase( ""+control.get("ARM")) ){
                    HashMap[] obs_fact = {};
                    
                    sql = (""+control.get("C_DIMCODE"));
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
                    
                    sql = " " + control.get("C_OPERATOR") + " " + sql;
                    /*
                    WHERE C_FULLNAME
                    
                    C_FACTTABLECOLUMN   C_TABLENAME             C_COLUMNNAME    C_COLUMNTYPE    C_OPERATOR  C_DIMCODE
                    PATIENT_NUM         PATIENT_DIMENSION       RACE_CD         T               =           i
                    PATIENT_NUM         PATIENT_DIMENSION       RACE_CD         T               IN          'his/white','hiw','c','w','white'
                    patient_num         patient_dimension       birth_date      N               >           sysdate - (365.25) 
                    patient_num         patient_dimension       birth_date      N               BETWEEN     sysdate - (365.25 * 2) AND sysdate - 365.25 
                    concept_cd          concept_dimension       concept_path    T               LIKE        \i2b2\Diagnoses\Hematologic diseases (280-289)\(282) Hereditary hemolytic anemias\(282-2) Anemias due to disorders ~\                                                                                                               
                    provider_id         provider_dimension      provider_path   T               LIKE        \i2b2\Providers\Internal Medicine\SHAMASKIN, JOEL A, MD\

                    */
                    
                    if( "PATIENT_DIMENSION".equalsIgnoreCase( ""+control.get("C_TABLENAME") )){
                        
                        //starting and ending date irrelevant for demographic details. hence the start of DOB and end of forever.
                        sql = "SELECT BIRTH_DATE AS START_DATE, NVL(DEATH_DATE,SYSDATE) AS END_DATE, " + control.get("C_COLUMNNAME") + " AS DATA " +
                            " FROM "+par.crcSchema+".PATIENT_DIMENSION " +
                            "WHERE PATIENT_NUM = " + pt.get("PID") + " AND " +
                            "" + control.get("C_COLUMNNAME") + " " + sql + " ORDER BY UPDATE_DATE ";
                        
                    } else if( "PROVIDER_DIMENSION".equalsIgnoreCase(""+control.get("C_TABLENAME")) ){
                                                                            
                        sql = "SELECT PROVIDER_DIMENSION.PROVIDER_PATH AS CONCEPT_PATH, START_DATE, END_DATE, PROVIDER_DIMENSION.NAME_CHAR AS DATA " + 
                              "FROM "+par.crcSchema+".OBSERVATION_FACT, "+par.crcSchema+".PROVIDER_DIMENSION " +
                              "WHERE PATIENT_NUM = " + pt.get("PID") + " AND " +
                              "PROVIDER_DIMENSION.PROVIDER_ID=OBSERVATION_FACT.PROVIDER_ID AND " + 
                              control.get("C_COLUMNNAME") + " " + sql + " ORDER BY OBSERVATION_FACT.UPDATE_DATE ";   
                        
                    } else if( "CONCEPT_DIMENSION".equalsIgnoreCase( ""+control.get("C_TABLENAME") )){
                        //do not process. 
                    } else if( "visit_dimension".equalsIgnoreCase(""+control.get("C_TABLENAME")) ){
                                
                        sql = "SELECT START_DATE, NVL(END_DATE,START_DATE), " + control.get("C_COLUMNNAME") + " AS DATA \n" + 
                              "FROM "+par.crcSchema+".VISIT_DIMENSION \n" +
                              "WHERE PATIENT_NUM = " + pt.get("PID") + " AND \n" +
                              "" + control.get("C_COLUMNNAME") + " " + sql + " ORDER BY UPDATE_DATE ";
                            
                    } else {
                        par.log("Unknown Table :" +control.get("C_TABLENAME"));
                    }
                    
                    if( sql.startsWith("SELECT") ){
                        
                        obs_fact = SQLUtilities.getTableHashedArray(par.datadb,sql);
                        locateAndFinalizeRecord( control , obs_fact );
                    }
                } //for each arm.
            } catch (Exception ex) {
                par.log( "Encountered issue in obsfact," + ex + ":"+ sql +":"+ ex.getStackTrace()[0] + " " + control );
            }
        }
    }
}