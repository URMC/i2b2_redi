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
 */

i2b2.ExportExcel.CurrentForm="";
i2b2.ExportExcel.SessionCode="";
i2b2.ExportExcel.sd = 0;

/**
 * this method switches the UI tab and sends the project when invoked.
 */
i2b2.ExportExcel.ResultsTabSelected = function(ev)
{
	if( ev.newValue.get('id')=='ExportExcel-TAB1' ){
		jQuery( "#ExportExcel-Controller" ).load(
			'js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_servicecontroller.php',
			{SESSIONCODE : i2b2.ExportExcel.SessionCode}
		);
	}

}

/**
 * this method pops up the download dialog box.
 */
i2b2.ExportExcel.showDownload = function( ){
	//offensively create the pop up window.
	if (!this.sd) {
		this.sd = new YAHOO.widget.SimpleDialog("ExcelPassword", {
			zindex: 700,
			width: "500px",
			fixedcenter: true,
			constraintoviewport: true,
			modal: true,
			buttons: [{
				text: "OK",
				isDefault: true,
				handler: i2b2.ExportExcel.commitDownload
			}, {
				text: "Cancel",
				handler: i2b2.ExportExcel.cancelDownload
			}]
		});
		YAHOO.util.Event.addListener("ExcelPasswordField","keyup",(function(e) {
			// anonymous function
			if (e.keyCode==13) {
				i2b2.ExportExcel.commitDownload();
			}
		}));
	}
	this.sd.render(document.body);
	$('ExcelPassword').show();
	// show the form
	this.sd.show();

}

/**
 * this method hides the popup box and clears the entered value.
 */
i2b2.ExportExcel.cancelDownload = function( ){
	jQuery('#ExportExcelRealPasswordField').val( "" );
	i2b2.ExportExcel.sd.hide();
}

/**
 * this method sends the password to the LDAP checker to see if your password is correct.
 */
i2b2.ExportExcel.commitDownload = function( ){
	jQuery.ajax({
		url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_download_auth.php",
		data: {
			SESSIONCODE : i2b2.ExportExcel.SessionCode,
			PASSWORD    : jQuery('#ExcelPasswordField').val()
		},
		type: "POST"
	}).done( function( html ){
		if( html == "Y" ){
			jQuery('#ExportExcelDownload').submit();
			i2b2.ExportExcel.sd.hide();
			jQuery('#ExcelPasswordField').val('');
		} else {
			alert( "Your password was incorrect." );
		}
	});
}

/**
 * this method initializes the view and sets up the functions when tabs are moved, etc.
 */
i2b2.ExportExcel.Init = function(loadedDiv)
{

	//load things now that we know who and what project we are.
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_datasource.php",
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
				i2b2.ExportExcel.SessionCode = obj.SESSIONCODE;

				jQuery( "#ExportExcel-Name" ).html( obj.PROJECTCODE + " - " + obj.PROJECTTITLE );
				if( obj.PATIENT_LIST_DESC ){
					jQuery( "#ExportExcel_PRSDROP" ).html( "<div class='concptItem'>"+obj.PATIENT_LIST_DESC+"</div>" );
				} else {
					jQuery( "#ExportExcel_PRSDROP" ).html( "<div class='concptItem'>Drop Patient / Encounter Set In Here</div>" );
				}

				jQuery( "#ExportExcel_CONCPT" ).html( "<div class='concptItem'>Drag a concepts or modifier here.</div>" );


				//call the part on the page that shows the table of selected fields.
				jQuery.ajax(
					{
						url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_concept_refresh.php",
						data: {
							SESSIONCODE : i2b2.ExportExcel.SessionCode
						},
						type: "POST"
					}
				).done( function( html ){
					jQuery("#ExportExcel_CONCPTS").html(html);
				});


				//add listeners to trigger when items are dropped. Note that the PHP side has already created
				//the DIVs for the items to land on, and those boxes will not change.
				//this was moved here so that if the plugin isn't configured, drag and drop doesn't work.
				var op_trgt = {dropTarget:true};
				i2b2.sdx.Master.AttachType('ExportExcel_CONCPT', 'CONCPT', op_trgt);
				i2b2.sdx.Master.setHandlerCustom('ExportExcel_CONCPT', 'CONCPT', 'DropHandler', i2b2.ExportExcel.ConceptDropped );

				i2b2.sdx.Master.AttachType('ExportExcel_PRSDROP', 'PRS', op_trgt);
				i2b2.sdx.Master.setHandlerCustom('ExportExcel_PRSDROP', 'PRS', 'DropHandler', i2b2.ExportExcel.PatientRecordSetDropped );

				i2b2.sdx.Master.AttachType('ExportExcel_PRSDROP', 'ENS', op_trgt);
				i2b2.sdx.Master.setHandlerCustom('ExportExcel_PRSDROP', 'ENS', 'DropHandler', i2b2.ExportExcel.PatientRecordSetDropped );

			}
		}
	});

	//set the username in question:
	jQuery("#ExportExcel-Username").html( i2b2.PM.model.login_username );


	// Manage YUI tabs
	this.yuiTabs = new YAHOO.widget.TabView("ExportExcel-TABS", {activeIndex:0});
	this.yuiTabs.on('activeTabChange', function(ev) { i2b2.ExportExcel.ResultsTabSelected(ev) } );

	// Fix IE scrollbar problem (thanks to Wayne Chan...)
	var z = $('anaPluginViewFrame').getHeight() - 34;
	var mainContentDivs = $$('DIV#ExportExcel-TABS DIV.ExportExcel-MainContent');
	for (var i = 0; i < mainContentDivs.length; i++)
	{
		mainContentDivs[i].style.height = z;
	}


}

