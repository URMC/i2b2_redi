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
 * 
 * Revisions:
 * 
 *      2016/03/01   cculbertson1
 *      Added reschedule() functionality and accompanying members and logic in 
 *      refresh().
 */

package edu.rochester.urmc.i2b2;

import edu.rochester.urmc.util.HashMapFunctions;
import edu.rochester.urmc.util.SQLUtilities;
import java.sql.Connection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class represents a line within the I2B2_JOBS table in the control database, and is a task set to run by the 
 * URMC tools. This class contains many of the utility functions, such as error logging, general logging, thread awareness
 * of whether it needs to be run, etc.
 * 
 * When jobs are started or are running, the status of checking whether something is cancelled, its status, are handled 
 * via a cached system. The updates to statuses are stored in memory, awaiting some interval, so that the database is 
 * not being hammered by tons of tiny requests updating everything.
 * 
 * @author png
 */
public class SQLQueryJob extends Thread implements Comparable{
    
    boolean isCancelled = false;
    boolean isRescheduled = false;
    
    public Connection cntldb = null;
    public Connection datadb = null;
    public HashMap project = null;
    
    public Properties settings = null;
    
    public Object project_code = null;
    public Object project_id = null;
    public String job_id = null;
    private String queuedState = null;
    
    int delay = 0;
    
    boolean isStarted = false;
    
    long lastChecked = System.currentTimeMillis();
    boolean dirty = false;
    String queuedStatus = "";
    int queuedProgress = 0;
    
    Logger logger = LogManager.getLogger(SQLQueryJob.class);
    
    /**
     * Initialize is always called by the Monitor thread in order to pass items normally in an class intialization
     * which can't be done in reflection.
     * 
     * @param cntldb - this is the control database where the I2B2_JOBS and its logs live.
     * @param datadb - This is the data database which contains i2b2.
     * @param job    - The specific line from the I2B2_JOBS table.
     * @param settings - The settings for this runtime
     * @return this class after being loaded with data.
     */
    public SQLQueryJob initialize( Connection cntldb, Connection datadb, HashMap job, Properties settings  ) {
        this.datadb = datadb;
        this.cntldb = cntldb;  
        this.project = job; 
        this.settings = settings;
        
        project_code = job.get("PROJECTCODE");
        project_id = job.get("PROJECTID");
        job_id = ""+job.get("JOB_ID");
        
        delay = new Random().nextInt( 10000 );
        
        logger = LogManager.getLogger(this.getClass());
        
        return this;
    }
    
    public HashMap getJob(){
        return project;
    }
    
    public String getID(){
        return job_id;
    }
    
    /**
     * This is a default run method that starts and stops itself, no work is done.
     */
    public void run(){
        updateStatus("Running");
        try{ Thread.sleep(delay); } catch (Exception ex ){}
        updateStatus("Complete");
    }
    
    public String toString(){
        return this.getClass().getName() + ": "+project+"";
    }
    public boolean isCancelled() {
        refresh(); 
        return isCancelled || !isRunnable();
    }
    
    /**
     * Refreshes the job status and checks if it has been rescheduled
     * 
     * @author cculbertson1 (curtis_culbertson@urmc.rochester.edu)
     * 
     * @return true if the job has been rescheduled, false otherwise
     */
    public boolean isRescheduled() {
        refresh();
        return isRescheduled && !isRunnable();
    }
    
