<?php

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"])){

		$DB->show_errors();

		$orig = $DB->get_row( "
				SELECT FORM_NAME,FIELD_NAME
				FROM PROJECTS_REDCAP_FIELDS
				WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
					ENTERORDER='".f($_POST["ENTERORDER"])."'
		"  ,OBJECT );

		if( $_POST["ATTRIBUTE"] == "CPTAGGR" ){

			$_POST["ATTRIBUTE"] = "AGGR_TYPE";
			//all of the options are valid looking variable names, so check that.
			if( preg_match('/^[a-zA-Z_\x7f-\xff][a-zA-Z0-9_\x7f-\xff]*$/', $_POST["VALUE"]) ){
				$_POST["VALUE"] = filefix( $_POST["VALUE"] );
			} else {
				$_POST["VALUE"] = "ERROR";
			}
		} elseif( $_POST["ATTRIBUTE"] == "OPTIONS" ){
			$_POST["ATTRIBUTE"] = "OPTIONS";
			//only options are before and after, so just look for that, regenerate the data for output.
			$before = substr( $_POST["VALUE"] , 0 , strpos( $_POST["VALUE"], ",") );
			$after = substr( $_POST["VALUE"] , strpos( $_POST["VALUE"], "," ) + 1 );

			$before = str_replace( "before=", "" );
			$after = str_replace( "after=", "" );

			if( is_numeric( $before ) && is_numeric( $after ) ){
				$_POST["VALUE"] = "before=$before,after=$after";
			}
		} else {
			$_POST["ATTRIBUTE"] = "";
			$_POST["VALUE"] = "";
		}

		if( $_POST["ATTRIBUTE"] != "" ){
			$DB->query("
				UPDATE PROJECTS_REDCAP_FIELDS_MAPPING
				SET ".f($_POST["ATTRIBUTE"])."='".$_POST["VALUE"]."'
				WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
					FORM_NAME='".f($orig->FORM_NAME)."' AND
					FIELD_NAME='".f($orig->FIELD_NAME)."'
			");
			echo( "ok" );
		} else {
			echo( "hmm" );
		}

	} else {
		echo( "huh?" );
	}
?>