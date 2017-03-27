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

import edu.rochester.urmc.util.ReadWriteTextFile;
import edu.rochester.urmc.util.SQLUtilities;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is using our i2b2 datamodel of creating new datamarts per each project. this provisions the datamarts
 * by creating a new schema, uploading baseline build from Harvard, then outputs a 1.7 ontology & query XML to reload
 * the i2b2 system. This does have a few interesting quirks - namely that the SQL files were edited so that the lines
 * would end with appropriate spacing so the dumb SQL parser would break at the appropriate lines. Also the operating
 * XML is greatly helped by moving the bootstrap datasources to a different XML do that these refreshes do not cause
 * a race condition with the loader.
 * @author png
 */
public class DatamartCreator extends SQLQueryJob {

    private static final Logger logger = LogManager.getLogger(DatamartLoader.class);
    Connection out = null;
    Connection mart = null;
    String[] sqlfiles = {
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/crc_create_datamart_oracle.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/crc_create_query_oracle.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/crc_create_uploader_oracle.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_CONCEPT_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_PATIENT_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_PID_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_EID_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_PROVIDER_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_VISIT_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_CONCEPT_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_ENCOUNTERVISIT_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_PATIENT_MAP_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_PATIENT_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_PID_MAP_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_EID_MAP_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_PROVIDER_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/REMOVE_TEMP_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/UPDATE_OBSERVATION_FACT.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/SYNC_CLEAR_CONCEPT_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/SYNC_CLEAR_PROVIDER_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/UPDATE_QUERYINSTANCE_MESSAGE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/CREATE_TEMP_MODIFIER_TABLE.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/INSERT_MODIFIER_FROMTEMP.sql",
        "edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/scripts/procedures/oracle/SYNC_CLEAR_MODIFIER_TABLE.sql"
    };

    String url = "";
    String driver =  "";
    String username = "";
    String password = "";
    String driverjar =  "";

    public void run(){

        this.updateStatus("Running",1);
        try{

            url = settings.getProperty("DATAMARTCREATEURL","jdbc:oracle:thin:@i2b202:1521:i2b2");
            driver = settings.getProperty("DATAMARTCREATEDRIVER","oracle.jdbc.OracleDriver" );
            username = settings.getProperty("DATAMARTCREATEUSER","i2b2datacreationuser" );
            password = settings.getProperty("DATAMARTCREATEPASS","" );
            driverjar = settings.getProperty("DATAMARTCREATEJAR","ojdbc6.jar" );

            Class.forName(driver);

            out = java.sql.DriverManager.getConnection(url, username, password);
            executeSqlFileWithReplace( out, new File ( settings.getProperty("SQLFILES","" ) + "datamart/datamart1.sql") , false );

            String martuser = transformUsername(project_code.toString());
            String martpass = transformPassword(project_code.toString());
            mart = java.sql.DriverManager.getConnection(url, martuser , martpass );
            for( int i = 0; i < sqlfiles.length && !isCancelled(); i++ ){
                executeSqlFileWithReplace( mart, new File ( settings.getProperty("SQLFILES","" ) + sqlfiles[i]) , false );
                updateStatus("Running", (int)(((i+1) * 100.0)/(sqlfiles.length + 3.0)));
            }

            executeSqlFileWithReplace( out, new File ( settings.getProperty("SQLFILES","" ) + "datamart/datamart2.sql") , false );

            //Regenerate ont-ds.xml and crc-ds.xml

            this.updateStatus("Running",98);
            modifyOntXML();

            this.updateStatus("Running",99);
            modifyCrcXML();

            updateStatus("Complete");

        } catch (Exception ex ){
            logError(ex);
            this.updateStatus("Error");
        } finally {
            try{
                out.close();
                mart.close();
            } catch ( Exception ex ){
                ex.printStackTrace();
            }
        }
    }

    public String[] getNotificationTos(){
        String ans = "phillip_ng@urmc.rochester.edu";
        if( project.get("LAST_UI_TOUCHED") != null && System.currentTimeMillis() - ((Date) project.get("LAST_UI_TOUCHED")).getTime() > 10000 ){
            ans += ",david_pinto@urmc.rochester.edu";
        }
        return ans.split(",");
    }

