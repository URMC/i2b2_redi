/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rochester.urmc.i2b2.redcap;

import edu.rochester.urmc.i2b2.SQLQueryJob;
import edu.rochester.urmc.i2b2.SQLQueryJobLogOnce;
import edu.rochester.urmc.util.ClientHTTPRequest;
import edu.rochester.urmc.util.ReadWriteTextFile;
import edu.rochester.urmc.util.SQLUtilities;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class was designed to further insulate the REDCap system from web attack by performing the data pull in Java instead.
 * @author png
 */
public class DataDictionarySync extends SQLQueryJobLogOnce{
    HashMap projectSettings = null;
    
    public SQLQueryJob initialize( Connection cntldb, Connection datadb, HashMap job, Properties settings  ) {
        ClientHTTPRequest.trustAll();
        super.initialize(cntldb, datadb, job, settings);
        try{
            projectSettings = SQLUtilities.getTableHashedArray(cntldb, 
                "SELECT CONTACTEMAIL, REDCAP_APIKEY, REDCAP_URL FROM PROJECTS WHERE PROJECTID='"+project_id+"'"
            )[0];
        } catch ( Exception ex ){
            logError( ex );
        }
        return this;
    }
    
    public void run() {
        
        try{
                
            this.updateStatus("Running",0);
            log("Processing Project " + project_id);
            
            this.updateStatus("Running",1);
            
            parseEvents();
            
            this.updateStatus("Running",33);
            
            boolean phantom = parseFormEventMapping();
            
            this.updateStatus("Running",66);
            
            this.parseFields(phantom);
    
            if( !isCancelled() ){
                this.updateStatus("Complete");
            } else {
                this.updateStatus("Cancelled");
            }

        } catch (Exception ex){
            logError( ex );
        } 
    }
    
    /**
     * This pulls the list of events, parses the XML and inserts into the database.
     * @throws Exception 
     */
    public void parseEvents() throws Exception {
        
        SQLUtilities.execSQL(cntldb, "DELETE FROM PROJECTS_REDCAP_EVENTS WHERE PROJECTID='"+this.project_id+"'");
        
        boolean hasEvent = false;
        Document document = this.communicateWithREDCap("event");
        StringBuilder output = new StringBuilder();
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        
        output.append("Events And Ranges\n<table border=1 cellspacing=0>\n");
		output.append("<tr style='background-color:#d0d0d0'><td>Arm</td><td>Event</td><td>Days From 0</td></tr>\n");
        
        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);

