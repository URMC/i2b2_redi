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

/**
 * This script is the base of the operations and provides the main method of
 * communicating with the database to store the data from mapping.
 *
 * The key thing to note is the careful interchange of ID numbers that is being
 * passed from the PHP side of the house, as a 5 value, underscore separated
 * value. The order is as such:
 *
 * VALUE      : items[0],
 * RANDOM     : items[1],
 * SYSID      : items[2],
 * ENTERORDER : items[3],
 * ITEMID     : items[4]
 *
 * The code goes in reverse order so that if we needed to add another value we could add it
 * to the front of the list, and to avoid the possibility that we had a variable name
 * being used with an underscore by accident :)
 */
 i2b2.ExportRedCAP.DEBUG_GetPropertyList = function(object)
 {
	var propertyList = "";

	for (var thisPropertyName in object) {
		propertyList += thisPropertyName + '\n';
	}

	return propertyList;
 }

i2b2.ExportRedCAP.SessionCode = "";
i2b2.ExportRedCAP.CurrentForm="";
i2b2.ExportRedCAP.sd = 0;
i2b2.ExportRedCAP.refXML = 0;
i2b2.ExportRedCAP.sdxData = 0;

/**
 * This method is invoked when the tabs are changed. This is set up in the initialize function.
 */
i2b2.ExportRedCAP.ResultsTabSelected = function(ev)
{
	if( ev.newValue.get('id')=='ExportRedCAP-TAB0' ){
		jQuery( "#ExportRedCAP-APISync" ).load(
			'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_syncstatus.php',
			{SESSIONCODE : i2b2.ExportRedCAP.SessionCode }
		);
	}
	if( ev.newValue.get('id')=='ExportRedCAP-TAB1' ){
		jQuery( "#ExportRedCAP-Controller" ).load(
			'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_servicecontroller.php',
			{SESSIONCODE : i2b2.ExportRedCAP.SessionCode }
		);
	}
	if( ev.newValue.get('id')=='ExportRedCAP-TAB2' ){
		jQuery( "#ExportRedCAP-Tester" ).load(
			'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_testingcontroller.php',
			{SESSIONCODE : i2b2.ExportRedCAP.SessionCode }
		);
	}
	if( ev.newValue.get('id')=='ExportRedCAP-TAB3' ){
		jQuery( "#ExportRedCAP-Mapper" ).load(
			'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_mapper.php',
			{SESSIONCODE : i2b2.ExportRedCAP.SessionCode, FORM_NAME: i2b2.ExportRedCAP.CurrentForm }
		);
	}
	if( ev.newValue.get('id')=='ExportRedCAP-TAB4' ){
		jQuery( "#ExportRedCAP-Sender" ).load(
			'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_sendcontroller.php',
			{SESSIONCODE : i2b2.ExportRedCAP.SessionCode }
		);
	}

	var z = $('anaPluginViewFrame').getHeight() - 34;
	var mainContentDivs = $$('DIV#ExportRedCAP-TABS DIV.ExportRedCAP-MainContent');
	for (var i = 0; i < mainContentDivs.length; i++)
	{
		mainContentDivs[i].style.height = z;
	}
}

/**
 * This method is invoked by the i2b2 plugin loader and this sets up the variables used, and the panes
 */
i2b2.ExportRedCAP.Init = function(loadedDiv)
{

	// Init global vars

	// Manage YUI tabs
	this.yuiTabs = new YAHOO.widget.TabView("ExportRedCAP-TABS", {activeIndex:0});
	this.yuiTabs.on('activeTabChange', function(ev) { i2b2.ExportRedCAP.ResultsTabSelected(ev) } );

	//load things now that we know who and what project we are.
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_datasource.php",
			data: {
				PROJECTCODE  : i2b2.PM.model.login_project,
				USERNAME     : i2b2.PM.model.login_username,
				TOKEN        : i2b2.PM.model.login_password
			},
			type: "POST"
		}
	).done( function( json ){
		if( json ){
			obj = JSON.parse(json);
			if( obj ){

				//lastly fetch the encoded validation code that has the project code, project id encoded within.
				i2b2.ExportRedCAP.SessionCode = obj.SESSIONCODE;

				//did not want to code the sync functionality into the landing page, so we're telling that DIV to
				//grab it's data.
				jQuery( "#ExportRedCAP-APISync" ).load(
					'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_syncstatus.php',
					{SESSIONCODE : i2b2.ExportRedCAP.SessionCode }
				);
			}
		}
	});




}

