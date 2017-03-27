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

import edu.rochester.urmc.util.SQLUtilities;
import java.util.Date;
import java.util.HashMap;

/**
 * So I will have to admit, this class is a hack. Once upon a time, the task to handle testing a single patient
 * was handled by the Sync class, it was cleaner to eventually move functions that needed to be used for testing into
 * this class. This is currently a to-do item.
 * 
 * @author png
 */
public class Testing extends RedcapSync {
    
    public void run(){
        try{
            super.testmode = true;
            //manually look up the information not in the jobs table.
            for( HashMap line : SQLUtilities.getTableHashedArray(cntldb, "SELECT TESTFIELD, TESTPATIENT FROM PROJECTS WHERE PROJECTID=" + project_id)){
                testfields = line;
            }
            super.run();
        } catch (Exception ex){
            logError( ex );
        }
    }
    
    public String[] getNotificationTos(){
        return null; //send your data to NO ONE. :-)
    }
}