/**
 * this method saves the data when a patient or encounter set is dropped.
 */
i2b2.ExportExcel.PatientRecordSetDropped = function(sdxData)
{
	sdxData = sdxData[0];	// only interested in first record

	// let the user know that the drop was successful by displaying the name of the patient set
	$("ExportExcel_PRSDROP").innerHTML = "<a class='concptItem'>"+i2b2.h.Escape(sdxData.sdxInfo.sdxDisplayName)+"</a>";

	// temporarly change background color to give GUI feedback of a successful drop occuring
	$("ExportExcel_PRSDROP").style.background = "#CFB";

	// optimization to prevent requerying the hive for new results if the input dataset has not changed
	setTimeout("$('ExportExcel_PRSDROP').style.background='#DEEBEF'", 250);

	// call the page to save it.
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_ptlist_upd.php",
			data: {
				SESSIONCODE : i2b2.ExportExcel.SessionCode,
				CONCEPTPATH : sdxData.sdxInfo.sdxKeyValue,
				CONCEPTNAME : sdxData.sdxInfo.sdxDisplayName
			},
			type: "POST"
		}
	)
}

/**
 * this method saves the data when a concept is selected.
 */
i2b2.ExportExcel.ConceptDropped = function(sdxData,divId)
{
	sdxData = sdxData[0];	// Consider first record only

	items = divId.split("_");
	if( sdxData.origData.isModifier ){
		jQuery.ajax(
			{
				url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_concept_ins.php",
				data: {
					SESSIONCODE : i2b2.ExportExcel.SessionCode,
					CONCEPTPATH : sdxData.origData.parent.key,
					CONCEPTNAME : sdxData.origData.parent.name + " ("+sdxData.origData.name+")",
					MODIFIER_CD : sdxData.origData.basecode
				},
				type: "POST"
			}
		).done( function( html ){
			jQuery("#ExportExcel_CONCPTS").html(html);
		});
	} else {
		jQuery.ajax(
			{
				url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_concept_ins.php",
				data: {
					SESSIONCODE : i2b2.ExportExcel.SessionCode,
					CONCEPTPATH : sdxData.sdxInfo.sdxKeyValue,
					CONCEPTNAME : sdxData.sdxInfo.sdxDisplayName
				},
				type: "POST"
			}
		).done( function( html ){
			jQuery("#ExportExcel_CONCPTS").html(html);
		});
	}

}

/**
 * this method is a generic for any items that need to be edited, say the drop down options to save to database.
 */
i2b2.ExportExcel.ConceptEditSettings = function(field,id,variable){
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_concept_edit.php",
			data: {
				SESSIONCODE : i2b2.ExportExcel.SessionCode,
				FIELD      : id,
				VARIABLE   : variable,
				VALUE      : field.value
			},
			type: "POST"
		}
	).done( function( html ){
		jQuery("#ExportExcel_CONCPTS").html(html);
	});
}

/**
 * this removes the concept from tree.
 */
i2b2.ExportExcel.ConceptDelete = function(divId){
	items = divId.split("_");
	jQuery.ajax(
		{
			url : "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_concept_del.php",
			data: {
				SESSIONCODE : i2b2.ExportExcel.SessionCode,
				ITEMID     : items[items.length-1]
			},
			type: "POST"
		}
	).done( function( html ){
		jQuery("#ExportExcel_CONCPTS").html(html);
	});
}

