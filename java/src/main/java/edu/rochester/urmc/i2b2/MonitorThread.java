package edu.rochester.urmc.i2b2;

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

import edu.rochester.urmc.util.SQLUtilities;
import java.awt.GraphicsEnvironment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * This is the main class of the i2b2 urmc servicing system, and it is the central hub of the system. 
 * 
 * It is responsible for setting up new jobs and it is continuously running. It should be invoked from the command line 
 * with Java -jar, or wrapped and run with the Tanuki wrapper if utilized in windows.
 * 
 * When this program starts up, it will hunt for the settings file, which it will look for in the same folder 
 * as the program is running in. If it is passed a file as an argument, it will utilize that file and attempt to 
 * read it and utilize that. If there is no settings file, it will pop up a GUI box inquiring where the settings are 
 * to be found.
 * 
 * The standard execution path of this class is that it creates itself as a thread and then runs until it encounters two
 * exceptions, and then whence it will exit. There are two failures to handle a transient database failure.
 * In the main loop, it will:
 * 
 *  - Check to see if there are new jobs it's not currently running this is from I2B2_JOBS table in the control database.
 *  - If there are jobs, to see if they are runnable
 *      - IF they are runnable, to start them up to the limit the class specifies
 *      - Otherwise to put them on a queued wait list * 
 * 
 * Otherwise, after two failures, exit.
 * 
 * @author png
 */
public class MonitorThread extends Thread {

    Properties settings = new Properties();
    Connection myCntlDb = null;
    Connection myDataDb = null;

    String drvd = "",urld= "",usrd= "",pwdd= "";
    String drvc = "",urlc= "",usrc= "",pwdc= "";

    HashMap<String, SQLQueryJob> jobs = new HashMap<String, SQLQueryJob>();
    
    private Logger logger = null;
    
    private LinkedList< String > ignore_list = new LinkedList();
    
    /**
     * This method loads the property file settings for database connection and sets up logging. 
     * @param propfile - property file that contains the settings for the system, may be null, but the defaults will not load.
     * @throws IOException - IF there are issues reading the properties file or there are issues with Log4J.
     * @throws ClassNotFoundException - If the database drivers specified don't exist.
     */
    public MonitorThread(File propfile) throws IOException, ClassNotFoundException {
        
        if( propfile != null && propfile.exists() ){
            settings.load(new FileInputStream(propfile));
        }
        
        String logConfigurationFile= settings.getProperty("LOG4J2","c:/log4j2.xml");
            
        ConfigurationSource source = new ConfigurationSource(new FileInputStream(logConfigurationFile));
        Configurator.initialize(null, source);
        
        drvd = settings.getProperty("I2B2DRIVER","oracle.jdbc.OracleDriver");
        urld = settings.getProperty("I2B2URL","jdbc:oracle:thin:@i2b202.urmc-sh.rochester.edu:1521:i2b2" );
        usrd = settings.getProperty("I2B2USERNAME","i2b2exports");
        pwdd = settings.getProperty("I2B2PASSWORD","");
        Class.forName( drvd );
        
        drvc = settings.getProperty("DRIVER","oracle.jdbc.OracleDriver");
        urlc = settings.getProperty("URL","jdbc:oracle:thin:@cdw1.urmc.rochester.edu:1521:CDWPROD" );
        usrc = settings.getProperty("USERNAME","trials");
        pwdc = settings.getProperty("PASSWORD","");
        
        for( String item : settings.getProperty("IGNORELIST","").split(",") ){
            ignore_list.add(item);
        }
        
        Class.forName( drvc );
        
        if( logger == null ){
            logger = LogManager.getLogger(MonitorThread.class);
        }
        
        logger.info("System initialized with settings : " + settings.toString().replaceAll(", ", "\n") );
    }