i2b2.ExportRedCAP.Unload = function()
{
	return true;
}

/**
 * When the drop down is selected, this is how it saves the data.
 */
i2b2.ExportRedCAP.AggrModified= function(divId){
	items = divId.split("_");
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_updateaggr.php",
			data: {
				SESSIONCODE: i2b2.ExportRedCAP.SessionCode ,
				ENTERORDER : items[items.length-2],
				ITEMID     : items[items.length-1],
				VALUE      : jQuery("#"+divId).val(),
				ATTRIBUTE  : items[1]
			},
			type: "POST"
		}
	);

}

/**
 * This function is invoked when a concept is dropped into boxes in generated by PHP. Since the
 * state of what box it gets dropped into has to be remembered, this function gets the ID number
 * and utilizes that to compute what options, etc belong to this concept.
 */
i2b2.ExportRedCAP.ConceptDropped = function(sdxData,divId){
	sdxData = sdxData[0];	// Consider first record only
	i2b2.ExportRedCAP.sdxData = sdxData;

	items = divId.split("_");
	var cdetails = 0;
	var modifier = '';

	if( sdxData.origData.isModifier ){

		var cdetails = i2b2.ONT.ajax.GetModifierInfo(
			"CRC:QueryTool",
			{
				modifier_applied_path:sdxData.origData.applied_path,
				modifier_key_value:sdxData.origData.key,
				ont_synonym_records: true,
				ont_hidden_records: true
			}
		);

		modifier = sdxData.origData.basecode;


		var c = i2b2.h.XPath(cdetails.refXML, 'descendant::modifier');
		if (c.length > 0) {
			sdxData.origData.xmlOrig = c[0];
		}



	} else {

		var cdetails = i2b2.ONT.ajax.GetTermInfo(
			"CRC:QueryTool",
			{
				concept_key_value:sdxData.origData.key,
				ont_synonym_records: true,
				ont_hidden_records: true
			}
		);

		//ask the system if there is anything to see.
		var c = i2b2.h.XPath(cdetails.refXML, 'descendant::concept');
		if (c.length > 0) {
			sdxData.origData.xmlOrig = c[0];
		}
	}

	var mdnodes = i2b2.h.XPath(sdxData.origData.xmlOrig, 'descendant::metadataxml/ValueMetadata[Version]');
	if (mdnodes.length > 0) {
		i2b2.ExportRedCAP.refXML = mdnodes[0];
		this.setupLabSelect(mdnodes[0]);
		this.showLabSelect();
	} else {
		i2b2.ExportRedCAP.refXML = 0;
		jQuery.ajax({
			url : "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_concept_ins.php",
			data: {
				SESSIONCODE: i2b2.ExportRedCAP.SessionCode ,
				ENTERORDER  : items[items.length-2],
				RANDOM      : items[items.length-1],
				CONCEPTPATH : (sdxData.origData.isModifier ? sdxData.origData.parent.key  : sdxData.sdxInfo.sdxKeyValue ),
				CONCEPTNAME : (sdxData.origData.isModifier ? sdxData.origData.parent.name : sdxData.sdxInfo.sdxDisplayName ),
				AGGRTYPE    : jQuery("#ExportRedCAP_CPTAGGR_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-1]).val(),
				OPTIONS     : jQuery("#ExportRedCAP_OPTIONS_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-1]).val(),
				VALUE       : items[items.length-4],
				MODIFIER    : modifier
			},
			type: "POST"
		}).done( function( html ){
			jQuery("#ExportRedCAP_CONCPT_"+items[items.length-4]+"_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-1]).html(html);
		});

	}

}
/**
 * This function is invoked when a concept clicked for deletion in generated by PHP.
 */
i2b2.ExportRedCAP.ConceptDelete = function(divId){
	items = divId.split("_");
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_concept_del.php",
			data: {
				VALUE      : items[items.length-5],
				RANDOM     : items[items.length-4],
				SESSIONCODE: i2b2.ExportRedCAP.SessionCode ,
				ENTERORDER : items[items.length-2],
				ITEMID     : items[items.length-1]
			},
			type: "POST"
		}
	).done( function( html ){
		jQuery("#ExportRedCAP_CONCPT_"+items[items.length-5]+"_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-4]).html(html);
	});
}

i2b2.ExportRedCAP.showLabSelect = function( ){

	$('REDCapitemLabRange').show();
	this.sd.render(document.body);
	// show the form
	this.sd.show();

}
/**
 * This function sets up the lab select box and adds the listeners.
 */
i2b2.ExportRedCAP.setupLabSelect = function( ){

	if (!this.sd) {
		this.sd = new YAHOO.widget.SimpleDialog("REDCapitemLabRange", {
			zindex: 700,
			width: "600px",
			fixedcenter: true,
			constraintoviewport: true,
			modal: true,
			buttons: [{
				text: "OK",
				isDefault: true,
				handler: i2b2.ExportRedCAP.commitLabSelect
			}, {
				text: "Cancel",
				handler: i2b2.ExportRedCAP.cancelLabSelect
			}]
		});
		YAHOO.util.Event.addListener("REDCapmlvfrmTypeNONE",       "click",  i2b2.ExportRedCAP.changeHandler);
		YAHOO.util.Event.addListener("REDCapmlvfrmTypeFLAG",       "click",  i2b2.ExportRedCAP.changeHandler);
		YAHOO.util.Event.addListener("REDCapmlvfrmTypeVALUE",      "click",  i2b2.ExportRedCAP.changeHandler);
		YAHOO.util.Event.addListener("REDCapmlvfrmOperator",       "change", i2b2.ExportRedCAP.changeHandler);
	}

	//reset the selection box.
	$('REDCapmlvfrmTypeNONE').checked = true;
	this.sd.setHeader("Choose value of "+i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'TestName')+" (Test:"+i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'TestID')+")");
	this.setupLabSelectFlags( i2b2.ExportRedCAP.refXML  );
	this.setupLabSelectValues( i2b2.ExportRedCAP.refXML  );

	//this should reset the view.
	this.changeHandler();

}

