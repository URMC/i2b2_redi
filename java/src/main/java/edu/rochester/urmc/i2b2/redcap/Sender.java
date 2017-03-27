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

import edu.rochester.urmc.i2b2.SQLQueryJob;
import edu.rochester.urmc.i2b2.SQLQueryJobLogOnce;
import edu.rochester.urmc.util.ClientHTTPRequest;
import edu.rochester.urmc.util.ReadWriteTextFile;
import edu.rochester.urmc.util.SQLUtilities;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;
import java.security.MessageDigest;

import java.sql.Blob;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class is the receiving end of the page within the plugin when you select Transmit to REDCap. The purpose of this
 * is to backup the data within the other side in REDCap, then proceed to send the generated EAV file, in large chunks to
 * REDCap. It sends it as large chunks as to not negatively impact the functioning of the system, since small chunks have
 * a smaller impact on the performance of the system then one big massive XML file.
 * 
 * @author png
 */
public class Sender extends SQLQueryJobLogOnce {
    
    HashMap projectSettings = null;
    public HashSet seenalerts = new HashSet();
    File outputFolder = null;
    String notificationTos = "";
    int numberOfErrorsGrouped = 10;
    int chunksize = 0;
    int msToWait = 0;
    
    public SQLQueryJob initialize( Connection cntldb, Connection datadb, HashMap job, Properties settings  ) {
        ClientHTTPRequest.trustAll();
        
        //first you want to make an output file to store the data/results/errors to be returned.
        outputFolder = new File( settings.getProperty("TEMPORARY_FILES", "/data01/temp/"));
        numberOfErrorsGrouped = Integer.parseInt( settings.getProperty("REDCAPSENDINGERRORSGROUPED", "10"));
        notificationTos = settings.getProperty("REDCAPSENDINGNOTIFICATIONTO", "");
        chunksize = Integer.parseInt(settings.getProperty("CHUNKSIZE", "1000"));
        msToWait = Integer.parseInt(settings.getProperty("SENDDELAY", "500"));
        
        if( !outputFolder.exists() ){
            outputFolder.mkdirs();
        }
        super.initialize(cntldb, datadb, job, settings);
        try{
            projectSettings = SQLUtilities.getTableHashedArray(cntldb, 
                "SELECT CONTACTEMAIL, REDCAP_APIKEY, REDCAP_URL FROM PROJECTS WHERE PROJECTID='"+project_id+"'"
            )[0];
        } catch ( Exception ex ){
            logError( ex );
        }
        return this;
    }
    
    /**
     * This method backs up the data from REDCAp in an EAV XML format so that we can undo what we did.
     * @param projectSettings - from the settings passed from the project.
     * @throws Exception on backup or write issues.
     */
    private void backup(HashMap projectSettings) throws Exception {
        
        log("Backup started");
        
        File currSession = new File( outputFolder.getAbsolutePath() + "/" + project_id + ".backup."+System.currentTimeMillis()+".xml");
        
        HashMap request = new HashMap();
        request.put("token", projectSettings.get("REDCAP_APIKEY"));
        request.put("content", "record");
        request.put("format", "eav");
        request.put("type", "eav");

        InputStream is = ClientHTTPRequest.post(new URL(""+projectSettings.get("REDCAP_URL")),request);
        FileOutputStream os = new FileOutputStream(currSession);
        IOUtils.copy(is, os);
        is.close();
        os.close();
        
        log("Backup file downloaded, size " + currSession.length() );
        
        is = new FileInputStream( currSession );
        
        SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyyMMddHHmmss");
        
        String outputFile = ""+project_id+"_" + YYYYMMDD.format( new Date() ) + ".xml";
        
        SQLUtilities.execSQL(cntldb, 
            "INSERT INTO PROJECTS_BLOBS (projectid,fieldname,filename,updated,filecontents )" +
            "VALUES ('"+project_id+"','BACKUP','"+outputFile+"',sysdate,empty_blob()) "
        );
        
        PreparedStatement sql = cntldb.prepareCall("select filecontents from PROJECTS_BLOBS where filename = ? for update");
        sql.setString(1, outputFile);
        final ResultSet result = sql.executeQuery();
        while (result.next()){
            // Get the blob...
            final Blob blob = result.getBlob(1);
            System.out.println("Class: " + blob.getClass().getName());
            // Print out...
            System.out.println("Blob length = " + blob.length());
            OutputStream writer = blob.setBinaryStream(0);
            IOUtils.copy(is, writer);
            writer.flush();
            writer.close();
            is.close();
            // Print out...
            System.out.println("Blob length = " + blob.length());
        }
        // Close resources...
        result.close();
        sql.close();
        log("Backup complete: " + outputFile);
    }
    
