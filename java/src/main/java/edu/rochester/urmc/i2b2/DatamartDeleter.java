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

import java.io.File;

/**
 * As the omega to the Creator code, this one removes and regenerates XML files after a datamart is removed. Note it is
 * a wrapper around the deletedatamart sql file.
 * @author png
 */
public class DatamartDeleter extends DatamartCreator {

    public void run(){

        this.updateStatus("Running",1);
        try{

            url = settings.getProperty("DATAMARTCREATEURL","jdbc:oracle:thin:@i2b2dev:1521:i2b2");
            driver = settings.getProperty("DATAMARTCREATEDRIVER","oracle.jdbc.OracleDriver" );
            username = settings.getProperty("DATAMARTCREATEUSER","i2b2datacreationuser" );
            password = settings.getProperty("DATAMARTCREATEPASS","" );
            driverjar = settings.getProperty("DATAMARTCREATEJAR","ojdbc6.jar" );

            Class.forName(driver);

            out = java.sql.DriverManager.getConnection(url, username, password);
            executeSqlFileWithReplace(out, new File ( settings.getProperty("SQLFILES","" ) + "datamart/deletedatamart.sql") , true );

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
            } catch ( Exception ex ){
                ex.printStackTrace();
            }
        }
    }

}