            if (node instanceof Element) {
                if( "item".equals(node.getNodeName()) ){
                    
                    //since it's <item><offset>1</offset><bob>2</bob></item> - all properties in one top sublevel,
                    //flatten all subnodes into a hashmap.
                    HashMap<String,String> line = collapseXML( node, null );
                    
                    //assemble the insert.
                    String vars = "PROJECTID"; String vals = "" + this.project_id;
                    if( line.containsKey("arm_num") ){ 
                        vars += ",arm";    vals += ",'"+line.get("arm_num").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("event_name") ){ 
                        vars += ",event_name";    vals += ",'"+line.get("event_name").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("unique_event_name") ){ 
                        vars += ",unique_event_name";    vals += ",'"+line.get("unique_event_name").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("day_offset") ){ 
                        vars += ",day_offset";    vals += ",'"+line.get("day_offset").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("offset_min") ){ 
                        vars += ",offset_min";    vals += ",'"+line.get("offset_min").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("offset_max") ){ 
                        vars += ",offset_max";    vals += ",'"+line.get("offset_max").replaceAll("'", "''")+"'"; 
                    }
                    SQLUtilities.execSQL(cntldb, "INSERT INTO PROJECTS_REDCAP_EVENTS ("+vars+") VALUES ("+vals+")");
                    output.append("\t<tr>"+
                        "<td>" + line.get("arm_num") + "</td>"+ 
                        "<td>" + line.get("event_name") + " - " + line.get("unique_event_name") + "</td>" + 
                        "<td>" + line.get("day_offset") +"(+" + line.get("offset_min") + "/-" + line.get("offset_max") + ")</td>" +
                        "</tr>\n"
                    );
                    hasEvent = true;
                } else {
                    this.log("Unknown XML entity " + node.getNodeName());
                } //it's an item level tag.
            } //it's an element
        } // all nodes in file.
        output.append("</table>\n");
        log( output.toString() );
        
        if( !hasEvent ){
            SQLUtilities.execSQL(cntldb, 
               "INSERT INTO PROJECTS_REDCAP_EVENTS (projectid,arm,event_name,unique_event_name,day_offset,offset_min,offset_max) VALUES "
                   + "('"+this.project_id+"',0,'default system created event','$SYS$',0,999999,999999)"
            );
        }
    } //parseEvents();
    
    private HashMap<String,String> collapseXML( Node node, HashMap<String, String> line ){
        NodeList attrList = node.getChildNodes();
        if( line == null ){
            line = new HashMap<String,String>();
        }
        for (int j = 0; j < attrList.getLength(); j++) {   
            Node attr = attrList.item(j);
            line.put(attr.getNodeName(), attr.getTextContent().trim());    
        }
        return line;
    }
    
    /**
     * This pulls the list of events, parses the XML and inserts into the database.
     * @throws Exception 
     */
    public boolean parseFormEventMapping() throws Exception {
        boolean answer = false;
        SQLUtilities.execSQL(cntldb, "DELETE FROM PROJECTS_REDCAP_FORMS WHERE PROJECTID='"+this.project_id+"'");
        
        Document document = this.communicateWithREDCap("formEventMapping");
        StringBuilder output = new StringBuilder();
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        
        output.append("Event To Form Mappings\n<table border=1 cellspacing=0>");
		output.append("<tr style='background-color:#d0d0d0'><td>Arm</td><td>Event</td><td>Form</td></tr>");
        
        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);
            if (node instanceof Element && "arm".equals(node.getNodeName()) ){

                NodeList event = node.getChildNodes();
                for (int k = 0; k < event.getLength(); k++) {
                    
                    Node eventnode = event.item(k);
                    if (eventnode instanceof Element && "event".equals(eventnode.getNodeName()) ){

                        NodeList forms = eventnode.getChildNodes();
                        for (int l = 0; l < forms.getLength(); l++) {
                            
                            Node formnode = forms.item(l);
                            if (formnode instanceof Element && "form".equals(formnode.getNodeName()) ){
                                
                                HashMap<String,String> line = collapseXML( node, null );
                                line = collapseXML( eventnode, line );
                                line = collapseXML( formnode, line );
                                
                                //assemble the insert.
                                String vars = "PROJECTID"; String vals = "" + this.project_id;
                                if( line.containsKey("number") ){ 
                                    vars += ",arm";    vals += ",'"+line.get("number").replaceAll("'", "''")+"'"; 
                                }
                                if( line.containsKey("unique_event_name") ){ 
                                    vars += ",event";    vals += ",'"+line.get("unique_event_name").replaceAll("'", "''")+"'"; 
                                }
                                if( line.containsKey("form") ){ 
                                    vars += ",form";    vals += ",'"+line.get("form").replaceAll("'", "''")+"'"; 
                                }
                  
                                SQLUtilities.execSQL(cntldb, "INSERT INTO PROJECTS_REDCAP_FORMS ("+vars+") VALUES ("+vals+")");
                                output.append("\t<tr>"+
                                    "<td>" + line.get("number") + "</td>"+ 
                                    "<td>" + line.get("unique_event_name") + "</td>" + 
                                    "<td>" + line.get("form") + "</td>" +
                                    "</tr>\n"
                                );  
                                answer = true;
                                
                            } //form elements
                        } //forms                        
                    } //event elements
                } //events
                    
            } else if (node instanceof Element && "item".equals(node.getNodeName()) ){
                //CRI-323 - Redcap 6.11 is apparently changed format. YAY.
                HashMap<String,String> line = collapseXML( node, null );
                
                String vars = "PROJECTID"; String vals = "" + this.project_id;
                if( line.containsKey("arm_num") ){ 
                    vars += ",arm";    vals += ",'"+line.get("arm_num").replaceAll("'", "''")+"'"; 
                }
                if( line.containsKey("unique_event_name") ){ 
                    vars += ",event";    vals += ",'"+line.get("unique_event_name").replaceAll("'", "''")+"'"; 
                }
                if( line.containsKey("form") ){ 
                    vars += ",form";    vals += ",'"+line.get("form").replaceAll("'", "''")+"'"; 
                }

                SQLUtilities.execSQL(cntldb, "INSERT INTO PROJECTS_REDCAP_FORMS ("+vars+") VALUES ("+vals+")");
                output.append("\t<tr>"+
                    "<td>" + line.get("arm_num") + "</td>"+ 
                    "<td>" + line.get("unique_event_name") + "</td>" + 
                    "<td>" + line.get("form") + "</td>" +
                    "</tr>\n"
                );  
            } //it's an element
        } // all nodes in file.
        output.append("</table>\n");
        log( output.toString() );
        return answer;
    } //parseEvents();
    
    
    /**
     * This pulls the list of events, parses the XML and inserts into the database.
     * @throws Exception 
     */
    public void parseFields( boolean insertPhantomEvent ) throws Exception {
        
        SQLUtilities.execSQL(cntldb, "DELETE FROM PROJECTS_REDCAP_FIELDS WHERE PROJECTID='"+this.project_id+"'");
        
        Document document = this.communicateWithREDCap("metadata");
        StringBuilder output = new StringBuilder();
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        
        output.append("Form And Field Elements\n<table border=1 cellspacing=0>\n");
		output.append("<tr style='background-color:#d0d0d0'><td>Form</td><td>Field</td><td>Type</td></tr>\n");
        
        HashSet seen = new HashSet();
        
        for (int i = 0; i < nodeList.getLength(); i++) {

            Node node = nodeList.item(i);

            if (node instanceof Element) {
                if( "item".equals(node.getNodeName()) ){
                    
                    //since it's <item><offset>1</offset><bob>2</bob></item> - all properties in one top sublevel,
                    //flatten all subnodes into a hashmap.
                    HashMap<String,String> line = collapseXML( node, null );
                    
                    if( "yesno".equals(line.get("field_type")) ){
                        line.put("select_choices_or_calculations", "1,Yes\\n0,No");
                    }
                    
                    if( "yesno".equals(line.get("truefalse")) ){
                        line.put("select_choices_or_calculations", "1,True\\n0,False");
                    }
                    
                    if( ("" + line.get("field_label")).length() > 512 ){
                        line.put("field_label", line.get("field_label").substring(0,512));
                    }
                    
                    //assemble the insert.
                    String vars = "PROJECTID,ENTERORDER"; String vals = "" + this.project_id +"," + i;
                    if( line.containsKey("field_name") ){ 
                        vars += ",field_name";    vals += ",'"+line.get("field_name").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("form_name") ){ 
                        vars += ",form_name";    vals += ",'"+line.get("form_name").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("section_header") ){ 
                        vars += ",section_header";    vals += ",'"+line.get("section_header").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("field_type") ){ 
                        vars += ",field_type";    vals += ",'"+line.get("field_type").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("field_label") ){ 
                        vars += ",field_label";    vals += ",'"+line.get("field_label").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("field_note") ){ 
                        vars += ",field_note";    vals += ",'"+line.get("field_note").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("select_choices_or_calculations") ){ 
                        vars += ",select_choices_or_calculations";    vals += ",'"+line.get("select_choices_or_calculations").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("text_validation_type_or_show_slider_number") ){ 
                        //waay too long of a variable name.
                        vars += ",text_validation_type";    vals += ",'"+line.get("text_validation_type_or_show_slider_number").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("text_validation_min") ){ 
                        vars += ",text_validation_min";    vals += ",'"+line.get("text_validation_min").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("text_validation_max") ){ 
                        vars += ",text_validation_max";    vals += ",'"+line.get("text_validation_max").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("identifier") ){ 
                        vars += ",identifiers";    vals += ",'"+line.get("identifier").replaceAll("'", "''")+"'"; //identifier is a keyword.
                    }
                    if( line.containsKey("branching_logic") ){ 
                        vars += ",branching_logic";    vals += ",'"+line.get("branching_logic").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("required_field") ){ 
                        vars += ",required_field";    vals += ",'"+line.get("required_field").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("custom_alignment") ){ 
                        vars += ",custom_alignment";    vals += ",'"+line.get("custom_alignment").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("question_number") ){ 
                        vars += ",question_number";    vals += ",'"+line.get("question_number").replaceAll("'", "''")+"'"; 
                    }
                    if( line.containsKey("matrix_group_name") ){ 
                        vars += ",matrix_group_name";    vals += ",'"+line.get("matrix_group_name").replaceAll("'", "''")+"'"; 
                    }
                    SQLUtilities.execSQL(cntldb, "INSERT INTO PROJECTS_REDCAP_FIELDS ("+vars+") VALUES ("+vals+")");
                    
                    if( insertPhantomEvent && !seen.contains(line.get("form_name"))){
                        
                        SQLUtilities.execSQL(cntldb, "INSERT INTO PROJECTS_REDCAP_FORMS (PROJECTID,ARM,EVENT,FORM) VALUES ( " +
                                "'"+this.project_id+"','0','$SYS$','" + line.get("form_name")+ "' )"
                        );
                        seen.add(line.get("form_name"));
                    }
                    
                    output.append("\t<tr>" 
                        + "<td>"+line.get("form_name")+"</td>" 
                        + "<td>"+line.get("field_name")+"</td>" 
                        + "<td>"+line.get("field_type")
                            +( !"".equals( line.get("text_validation_type_or_show_slider_number") ) ? 
                                " (" + line.get("text_validation_type_or_show_slider_number") + ")" : ""
                            ) + "</td>" 
                        + "</tr>\n");
                  

                } //it's an item level tag.
            } //it's an element
        } // all nodes in file.
        output.append("</table>\n");
        log( output.toString() );
        
    } //parseEvents();
    
    private Document communicateWithREDCap( String action ) throws IOException, ParserConfigurationException, SAXException{
        //construct request to talk to redcap
        //$data = array( 'content' => 'event', 'format' => 'xml', 'token' => $redcap_settings->REDCAP_APIKEY );
        HashMap request = new HashMap();
        request.put("token", projectSettings.get("REDCAP_APIKEY"));
        request.put("content", action );
        request.put("format", "xml");
        InputStream is = ClientHTTPRequest.post(new URL(""+projectSettings.get("REDCAP_URL")),request);
        String fromserver = new java.util.Scanner(is).useDelimiter("\\A").next();
        
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream( fromserver.getBytes() ));
        return document;
    }
    
}