/**
 * This function takes the selected concept and builds the high/low boxes into the pick-list.
 */
i2b2.ExportRedCAP.setupLabSelectFlags = function( ){
	//figure out flags.

	var sn = $('REDCapmlvfrmFlagValue');

	//purge old entries.
	while( sn.hasChildNodes() ) { sn.removeChild( sn.lastChild ); }

	try {
		var t = i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'Flagstouse');
		var flags = [];
		if (t) {
			if (t == "A") {
				flags = [{name:'Normal', value:'@'},{name:'Abnormal', value:'A'}];
			} else if (t == "HL") {
				flags = [{name:'Normal', value:'@'},{name:'High', value:'H'},{name:'Low', value:'L'}];
			}

			// insert the flags into the range select control
			for (var i=0; i<flags.length; i++) {
				// ONT options dropdown
				var sno = document.createElement('OPTION');
				sno.setAttribute('value', flags[i].value);
				var snt = document.createTextNode(flags[i].name);
				sno.appendChild(snt);
				sn.appendChild(sno);
			}


		}
	} catch(e) {
	}

	//if there is anything to see, flip the selection section.
	if( sn.hasChildNodes() ){
		Element.show($('REDCapmlvfrmTypeFLAG').parentNode);
	} else {
		Element.hide($('REDCapmlvfrmTypeFLAG').parentNode);
	}

}

/**
 * This function takes the selected concept and if it is an enum, to build the pick-list.
 */
i2b2.ExportRedCAP.setupLabSelectEnums = function( ){

	//figure out flags. Generating items this way is so painful to do manually in JS.
	try {
		var t1 = i2b2.h.XPath(i2b2.ExportRedCAP.refXML,"descendant::EnumValues/Val");

		var sn = $('REDCapmlvfrmEnumValue');
		// clear the drop down
		while( sn.hasChildNodes() ) { sn.removeChild( sn.lastChild ); }

		var t2 = new Array();
		for (var i=0; i<t1.length; i++) {
			if (t1[i].attributes[0].nodeValue != "" ) {
				var name = t1[i].attributes[0].nodeValue;
			} else {
				var name = t1[i].childNodes[0].nodeValue;
			}
			t2[(t1[i].childNodes[0].nodeValue)] = name;

			var sno = document.createElement('OPTION');
			sno.setAttribute('value', (t1[i].childNodes[0].nodeValue));
			var snt = document.createTextNode(name);
			sno.appendChild(snt);
			sn.appendChild(sno);

		}

	} catch(e) {
	}

}

/**
 * This function takes the selected concept and then looks up the data from the XML to extract the select values.
 */