    public String[] getNotificationTos(){
        String ans = this.notificationTos ;
        return ans.split(",");
    }

    
    public void run() {
        
        try{
                
            this.updateStatus("Running",0);
            log("Processing Project " + project_id);
            
            this.updateStatus("Running",1);
            
            backup(projectSettings);
            
            SQLUtilities.execSQL(datadb , "UPDATE PROJECTS_REDCAP_OUTPUT_EAV SET SENDLOG=null WHERE PROJECTID='"+project_id+"'" );
            
            int tries = 0, session_num= 0;
            
            int totalCount = Integer.parseInt( SQLUtilities.getTableHashedArray(datadb, 
                "SELECT COUNT(*) AS INNERDS " +
                "FROM PROJECTS_REDCAP_OUTPUT_EAV " +
                "WHERE PROJECTID='"+project_id+"' AND SENDLOG IS NULL ")[0].get("INNERDS").toString()
            );
            
            
            do {
                
                this.updateStatus("Running", (int)((100.0 * chunksize * session_num)/(0.001 + totalCount)));
                
                SQLUtilities.execSQL(datadb , 
                    "UPDATE PROJECTS_REDCAP_OUTPUT_EAV set SENDLOG='"+project_id+"' WHERE ROWID IN ( " +
                        "SELECT INNERDS FROM ( " +
                                "SELECT INNERDS, ROWNUM AS RNUM FROM " +
                                "( " +
                                        "SELECT ROWID AS INNERDS " +
                                        "FROM PROJECTS_REDCAP_OUTPUT_EAV " +
                                        "WHERE PROJECTID='"+project_id+"' AND SENDLOG IS NULL " +
                                        "ORDER BY PATIENT_NUM,STUDYID,EVENT_NAME,VAR " +
                                ") SORTED " +
                        ") WHERE RNUM <= "+chunksize+" " +
                    ")"
                );
                
                //insanity check to make sure you have work to do.
                if( "0".equals( SQLUtilities.getTableArray(datadb, "SELECT COUNT(*) FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='"+project_id+"' AND SENDLOG='"+project_id+"'")[0][0].toString() )){
                    break;
                }
                
                for( tries = 0; tries < 4; tries ++ ){
                    
                    HashMap[] data = SQLUtilities.getTableHashedArray(datadb, "SELECT * FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='"+project_id+"' AND SENDLOG='"+project_id+"' ORDER BY PATIENT_NUM,STUDYID,EVENT_NAME,VAR");
                    
                    if( data.length > 0 && !this.isCancelled() ){
                        
                        StringBuffer xml = generateSendingXml( data );
                        
                        try{
                            
                            String fromserver = communicateWithREDCap( xml, session_num );
                            
                            processREDCapXml( fromserver );
                            
                        } catch( Exception ex ){
                            ex.printStackTrace();
                            String truncated = (ex.getClass() + ":" + ex.getMessage()).replaceAll("'", "''");
                            if( truncated.length() > 2000 ) {
                                truncated = truncated.substring(0,1950) + "... ";
                            }
                            SQLUtilities.execSQL(datadb , 
                                "UPDATE PROJECTS_REDCAP_OUTPUT_EAV "
                                    + "SET SENDLOG='<error>failed general"
                                    + truncated
                                    + "</error>' WHERE "
                                    + "PROJECTID='"+project_id+"' AND "
                                    + "SENDLOG='"+project_id+"'" 
                            );
                        }
                        
                        
                        Thread.sleep(msToWait);
                        
                    } 
    
                } //try four tumes
                
                if( tries >= 4 ){                
                    //everything else unlisted is a failure, move onto the next batch.
                    SQLUtilities.execSQL(datadb , 
                        "UPDATE PROJECTS_REDCAP_OUTPUT_EAV "
                            + "SET SENDLOG='<error>did not receive a response from redcap in regards to this patient</error>' "
                            + "WHERE PROJECTID='"+project_id+"' AND "
                            + "SENDLOG='"+project_id+"'" 
                    );
                }
                
                session_num ++;
                
            } while( !this.isCancelled() && tries < 999 );
            
            generateErrorLog();

            if( !isCancelled() ){
                this.updateStatus("Complete");
            } else {
                this.updateStatus("Cancelled");
            }

        } catch (Exception ex){
            logError( ex );
        } 
    }
    
