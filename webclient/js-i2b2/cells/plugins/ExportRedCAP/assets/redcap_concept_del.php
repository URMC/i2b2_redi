<?php

	require_once("../config.php");


	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"]) && isset($_POST["ITEMID"]) && isset($_POST["VALUE"]) ){

		$DB->show_errors();

		$orig = $DB->get_row( "
				SELECT FORM_NAME,FIELD_NAME
				FROM PROJECTS_REDCAP_FIELDS
				WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
					ENTERORDER='".f($_POST["ENTERORDER"])."'
		"  ,OBJECT );

		$DB->query("
			DELETE FROM PROJECTS_REDCAP_FIELDS_MAPPING WHERE SYSID='".f($_POST["ITEMID"])."'
		");

		$mappings = $DB->get_results( "
			SELECT * FROM PROJECTS_REDCAP_FIELDS_MAPPING
			WHERE FIELD_NAME='".f($orig->FIELD_NAME)."' AND
				FORM_NAME='".f($orig->FORM_NAME)."' AND
				PROJECTID='".$_ENC["PROJECTID"]."' AND ".
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
							'ExportRedCAP_dynamic_".$_POST["VALUE"]."_".$_POST["RANDOM"]."_".$_ENC["PROJECTID"]."_".$_POST["ENTERORDER"]."_".$item->SYSID."'
						);\">".
							($_POST["VALUE"]==""?"":$item->ORDNUNG.": ").
							htmlspecialchars($item->CONCEPTNAME).
							($item->MODIFIER != '' ? ' ('.$item->MODIFIER.')' : '' ).
							($item->FILTERED != '' ? ' ['.htmlspecialchars(str_replace("\n",' ',$item->FILTERED)).']' : '' ).
					"</a>"
				);
				$counter++;
			}
		} else {
			echo("<div class='concptItem'>&nbsp;</div>");
		}

	} else {
		echo( "huh?" );
	}
?>