    /**
     * This method performs the synchronization between the local memory and the database.
     * 
     * Updated by cculbertson1 (curtis_culbertson@urmc.rochester.edu) to include rescheduling logic
     */
    public void refresh(){
        
        //since this changes the operation, you really don't want the statuses to change in here while you're trying to 
        //update and get them correct.
        synchronized( this ){
            
            //placed check in here so we're not hammering the server for updates. System status update changes get 
            //immediate change however. Check every 5 seconds or less for status changes.            
            if( !dirty && ( System.currentTimeMillis() - lastChecked > 5000 || 
                            queuedStatus.toLowerCase().startsWith("complete") || 
                            queuedStatus.toLowerCase().startsWith("error") || 
                            queuedStatus.toLowerCase().startsWith("cancel") ) ){
                
                //this is so we don't infinitely stack overflow when refresh is called from something in refresh().
                dirty = true; 
                
                logger.debug("Checking for updates " + new Date() + " : " + project.get("STATUS") + ": " +queuedStatus );
                try{
                    
                    if( !(queuedStatus.toUpperCase().startsWith("ERROR") || queuedStatus.toUpperCase().startsWith("QUEUE") || queuedStatus.toUpperCase().startsWith("RESCHEDULED"))  ){
                        if( project.get("ACTUAL_START_TIME") == null ){
                            SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET ACTUAL_START_TIME=SYSDATE WHERE  JOB_ID='"+job_id+"'");
                        }
                        isStarted = true;
                        isRescheduled = false;
                        
                    }
                    if( "ERROR".equalsIgnoreCase(queuedStatus) || 
                        queuedStatus.toUpperCase().startsWith("COMPLETE") || 
                        "CANCELLED".equalsIgnoreCase(queuedStatus) ||
                        queuedStatus.toUpperCase().startsWith("RESCHEDULED")
                    ){
                        if( project.get("ACTUAL_END_TIME") == null ){
                            SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET ACTUAL_END_TIME=SYSDATE WHERE JOB_ID='"+job_id+"'");
                        }
                    }
                    if( queuedStatus.toUpperCase().startsWith("COMPLETE") ){
                        queuedProgress = 100;
                        project.put("PERCENT_COMPLETE","100");
                    }
                    
                    if(!isRunnable()){
                        logger.debug("Thread Marked as Not Runnable.");
                    }
                    
                    //refresh your data! This is so if for some reason, the numbers are changed since we last ran it, we can reverse the 
                    project = SQLUtilities.getTableHashedArray(cntldb,
                        "SELECT I2B2_JOBS.* , PROJECTS.CONTACTEMAIL, PROJECTS.PROJECTCODE  " + 
                        "FROM I2B2_JOBS LEFT JOIN PROJECTS " +
                        "ON ( PROJECTS.PROJECTID=I2B2_JOBS.PROJECTID ) " +
                        "WHERE JOB_ID='"+job_id+"'"
                    )[0];
                    
                    logger.debug( "requested to " + queuedStatus + ":" + queuedProgress + " " + this);
                    
                    //since cancellation is very important to check, do that comparison first.
                    isCancelled |= (""+project.get("STATUS")).toUpperCase().startsWith("CANCEL");
                    isRescheduled |= (""+project.get("STATUS")).toUpperCase().startsWith("RESCHEDULED");
                    
                    if( isCancelled ){
                        log( "Cancelled called" );
                    }
                    
// Removed because this line gets called continuously while in the rescheduled state                    
//                    if(isRescheduled) {
//                        log("Rescheduled called");
//                    }
                    

                    //just check that you don't go backward in statuses if you cancel.
                    if( !isCancelled || !isRescheduled || 
                            ((isCancelled || isRescheduled) && 
                            (queuedStatus.toUpperCase().startsWith("QUEUED") || 
                            queuedStatus.toUpperCase().startsWith("ERROR") ||
                            queuedStatus.toUpperCase().startsWith("CANCEL") ||
                            queuedStatus.toUpperCase().startsWith("RESCHEDULED")))) {
                        if( !queuedStatus.equals( project.get("STATUS") ) ){
                            logger.info("Updated Status to " + queuedStatus);
                        }
                        
                        SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET LAST_TOUCHED=SYSDATE, STATUS='"+queuedStatus+"' WHERE  JOB_ID='"+job_id+"'");
                    } else {
                        logger.error( "Current status is " + project.get("STATUS") + " tried to set " + queuedStatus );
                    }
                    
                    // Update percent complete
                    if( queuedProgress > 0 ){
                        SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET LAST_TOUCHED=SYSDATE, PERCENT_COMPLETE='"+queuedProgress+"' WHERE  JOB_ID='"+job_id+"'");
                    }
                    
                    // Update params
                    if( queuedState != null ){
                        SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET LAST_TOUCHED=SYSDATE, PARAMS='"+ queuedState.replaceAll("'","''") +"' WHERE JOB_ID='"+job_id+"'");
                    }

                    lastChecked = System.currentTimeMillis();

                } catch ( Exception ex ){
                    logger.error("Uncaught Exception", ex);
                } finally {
                    dirty = false;
                }
            
            } //if not dirty and either the time is within window or there's a status change.
            
        } //synchronized - you don't want to collide mid execution with a status change.
    }
    
    public boolean isStarted(){
        return isStarted;
    }
    public void updateStatus(String msg){
        updateStatus( msg, -1 ); 
    }
    public void updateStatus(String msg, int currentpct ) {
        updateStatus( msg, currentpct, null ); 
    }
    public void updateStatus(String msg, int currentpct, String savestate ) {
        synchronized( this ){
            queuedStatus = msg.replaceAll("'", "''");
            queuedProgress = currentpct;   
            queuedState = savestate;
        }
        refresh();
    }
    
    public void reschedule(String startTime, String endTime) throws Exception {
        reschedule(startTime, endTime, -1);
    }
    
    public void reschedule(String startTime, String endTime, int percentComplete) throws Exception {
        reschedule(startTime, endTime, percentComplete, null);
    }
    
    /**
     * Reschedule a thread for the specified startTime time, putting it into a 
     *  queued status. Any required parameters will be recorded.
     * 
     * @author cculbertson1 (curtis_culbertson@urmc.rochester.edu)
     *
     * @param startTime the time that the thread should resume as a String
     * @param endTime the time that the thread should end as a String
     * @param percentComplete the current progress
     * @param params any parameters that should be used when resuming
     * @throws Exception
     */
    public void reschedule(String startTime, String endTime, int percentComplete, String params) throws Exception {
        //Commenting these lines out so the job isn't actually rescheduled aside from status
        //SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET START_TIME=TO_DATE('" + startTime + "','yyyymmddhh24miss') WHERE  JOB_ID='"+job_id+"'");
        //SQLUtilities.execSQL(cntldb,"UPDATE I2B2_JOBS SET END_TIME=TO_DATE('" + endTime + "','yyyymmddhh24miss') WHERE  JOB_ID='"+job_id+"'");
        
        synchronized( this ){
            isRescheduled = true;
            queuedStatus = "Rescheduled";
            queuedProgress = percentComplete;   
            queuedState = params;
        }
        refresh();
    }
    
