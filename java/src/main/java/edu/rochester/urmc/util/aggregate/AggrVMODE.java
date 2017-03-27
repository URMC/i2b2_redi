package edu.rochester.urmc.util.aggregate;

import edu.rochester.urmc.util.AggregateOnePass;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;


public class AggrVMODE implements AggregateOnePass {
    
    HashMap<Object, Double> memory = new HashMap();
    double currmax = 0;
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
        if( icare && record.containsKey("ENDING") &&  record.get("ENDING") != null && start != null){        
            icare &= ((Date) record.get("ENDING")).after(start);
        }
        if( icare ){
            if( !memory.containsKey(record.get("VAL")) ){
                memory.put(record.get("VAL"), new Double(0) );
            }
            double iter = memory.get(record.get("VAL")).doubleValue()+1;
            memory.put(record.get("VAL"), new Double(iter) );
            if( iter > currmax ){
                currmax = iter;
                answer = record.get("VAL");
            }
        }
    }
    public Object result(){
        return answer;
    }
    public HashMap getField(){
        return field;
    }
}
