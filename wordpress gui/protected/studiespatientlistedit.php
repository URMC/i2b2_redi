<?php require_once("../common.php");?>
<?php


	/* Array of database columns which should be read and sent back to DataTables. Use a space where
	 * you want to insert a non-database field (for example a counter or static image)
	 */

	$aColumns = array( 'SYSID', 'STUDYID','MRN', 'SITE', 'FIRSTNAME', 'LASTNAME', "DOB_DATE", "ENROLLED","ARM", "MESSAGES" );

	function escape( $s = "" ){
		 return f(str_replace(array("\\","<",">","'",'"','|',':','*','?','%'),"", $s));
	}

	function fatal_error ( $sErrorMessage = '' ){
		header( $_SERVER['SERVER_PROTOCOL'] .' 500 Internal Server Error' );
		die( $sErrorMessage );
	}

	if( isLoggedIn() && isset($_POST["value"]) && isset($_POST["row_id"]) && isset($_POST["column"]) ){

		$column = $aColumns[ intval($_POST["column"]) ];
		$val = escape($_POST["value"]);
		$val = "'$val'";

		if( $column == 'MESSAGES' || $column == 'SYSID' ){
			fatal_error( "You cannot change this field" );
		}

		if( $column == 'SITE' ){
			if( $val != "'SMH'" && $val != "'HH'" && $val != "''"){
				fatal_error( $val . " is not SMH or HH?" );
			}
		}

		if( $column == 'DOB_DATE' || $column == 'ENROLLED' ){
			if( strtotime( $_POST["value"] ) !== FALSE ){
				$val = "to_date('".date( "m/d/Y H:i:s", strtotime($_POST["value"]) )."','mm/dd/yyyy hh24:mi:ss')";
			} elseif ( $_POST["value"] == "" ){
				$val = "null";
			} else {
				fatal_error( $val . " is not a valid date, please input as YYYY/MM/DD" );
			}
		}

		$pest = $DB->query( "UPDATE ENROLLED_PATIENT SET PID=null, MESSAGES='Patient Not Matched', $column=$val WHERE SYSID = '".intval($_POST["row_id"])."' "  );

		//do database lookup.
		$pest = $DB->get_row( "SELECT ENROLLED_PATIENT.* FROM ENROLLED_PATIENT WHERE SYSID = '".intval($_POST["row_id"])."' "  ,OBJECT );

		if( $pest->MRN != "" && $pest->DOB_DATE != "" ){
			echo("A");
			$mydb = $DB->get_results( "
				SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.PATIENT.FIRST_NAME, EXTPAT.EXTAPP_CD AS SITE, CDWMGR.PATIENT.LAST_NAME, A.* FROM
				(SELECT SYSID, PID, SITE, TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB FROM ENROLLED_PATIENT WHERE PID IS NULL AND SYSID = '".intval($_POST["row_id"])."') A,
				CDWMGR.PATIENT, CDWMGR.EXTPAT
				WHERE PATIENT.PID=EXTPAT.PID AND PATIENT.BIRTH_DATE=A.DOB AND EXTPAT.MRNUM=A.MATCHEDMRN ", OBJECT );

			foreach($mydb as &$aRow){
				$pest = $DB->query(
					"UPDATE ENROLLED_PATIENT SET PID=".$aRow->NU.",
						FIRSTNAME='".escape($aRow->FIRST_NAME)."',
						LASTNAME='".escape($aRow->LAST_NAME)."',
						SITE='".escape($aRow->SITE)."',
						MESSAGES='Matched Successfully'
					 WHERE SYSID = '".intval($aRow->SYSID)."' "
				);
			}

		} elseif ( $pest->LASTNAME != "" && $pest->FIRSTNAME != "" && $pest->DOB_DATE != "" ){
			echo("B");
			if( $pest->SITE != "" ){

				$mydb = $DB->get_results( "
				SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.EXTPAT.MRNUM, EXTPAT.EXTAPP_CD , A.* FROM
				(SELECT SYSID, PID, SITE,TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB, FIRSTNAME, LASTNAME FROM ENROLLED_PATIENT WHERE PID IS NULL AND SYSID = '".intval($_POST["row_id"])."') A,
				CDWMGR.PATIENT, CDWMGR.EXTPAT
				WHERE PATIENT.PID=EXTPAT.PID AND PATIENT.BIRTH_DATE=A.DOB AND
					UPPER(PATIENT.FIRST_NAME)=UPPER(A.FIRSTNAME) AND
					UPPER(PATIENT.LAST_NAME)=UPPER(A.LASTNAME) AND
					EXTPAT.EXTAPP_CD=A.SITE AND
					", OBJECT );

				foreach($mydb as &$aRow){
					$pest = $DB->query(
						"UPDATE ENROLLED_PATIENT SET PID=".$aRow->NU.",
							SITE='".escape($aRow->EXTAPP_CD)."',
							MRN='".escape($aRow->MRNUM)."',
							MESSAGES='Matched Successfully'
						 WHERE SYSID = '".intval($aRow->SYSID)."' "
					);
					echo("HI");
				}

			} else {
				$pest = $DB->query( "UPDATE ENROLLED_PATIENT SET MESSAGES='SITE is blank' WHERE SYSID = '".intval($_POST["row_id"])."' "  );
			}

		} elseif ( $pest->LASTNAME != "" && $pest->FIRSTNAME != "" && $pest->MRN != "" ){
			echo("C");
			$mydb = $DB->get_results( "
				SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.EXTPAT.MRNUM, SUBSTR(PATIENT.BIRTH_DATE,0,8) AS DOB, A.* FROM
				(SELECT SYSID, PID, SITE,TO_NUMBER(MRN) AS MATCHEDMRN, FIRSTNAME, LASTNAME FROM ENROLLED_PATIENT WHERE PID IS NULL AND SYSID = '".intval($_POST["row_id"])."') A,
				CDWMGR.PATIENT, CDWMGR.EXTPAT
				WHERE PATIENT.PID=EXTPAT.PID AND
					UPPER(SUBSTR(PATIENT.FIRST_NAME,1,1))=UPPER(SUBSTR(A.FIRSTNAME,1,1)) AND
					UPPER(SUBSTR(PATIENT.LAST_NAME,1,1))=UPPER(SUBSTR(A.LASTNAME,1,1))  AND
					EXTPAT.MRNUM=A.MATCHEDMRN
					", OBJECT );

			foreach($mydb as &$aRow){
				$pest = $DB->query(
					"UPDATE ENROLLED_PATIENT SET PID=".$aRow->NU.",
						DOB_DATE=TO_DATE('".escape($aRow->DOB)."','YYYYMMDD'),
						MESSAGES='Matched Successfully'
					 WHERE SYSID = '".intval($aRow->SYSID)."' "
				);
				echo("HI");
			}

		} else {
			echo("ELSE");
			$pest = $DB->query( "UPDATE ENROLLED_PATIENT SET MESSAGES='Insufficient Info DOB&MRN OR NAMES&DOB needed' WHERE SYSID = '".intval($_POST["row_id"])."' "  );
		}
		$DB->query( "UPDATE ENROLLED_PATIENT SET FIRSTNAME='', LASTNAME='' WHERE NOT PID IS NULL AND PROJECTID IN (SELECT PROJECTID FROM PROJECTS WHERE PROJECTTITLE LIKE 'TRN%' )" );

	}

	echo $val;
?>