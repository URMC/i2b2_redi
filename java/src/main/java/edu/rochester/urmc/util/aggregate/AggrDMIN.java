package edu.rochester.urmc.util.aggregate;

import edu.rochester.urmc.util.AggregateOnePass;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;


public class AggrDMIN implements AggregateOnePass {
    
    double currmin = Double.MAX_VALUE;
    HashMap field = null;
    Object levelcd = null;
    Object answer = null;
    Date start = null;
    Date end = null;
    
    public void initialize( HashMap record, Date start, Date end )throws Exception{
        this.field = record;
        this.start = start;    
        this.end = end;
        this.levelcd = record.get("SYSID");
        if( levelcd == null ){
            throw new IllegalArgumentException( "Level Code for calculation Cannot be null: " + record );
        }
    }
    public void process( HashMap record ) throws Exception{
        boolean icare = levelcd.equals( record.get("LEVEL_CD"));            
        if( icare && record.containsKey("STARTING") && end != null ){        
            icare &= ((Date) record.get("STARTING")).before(end);
        }
        if( icare && record.containsKey("ENDING") && record.get("ENDING") != null && start != null){        
            icare &= ((Date) record.get("ENDING")).after(start);
        }
        if( icare ){
            Double temp = val(record.get("VAL"));
            if( temp != null && temp.doubleValue() < currmin ){
                currmin = temp.doubleValue();
                answer = record.get("DATES");
            }
        }
    }
    public Object result(){
        return answer;
    }
    public HashMap getField(){
        return field;
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
}
