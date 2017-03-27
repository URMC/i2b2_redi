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

import java.util.Collection;
import java.util.HashMap;

/**
 *
 * @author png
 */
public class HashMapFunctions {
    
    public static String hashMapToTable( HashMap in ){
        String header = "";
        String HashMapter = "";
        for( Object i : in.keySet()){
            if( in.get(i) != null ){
                header += "<th>" + i + "</th>";
                HashMapter += "<td>" + (in.get(i) != null ? in.get(i) : "" ) + "</td>";
            }
        }
        return "<br><table border=1 cellspacing=0 cellpadding=0 ><tr>" + header + "</tr><tr>" + HashMapter + "</tr></table>";
    }
    public static String hashMapArrayToTable( HashMap[] ins ){
        String header = "";
        String datas = "";
        if( ins.length > 0 ){
            for( HashMap in : ins ){
                String footer = "";
                for( Object i : ins[0].keySet()){        
                    if( in == ins[0] ){
                        header += "<th>" + i + "</th>";
                    }
                    footer += "<td>" + (in.get(i) != null ? in.get(i) : "" ) + "</td>";
                }
                datas += "<tr>" + footer + "</tr>";
            }
        }
        return "<br><table border=1 cellspacing=0 cellpadding=0 ><tr>" + header + "</tr>"+datas+"</table>";
    }
    public static String logHashMapArrayToTable( String comment, HashMap[] ins ){
        return logHashMapArrayToTable( comment, ins, null );
    }
    public static String logHashMapArrayToTable( String comment, HashMap[] ins, Collection keys ){
        return logHashMapArrayToTable( comment, ins, keys, "" );
    }
    public static String logHashMapArrayToTable( String comment, HashMap[] ins, Collection keys, String footer ){
        String header = "";
        String datas = "";
        if( ins.length > 0 ){
            for( HashMap in : ins ){
                String foot = "";
                for( Object i : (keys == null ? ins[0].keySet() : keys) ){
                    if( ins.length > 1 || in.get(i) != null ){
                        if( in == ins[0] ){
                            header += "<th>" + i + "</th>";
                        }
                        foot += "<td>" + (in.get(i) != null ? in.get(i) : "" ) + "</td>";
                    }
                }
                datas += "<tr>" + foot + "</tr>";
            }
        } else {
            header = "No Data Found For Timeperiod.";
        }
        return( comment + "<br><table border=1 cellspacing=0 cellpadding=0 ><tr>" + header + "</tr>"+datas+"</table>" + footer );
    }
    public String reducioHashMap( HashMap in ){
        String output = "";
        for( Object i : in.keySet()){
            if( in.get(i) != null ){
                output += i + ": " + in.get(i) + ", ";
            }
        }
        return output;
    }
    HashMap[] concat(HashMap[] A, HashMap[] B) {
       int aLen = A.length;
       int bLen = B.length;
       HashMap[] C= new HashMap[aLen+bLen];
       System.arraycopy(A, 0, C, 0, aLen);
       System.arraycopy(B, 0, C, aLen, bLen);
       return C;
    }
}
