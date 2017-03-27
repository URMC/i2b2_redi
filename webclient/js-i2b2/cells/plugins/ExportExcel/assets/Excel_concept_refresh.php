<?php

	require_once("../../../../../../../common.php");

	if(!isset($_POST["PROJECTID"]) || $_POST["PROJECTID"] == ''){
		$_POST["PROJECTID"]='0';
	}

	if( isLoggedIn() && isset($_POST["PROJECTID"])  ){

		$mappings = $EXP->get_results( "
			SELECT * FROM PROJECTS_XLS_EXP_FIELDS
			WHERE
				USERNAME='".f($_SESSION["USERNAME"])."' AND
				PROJECTID='".f($_POST["PROJECTID"])."'
			ORDER BY SYSID
		"  ,OBJECT );
		$counter = 0;

		echo("<table border=1 width=90%>");
		echo("<tr><th>Excel Column / Concept Name</th><th>What Data? How to View?</th><th>Delete</th></tr>");
		foreach ( $mappings as &$map) {

			$aggr_type = $map->AGGR;

			if($aggr_type == ''){ $aggr_type = 'VLAST'; }

			echo("<tr>");
			echo("<td width=50% title='".f($map->CONCEPT_PATH)."'>".$map->CONCEPT_NAME."</td>");

			echo("<td width=20%><select onChange=\"i2b2.ExportExcel.ConceptEditSettings(this,'$map->SYSID','AGGR');\">");

				echo( "<option value='AGGR_STR'        ".($aggr_type=='AGGR_STR'       ?"selected":"").">ALL Values, Delimited.</option>" );

				if( $_POST["PROJECTID"]!='0' && $_POST["PROJECTID"]!='2370' || (isset( $_SESSION['ADMIN'] ) && $_SESSION['ADMIN'] == 'Y') ){
					echo( "<option value='AGGR_STR_N_DATE' ".($aggr_type=='AGGR_STR_N_DATE'?"selected":"").">ALL Values & Dates, Delimited.</option>" );
				}

				echo( "<option value='VAVG'  ".($aggr_type=='VAVG'    ?"selected":"").">Numeric Value, Average</option>" );
				echo( "<option value='VMAX'  ".($aggr_type=='VMAX'    ?"selected":"").">Numeric Value, Maximum</option>" );
				echo( "<option value='VMIN'  ".($aggr_type=='VMIN'    ?"selected":"").">Numeric Value, Minimum</option>" );
				echo( "<option value='VMODE' ".($aggr_type=='VMODE'   ?"selected":"").">Value, Mode</option>" );

				if( $_POST["PROJECTID"]!='0' && $_POST["PROJECTID"]!='2370' || (isset( $_SESSION['ADMIN'] ) && $_SESSION['ADMIN'] == 'Y')){
					echo( "<option value='DMAX'  ".($aggr_type=='DMAX'    ?"selected":"").">Date, Largest Numeric Value</option>" );
					echo( "<option value='DMIN'  ".($aggr_type=='DMIN'    ?"selected":"").">Date, Smallest Numeric Value</option>" );
				}

				echo( "<option value='VFIRST'".($aggr_type=='VFIRST'  ?"selected":"").">Value, First Occurance</option>" );
				echo( "<option value='V2ND'  ".($aggr_type=='V2ND'    ?"selected":"").">Value, Second Occurance</option>" );
				echo( "<option value='V3RD'  ".($aggr_type=='V3RD'    ?"selected":"").">Value, Third Occurance</option>" );
				echo( "<option value='V4TH'  ".($aggr_type=='V4TH'    ?"selected":"").">Value, Fourth Occurance</option>" );
				echo( "<option value='V5TH'  ".($aggr_type=='V5TH'    ?"selected":"").">Value, Fifth Occurance</option>" );

				echo( "<option value='VLAST' ".($aggr_type=='VLAST'   ?"selected":"").">Value, Most Recent</option>" );
				echo( "<option value='VE2ND' ".($aggr_type=='VE2ND'   ?"selected":"").">Value, Second From End</option>" );
				echo( "<option value='VE3RD' ".($aggr_type=='VE3RD'   ?"selected":"").">Value, Third From End</option>" );
				echo( "<option value='VE4TH' ".($aggr_type=='VE4TH'   ?"selected":"").">Value, Fourth From End</option>" );
				echo( "<option value='VE5TH' ".($aggr_type=='VE5TH'   ?"selected":"").">Value, Fifth From End</option>" );

				if( $_POST["PROJECTID"]!='0' && $_POST["PROJECTID"]!='2370' || (isset( $_SESSION['ADMIN'] ) && $_SESSION['ADMIN'] == 'Y') ){

					echo( "<option value='DFIRST'".($aggr_type=='DFIRST'  ?"selected":"").">Date, First Occurance</option>" );
					echo( "<option value='D2ND'  ".($aggr_type=='D2ND'    ?"selected":"").">Date, Second Occurance</option>" );
					echo( "<option value='D3RD'  ".($aggr_type=='D3RD'    ?"selected":"").">Date, Third Occurance</option>" );
					echo( "<option value='D4TH'  ".($aggr_type=='D4TH'    ?"selected":"").">Date, Fourth Occurance</option>" );
					echo( "<option value='D5TH'  ".($aggr_type=='D5TH'    ?"selected":"").">Date, Fifth Occurance</option>" );

					echo( "<option value='DLAST' ".($aggr_type=='DLAST'   ?"selected":"").">Date, Most Recent</option>" );
					echo( "<option value='DE2ND' ".($aggr_type=='DE2ND'   ?"selected":"").">Date, Second From End</option>" );
					echo( "<option value='DE3RD' ".($aggr_type=='DE3RD'   ?"selected":"").">Date, Third From End</option>" );
					echo( "<option value='DE4TH' ".($aggr_type=='DE4TH'   ?"selected":"").">Date, Fourth From End</option>" );
					echo( "<option value='DE5TH' ".($aggr_type=='DE5TH'   ?"selected":"").">Date, Fifth From End</option>" );

				}

				echo( "<option value='COUNT' ".($aggr_type=='COUNT'   ?"selected":"").">Count of Observations</option>" );



			echo("</select></td>");

			echo("<td width=10%><a class='concptItem' href=\"JavaScript:i2b2.ExportExcel.ConceptDelete('ExportExcel_dynamic_".$map->SYSID."');\" style='color:red;font-weight:bold;'>Delete</a></td>");
			echo("</tr>");
			$counter++;
		}
		if( $counter == 0 ){
			echo("<tr><td colspan=4>Drag one or more concepts to the bar above!</td></tr>");
		}
		echo("</table>");
	} else {
		echo( "huh?" );
	}
?>