    /**
     * This generates a debugging log out so that the end user doesn't sit and wonder what went wrong. This used to 
     * exist as a PHP page after operations, this has been moved into the Java side so that we can eventually unify
     * all of the Java Services command into a single common page eventually. This should generate a table that looks like
     * <table>
     *  <tr><th>Variable</th><th>Error                                                        </th></tr>
     * 
     * <!-... error 1 ...->
     * 
     *  <tr><td>chol_ldl</td>
     *  <td> The data wasn't a number,
     *   <strong>Sample Errors:</strong>
     *   <table>
     *        <tr><th>Study ID</th><th>Event</th><th>Value      </th></tr>
     *        <tr><td>STUDY001</td><td>enrol</td><td>Didn't Draw</td></tr>
     *        <tr><td>STUDY002</td><td>vst 2</td><td>Contaminated</td></tr>
     *   </table>
     * </td></tr>
     * 
     *  <!-... error 2 ...->
     * 
     * </table>
     * 
     * @throws Exception 
     */
    private void generateErrorLog() throws Exception {

        this.log("Completed sending, creating error log:");
        Object currentvar = null;
        int currentdescriptioncount = 0;

        String table = "<table border=1><tr><th>Variable</th><th>Error</th></tr>\n";
        String subtable = "";
        String lastrow = "";
        String coloring = "";

        //now let's assemble the data for simpler viewing.
        SQLUtilities errors = new SQLUtilities( datadb , 
            "SELECT * FROM PROJECTS_REDCAP_OUTPUT_EAV "
                + "WHERE SENDLOG LIKE '<error%' AND PROJECTID='"+project_id+"'" 
                + " ORDER BY VAR, STUDYID, SENDLOG"
        );

        for( Object error : errors ){

            HashMap line = (HashMap) error;
            String comparison = "" + line.get("VAR") +":" + line.get("SENDLOG");

            //hey it's changed!
            if( currentvar == null || !currentvar.equals(comparison) ){                    
                
                coloring = coloring.equals("") ? " style='background-color:#f0f0f0' " : "";
                
                table += (!"".equals( subtable ) ? (subtable + lastrow + "</table>\n</td></tr>\n\n<tr>") : "") ;
                table += "<td " +coloring+ ">" + line.get("VAR") + "</td>";
                table += "<td " +coloring+ ">" + line.get("SENDLOG").toString().replaceAll("<(.?)error>", "");
                
                subtable = "<br><br><strong>Sample Errors:</strong><br>\n<table border=1><tr><th>Study ID</th><th>Event</th><th>Value</th></tr>\n";
                
                currentdescriptioncount = 0;
                lastrow = "";

            }
            
            //generate examplar rows.
            if(currentdescriptioncount < numberOfErrorsGrouped){
                subtable += "\t<tr><td>" + line.get("STUDYID") + "</td><td>" + line.get("EVENT_NAME") + "</td><td>"+line.get("VALUE")+"</td></tr>\n"; 
            } else {
                lastrow = "\t<tr><th colspan=3>"+ ( currentdescriptioncount - numberOfErrorsGrouped ) +" other patients have the same issue</th></tr>\n"; 
            }

            currentdescriptioncount ++;

            currentvar = comparison;

        }

        //always complete the last row of issues.
        table += (!"".equals( subtable ) ? (subtable + lastrow + "</table>\n</td></tr>\n\n<tr>") : "") ;
        table += "</tr></table>";

        this.log(table);
        
        this.log("Error Log Generated.");
    }
    
    public String xmlfix( String xml ){
        //currently as of 6/2015 redcap does not accept the escaped XML characters as per spec. replacing
        //with english equivalents until further notice.
        return xml.replaceAll("&", "and").replaceAll(">", "greater than").replaceAll("<", "less than");
    }

    /**
     * This function assembles the EAV data into a XML file for sending.
     * @param data
     * @return 
     */
    private StringBuffer generateSendingXml(HashMap[] data) {
        StringBuffer xml = new StringBuffer();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><records>\n");

        for( HashMap line : data ){
            String value = xmlfix(""+line.get("VALUE"));
            if( value.length() > 4000 ){
                value = value.substring(0,4000);
            }
            xml.append("<item><record>"+xmlfix(""+line.get("STUDYID"))+"</record>");
            if( !"$".equals(line.get("EVENT_NAME"))){
                xml.append("<redcap_event_name>"+xmlfix(""+line.get("EVENT_NAME"))+"</redcap_event_name>");
            }
            xml.append("<field_name>"+xmlfix(""+line.get("VAR"))+"</field_name><value>"+value+"</value>");
            xml.append("</item>\r\n");
        }

        xml.append("</records></xml>");
        return xml;
    }
    