i2b2.ExportRedCAP.setupLabSelectValues = function(  ){
	//figure out flags.
	try {

		var t = i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'DataType');
		switch(t) {
		case "Enum":
			i2b2.ExportRedCAP.setupLabSelectEnums();
			break;
		case "PosFloat":
		case "PosInteger":
		case "Float":
		case "Integer":
			$('REDCapvalueContraintText').innerHTML = "Extracts can be constrained by either a flag set by the source system or by the values themselves.";
			$('REDCapmlvfrmTypeNONE').nextSibling.nodeValue = "Data As-Is";
			$('REDCapmlvfrmTypeVALUE').nextSibling.nodeValue = "By Value";
			break;

		case "String":
		case "largestring":
			$('REDCapvalueContraintText').innerHTML = "You are allowed to search within the narrative text associated with the term " + i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'TestName');
			this.sd.setHeader("Search within the "+i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'TestName'));
			$('REDCapmlvfrmTypeNONE').nextSibling.nodeValue = "Save Data As-Is";
			$('REDCapmlvfrmTypeVALUE').nextSibling.nodeValue = "Search within Text";
			break;
		}

		// update the unit drop downs, grab all available units that are possible. This here is the magic - it tells the system
		// go and make another request to the CRC cell to force it to get the rest of the infomation from the metadata tables.
		var t = i2b2.h.XPath(i2b2.ExportRedCAP.refXML,"descendant::UnitValues/descendant::text()[parent::NormalUnits or parent::EqualUnits or parent::Units]");

		var sn = $('REDCapmlvfrmUnits');
		// clear the drop down
		while( sn.hasChildNodes() ) { sn.removeChild( sn.lastChild ); }
		// populate values

		for (var i=0; i<t.length; i++) {
			var sno = document.createElement('OPTION');
			sno.setAttribute('value', t[i].data);
			var snt = document.createTextNode(t[i].data);
			sno.appendChild(snt);
			sn.appendChild(sno);
		}

		//show warning if necessary
		if( t.length == 1 ){
			Element.hide($('REDCapmlvUnitExcluded'));
		} else {
			Element.show($('REDCapmlvUnitExcluded'));
		}

	} catch(e) {
	}

}

i2b2.ExportRedCAP.cancelLabSelect = function( ){
	//don't do anything, no mapping is fine.
	i2b2.ExportRedCAP.sd.hide();
}


/**
 * This function validates the pop up values and then sends it to the PHP page to save.
 */
