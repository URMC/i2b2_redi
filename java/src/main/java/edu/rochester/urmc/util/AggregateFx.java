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

import java.text.DecimalFormat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;


public class AggregateFx {
    public Object aggrVFIRST( HashMap[] in , HashMap control){
        Object ans = "";
        for( HashMap i : in ){
            ans = ""+i.get("DATA");
            i.put("RATIONALE", "First Value");
            break;
        }
        return ans;
    }
    public static Double val( Object x ){
        Double answer = null;
        if( x instanceof Number ){
            answer = new Double(((Number) x).doubleValue());
        } else if( !isKindOfNull( x ) ){
            try{ answer = new Double( "" + x ); }catch (Exception ex){}
        }
        return answer;
    }
    public static boolean isKindOfNull( Object in ){
        boolean ans = false;
        ans |= in == null;
        
        //boolean temp =( in == null );
        //boolean temp3 =(".".equals(in.toString().trim())) );
        //....
        //answer = temp || temp2 || .......;
        
        ans |= (in != null && "".equals(in.toString().trim()));
        ans |= (in != null && ".".equals(in.toString().trim()));
        ans |= (in != null && ":".equals(in.toString().trim()));
        ans |= (in != null && "/".equals(in.toString().trim()));
        ans |= (in != null && "N".equals(in.toString().trim()));
        ans |= (in != null && "N.".equals(in.toString().trim()));
        ans |= (in != null && ".N".equals(in.toString().trim()));
        ans |= (in != null && "-".equals(in.toString().trim()));
        //ans |= (in != null && "0".equals(in.toString().trim()));
        return ans;
    }
    public Object aggrVMIN( HashMap[] in , HashMap control){
        Object ans = "";
        
        Double minsofar = Double.MAX_VALUE;
        
        for( HashMap i : in ){
            
            Double test = val(i.get("DATA"));
            if( test != null && minsofar.compareTo(test) > 0 ){
                minsofar = test;
                i.put("RATIONALE", " current min..");
            }
        }
        return ""+(Double.MAX_VALUE == minsofar?"":minsofar);
    }
    public Object aggrD2ND( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 2 ){
                ans = ""+ i.get("START_DATE");
                break;
            }
        }
        return ans;
    }
    public Object aggrDE2ND( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 2 ){
                ans = ""+ in[iter].get("START_DATE");
                break;
            }
        }
        return ans;
    }
    public Object aggrDE3RD( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 3 ){
                ans = ""+ in[iter].get("START_DATE");
                break;
            }
        }
        return ans;
    }
    public Object aggrDE4TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 4 ){
                ans = ""+ in[iter].get("START_DATE");
                break;
            }
        }
        return ans;
    }
    public Object aggrDE5TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 5 ){
                ans = ""+ in[iter].get("START_DATE");
                break;
            }
        }
        return ans;
    }
    public Object aggrD3RD( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 3 ){
                ans = ""+ i.get("START_DATE");
                break;
            }
        }
        return ans;
    }
    public Object aggrD4TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 4 ){
                ans = ""+ i.get("START_DATE");
                break;
            }
        }
        return ans;
    }   
    public Object aggrD5TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 5 ){
                ans = ""+ i.get("START_DATE");
                break;
            }
        }
        return ans;
    }    
    public Object aggrV2ND( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 2 ){
                ans = ""+ i.get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrVE2ND( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 2 ){
                ans = ""+ in[iter].get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrVE3RD( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 3 ){
                ans = ""+ in[iter].get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrVE4TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 5 ){
                ans = ""+ in[iter].get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrVE5TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( int iter = in.length-1; in != null && iter > 0; iter-- ){
            count++;
            in[iter].put("RATIONALE", "#" + count );
            if( count >= 5 ){
                ans = ""+ in[iter].get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrV3RD( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 3 ){
                ans = ""+ i.get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrV4TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 4 ){
                ans = ""+ i.get("DATA");
                break;
            }
        }
        return ans;
    }   
    public Object aggrV5TH( HashMap[] in , HashMap control){
        Object ans = "";
        String test;
        int count = 0;
        for( HashMap i : in ){
            count++;
            
            i.put("RATIONALE", "#" + count );
            if( count >= 5 ){
                ans = ""+ i.get("DATA");
                break;
            }
        }
        return ans;
    }
    public Object aggrDMIN( HashMap[] in , HashMap control){
        Object ans = "";
        
        Double minsofar = Double.MAX_VALUE;
        
        for( HashMap i : in ){
            Double test = val(i.get("DATA"));
            if( test != null &&  minsofar.compareTo(test) > 0 ){
                minsofar = test;
                ans = i.get("START_DATE");
                i.put("RATIONALE", " current min..");
            }
        }
        return ans;
    }
    public Object aggrVAVG( HashMap[] in , HashMap control){
        Object ans = "";
        
        double sum = new Double(0);
        double count = 0;
        
        for( HashMap i : in ){
            
            Double test = val(i.get("DATA"));
            if( test != null  ){
                sum += test.doubleValue();
                ;
                i.put("RATIONALE", ""+new DecimalFormat("0.000").format(sum) +"/"+ (++count) );
            }
        }
        return "" + (count == 0? "" : new DecimalFormat("0.00000").format(sum/count) );
    }
    public Object aggrVMAX( HashMap[] in , HashMap control){
        Object ans = "";
        
        Double minsofar = Double.MIN_VALUE;
        
        for( HashMap i : in ){
            Double test = val(i.get("DATA"));
            if( test != null &&  minsofar.compareTo(test) < 0 ){
                minsofar = test;
                i.put("RATIONALE", " current max..");
            }
        }
        return ""+(Double.MIN_VALUE == minsofar?"":minsofar);
    }
    public Object aggrDMAX( HashMap[] in , HashMap control){
        Object ans = "";
        
        Double minsofar = Double.MIN_VALUE;
        
        for( HashMap i : in ){
            Double test = val(i.get("DATA"));
            if( test != null &&  minsofar.compareTo(test) < 0 ){
                minsofar = test;
                ans = i.get("START_DATE");
                i.put("RATIONALE", " current max..");
            }
        }
        return ans;
    }
    public Object aggrCOUNT( HashMap[] in , HashMap control){
        int count =0;
        for( HashMap i : in ){
            i.put("RATIONALE", ""+(++count));
        }
        return in.length == 0 ? "" : (""+in.length);
    }
    public Object aggrVMODE( HashMap[] in , HashMap control){
        Object ans = "";
        Arrays.sort(in,new Comparator<HashMap>(){
            public int compare(HashMap a, HashMap b) {
                return ((Comparable)a.get("DATA")).compareTo(((Comparable)b.get("DATA")));
            }
        });
        
        Object sofar = "";
        Object curr = "";
        int maxsofar = 0;
        int currcount = 0;
        
        for( HashMap i : in ){
            if( !curr.equals(i.get("DATA")) ){
                currcount = 0;
                curr = i.get("DATA");
            }
            currcount ++;
            if( currcount > maxsofar ){
                sofar = curr;
                maxsofar = currcount;
                i.put("RATIONALE", ""+currcount+ " current mode..");
            } else {
                i.put("RATIONALE", ""+currcount);   
            }
        }
        return ""+sofar;
    }
    public Object aggrPRESENCE( HashMap[] in , HashMap control){
        return in.length > 0 ? "1" : "0";
    }
    public Object aggrVLAST( HashMap[] in , HashMap control){
        Object ans = "";
        if( in.length > 0 ){
            HashMap i = in[ in.length -1 ];
            ans = ""+i.get("DATA");
            i.put("RATIONALE", "Last Value");
        }
        return ans;
    }

    public Object aggrDFIRST( HashMap[] in , HashMap control){
        Object ans = "";
        for( HashMap i : in ){
            i.put("RATIONALE", "First Date");
            ans = i.get("START_DATE");
            break;
        }
        return ans;
    }
    public Object aggrDLAST( HashMap[] in , HashMap control){
        Object ans = "";
        if( in.length > 0 ){
            HashMap i = in[ in.length -1 ];
            i.put("RATIONALE", "Last Date");
            ans = i.get("START_DATE");
        }
        return ans;
    }
    public Object aggrAGGR_STR( HashMap[] in , HashMap control){
        String ans = "";
        int counter = 0;
        for( HashMap i : in ){
            counter ++;
            i.put("RATIONALE", "adding");
            String addition = (""+i.get("DATA")).replace(';', ',') + ";\n";
            if( ans.length() + 50 >= 4000 ){
                ans += ";\n*** TRUNCATED, " + (in.length - counter) + " ITEMS REMAINING ***";
                break;
            } else {
                ans += addition;
            }
        }
        
        return ans;
    }
    public Object aggrAGGR_STR_N_DATE( HashMap[] in , HashMap control){
        String ans = "";
        int counter = 0;
        for( HashMap i : in ){
            i.put("RATIONALE", "adding");
            String addition = REDCapFormatter.format(i.get("START_DATE"),"datetime")+"-"+(""+i.get("DATA")).replace(';', ',') + ";\n";
            if( ans.length() + 50 >= 4000 ){
                ans += ";\n*** TRUNCATED, " + (in.length - counter) + " ITEMS REMAINING ***";
                break;
            } else {
                ans += addition;
            }
        }
        return ans;
    }
    public Object aggrAGGR_HISTOGRAM( HashMap[] in , HashMap control){
        
        String ans = "";
        HashMap< String, Integer > db = new HashMap();
        int counter = 0;
        for( HashMap i : in ){
            i.put("RATIONALE", "adding");
            String addition = (""+i.get("DATA")).replace(';', ',') ;
            if( !db.containsKey(addition) ){
                db.put(addition, 0);
            }
            db.put(addition,db.get(addition) + 1);
        }
        for( String key : db.keySet() ){
            String addition = key.replace(';', ',')+" - (" + db.get(key) + " items );\n";
            if( ans.length() + 50 >= 4000 ){
                ans += ";\n*** TRUNCATED, " + (in.length - counter) + " ITEMS REMAINING ***";
                break;
            } else {
                ans += addition;
            }
        }
        return ans;
    }
    public Object format( Object ans, HashMap control ){
        return "" + ans;
    }
}