    /**
     * This returns whether or not this thread should be running,based on what time it is allowed to run in the schedule.
     * @return 
     */
    public boolean isRunnable(){
        boolean ans = !isCancelled ;
        
        refresh();
        
        Date requested_start = (Date) project.get("START_TIME");
        if( requested_start != null ){          
            ans &= requested_start.before(new Date());
        }

        Date requested_end = (Date) project.get("END_TIME");
        if( requested_end != null ){
            ans &= requested_end.after(new Date());
        }
        
        return ans;
    }
    
    public void log( String data ){
        logger.info( data );
        refresh();
        String insert = data.replaceAll("'", "''");
        
        try{
            //insert more than one line for exceptionally long output.
            while( insert.length() > 0 ){
                
                String line = insert;
                if( insert.length() > 980 ){
                    line = insert.substring(0,980);
                    insert = insert.substring(980);
                    if( line.endsWith("'") && !line.endsWith("''") ){
                        line += "'";
                        insert = insert.substring(1);
                    }
                    line += "<continues/>";
                } else {
                    insert = "";
                }
                //create table trials.i2b2_jobs_log ( JOB_ID number, DATEOF TIMESTAMP, DESCR VARCHAR2(1000));
                SQLUtilities.execSQL(cntldb,"INSERT INTO i2b2_jobs_log( JOB_ID, DATEOF, DESCR ) VALUES ( '"+job_id+"', SYSTIMESTAMP, '"+ line +"') ");
            }
        }catch ( Exception ex ){
            logger.error("Uncaught Exception", ex);
        }
            
    }
    
    public void logError( Exception ex ){
        logger.error("Uncaught Exception", ex);
        String aggregate = "" + ex.getClass() + ": " + ex.getMessage() + "\n" ;
        for( Object line: ex.getStackTrace() ){
            aggregate += "\t"+line + "\n";
        }
        
        try{
            updateStatus( "Error" );
            SQLUtilities.execSQL(cntldb,"UPDATE i2b2_jobs SET ERROR_CLOB='"+ aggregate.replaceAll("'", "`") +"') WHERE JOB_ID='"+job_id+"' ");
        }catch (Exception ex1){
            logger.error("Uncaught Exception", ex1);
        }
        
        log( aggregate );
    }
    
    public static int getMaxInstances(){
        return 5;
    }

    @Override
    public int compareTo(Object o) {
        int answer = -1;
        if( o != null && o instanceof SQLQueryJob ){
            answer = job_id.compareTo(((SQLQueryJob) o).getID());
        }
        return answer;
    }
    public String[] getNotificationTos(){
        String answer = settings.getProperty("mail.default.sendto" );
        if( project.get("LAST_UI_TOUCHED") != null && System.currentTimeMillis() - ((Date) project.get("LAST_UI_TOUCHED")).getTime() > 10000 ){
            answer += "," + project.get("CONTACTEMAIL");
        }
        return answer.split(",");
    }
    public String getNotificationFrom(){
        return settings.getProperty("mail.default.sendfrom" );
    }
    public Date getLastUITouch(){
        return (Date) project.get("LAST_UI_TOUCHED");
    }
    public String getStatus(){
        return queuedStatus;
    }
    public String getNotificationSubject(){
        return this.getClass().getSimpleName() + " for project " + this.project_code + ", is " + getStatus();
    }
    public String generateHtmlReport() throws Exception {
        StringBuffer buf = new StringBuffer();
        
        buf.append("<p>Hello,<br>");
        
        buf.append("<br>You are seeing this message because it looks like you've left the i2b2 web user interface, we wanted to let you know what happened with your request.");
        
        buf.append("<br><br>The status of the request was <strong>" + getStatus() +"</strong>.");
        
        buf.append("<br><br>Your request was as follows:");
        buf.append( HashMapFunctions.hashMapToTable(project));
        
        buf.append("<br>The log of this process is below: <br><hr><ul>");
        boolean continues = false;
        for( Object l : new SQLUtilities(cntldb, "SELECT DESCR, TO_CHAR(DATEOF,'HH24:MI:SS') AS TIMEOF FROM i2b2_jobs_log log WHERE JOB_ID = " + job_id + " ORDER BY DATEOF")){
            HashMap line = (HashMap)l;
            String descr = ""+line.get("DESCR");
            if( !continues ){
                buf.append("<li>" + line.get("TIMEOF") + " - " );
            }
            if(descr.contains("<continues/>")){
                continues = true;
            } else {
                continues = false;
            }
            buf.append(descr.replaceAll("<continues/>", ""));
            if( !continues ){
                buf.append("</li>");
            }
        }
        buf.append("</ul>");
        
        return buf.toString();
    }
    
    private String htmlfix( String xml ){
        return xml.replaceAll("&", "&amp;").replaceAll(">", "&gt;").replaceAll("<", "&lt;");
    }
}
