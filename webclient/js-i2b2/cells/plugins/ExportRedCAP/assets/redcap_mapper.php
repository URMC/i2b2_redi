<?php

	echo("<h1>Field Mapping</h1>");
	echo("<p>Use this form to map data between i2b2 concepts and then to your redcap form items. You can drag and drop multiple concepts into a field.
		Every field has an aggregation setting so that you can pick How that is data is to mapped into your study. Note that some options are disabled.
		Please First select the form you want mapped. </p>");

	echo("<p>Note that Radio and Dropdown options are processed in the order in which they are mapped. You will want to start with the most important to least. To assist you, the order in which drop downs are processed are shown with the concept dragged; thus 1: CONCEPT means that this will be processed first.</p>");


	require_once("../config.php");


	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	$javascripts = "";

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"]) ){
		echo( "<h1 style='color:red'>The plugin has not been configured, please contact the i2b2 administrator</h1>" );
	} else {

		$redcap_forms = $DB->get_results( "SELECT DISTINCT FORM_NAME FROM PROJECTS_REDCAP_FIELDS WHERE PROJECTS_REDCAP_FIELDS.PROJECTID='".f($_ENC["PROJECTID"])."'" );
		if( sizeof( $redcap_forms ) > 1 ){
			echo("<h2>Select A Form</h2>");
			echo("<select name='form_name' onChange=\"
				jQuery.ajax({
					url:'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_mapper.php',
					data: {
						FORM_NAME:this.value,
						SESSIONCODE:'".$_POST["SESSIONCODE"]."'
					},
					type:'post'
				}).done(
					function(html){
						jQuery('#ExportRedCAP-Mapper').html(html);
					}
				);\">");

			$formname = f(decryptParameters( "RedCAPExport", $_POST["FORM_NAME"] ));

			echo("<option value=''>Please Select A Form To View</option>");
			foreach ( $redcap_forms as $item) {
				$encformname = encryptParameters( "RedCAPExport", $item->FORM_NAME );
				echo("<option value='".$encformname."'".(f($item->FORM_NAME) == $formname?" selected":"").">".f($item->FORM_NAME)."</option>");
			}
			echo("</select>");
		} else {
			foreach ( $redcap_forms as $item) {
				$formname = $item->FORM_NAME;
			}
		}
		if( $formname ){

			$redcap_fields = $DB->get_results( "
				SELECT A.PROJECTID,
					A.ENTERORDER,
					A.field_name,
					A.form_name ,
					A.field_type,
					A.field_label,
					A.text_validation_type,
					A.SELECT_CHOICES_OR_CALCULATIONS,
					MIN( PROJECTS_REDCAP_FIELDS_MAPPING.AGGR_TYPE ) AS AGGR_SETTING
				FROM (
					SELECT * FROM PROJECTS_REDCAP_FIELDS
					WHERE PROJECTS_REDCAP_FIELDS.PROJECTID='".$_ENC["PROJECTID"]."' AND
						  FORM_NAME='".f($formname)."'
				) A LEFT JOIN PROJECTS_REDCAP_FIELDS_MAPPING
				ON (
					A.PROJECTID=PROJECTS_REDCAP_FIELDS_MAPPING.PROJECTID AND
					A.field_name=PROJECTS_REDCAP_FIELDS_MAPPING.field_name AND
					A.form_name=PROJECTS_REDCAP_FIELDS_MAPPING.form_name
				)
				GROUP BY A.PROJECTID,
					A.ENTERORDER,
					A.field_name,
					A.form_name ,
					A.field_type,
					A.field_label,
					A.text_validation_type,
					A.SELECT_CHOICES_OR_CALCULATIONS
				ORDER BY ENTERORDER
			"  ,OBJECT );

			echo("<script>i2b2.ExportRedCAP.CurrentForm='".$_POST["FORM_NAME"]."';</script>");
			echo("<h3>Forms And Field Definitions</h3>");
			echo("<table border=1 cellspacing=0>");
			echo("<tr style='background-color:#d0d0d0'><th>Drag&Drop Mapping</th><th>Aggregation Option / Selection</th><th>Form, Field, Type</th><th>Options/Test</th></tr>");

			$javascripts = "";
			$random = rand( 0, 10000 );
			foreach ( $redcap_fields as $item) {

				//thanks redcap 1.10.
				$item->SELECT_CHOICES_OR_CALCULATIONS = str_replace('|',"\\n",$item->SELECT_CHOICES_OR_CALCULATIONS);
				$dropdownvalues = explode("\\n",$item->SELECT_CHOICES_OR_CALCULATIONS);
				$itemnuminradio = 0;
				foreach ( $dropdownvalues as $values ) {

					$radiovalpair = explode(",",trim($values),2);
					$itemnuminradio ++;
					if( $item->FIELD_TYPE == 'text' || $item->FIELD_TYPE == 'notes' || $item->FIELD_TYPE == 'radio' || $item->FIELD_TYPE == 'dropdown' || $item->FIELD_TYPE == 'radio' || $item->FIELD_TYPE == 'yesno'|| $item->FIELD_TYPE == 'checkbox'|| $item->FIELD_TYPE == 'truefalse'){

						echo("<tr>");
						echo("<td bgcolor='#deebef'>");

								echo("<div id='ExportRedCAP_CONCPT_".$radiovalpair[0]."_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."' class='droptrgt SDX-CONCPT' >");

								$mappings = $DB->get_results( "
									SELECT * FROM PROJECTS_REDCAP_FIELDS_MAPPING
									WHERE FIELD_NAME='".f($item->FIELD_NAME)."' AND
										FORM_NAME='".f($item->FORM_NAME)."' AND
										PROJECTID='".f($_ENC["PROJECTID"])."' AND ".
										($radiovalpair[0] == "" ? "VALUE IS NULL" : "VALUE='".f($radiovalpair[0])."'")."
									ORDER BY SYSID
								"  ,OBJECT );



								$counter = 0;
								$aggr_type = "";
								if( $mappings ){
									foreach ( $mappings as &$map) {
										if( $counter > 0 ){
											echo("<div class='concptDiv'></div>");
										}
										echo("<a class='concptItem'
												href=\"JavaScript:i2b2.ExportRedCAP.ConceptDelete(
													'ExportRedCAP_dynamic_".$radiovalpair[0]."_".$random."_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$map->SYSID."'
												);\"
											  >".($radiovalpair[0]==""?"":$map->ORDNUNG.": ").htmlspecialchars($map->CONCEPTNAME).
												($map->MODIFIER != '' ? ' ('.htmlspecialchars($map->MODIFIER).')' : '' ).
												($map->FILTERED != '' ? ' ['.htmlspecialchars(str_replace("\n",' ',$map->FILTERED)).']' : '' ).
											"</a>"
										);
										$aggr_type = $map->AGGR_TYPE;
										$counter++;
									}
								} else {
									echo("<div class='concptItem'>&nbsp;</div>");
								}
								echo("</div>");

						echo("</td>");

						echo("<td bgcolor='#deebef'>");

							if($item->FIELD_TYPE == 'text' || $item->FIELD_TYPE == 'notes' ){

								echo("<select id='ExportRedCAP_CPTAGGR_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."' onChange='i2b2.ExportRedCAP.AggrModified(this.id);'>");

								echo( "<option value='None'>&nbsp;</option>" );
								//if(strstr($item->TEXT_VALIDATION_TYPE,"date") === FALSE){
									echo( "<option value='VFIRST' ".($aggr_type=='VFIRST'?"selected":"").">Value, First Occurance</option>" );
									echo( "<option value='VLAST' ".($aggr_type=='VLAST'?"selected":"").">Value, Most Recent</option>" );
									echo( "<option value='VMODE' ".($aggr_type=='VMODE'?"selected":"").">Value, Mode</option>" );
								//}
								//if( $item->TEXT_VALIDATION_TYPE == 'integer' || $item->TEXT_VALIDATION_TYPE == 'number' || $item->TEXT_VALIDATION_TYPE == 'email' || $item->TEXT_VALIDATION_TYPE == 'phone' || strstr($item->TEXT_VALIDATION_TYPE,"date") !== FALSE ){
									//cant have aggrstr for # and known data, like dates.
									//cant shove a date into a #
								//} else {
									echo( "<option value='AGGR_STR' ".($aggr_type=='AGGR_STR'?"selected":"").">ALL Values, Delimited.</option>" );
									echo( "<option value='AGGR_HISTOGRAM' ".($aggr_type=='AGGR_HISTOGRAM'?"selected":"").">ALL Values, Histogram.</option>" );
									echo( "<option value='AGGR_STR_N_DATE' ".($aggr_type=='AGGR_STR_N_DATE'?"selected":"").">ALL Values & Dates, Delimited.</option>" );
									echo( "<option value='DFIRST' ".($aggr_type=='DFIRST'?"selected":"").">Date, First Occurance</option>" );
									echo( "<option value='DLAST' ".($aggr_type=='DLAST'?"selected":"").">Date, Most Recent</option>" );
								//}
								//if( $item->TEXT_VALIDATION_TYPE == 'integer' || $item->TEXT_VALIDATION_TYPE == 'number' || $item->TEXT_VALIDATION_TYPE == '' ){
									echo( "<option value='VAVG' ".($aggr_type=='VAVG'?"selected":"").">Numeric Value, Average</option>" );
									echo( "<option value='VMIN' ".($aggr_type=='VMIN'?"selected":"").">Numeric Value, Minimum</option>" );
									echo( "<option value='VMAX' ".($aggr_type=='VMAX'?"selected":"").">Numeric Value, Maximum</option>" );
									echo( "<option value='DMIN' ".($aggr_type=='DMIN'?"selected":"").">Date, Smallest Numeric Value</option>" );
									echo( "<option value='DMAX' ".($aggr_type=='DMAX'?"selected":"").">Date, Largest Numeric Value</option>" );
								//}

								//if( $item->TEXT_VALIDATION_TYPE == 'email' || $item->TEXT_VALIDATION_TYPE == 'phone' || strstr($item->TEXT_VALIDATION_TYPE,"date") !== FALSE ){
									//cant shove dates or counts into a email or date field.
								//} else {
									echo( "<option value='DFIRST' ".($aggr_type=='DFIRST'?"selected":"").">Date, First Occurance</option>" );
									echo( "<option value='DLAST' ".($aggr_type=='DLAST'?"selected":"").">Date, Most Recent</option>" );
									echo( "<option value='D2ND'  ".($aggr_type=='D2ND'    ?"selected":"").">Date, Second Occurance</option>" );
									echo( "<option value='D3RD'  ".($aggr_type=='D3RD'    ?"selected":"").">Date, Third Occurance</option>" );
									echo( "<option value='D4TH'  ".($aggr_type=='D4TH'    ?"selected":"").">Date, Fourth Occurance</option>" );
									echo( "<option value='D5TH'  ".($aggr_type=='D5TH'    ?"selected":"").">Date, Fifth Occurance</option>" );
									echo( "<option value='DE2ND' ".($aggr_type=='DE2ND'   ?"selected":"").">Date, Second From End</option>" );
									echo( "<option value='DE3RD' ".($aggr_type=='DE3RD'   ?"selected":"").">Date, Third From End</option>" );
									echo( "<option value='DE4TH' ".($aggr_type=='DE4TH'   ?"selected":"").">Date, Fourth From End</option>" );
									echo( "<option value='DE5TH' ".($aggr_type=='DE5TH'   ?"selected":"").">Date, Fifth From End</option>" );
									echo( "<option value='COUNT' ".($aggr_type=='COUNT'?"selected":"").">Count of Observations</option>" );
								//}
								//if(strstr($item->TEXT_VALIDATION_TYPE,"date") === FALSE){
									echo( "<option value='V2ND' ".($aggr_type=='V2ND'?"selected":"").">Value, Second Occurance</option>" );
									echo( "<option value='V3RD' ".($aggr_type=='V3RD'?"selected":"").">Value, Third Occurance</option>" );
									echo( "<option value='V4TH' ".($aggr_type=='V4TH'?"selected":"").">Value, Fourth Occurance</option>" );
									echo( "<option value='V5TH' ".($aggr_type=='V5TH'?"selected":"").">Value, Fifth Occurance</option>" );
									echo( "<option value='VE2ND' ".($aggr_type=='VE2ND'?"selected":"").">Value, Second From End</option>" );
									echo( "<option value='VE3RD' ".($aggr_type=='VE3RD'?"selected":"").">Value, Third From End</option>" );
									echo( "<option value='VE4TH' ".($aggr_type=='VE4TH'?"selected":"").">Value, Fourth From End</option>" );
									echo( "<option value='VE5TH' ".($aggr_type=='VE5TH'?"selected":"").">Value, Fifth From End</option>" );
								//}


								echo("</select>");

							} else {

								//need to have a blank here so that we don't explode when we get to the insert.
								echo("<input type='hidden' value='PRESENCE' id='ExportRedCAP_CPTAGGR_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."'>");
								echo( htmlspecialchars($values) );
							}
						echo("</td>");

						$javascripts .= "\n\t\t
							i2b2.sdx.Master.AttachType(
								'ExportRedCAP_CONCPT_".$radiovalpair[0]."_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."',
								'CONCPT',
								op_trgt
							);";

						$javascripts .= "\n\t\t
							i2b2.sdx.Master.setHandlerCustom(
								'ExportRedCAP_CONCPT_".$radiovalpair[0]."_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."',
								'CONCPT',
								'DropHandler',
								function(sdxData){
									i2b2.ExportRedCAP.ConceptDropped(
										sdxData,
										'ExportRedCAP_CONCPT_".$radiovalpair[0]."_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."'
									);
								}
							);";

						if( sizeof( $dropdownvalues ) == 0 || $itemnuminradio == 1 ){
							echo("<td title='".str_replace("'","`",htmlspecialchars($item->FIELD_LABEL) )."'");
							if( sizeof( $dropdownvalues ) > 0 ){
								echo( "rowspan=".sizeof( $dropdownvalues ) );
							}
							echo(">");
							echo("<span style='font-size: 1.6em; font-weight:strong;color:#000044'>".htmlspecialchars($item->FIELD_NAME)."</span><br>");
							//echo("<span style='font-size:.8em;color:#666666'><b>".$item->FORM_NAME."</b></span><br>");
							echo("<span style='font-size:.8em;color:#666666'>".
								 	htmlspecialchars($item->FIELD_TYPE).
								 	($item->TEXT_VALIDATION_TYPE != "" ? ("(".htmlspecialchars($item->TEXT_VALIDATION_TYPE).")") : "" ).
								 	"<br>". htmlspecialchars($item->FIELD_LABEL) .
								"</span>"
							);
							echo("</td>");
						}

						if( sizeof( $dropdownvalues ) == 0 || $itemnuminradio == 1 ){
							echo("<td ");
							if( sizeof( $dropdownvalues ) > 0 ){
								echo( "rowspan=".sizeof( $dropdownvalues ) );
							}
							echo("><input type=\"button\" onClick=\"
									i2b2.ExportRedCAP.yuiTabs.set('activeIndex',3);
									jQuery('#ExportRedCAP-Tester').load(
										'js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_testingcontroller.php',
										{SESSIONCODE : '".$_POST["SESSIONCODE"]."', SETFIELD : '".htmlspecialchars($item->FIELD_NAME)."', ACTION : 'Start' }
									);
								\" value=\"Test\">");

							echo("<input type=\"button\" onClick=\"

								before = prompt( 'How many Days to go before? (If you enter -5, it will override the standard date lookup and lookup 5 days before this visit. If you enter 12/25/2001, it will look up from Christmas 2001 onward.');
								if( Date.parse( before ) || (!isNaN(parseFloat(before)) && isFinite(before)) ){

									after = prompt( 'How many Days to go after? (If you enter 5, it will override the standard date lookup and lookup 5 days after this visit. If you enter 12/25/2001, it will look no further than Christmas 2001.');
									if( Date.parse( after ) || (!isNaN(parseFloat(after)) && isFinite(after)) ){

										jQuery('#ExportRedCAP_OPTIONS_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."').val('before=' + before +',after='+after );
										jQuery('#ExportRedCAP_OPTIOND_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."').html('before=' + before +'<br>after='+after );

										i2b2.ExportRedCAP.AggrModified('ExportRedCAP_OPTIONS_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."');
									}
								}

							\" value=\"Options\">");
							$option = "";
							if( sizeof($mappings) ){
								 $option = $mappings[0]->OPTIONS ;
							}

							echo("<input type='hidden' id='ExportRedCAP_OPTIONS_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."' value='".htmlspecialchars($option) ."'>");
							echo("<div id='ExportRedCAP_OPTIOND_".$_ENC["PROJECTID"]."_".$item->ENTERORDER."_".$random."'>".str_replace(',','<br>',htmlspecialchars($option)) ."</div>");
							echo("</td>");
						}
					} else {
						echo("<td>this type cannot be mapped using this tool.</td><td>&nbsp;</td><td>");
						echo("<span style='font-size: 1.6em; font-weight:strong;color:#000044'>".$item->FIELD_NAME."</span><br>");
						//echo("<span style='font-size:.8em;color:#666666'><b>".$item->FORM_NAME."</b></span><br>");
						echo("<span style='font-size:.8em;color:#666666'>".htmlspecialchars($item->FIELD_TYPE). ($item->TEXT_VALIDATION_TYPE != "" ? ("(".htmlspecialchars($item->TEXT_VALIDATION_TYPE).")") : "" )."</span>");
						echo("</td><td></td>");
					}
				}
				echo("</tr>");
			}
			echo("</table>");
		}
		echo("<div id='ExportRedCAP_CONCPT_status'></div>");

	}
?>

<script>
jQuery( document ).ready(function() {
	// Register DIV as valid drag&drop target for Patient Record Set (PRS) objects
	var op_trgt = {dropTarget:true}

	<?php echo($javascripts);?>

	jQuery("#ExportRedCAP_CONCPT_status").html("Loaded!");
});
</script>