    public void executeSqlFileWithReplace( Connection db, File input, boolean acceptFailure ) throws SQLException {
        if( input.exists() ){
            this.log( "Executing File " + input +"<br><ol>");
            String[] cmds  = ReadWriteTextFile.getContents(input).replaceAll(";\\s+(\n|\r)", ";\n").split(";\n");

            for( String cmd : cmds ){
                cmd = cmd.trim();
                cmd = cmd.replace("\"projectcode\"",project_code.toString());
                cmd = cmd.replace("\"projid\"",project_id.toString());
                cmd = cmd.replace("\"username\"",transformUsername(project_code.toString()));
                cmd = cmd.replace("\"password\"",transformPassword(password));
                if( cmd.endsWith(";") ){
                    cmd = cmd.substring(0, cmd.length()-1);
                }
                boolean success = false;
                if(cmd.length() > 0 ){
                    try{
                        SQLUtilities.execSQL(db, cmd);
                        success = true;
                    } catch ( Exception ex ){
                        this.log("<li><b style='color:red'>FAILED<pre>" + cmd + "</pre></b></li>");
                        if( !acceptFailure ){
                            throw ex;
                        }
                    }
                }
            }
            this.log( "</ol><br>");
        } else {
            throw new IllegalArgumentException( "File does not exist: " + input );
        }
    }
    public void modifyOntXML() throws Exception {
        File xml = new File ( settings.getProperty("DATAMARTCREATEONTXML","/opt/jboss-as-7.1.1.Final/standalone/deployments/ont-ds.xml") );
        if( xml.exists() ){

            String output = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" ;
            output += "<datasources xmlns=\"http://www.jboss.org/ironjacamar/schema\">\n";

            //no longer adding bootstrap to avoid connection restart errors.
            //always add the bootstrap data connection.
            //output += outputXmlLine( "OntologyBootStrapDS", "i2b2hive", "" );

            String query = "SELECT C_PROJECT_PATH, C_DB_FULLSCHEMA from I2B2HIVE.ONT_DB_LOOKUP";
            for( HashMap ontdata : SQLUtilities.getHashedTable(out, query)){
                String prcode = ontdata.get("C_PROJECT_PATH").toString();
                String schema = ontdata.get("C_DB_FULLSCHEMA").toString();
                prcode = prcode.replace("/","");
                output += outputXmlLine( "Ontology"+prcode+"DS", schema, transformPassword(prcode) );
            }
            output += "</datasources>";
            ReadWriteTextFile.setContents(xml, output);

        } else {
            throw new IOException( "File does not exist: " + xml );
        }
    }
    public void modifyCrcXML() throws Exception {
        File xml = new File ( settings.getProperty("DATAMARTCREATECRCXML","/opt/jboss-as-7.1.1.Final/standalone/deployments/crc-ds.xml") );
        if( xml.exists() ){
            String output = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" ;
            output += "<datasources xmlns=\"http://www.jboss.org/ironjacamar/schema\">\n";

            //no longer adding bootstrap to avoid connection restart errors.
            //always add the bootstrap data connection.
            //output += outputXmlLine( "CRCBootStrapDS", "i2b2hive", "" );

            String query = "SELECT  C_DB_NICENAME, C_DB_FULLSCHEMA from I2B2HIVE.CRC_DB_LOOKUP";
            for( HashMap ontdata : SQLUtilities.getHashedTable(out, query)){
                String prcode = ontdata.get("C_DB_NICENAME").toString();
                String schema = ontdata.get("C_DB_FULLSCHEMA").toString();
                prcode = prcode.replace("/","");
                output += outputXmlLine( "QueryTool"+prcode+"DS", schema, transformPassword(prcode));
            }
            output += "</datasources>";
            ReadWriteTextFile.setContents(xml, output);

        } else {
            throw new IOException( "File does not exist: " + xml );
        }
    }
    public String transformPassword(String password){
        return "";
    }
    public String transformUsername(String username){
        return "I2B2MART" + username.toUpperCase();
    }
    public String outputXmlLine( String poolname, String username, String password ){
        String output = "";
        output += "<datasource jta=\"false\" jndi-name=\"java:/"+poolname+"\" pool-name=\""+poolname+"\" enabled=\"true\" use-ccm=\"false\">\n";
        output += "\t<connection-url>"+url+"</connection-url>\n";
        output += "\t<driver-class>"+driver+"</driver-class>\n";
        output += "\t<driver>"+driverjar+"</driver>\n";
        output += "\t<security>\n";
        output += "\t\t<user-name>"+username+"</user-name>\n";
        output += "\t\t<password>"+password+"</password>\n";
        output += "\t</security>\n";
        output += "\t<validation>\n";
        output += "\t\t<validate-on-match>false</validate-on-match>\n";
        output += "\t\t<background-validation>false</background-validation>\n";
        output += "\t</validation>\n";
        output += "\t<statement>\n";
        output += "\t\t<share-prepared-statements>false</share-prepared-statements>\n";
        output += "\t</statement>\n";
        output += "</datasource>\n\n";
        return output;
    }
}