    /**
     * This method extends the Thread run and performs a database poll to determine if there are tasks to be done.
     */
    public void run(){

        int delay=15;

        try{ delay=Integer.parseInt(settings.getProperty("DELAY_QUERY_SECONDS","5")); }catch (Exception ex){}
        
        String environment= settings.getProperty("ENVIRONMENT","T"); //P for production.
        String environment_filter = "ENVIRONMENT = '"+environment+"'";
        if( "P".equals( environment ) ){
            //add exception to make sure that the uncategorized jobs run.
            System.err.println("Warning: Production Mode Server");
            environment_filter = "(" + environment_filter + " OR ENVIRONMENT IS NULL)";
        }
            
        int failurecount = 0;
        while(failurecount < 2){

            try{
                
                boolean hasChanges = false;
                
                if( myCntlDb == null || myCntlDb.isClosed() ){
                    myCntlDb  = java.sql.DriverManager.getConnection( urlc, usrc, pwdc );
                }
                if( myDataDb == null || myDataDb.isClosed() ){
                    myDataDb  = java.sql.DriverManager.getConnection( urld, usrd, pwdd );
                }
                String current_running = "0";
                for(String pid : jobs.keySet()){
                    current_running += "," + pid;
                }
                    
                for( HashMap job : SQLUtilities.getHashedTable(myCntlDb,
                    "SELECT I2B2_JOBS.* , PROJECTS.CONTACTEMAIL, PROJECTS.PROJECTCODE  " + 
                    "FROM I2B2_JOBS LEFT JOIN PROJECTS " +
                    "ON ( PROJECTS.PROJECTID=I2B2_JOBS.PROJECTID )" +
                    "WHERE "
                        + "(NOT LOWER(I2B2_JOBS.STATUS) IN ('complete' ,'completed' ,'error' ,'running','cancel','cancelled') "
                        //added this line to detect jobs not currently running and not apparently active
                        + " OR (LOWER(I2B2_JOBS.STATUS)='running' AND (SYSDATE-LAST_TOUCHED)*1440 > 10)" 
                        + ") AND "
                        + "NOT I2B2_JOBS.JOB_ID IN ("+current_running+") AND " 
                        + environment_filter 
                )){
                
                    logger.info(" Processing Job:"  + job);
                    
                    //Let's see what kind of job it is. Let's assume it's something to try to create a class for.
                    String job_id = ""+job.get("JOB_ID");
                    if( !jobs.containsKey(job_id)){
                        SQLQueryJob runthis = createBabyThread(job);
                        if( runthis != null ){
                            jobs.put(job_id, runthis);
                        }
                    }
                    
                }
                
                cleanupDeadThreads();
                checkQueues();
                sleep( delay * 1000 );
                failurecount = 0;
                
            } catch (Exception ex){
                
                failurecount ++;
                logger.error("Failure: #" + failurecount, ex);
                //well the only think I can think of that will get you here is a SQL issue. if we set the
                //db variable to null, it will be recreated at the top of the function and hopefully stay up.

                try{ myCntlDb.close(); }catch (Exception ex1){}
                myCntlDb = null;
                try{ myDataDb.close(); }catch (Exception ex1){}
                myDataDb = null;
                
                //try to outwait the database/network connectivity issue.
                try{ sleep( 10000 ); } catch (Exception dontcare){}
            }

        } //loop forever!
        
        //if you get, here, you've bugged out.
        logger.error("Now exiting system.");
        System.exit(1);
    }
    
