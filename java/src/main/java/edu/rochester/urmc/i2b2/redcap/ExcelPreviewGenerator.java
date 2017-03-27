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


import edu.rochester.urmc.util.HashMapFunctions;
import edu.rochester.urmc.util.SQLUtilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.sql.Connection;
import java.sql.PreparedStatement;

import java.util.HashMap;
import java.util.Properties;

import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * This class takes the raw EAV file from the generated data, for each row, seeing if the ID has changed, otherwise
 * outputting that variable to the excel underneath that column designated for that value.
 * @author png
 */
public class ExcelPreviewGenerator {
    
    RedcapSync par = null; 
    HashMap job = null;
    int counter = 0;
    
    public ExcelPreviewGenerator(RedcapSync parent, HashMap job) {
        this.par=parent;
        this.job = job;
    }
    public void generateFile() throws Exception {
        //first select the distinct events, this will be our sheets.
        XSSFWorkbook hwb = new XSSFWorkbook();
        
        par.log("Creating Excel Preview...");
        boolean hasEvent = false;
        
        //for each event, create a sheet for it.
        for( HashMap events : SQLUtilities.getHashedTable(par.datadb, 
            "SELECT DISTINCT O.EVENT_NAME " +
            "FROM PROJECTS_REDCAP_OUTPUT_EAV O " +
            "WHERE O.PROJECTID='"+job.get("PROJECTID")+"' ORDER BY EVENT_NAME")
        ){
            
            //set some basic headers so that it doesn't move. :-)
            String event_name = events.get("EVENT_NAME").toString().trim();
            XSSFSheet sheet = hwb.createSheet( WorkbookUtil.createSafeSheetName( "$".equals(event_name)? "Preview : " + job.get("PROJECTTITLE") : event_name ));
            sheet.createFreezePane( 0, 1, 0, 1 );
            XSSFRow rowhead = sheet.createRow((short) 0);
            rowhead.createCell(0).setCellValue("STUDYID");  
            
            String sql_field = "UNIQUES.STUDYID ";
            String sql_table = 
                "( SELECT DISTINCT STUDYID FROM PROJECTS_REDCAP_OUTPUT_EAV " +
                "WHERE PROJECTID='"+job.get("PROJECTID")+"' AND "+
                "EVENT_NAME='"+event_name.replaceAll("'","''")+"' ) UNIQUES \n";
            
            HashMap[] insanitycheck = SQLUtilities.getTableHashedArray(par.datadb, 
                "SELECT VAR, STUDYID, EVENT_NAME FROM PROJECTS_REDCAP_OUTPUT_EAV " + 
                "GROUP BY STUDYID, EVENT_NAME, VAR, PROJECTID " +
                "HAVING PROJECTID='"+job.get("PROJECTID")+"' AND "+
                "EVENT_NAME='"+event_name.replaceAll("'","''")+"' AND COUNT(*) > 1");
            
            if( insanitycheck.length > 0 ){
                par.log(HashMapFunctions.logHashMapArrayToTable("The following rows are not unique and this error should not occur.", insanitycheck));
                throw new IllegalArgumentException("Failed Excel Export Check");
            }
            
            //going accross the right, select the variables that you have data for, and creating a new column for that 
            //data. Thus this is the map that remembers the values of the column headers, so that when it's exported
            //below the column has already been set.
            int cell = 1;
            HashMap<String,Integer> colsettings = new HashMap();
            for( HashMap fld : SQLUtilities.getHashedTable(par.datadb, 
                "SELECT VAR FROM PROJECTS_REDCAP_OUTPUT_EAV " + 
                "GROUP BY EVENT_NAME, VAR, PROJECTID " +
                "HAVING PROJECTID='"+job.get("PROJECTID")+"' AND "+
                "EVENT_NAME='"+event_name.replaceAll("'","''")+"'")
            ){
                rowhead.createCell(cell).setCellValue(""+fld.get("VAR"));    
                colsettings.put( ""+fld.get("VAR"), new Integer(cell));
                counter ++;
                cell++;
            }
            
            //select that patient/event that you're on the sheet for.
            String final_sql = "SELECT STUDYID, VAR, VALUE FROM PROJECTS_REDCAP_OUTPUT_EAV " + 
                "WHERE PROJECTID='"+job.get("PROJECTID")+"' AND EVENT_NAME='"+event_name.replaceAll("'","''")+"' " +
                "ORDER BY STUDYID" ;
            

            SQLUtilities output = new SQLUtilities( par.datadb, final_sql );
            par.log("exporting event " + event_name + ", " + output.getSize() + " data elements in export file." );
          
            counter = 0;
            Object currpt = "";
            XSSFRow row = null;
            
            for( int i = 0; i < output.getSize(); i++ ){
                
                HashMap data = output.getNextHashLine();
                if( currpt == null || !currpt.equals(data.get("STUDYID")) ){
                    counter ++;
                    row = sheet.createRow((short) counter );
                    currpt = data.get("STUDYID");
                    row.createCell(0).setCellValue(currpt.toString());  
                }
                Object out = data.get("VALUE");
                if( colsettings.containsKey(data.get("VAR")) ){
                    row.createCell(colsettings.get(data.get("VAR")).intValue()).setCellValue( out == null ? "" : out.toString() );    
                } else {
                    System.err.println("somehow " + data.get("VAR") + " not in " + colsettings );
                }
                
            }
            output.close();
            hasEvent = true;
            
        } //for each event
        
        //just to make sure that there was some error message so that we would carry on...
        if( !hasEvent ){
            XSSFSheet sheet = hwb.createSheet( WorkbookUtil.createSafeSheetName( "ERROR" ));
            sheet.createFreezePane( 0, 1, 0, 1 );
            XSSFRow rowhead = sheet.createRow((short) 0);
            rowhead.createCell(0).setCellValue("There was no data extracted.");  
        }
        
        SQLUtilities.execSQL(par.cntldb, "DELETE FROM PROJECTS_BLOBS WHERE FIELDNAME='EXCELPREVIEW' AND PROJECTID="+job.get("PROJECTID")+"");
        
        File tempFile = File.createTempFile( "preview"+job.get("PROJECTID")+"."+System.currentTimeMillis(), ".xlsx" );
        FileOutputStream fos = new FileOutputStream( tempFile );
        hwb.write(fos);
        fos.flush();
        fos.close();
        
        FileInputStream fis = new FileInputStream(tempFile);
        System.out.println("Blob length = " + tempFile.length());
        PreparedStatement sql = par.cntldb.prepareStatement("INSERT INTO PROJECTS_BLOBS (FIELDNAME, PROJECTID, FILENAME, UPDATED, FILECONTENTS) VALUES ('EXCELPREVIEW',"+job.get("PROJECTID")+",'PREVIEW.XLSX',SYSDATE,?)");
        sql.setBinaryStream(1, fis , (int) tempFile.length());        
        sql.execute();
        sql.close();
        
        
        par.log("Excel File Saved To Database " );
    }
}