i2b2.ExportRedCAP.commitLabSelect = function( ){

	var errorMessage = '';
	var finalAssembly = '';
	var modifier = '';

	var sdxData = i2b2.ExportRedCAP.sdxData;
	if( sdxData.origData.isModifier ){
		modifier = sdxData.origData.basecode;
	}

	switch (jQuery("input:radio[name ='REDCapmlvfrmType']:checked").val()) {
	case "BY_VALUE":
		var t = i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'DataType');
		switch(t) {
		case "Enum":
			var selected = jQuery('#REDCapmlvfrmEnumValue').val();
			if( selected.length < 1 ){
				errorMessage = 'You must select a value from the list to filter by flag.\nTo select more than one, press control while clicking';
			} else {
				finalAssembly = "IN\n";
				for (var i=0; i<selected.length; i++) {
					finalAssembly += selected[i] + "\n" ;
				}
			}
			break;
		case "PosFloat":
		case "PosInteger":
		case "Float":
		case "Integer":

			//gather the unit and operator. Unitonly if there is data in the drop down.
			var operator = jQuery("#REDCapmlvfrmOperator").val();
			var unit = $('REDCapmlvfrmUnits').hasChildNodes() ? jQuery("#REDCapmlvfrmUnits").val() : "";
			finalAssembly = operator + "\n";

			if (operator=="BETWEEN") {
				var low = jQuery("#REDCapmlvfrmNumericValueLow").val();
				var high = jQuery("#REDCapmlvfrmNumericValueHigh").val();
				if( isNaN(parseFloat(low)) || !isFinite(low) ){
					errorMessage = "The low range ("+low+") is not a number." ;
				} else if( isNaN(parseFloat(high)) || !isFinite(high) ){
					errorMessage = "The low range ("+high+") is not a number." ;
				} else {
					finalAssembly = operator + "\n" + low + "\n" + high + "\n" + unit ;
				}
			} else {
				var val = jQuery("#REDCapmlvfrmNumericValue").val();
				if( isNaN(parseFloat(val)) || !isFinite(val) ){
					errorMessage = "The item entered ("+val+") is not a number." ;
				} else {
					finalAssembly = operator + "\n" + val + "\n" + unit ;
				}
			}
			break;
		case "largestring":
		case "String":

			//largestring are only contains - the original allows for fancy database language, no allowed here.
			var operator = "LIKE[contains]" ;
			if( t == "String" ){
				operator = jQuery("#REDCapmlvfrmStringOperator").val();
			}
			var val = jQuery("#REDCapmlvfrmStrValue").val();
			if( val.length > 256 ){
				errorMessage = "The text entered ("+val+") is too long." ;
			} else {
				finalAssembly = operator + "\n" + val ;
			}
			break;
		}
		break;
	case "BY_FLAG":
		errorMessage = '';
		finalAssembly = "VALUEFLAG_CD\n" + jQuery("#REDCapmlvfrmFlagValue").val();
		break;
	case "NO_VALUE":
		errorMessage = '';
		finalAssembly = '';
		break;
	} // input:radio[name ='REDCapmlvfrmType']:checked

	if( errorMessage == '' ){
		jQuery.ajax({
			url : "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_concept_ins.php",
			data: {
				SESSIONCODE: i2b2.ExportRedCAP.SessionCode ,
				ENTERORDER  : items[items.length-2],
				RANDOM      : items[items.length-1],
				CONCEPTPATH : (sdxData.origData.isModifier ? sdxData.origData.parent.key  : sdxData.sdxInfo.sdxKeyValue ),
				CONCEPTNAME : (sdxData.origData.isModifier ? sdxData.origData.parent.name : sdxData.sdxInfo.sdxDisplayName ),
				AGGRTYPE    : jQuery("#ExportRedCAP_CPTAGGR_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-1]).val(),
				OPTIONS     : jQuery("#ExportRedCAP_OPTIONS_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-1]).val(),
				VALUE       : items[items.length-4],
				MODIFIER    : modifier,
				FILTERED    : finalAssembly
			},
			type: "POST"
		}).done( function( html ){
			jQuery("#ExportRedCAP_CONCPT_"+items[items.length-4]+"_"+items[items.length-3]+"_"+items[items.length-2]+"_"+items[items.length-1]).html(html);
		});
		i2b2.ExportRedCAP.sd.hide();
	} else {
		alert( errorMessage );
	}


} //i2b2.ExportRedCAP.commitLabSelect

/**
 * This function does the state and skip/show logic within the lab popup.
 */
i2b2.ExportRedCAP.changeHandler = function( e ){

	//jquery so much better....
	switch (jQuery("input:radio[name ='REDCapmlvfrmType']:checked").val()) {
	case "BY_VALUE":
		$('REDCapmlvfrmVALUE').show();
		$('REDCapmlvfrmFLAG').hide();

		// hide all inputs panels
		$('REDCapmlvfrmEnterOperator').hide();
		$('REDCapmlvfrmEnterStringOperator').hide();
		$('REDCapmlvfrmEnterVal').hide();
		$('REDCapmlvfrmEnterVals').hide();
		$('REDCapmlvfrmEnterStr').hide();
		$('REDCapmlvfrmEnterEnum').hide();
		$('REDCapmlvfrmEnterDbOperator').hide();

		//handle the data types that are available.
		switch(i2b2.h.getXNodeVal(i2b2.ExportRedCAP.refXML, 'DataType')) {
		case "PosFloat":
		case "PosInteger":
		case "Float":
		case "Integer":

			$('REDCapmlvfrmEnterOperator').show();
			if (jQuery("#REDCapmlvfrmOperator").val()=="BETWEEN") {
				$('REDCapmlvfrmEnterVals').show();
			} else {
				$('REDCapmlvfrmEnterVal').show();
			}
			break;

		case "largestring":
			$('REDCapmlvfrmEnterStr').show();
			break;
		case "String":
			$('REDCapmlvfrmEnterStringOperator').show();
			$('REDCapmlvfrmEnterStr').show();
			break;
		case "Enum":
			$('REDCapmlvfrmEnterEnum').show();
			break;
		}

		//now handle units - if the select box don't have nothing, no point showing it eh?
		var sn = $('REDCapmlvfrmUnits');
		if( sn.hasChildNodes() ){
			Element.show($('REDCapmlvfrmUnitsContainer'));
		} else {
			Element.hide($('REDCapmlvfrmUnitsContainer'));
		}

		break;



	case "BY_FLAG":
		$('REDCapmlvfrmUnitsContainer').hide();
		$('REDCapmlvfrmVALUE').hide();
		$('REDCapmlvfrmFLAG').show();
		break;
	case "NO_VALUE":
	default:
		$('REDCapmlvfrmUnitsContainer').hide();
		$('REDCapmlvfrmFLAG').hide();
		$('REDCapmlvfrmVALUE').hide();
		break;
	break;
	}

}