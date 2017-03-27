<?php

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_REQUEST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_REQUEST["SESSIONCODE"] );

	}

	if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"]) ){

		$sql = "SELECT FILECONTENTS FROM PROJECTS_BLOBS
				WHERE PROJECTID='".intval($_ENC["PROJECTID"])."' AND
					  FIELDNAME='EXCELPREVIEW'
				";

		//make a null query just to ensure that the connection is on, so the connection information is in the common.php.
		$DB->get_row( "SELECT SYSDATE FROM DUAL"  ,OBJECT );
		$conn = $DB->dbh;

		$stid = oci_parse($conn, $sql);
		$img = "";
		oci_execute($stid);
		$row = oci_fetch_array($stid, OCI_ASSOC+OCI_RETURN_NULLS);
		if (!$row) {
			header('Status: 404 Not Found');
		} else {
			$img = $row['FILECONTENTS']->load();
		}

		$filename =  "Excel Preview For Project ".intval($_ENC["PROJECTID"])." - " . date("YmdHi");

		header("Content-type: application/vnd.ms-excel; name='excel'");
		header("Content-disposition: attachment; filename=$filename.xlsx");
		header('Content-Length: '.strlen($img) );
		header("Cache-Control: no-Store,no-Cache' ");
		header("Pragma: no-Cache");

		print $img;

	} else {
		echo ("not logged in");
	}
?>