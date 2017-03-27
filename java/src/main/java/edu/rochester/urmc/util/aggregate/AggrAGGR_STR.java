package edu.rochester.urmc.util.aggregate;

import edu.rochester.urmc.util.AggregateOnePass;

import java.util.Date;
import java.util.HashMap;

public class AggrAGGR_STR implements AggregateOnePass {
    
    String ans = "";
    HashMap field = null;
    Object levelcd = null;
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
            ans += "" + record.get("CONCEPTS") + " \t" + record.get("VAL") + "\n";
        }
    }
    public Object result(){
        return ans;
    }
    public HashMap getField(){
        return field;
    }
}
