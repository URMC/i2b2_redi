/**
 * @projectDescription	Data dictionary for i2b2 (controller code).
 * @inherits	i2b2
 * @namespace	i2b2.DataDictionary
 * @author	Curtis Culbertson
 * @version 	2.0
 * ----------------------------------------------------------------------------------------
 * updated 08-28-2015: 	Initial Launch [Curtis Culbertson] 
 * updated 04-07-2016:  Added functionality to display parent node information
 */

i2b2.DataDictionary.Init = function(loadedDiv) {
	// this function is called after the HTML is loaded into the viewer DIV
	
	// register for drag drop events for the following data types: CONCPT
	var op_trgt = {dropTarget:true};
	i2b2.sdx.Master.AttachType('DataDictionary-PRSDROP', 'CONCPT', op_trgt);	
	// route event callbacks to a single drop event handler used by this plugin
	var eventRouterFunc = (function(sdxData) { i2b2.DataDictionary.doDrop(sdxData); });
	i2b2.sdx.Master.setHandlerCustom('DataDictionary-PRSDROP', 'CONCPT', 'DropHandler', eventRouterFunc);

	// manage YUI tabs
	var cfgObj = {activeIndex : 0};
	this.yuiTabs = new YAHOO.widget.TabView("DataDictionary-TABS", cfgObj);
};


i2b2.DataDictionary.Unload = function() {
	// this function is called before the plugin is unloaded by the framework
	return true;
};

i2b2.DataDictionary.doDrop = function(sdxData) {    
	sdxData = sdxData[0];	// only interested in first record
	// save the info to our local data model
	i2b2.DataDictionary.model.currentRec = sdxData;
	// let the user know that the drop was successful by displaying the name of the object
	$("DataDictionary-PRSDROP").innerHTML = i2b2.h.Escape(sdxData.sdxInfo.sdxDisplayName);
	// optimization to prevent requerying the hive for new results if the input dataset has not changed
	i2b2.DataDictionary.model.dirtyResultsData = true;	
        
        i2b2.DataDictionary.model.xmlOrig = [];
        var currentNode = sdxData.origData;
        
        // Recurse upward through nodes, requerying XML for comments and adding to the xmlOrig array
        // Stops when there is no parent defined
        while(currentNode !== undefined) {
            var nextNode = currentNode.parent;
                        
            var cdetails = i2b2.ONT.ajax.GetTermInfo(
                "CRC:QueryTool",
                {
                        concept_key_value:currentNode.key,
                        ont_synonym_records: true,
                        ont_hidden_records: true
                }
            );

            //ask the system if there is anything to see.
            var c = i2b2.h.XPath(cdetails.refXML, 'descendant::concept');
            if (c.length > 0) {
                i2b2.DataDictionary.model.xmlOrig[currentNode.level-1] = c[0];
            }
            
            // Set the parent node as the current node
            currentNode = nextNode;
        }
        
        // Only start if we have a data record
        if (i2b2.DataDictionary.model.currentRec) { 
                // Gather statistics only if we have data
                if (i2b2.DataDictionary.model.dirtyResultsData) {
                        // Recalculate the results only if the input data has changed
                        i2b2.DataDictionary.getResults();
                }
        }
}


i2b2.DataDictionary.getResults = function() {
	// Refresh the display with comment of the SDX record that was DragDropped
	if (i2b2.DataDictionary.model.dirtyResultsData) {
		//var dropRecord = i2b2.DataDictionary.model.currentRec;
		$$("DIV#DataDictionary-mainDiv DIV#DataDictionary-TABS DIV.results-directions")[0].hide();
		$$("DIV#DataDictionary-mainDiv DIV#DataDictionary-TABS DIV.results-finished")[0].show();		
		var sdxDisplay = $$("DIV#DataDictionary-mainDiv DIV#DataDictionary-InfoSDX")[0];
                
                // Default value to be displayed
                var xmlComment = "The selected concept has no information available for display.";
                
                // Loop over all comments, starting with the terminal node
                for (i = i2b2.DataDictionary.model.xmlOrig.length-1; i >= 0 ; i--) {
                     
                    if(i == i2b2.DataDictionary.model.xmlOrig.length-1) {
                        // If this is the terminal node, make sure to overwrite the contents of the xmlComment
                        xmlComment = i2b2.h.getXNodeVal(i2b2.DataDictionary.model.xmlOrig[i],'comment',true) !== undefined ? i2b2.h.getXNodeVal(i2b2.DataDictionary.model.xmlOrig[i],'comment',true) : "The selected concept has no information available for display.";
                    } else {
                        // Otherwise prepend the xmlComment with this level's comment
                        xmlComment = (i2b2.h.getXNodeVal(i2b2.DataDictionary.model.xmlOrig[i],'comment',true) !== undefined ? i2b2.h.getXNodeVal(i2b2.DataDictionary.model.xmlOrig[i],'comment',true) : "") + xmlComment;
                    }
                }
                
		Element.select(sdxDisplay, '.originalXML')[0].innerHTML = xmlComment;
	}
	
	// optimization - only requery when the input data is changed
	i2b2.DataDictionary.model.dirtyResultsData = false;		
}
