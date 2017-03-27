<?php

	require_once("../config.php");


	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( isset($_ENC["PROJECTID"]) &&
	    is_numeric($_ENC["PROJECTID"]) &&
		isset($_POST["ENTERORDER"]) &&
		isset($_POST["CONCEPTPATH"]) &&
		isset($_POST["RANDOM"]) &&
		isset($_POST["CONCEPTNAME"]) &&
		isset($_POST["AGGRTYPE"])  &&
		isset($_POST["VALUE"]) ){

		$_POST["CONCEPTPATH"] = str_replace( '\\\\','\\',$_POST["CONCEPTPATH"]);

		$DB->show_errors();

		$orig = $DB->get_row( "
				SELECT FORM_NAME,FIELD_NAME
				FROM PROJECTS_REDCAP_FIELDS
				WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
					ENTERORDER='".intval($_POST["ENTERORDER"])."'
		"  ,ARRAY_A );

		if( sizeof($orig) && !( strpos($_POST["CONCEPTPATH"],'\\i2b2') === false ) ){

			$ordnung = $DB->get_var( "
				SELECT MIN(ORDNUNG) FROM PROJECTS_REDCAP_FIELDS_MAPPING
				WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
					FORM_NAME='".f($orig["FORM_NAME"])."' AND
					FIELD_NAME='".f($orig["FIELD_NAME"])."' AND ".
					($_POST["VALUE"] == "" ? "VALUE IS NULL" : "VALUE='".f($_POST["VALUE"])."'")."
			" );

			if( $ordnung  == "" ){
				$ordnung = $DB->get_var( "
					SELECT MAX(ORDNUNG) FROM PROJECTS_REDCAP_FIELDS_MAPPING
					WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
						FORM_NAME='".f($orig["FORM_NAME"])."' AND
						FIELD_NAME='".f($orig["FIELD_NAME"])."'"
				);
				if( $ordnung  == "" ){
					$ordnung  = 1;
				} else {
					$ordnung ++;
				}
			}
			$vars = "SYSID, PROJECTID, FIELD_NAME, VALUE, ORDNUNG, FORM_NAME, CONCEPTPATH, CONCEPTNAME, AGGR_TYPE";
			$vals = "SYSIDS.NEXTVAL,'".f($_ENC["PROJECTID"])."','".f($orig["FIELD_NAME"])."', '".f($_POST["VALUE"])."', '$ordnung', '".f($orig["FORM_NAME"])."','".f($_POST["CONCEPTPATH"])."','".f($_POST["CONCEPTNAME"])."','".filefix($_POST["AGGRTYPE"])."'";

			if( isset($_POST["OPTIONS"])&& $_POST["OPTIONS"]!=''  ){
				$vars .= ', OPTIONS';
				$vals .= ",'".f($_POST["OPTIONS"])."'";
			}
			if( isset($_POST["MODIFIER"])&& $_POST["MODIFIER"]!=''  ){
				$vars .= ', MODIFIER';
				$vals .= ",'".f($_POST["MODIFIER"])."'";
			}
			if( isset($_POST["FILTERED"]) && $_POST["FILTERED"]!='' ){
				$vars .= ', FILTERED';
				$vals .= ",'".str_replace("<br>","\n",f(str_replace("\n","<br>",$_POST["FILTERED"])))."'";
			}

			$DB->query(" INSERT INTO PROJECTS_REDCAP_FIELDS_MAPPING ($vars) VALUES ($vals) ");
		} //if the item starts with \\i2b2\ then insert.

		$mappings = $DB->get_results( "
			SELECT * FROM PROJECTS_REDCAP_FIELDS_MAPPING
			WHERE FIELD_NAME='".f($orig["FIELD_NAME"])."' AND
				FORM_NAME='".f($orig["FORM_NAME"])."' AND
				PROJECTID='".f($_ENC["PROJECTID"])."' AND ".
				($_POST["VALUE"] == "" ? "VALUE IS NULL" : "VALUE='".f($_POST["VALUE"])."'")."
			ORDER BY SYSID
		"  ,OBJECT );

		$counter = 0;
		if( $mappings ){
			foreach ( $mappings as &$item) {
				if( $counter > 0 ){
					echo("<div class='concptDiv'></div>");
				}
				echo("
					<a class='concptItem'
					   href=\"JavaScript:i2b2.ExportRedCAP.ConceptDelete(
							'ExportRedCAP_dynamic_".$_POST["VALUE"]."_".intval($_POST["RANDOM"])."_".$_ENC["PROJECTID"]."_".intval($_POST["ENTERORDER"])."_".$item->SYSID."'
						);\">".
							($_POST["VALUE"]==""?"":$item->ORDNUNG.": ").
							htmlspecialchars($item->CONCEPTNAME).
							($item->MODIFIER != '' ? ' ('.$item->MODIFIER.')' : '' ).
							($item->FILTERED != '' ? ' ['.htmlspecialchars(str_replace("\n",' ',$item->FILTERED)).']' : '' ).
					"</a>"
				);
				$counter++;
			}
		}

	} else {
		echo( "huh?" );
	}
?>