    /**
     * This method creates new instances of SQLQueryJobs from the data from the I2B2_JOBS table and adds them to workqueue.
     * @param job - This is the query parameters from the I2B2_JOBS table.
     * @return new instance of subclass implementer of a job.
     */
    private SQLQueryJob createBabyThread( HashMap job ){
        
        SQLQueryJob runthis = null;
        
        String job_id = ""+job.get("JOB_ID");
        String job_type = "" + job.get("JOB_TYPE");
        
        //shortcut known bad entities. This is so that you can run different systems out there, each one with only a 
        //limited list of items that it should run, by configuration. This is so say a production clarity run 
        //doesn't run in test, or etc.
        for( String ignore : ignore_list ){
            //exact match ignore
            if( ignore.equalsIgnoreCase(job_type) ){ 
                return null;
            }
            //look for wildcard endings
            if(ignore.endsWith("*") && job_type.startsWith(ignore.replace('*', ' ').trim())){
                return null;
            }
        }
        
        try{
            
            logger.info("Adding Job " + job );
            
            Class inner = Class.forName(job_type);
            if( inner.newInstance() instanceof SQLQueryJob ){
                runthis = (SQLQueryJob) inner.newInstance();
                runthis.initialize(myCntlDb, myDataDb, job, settings);
                runthis.updateStatus("Queued");
            }

        } catch( ClassNotFoundException ex1 ){
            try{
                Class inner = Class.forName("edu.rochester.urmc.i2b2." +job_type);
                if( inner.newInstance() instanceof SQLQueryJob ){
                    runthis = (SQLQueryJob) inner.newInstance();
                    runthis.initialize(myCntlDb, myDataDb, job, settings);
                    runthis.updateStatus("Queued");
                }
            } catch ( Exception ex2 ){
                logger.error("Class not createable, try 2 " + ex2.getMessage(), ex2 );
                ignore_list.add(job.get("JOB_TYPE").toString());
            }
        } catch ( Exception ex2 ){
            logger.error("Class not createable " + ex2.getMessage(), ex2 );
        }
        return runthis;
    }
    
    /**
     * This method performs a cleanup of all threads in the system.
     * @return the number of threads that have stopped running 
     */
    private int cleanupDeadThreads(){
        int ans = 0;
        //now that all of the jobs have been created, let's go through them to see what codes to create.
        //check to see if you have any jobs to clear. Bring out your dead!
        LinkedList< String > removes = new LinkedList< String >();
        for( Iterator i = jobs.values().iterator(); i.hasNext(); ){
            SQLQueryJob inspect = (SQLQueryJob) i.next();
            if( inspect != null && !inspect.isAlive() && inspect.getState() != Thread.State.NEW ){
                logger.info("Killing job : " + inspect );
                //one last push to make sure its all updated.
                inspect.refresh();
                notify(inspect);
                removes.add(inspect.getID());
                inspect = null;
                System.gc();
            }
        }

        //now we know the dead, cull the herd.
        for( String aa : removes ){
            jobs.remove(aa);
            ans ++;
        }
        return ans;
    }
    
    /**
     * This method checks the actively known workqueue in the system and starts the threads in the queue if they
     * are ready to work or there's free space available.
     * 
     * @throws Exception 
     */
    public void checkQueues() throws Exception {
        
        //now that all of the hanging chads are completed, let's see if there are any that need to be created. 
        //the easist way is to count them all to see what you have. the jobs will be naturally ordered 
        //by the id 
        HashMap< Class, SortedSet< SQLQueryJob >> pendings = new HashMap< Class, SortedSet< SQLQueryJob >>();
        for( Iterator i = jobs.values().iterator(); i.hasNext(); ){
            SQLQueryJob inspect = (SQLQueryJob) i.next();
            SortedSet<SQLQueryJob> stack = pendings.get(inspect.getClass());

            //create a new stack if there isn't a class out there for you.
            if( stack == null ){
                stack = new TreeSet<SQLQueryJob>();
            }
            stack.add(inspect);
            pendings.put(inspect.getClass(), stack);

        }

        //now that you've figured out all of the jobs, let's start the requisite number of them.
        for( Class typeof : pendings.keySet() ){
            
            //let's figure out how many to work on. The concurrency count of each operation is in a static function 
            //of the class, so let's ask it how many to make.
            SortedSet<SQLQueryJob> stack = pendings.get( typeof );
            int maxThreads = stack.first().getMaxInstances();
            
            int running = 0;
            int runnable = 0;
            for( SQLQueryJob single : stack ){
                if( single.isStarted() && single.isAlive() ){
                    running ++;
                } else if(single.isRunnable()) {
                    runnable ++;
                }
            }
            
            if( runnable > 0 && running < maxThreads ) {
                
                logger.info("Currently " + running + " out of " + maxThreads + " running, for class : " + typeof );
                for( SQLQueryJob single : stack ){

                    //if there's things to run, let's stator them up.
                    if( running < maxThreads && single.isRunnable() && single.getState() == Thread.State.NEW){
                        //reinitialize to make sure the database connection is current.
                        single.initialize(myCntlDb, myDataDb, single.getJob(), settings);
                        logger.info("Starting " + single );
                        single.start();
                        running ++;
                    }
                }
            } //if there are jobs to run 
        } //for each type of job (java class)
    }
    
