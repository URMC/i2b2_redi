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
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Date;

public class DateConverter  {
    
    private static SimpleDateFormat DTCON= new SimpleDateFormat("MM/dd/yyyy");
    private static SimpleDateFormat TMCON= new SimpleDateFormat("HH:mm");
    
    long MSECONDS_IN_24HRS = 60 * 60 * 24 * 1000;
    long MSECONDS_IN_04YRS = ((3*365) + 366) * 60 * 60 * 24 * 1000;
    
    
    public static double microsoftDate( Date input ){
    
        //ok people, i'm getting sick and tired of java dates that you can't like add 1 to.
        //so screw sun. I'm using doubles.
        //
        //   Double         Date      Actual             Time      Actual
        //   number         portion   date               portion   time
        //------------------------------------------------------------------
        //        1.0           1     December 31, 1899  .0        12:00:00 A.M.
        //        2.5           2     January 1, 1900    .5        12:00:00 P.M.
        //    27468.96875   27468     March 15, 1975     .96875    11:15:00 P.M.
        //    36836.125     36836     November 6, 2000   .125       3:00:00 A.M.
        //   epoch is 1/1/1970. (25569)
        
        long ms = input.getTime();
        //divide by 400 years, (stupid gregory & julius)
        //then divide by 4 years
        //then divide by 1 year
        //now figure days.
        
        return 0;
        
    }
    
    public static String format( Object aDate, String fmt ){
        String ans = null;
        try{ 
            
            fmt.replaceAll("Y","y"); //this is not reserved for some reason.
            fmt.replaceAll("D","d"); //i really don"t care about the day of the year.
            fmt.replaceAll("m","M");
            fmt.replaceAll("A","a");
            
            //if there isn"t an AM/PM indicator, assume 24 hr. 
            if( fmt.indexOf("a") <= 0 ){
                fmt.replaceAll("h","H"); //put out 24 hour increments
            }
            Date in = (Date) convertToDB( aDate );
            
            SimpleDateFormat formatter = new SimpleDateFormat(fmt);
            formatter.setLenient(true);
            
            ans = formatter.format(in);
            
        }catch (Exception ex){}
        return ans;
    }
    
    
    public static void setOutputFormats ( String formatdt, String formattm ){
        DTCON= new SimpleDateFormat(formatdt);
        TMCON= new SimpleDateFormat(formattm);
    }
    
    public static Object convertToDB( Object in ) throws Exception {
        Object answer = null;
        
        if( in == null || in instanceof java.util.Date ){
            answer = in;
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
            if( answer == null ){ 
                throw new Exception( "'"+ aDt +"' is not any " +
                     "Date or Time that I understand. Try to enter your "+
                     "Dates as follows - 'mm/dd/yyyy' and your time in " +
                     "military time.");
            }
        
        }
        return answer;
    }
    public static Object convertFromDB( Object in ){ 
        String answer = "";
        
        if( in != null && in instanceof java.util.Date ){
            java.util.Date tempDt = ((java.util.Date) in);
            String dt = DTCON.format(tempDt);
            String tm = TMCON.format(tempDt);
            
            //System.out.println("" + dt + " " + tm );
            
            if( dt.equals( "01/01/1970") ){ dt = ""; } //windows/unix/java zero date
            if( dt.equals( "12/30/1899") ){ dt = ""; } //access zero date
            if( dt.equals( "01/01/1960") ){ dt = ""; } //vax zero date
            if( dt.equals(   "1/1/1960") ){ dt = ""; } //vax zero date
            if( tm.equals( "00:00"     ) ){ tm = ""; }
            
            if( !dt.equals("") && !tm.equals("") ){ dt += " "; }
            
            answer = (dt+tm);
            
            if("".equals( answer )){ answer = "00:00"; }

        } else if( in != null ){
            try{ 
                answer = convertFromDB( convertToDB( in )).toString() ; 
            }catch(Exception e){
            }
        }
        return answer;
    }
    
    /**
     * Here's a method to find the number of days between two dates. 
     * Topic: JSP
     *   Doug Bell, Oct 6, 2001
     * I wrote a method a while back to calculate the number of days between two dates that is independent of the calendar type:
     * 
     * Calculates the number of days between two calendar days in a manner
     * which is independent of the Calendar type used.
     *
     * @param d1    The first date.
     * @param d2    The second date.
     *
     * @return      The number of days between the two dates.  Zero is
     *              returned if the dates are the same, one if the dates are
     *              adjacent, etc.  The order of the dates
     *              does not matter, the value returned is always >= 0.
     *              If Calendar types of d1 and d2
     *              are different, the result may not be accurate.
     */
    public static int getDaysBetween (java.util.Calendar d1, java.util.Calendar d2) {
        if (d1.after(d2)) {  // swap dates so that d1 is start and d2 is end
            java.util.Calendar swap = d1;
            d1 = d2;
            d2 = swap;
        }
        int days = d2.get(java.util.Calendar.DAY_OF_YEAR) -
                   d1.get(java.util.Calendar.DAY_OF_YEAR);
        int y2 = d2.get(java.util.Calendar.YEAR);
        if (d1.get(java.util.Calendar.YEAR) != y2) {
            d1 = (java.util.Calendar) d1.clone();
            do {
                days += d1.getActualMaximum(java.util.Calendar.DAY_OF_YEAR);
                d1.add(java.util.Calendar.YEAR, 1);
            } while (d1.get(java.util.Calendar.YEAR) != y2);
        }
        return days;
    } // getDaysBetween()

}