    /**
     * This function processes the string data returned from the redcap API and determines what errors there was.
     * It logs each issue to the EAV file generated on the server. 
     * @param fromserver - the XML string from the server
     * @throws ParserConfigurationException - in case it can't create a SAX Parser
     * @throws SAXException - if the XML is incorrect
     * @throws IOException - If the string can't be read from memory (really?)
     * @throws SQLException - If there's an issue logging the errors to database.
     */
    private void processREDCapXml(String fromserver) throws ParserConfigurationException, SAXException, IOException, SQLException {
        
        //build the XML parsers
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream( fromserver.getBytes() ));

        //the data is received from the server in this format:
        //<hash><error>There were data validation errors</error>
        //<field>
        //  <record>1481 (event_43_arm_2)</record>
        //  <field_name>diast_bp</field_name>
        //  <value>76;72;77;68;</value>
        //  <message> 
        //     The value you provided could not be validated because it does not 
        //     follow the expected format. Please try again.
        //  </message>
        //</field>
        //...more field (s)...       
        
        //or 
        
        //<ids><id>1509</id><id>1343</id><id>1517</id></ids>
        
        //serialize everything from the <hash> or <ids> onwards, since they are the root nodes.
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);

            if (node instanceof Element) {
                
                if( "field".equals(node.getNodeName()) ){
                    
                    //serialize all of the descendents into one hashset since it's just one item.
                    NodeList attrList = node.getChildNodes();
                    HashMap<String,String> line = new HashMap<String,String>();
                    for (int j = 0; j < attrList.getLength(); j++) {   
                        Node attr = attrList.item(j);
                        line.put(attr.getNodeName(), attr.getTextContent().trim());    
                    }
                    
                    //now extract the settings.
                    String record = line.get("record").replaceAll("'","''");
                    String field_name = line.get("field_name").replaceAll("'","''");
                    String message = line.get("message") == null ? "" : ( line.get("message").replaceAll("'","''") );
                    String value =   line.get("value")   == null ? "" : ( line.get("value").replaceAll("'","''") );
                    String event = "$";
                    
                    //break out the event, which is in parentheses. <record>1481 (event_43_arm_2)</record>
                    if( record != null && record.indexOf('(') > 0 && record.indexOf(')') > 0){
                        line.put( "event", event = record.substring(record.indexOf('(')+1,record.indexOf(')')).trim() );
                        line.put( "record", record = record.substring(0, record.indexOf('(')).trim() );
                    }

                    SQLUtilities.execSQL(datadb , 
                        "UPDATE PROJECTS_REDCAP_OUTPUT_EAV SET SENDLOG='<error>"+message+"</error>' "
                            + "WHERE PROJECTID='"+project_id+"' AND "
                            + "SENDLOG='"+project_id+"' AND "
                            + "STUDYID='"+record+"' AND "
                            + "VAR='"+field_name+"' AND "
                            + "EVENT_NAME='"+event+"'" 
                    );

                } else if( "fields".equals(node.getNodeName()) ){ 

                    log("Unknown fields error " + node.getNodeName());

                } else if( "id".equals(node.getNodeName())){
                    //all is well! the data is in the format of <ids><id>1509</id><id>1343</id><id>1517</id></ids>
                    SQLUtilities.execSQL(datadb , 
                        "UPDATE PROJECTS_REDCAP_OUTPUT_EAV "
                            + "SET SENDLOG='SUCCESS' "
                            + "WHERE PROJECTID='"+project_id+"' AND "
                            + "SENDLOG='"+project_id+"' AND "
                            + "STUDYID='"+node.getTextContent().trim()+"'" 
                    );
                    
                } else if( "error".equals(node.getNodeName())){
                    //saving the error message so we all don't sit here wondering WTF happened.
                    String truncated = node.getTextContent().trim();
                    
                    //see if you're a 6.11 REDCap error string. Good job for moving the goalposts again Vanderbuilt.
                    boolean isV6_11_error = true;
                    for( String line : truncated.split("\n") ){
                        String[] cols = line.split("\",\"");
                        isV6_11_error &= cols.length == 4;
                    }
                    if( isV6_11_error ){
                    
                        for( String line : truncated.split("\n") ){
                            
                            System.err.println("Logging:" + line );
                            String[] cols = line.split("\",\"");
                    
                            String record = cols[0].replaceAll("\"", "");
                            String field_name = cols[1].replaceAll("'","''");
                            String value =   cols[2]== null ? "" : ( cols[2].replaceAll("'","''") );
                            String message = cols[3] == null ? "" : ( cols[3].replaceAll("'","''").replaceAll("\"", "") );
                            String event = "$";

                            //break out the event, which is in parentheses. <record>1481 (event_43_arm_2)</record>
                            if( record != null && record.indexOf('(') > 0 && record.indexOf(')') > 0){
                                event = record.substring(record.indexOf('(')+1,record.indexOf(')')).trim();
                                record = record.substring(0, record.indexOf('(')).trim();
                            }
                            System.err.println( "UPDATE PROJECTS_REDCAP_OUTPUT_EAV SET SENDLOG='<error>"+message+"</error>' "
                                    + "WHERE PROJECTID='"+project_id+"' AND "
                                    + "SENDLOG='"+project_id+"' AND "
                                    + "STUDYID='"+record+"' AND "
                                    + "VAR='"+field_name+"' AND "
                                    + "EVENT_NAME='"+event+"'" );
                            SQLUtilities.execSQL(datadb , 
                                "UPDATE PROJECTS_REDCAP_OUTPUT_EAV SET SENDLOG='<error>"+message+"</error>' "
                                    + "WHERE PROJECTID='"+project_id+"' AND "
                                    + "SENDLOG='"+project_id+"' AND "
                                    + "STUDYID='"+record+"' AND "
                                    + "VAR='"+field_name+"' AND "
                                    + "EVENT_NAME='"+event+"'" 
                            );
                        }
                    } else {
                        if( truncated.length() > 2000 ) {
                            truncated = truncated.substring(0,1950) + "... ";
                        }
                        SQLUtilities.execSQL(datadb , 
                            "UPDATE PROJECTS_REDCAP_OUTPUT_EAV SET SENDLOG='<error>"+truncated+"</error>' "
                                + "WHERE PROJECTID='"+project_id+"' AND "
                                + "SENDLOG='"+project_id+"'"
                        );
                    }
                } else {
                    log("Unknown node name " + node.getNodeName());
                }
            } else if( !"#text".equals( node.getNodeName() ) ){ 
                
                //all of the items have line breaks and other text items in between. if it's not that then log it.
                log("not an Element? " + node.getClass().getName() + ": " + node.getNodeName() + " " + node.getTextContent() );
                
            }
        } //all the node parseing
    }
    
    /**
     * This method opens communication with the server as well as creates a log file in the temporary folder set up in 
     * the settings ini so that the transactions can be logged and viewed for debugging purposes.
     * 
     * @param xml - the XML received from the API
     * @param session_num - what sending group this is, each chunk of data is represented by this session.
     * @return - The from the server
     * @throws IOException  - if any of the files can't write.
     */
    private String communicateWithREDCap(StringBuffer xml, int session_num ) throws IOException {
        String fromserver ;
        //now that the file is generated, let's save it to file for inspection.
        File currSession = new File( outputFolder.getAbsolutePath() + "/" + project_id + "." + session_num + "."+System.currentTimeMillis()+".to.xml");
        File currReceive = new File( outputFolder.getAbsolutePath() + "/" + project_id + "." + session_num + "."+System.currentTimeMillis()+".from.xml");

        HashMap request = new HashMap();

        request.put("token", projectSettings.get("REDCAP_APIKEY"));
        request.put("content", "record");
        request.put("format", "xml");
        request.put("type", "eav");
        request.put("returnFormat", "xml");
        request.put("returnContent", "ids");
        request.put("data", xml.toString());

        ReadWriteTextFile.setContents(currSession, xml.toString());
        InputStream is = ClientHTTPRequest.post(new URL(""+projectSettings.get("REDCAP_URL")),request);

        fromserver = new java.util.Scanner(is).useDelimiter("\\A").next();

        //remove the new indentation added by redcap in its current version. yay.
        ReadWriteTextFile.setContents(currReceive, fromserver);
        
        return fromserver;
    }
}