    /**
     * This private method performs the basic notification via email of a job's status.
     * @param task from workqueue.
     */
    private void notify( SQLQueryJob task ){
        try{
            task.refresh();
            if( "Y".equals( settings.getProperty("mail.enabled", "Y") ) ){
                mail( 
                    task.getNotificationFrom(), 
                    task.getNotificationTos(),
                    task.getNotificationSubject(),
                    task.generateHtmlReport()
                );
                logger.info( "Email Sent.");
            } else {
                logger.info( "Email not sent, mail.enabled not set to Y." );
            }
        } catch (Exception ex){
            logger.error(ex);
        }
    }
    
    /**
     * This method performs the generic emailing from data within the settings.
     * @param from    - single email from address
     * @param tos     - string array of receipient addresses
     * @param subject - subject of email
     * @param content - HTML content of the email.
     */
    public void mail( String from, String[] tos, String subject, String content ){
        
        if( from == null || tos == null || tos.length == 0 || subject == null || content == null ){
            
            logger.info("Email not sent, field not filled in:\n"
                + "From:" + from + "\n"
                + "To: " + Arrays.asList(tos) + "\n"
                + "Subj: " + subject + "\n" 
                + "Msg: " + content
            );
            
        } else {
            logger.info("Attempting Email send of :\n"
                + "From:" + from + "\n"
                + "To: " + Arrays.asList(tos) + "\n"
                + "Subj: " + subject + "\n"
            );
            try{

                if( !settings.containsKey("mail.transport.protocol")) {
                    settings.setProperty("mail.transport.protocol", "");
                }
                if( !settings.containsKey("mail.host")) {
                    settings.setProperty("mail.host", "");
                }
                if( !settings.containsKey("mail.user")) {
                    settings.setProperty("mail.user", "");
                }
                if( !settings.containsKey("mail.password")) {
                    settings.setProperty("mail.password", "");
                }
                

                /*
                //on hold till we can send as this user.
                if( !settings.containsKey("mail.smtp.auth")) {
                    settings.setProperty("mail.smtp.auth", "true");
                }
                Session mailSession = Session.getDefaultInstance(settings, 
                    new javax.mail.Authenticator() {
                      protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                              settings.getProperty("mail.user"), 
                              settings.getProperty("mail.password")
                        );
                      }
                });
                */
                Session mailSession = Session.getDefaultInstance(settings, null );
                mailSession.setDebug(true);
                Transport transport = mailSession.getTransport();
                
                MimeMessage message = new MimeMessage(mailSession);
                message.setSubject(subject);
                message.setFrom(new InternetAddress(from));
                message.setContent(content, "text/html");
                for( String to : tos ){
                    message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                }

                transport.connect();
                transport.sendMessage(message,message.getRecipients(Message.RecipientType.TO));
                transport.close();
                
            } catch ( Exception ex ){
                logger.error( ex );
            }
        }
    }
    
    /**
     * This will create and start the monitor process to create and process jobs created by i2b2
     * @param args - if provided a settings file, it will use that if it exists, otherwise show GUI or error message.
     */
    public static void main(String[] args) {
        try{
            
            File properties = null;
            if( args.length == 0 ){
                
                //see if you have the settings enabled if not specified.
                if( new File("settings.ini").exists() ){
                    properties = new File("settings.ini");
                } else if( !GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless() ){
                    properties = FileSelector.getFile();
                }

            } else if( args.length == 1 ){
                if( new File( args[0] ).exists() ){
                    properties = new File(args[0]);
                }
            }
            
            if( properties != null ){
                new MonitorThread(properties ).start();
            } else {
                System.out.println("Usage: java -jar JavaServices.jar settings.ini\n"
                        + "You may also have a settings.ini in the working folder as well.");
            }
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

}
