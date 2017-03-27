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
package edu.rochester.urmc.util;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * This class represents a column for each aggregate assembler, who's duty is to receive data, a series of them one obs,
 * sorted by patientID, then by date. Then after the patient switches to a new id, this class will be told to tally up 
 * the results and return them for final output.
 * @author png
 */
public class AggrAssemblerOnePass {
    
    HashMap patient = null;
    HashMap[] fields = null;
    
    Object currpt;
    Date currstarting = null;
    Date currending = null;
    
    LinkedList< AggregateOnePass > processors = new LinkedList();
    
    private static SimpleDateFormat FORMATTER = new SimpleDateFormat( "yyyyMMddHHmm" );
    
    public AggrAssemblerOnePass(HashMap patient, HashMap[] fields ) throws Exception {
        this.patient = patient;
        this.fields = fields;
        try{
            currstarting = ((Date) patient.get("STARTING"));
        } catch (Exception ex ){
        }
        try{
            currending = ((Date) patient.get("ENDING"));
        } catch (Exception ex ){
        }
        for( HashMap fld : fields ){
            Class clazz = Class.forName("edu.rochester.urmc.util.aggregate.Aggr" + fld.get("AGGR"));
            AggregateOnePass proc = (AggregateOnePass) clazz.newInstance();
            proc.initialize(fld, currstarting, currending);
            processors.add( proc );         
        }
    }
    public void process( HashMap line ) throws Exception {
        for( AggregateOnePass proc : processors ){
            proc.process(line);
        }
    }
    public void markComplete(){ 
        patient.put("DONE", "Y");
    }
    public HashMap serialize(){
        HashMap answer = new HashMap();
        answer.put( "PATIENT", patient );
        for( AggregateOnePass fld : processors ){
            answer.put(fld.getField(), fld.result());
        }
        return answer;
    }
    public String toString(){
        return "Aggregator : " + patient + " >> " + processors ;
    }
    
}