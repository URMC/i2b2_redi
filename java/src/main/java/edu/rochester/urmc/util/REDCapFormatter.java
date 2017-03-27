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

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.HashMap;


public class REDCapFormatter {
    /*
        alpha_only             will not worry about if you send a number in thats your issue.
        date_mdy
        date_ymd
        datetime_mdy
        datetime_seconds_mdy
        datetime_seconds_ymd
        datetime_ymd
        email                  will not worry about - you drag in phone number not my issue.
        integer
        number
        number_1dp
        number_2dp
        number_3dp
        number_4dp
        phone
        text_max_81
        time
        zipcode
     */
    
    private static SimpleDateFormat DATES = new SimpleDateFormat("yyyy-MM-dd");
    private static SimpleDateFormat TIMES = new SimpleDateFormat("HH:mm");
    private static SimpleDateFormat DTTMS = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static SimpleDateFormat SCNDS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public static String format( Object ans, HashMap control ){
        return format( ans , (""+control.get("TEXT_VALIDATION_TYPE")).toLowerCase());
    }
    
    public static String format( Object ans, String validation_type ){
        
        if( ans == null ){
            ans = "";
        }
        //first see if its a datelike object that you are trying to format.
        if( validation_type.indexOf("date") >= 0 || validation_type.indexOf("time") >= 0 ){
            if( !(ans instanceof Date )){
                Date temp = dateval( ans );
                if( temp == null ){
                    ans = "" + ans;
                } else {
                    ans = temp;
                }
            }
            if( ans instanceof Date ){
                SimpleDateFormat format = DATES;
                if( validation_type.indexOf("time") == 0 ){ 
                    format = TIMES; 
                } else if( validation_type.indexOf("seconds") > 0 ){ 
                    format = SCNDS; 
                } else if( validation_type.indexOf("datetime") == 0 ){ 
                    format = DTTMS; 
                } 
                ans = format.format(ans);
            }
        }
        
        if( validation_type.indexOf("zipcode") >= 0 || 
            validation_type.indexOf("integer") >= 0 || 
            validation_type.indexOf("number") >= 0 ){
            
            if( !(ans instanceof Number) ){
                Double temp = val( ans );
                if( temp == null ){
                    ans = "" + ans;
                } else {
                    ans = temp;
                }
            }
            if( ans instanceof Number ){
                Number temp = (Number) ans;
                if( "integer".equals(validation_type) ){
                    ans = "" + temp.intValue();
                }
                String output = "";
                DecimalFormat df = null;
                if( validation_type.indexOf("zip") >= 0 ){ 
                    df = new DecimalFormat("00000");
                } else if( validation_type.indexOf("1dp") > 0 ){ 
                    df = new DecimalFormat("0.0");
                } else if( validation_type.indexOf("2dp") > 0 ){ 
                    df = new DecimalFormat("0.00");
                } else if( validation_type.indexOf("3dp") > 0 ){ 
                    df = new DecimalFormat("0.000");
                } else if( validation_type.indexOf("4dp") > 0 ){ 
                    df = new DecimalFormat("0.0000");
                }
                
                if( df == null ){
                    ans = ""+ans.toString();
                } else {
                    ans = df.format(temp.doubleValue());
                }
            }
        }
        
        return ans.toString(); 
    }
    
    public static Double val( Object x ){
        Double answer = null;
        if( x instanceof Number ){
            answer = new Double(((Number) x).doubleValue());
        } else if( !AggregateFx.isKindOfNull( x ) ){
            try{ answer = new Double( "" + x ); }catch (Exception ex){}
        }
        return answer;
    }
    
    public static Date dateval( Object in ){
        Date answer = null;
        
        if( in == null || in instanceof java.util.Date ){
            answer = (Date) in;
            //System.out.println("" + in);
        } else {
            String aDt = in.toString(); 

            //try to parse it normally.
            DateFormat df = DateFormat.getDateInstance();
            try { answer = df.parse(aDt); } catch(ParseException e) {}            
            
            for( int i = 0; answer == null && i < 24; i++ ){
                for( int j = 0; answer == null && j < 8; j++ ){
                    String fmt = "", fmt2 = "";
                    
                    switch(i){
                        case  0: fmt = "" ;break;
                        case  1: fmt = "M/d/yy";  break;
                        case  2: fmt = "MM/d/yy";  break;
                        case  3: fmt = "MM/dd/yy";  break;
                        case  4: fmt = "M/d/yyyy";  break;
                        case  5: fmt = "MM/d/yyyy";  break;
                        
                        case  6: fmt = "M-d-yy";  break;
                        case  7: fmt = "MM-d-yy";  break;
                        case  8: fmt = "M-d-yyyy";  break;
                        case  9: fmt = "MM-d-yyyy";  break;

                        
                        case 10: fmt = "dd-MMM-yyyy";  break; //new
                        case 11: fmt = "d MMM yyyy";  break;
                        case 12: fmt = "d MMM. yyyy";  break;
                        case 13: fmt = "d MMM, yyyy";  break;
                        

                        case 14: fmt = "MMM d yy";  break;
                        case 15: fmt = "MMM d, yy";  break;
                        case 16: fmt = "MMM. d yy";  break;
                        case 17: fmt = "MMM. d, yy";  break;

                        
                        case 18: fmt = "MMM d yyyy";  break;
                        case 19: fmt = "MMM. d yyyy";  break;
                        
                        case 20: fmt = "yyyy MMM d";  break;
                        case 21: fmt = "yyyy MMM. d";  break;
                        case 22: fmt = "yyyy/MM/dd";  break;
                        case 23: fmt = "yyyy-MM-dd";  break; //new
                        
                        default: fmt = ""; break;

                    }
                    switch(j){
                        case  0: fmt2 = "HH:mm:ss"; break; //new
                        case  1: fmt2 = "HH:mm:ss.S"; break; //new
                        case  2: fmt2 = "hh:mm a";  break;
                        case  3: fmt2 = "hh:mma";  break;
                        case  4: fmt2 = "HH:mm";  break;
                        case  5: fmt2 = "h a";break;
                        case  6: fmt2 = "ha";break;
                        case  7: fmt2 = "" ;break;
                        
                        default: fmt2 = ""; break;
                    }
                    
                    if( !("".equals(fmt)) && !("".equals(fmt2)) ){
                        fmt = fmt + " " + fmt2;
                    }
                    if( ("".equals(fmt)) && !("".equals(fmt2)) ){
                        fmt = fmt2;
                    }
                    if( !("".equals(fmt)) && ("".equals(fmt2)) ){
                        fmt = fmt;
                    }
                
                    try{ 
                        SimpleDateFormat formatter = new SimpleDateFormat(fmt);
                        formatter.setLenient(false);
                        answer = formatter.parse( aDt );
                    } catch (Exception e){}
                    /*if( answer != null ) {
                        System.out.println(fmt + " " + fmt2 + ":" + aDt + " : " +answer + ":" + answer.getClass().getName() );
                    }*/
                }
            }        
        }
        return answer;
    }
}