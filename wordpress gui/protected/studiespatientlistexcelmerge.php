<?php

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
 * This is a page that performs test merges from the production clinical repository
 * and provides re-identification of data into the temporary tables. This has been
 * left in URMC specific format as there needs to be massive rewrite to make this
 * work for your institution.
 *
 * This is downstream from studiespatientexcelupload.php
 * after successful processing of studiesexcelproc.php
 */

require_once("../common.php");
if( !isset( $_POST["ENC"] ) ){
	$_ENC = decryptGetMethod();
} else {
	$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
}


//clear the done sheets.
$mydb = $DB->get_results( "SELECT ID,LOC FROM PROJECTS_UPLOADS WHERE STATUS='Done'", OBJECT );
foreach($mydb as &$aRow){
	unlink("/var/www/html/".$aRow->LOC);
	unlink("/var/www/html/".$aRow->LOC.".log");
	$DB->query( "UPDATE PROJECTS_UPLOADS SET STATUS='Cleaned' WHERE ID='".intval($aRow->ID)."'");
}


if( isLoggedIn() && isset($_ENC["SYSID"]) ){

	get_header();

	//new dBug( $DB );

	$sysid = $_ENC["SYSID"];

	$pest = $DB->get_row( "SELECT PROJECTS.* FROM PROJECTS WHERE PROJECTID = '".$sysid."' "  ,OBJECT );
	$istraining = (strpos(strtoupper($pest->PROJECTTITLE) ,'TRN') === 0);


?>
	<style>
		#main_content ol,ul{ margin:auto;padding-left: 20px; }
		form table,td,tr,th{ padding:5px; border:1px solid black;}
	</style>

	<h2><?php echo($pest->PROJECTTITLE); ?></h2>
	<p>

		You can fix the excel spreadsheet, or you can accept these "hanging chads" into the database.<br> These patients will not be imported from to the warehouse.<br>
	</p>
	<p>
		<input type="button" value="Fix Spreadsheet and Re-Upload" onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$sysid));?>'">
		<input type="button" value="Import All Data As-is" onClick="if( confirm('Are you sure?')){ window.location='studiespatientexcelfinal.php?<?php echo(encryptParameters('studiespatientexcelfinal.php','SYSID='.$sysid));?>';}">
	</p>

	<p>
		The Excel File that was imported had the following inconsistencies:
	</p>
	<?php

		$DB->show_errors(true);

		$mydb = $DB->get_results( "
			SELECT MRN AS NU, SAMP.FIRSTNAME AS FNAME, SAMP.LASTNAME AS LNAME,  A.SYSID FROM
			(SELECT SYSID, PID, TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB, SITE FROM ENROLLED_PATIENT_TEMP WHERE PID IS NULL) A,
			SAMPLE_PATIENTS SAMP
			WHERE SAMP.DOB=A.DOB AND SAMP.MRN=A.MATCHEDMRN
			", OBJECT );


		if( sizeof( $mydb ) > 0 ){
			foreach($mydb as &$aRow){
				$pest = $DB->query(
					"UPDATE ENROLLED_PATIENT_TEMP SET PID=".$aRow->NU.",
						FIRSTNAME='".f($aRow->FNAME)."',
						LASTNAME='".f($aRow->LNAME)."',
						SITE='SMH',
						MESSAGES='Matched Successfully'
					 WHERE SYSID = '".intval($aRow->SYSID)."' "
				);
			}
			echo( "<br>Tent Card Matched by MRN/DOB: ". sizeof($mydb) );
		}


		//do database lookup - via mrn/dob
		$mydb = $DB->get_results( "
			SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.PATIENT.FIRST_NAME AS FNAME, CDWMGR.PATIENT.LAST_NAME AS LNAME, EXTPAT.EXTAPP_CD AS SITE, A.SYSID FROM
			(SELECT SYSID, PID, TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB FROM ENROLLED_PATIENT_TEMP WHERE PID IS NULL) A,
			CDWMGR.PATIENT, CDWMGR.EXTPAT
			WHERE PATIENT.PID=EXTPAT.PID AND PATIENT.BIRTH_DATE=A.DOB AND EXTPAT.MRNUM=A.MATCHEDMRN
			", OBJECT );


		foreach($mydb as &$aRow){
			$pest = $DB->query(
				"UPDATE ENROLLED_PATIENT_TEMP SET PID=".$aRow->NU.",
					FIRSTNAME='".f($aRow->FNAME)."',
					LASTNAME='".f($aRow->LNAME)."',
					SITE='".f($aRow->SITE)."',
					MESSAGES='Matched Successfully'
				 WHERE SYSID = '".intval($aRow->SYSID)."' "
			);
		}
		echo( "<br>Matched by MRN: ". sizeof($mydb) );
		//do database lookup via first initials and mrn
		$mydb = $DB->get_results( "
			SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.PATIENT.FIRST_NAME, CDWMGR.PATIENT.LAST_NAME, EXTPAT.EXTAPP_CD AS SITE, A.* FROM
			(
				SELECT SYSID, FIRSTNAME, LASTNAME, PID, TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB
				FROM ENROLLED_PATIENT_TEMP
				WHERE PID IS NULL AND
					NOT FIRSTNAME IS NULL AND
					NOT LASTNAME IS NULL AND
					NOT TO_NUMBER(MRN) IS NULL
			) A,
			CDWMGR.PATIENT, CDWMGR.EXTPAT
			WHERE
				  PATIENT.PID=EXTPAT.PID AND
				  UPPER(SUBSTR(PATIENT.FIRST_NAME,1,1))=UPPER(SUBSTR(A.FIRSTNAME,1,1)) AND
				  UPPER(SUBSTR(PATIENT.LAST_NAME,1,1))=UPPER(SUBSTR(A.LASTNAME,1,1)) AND
				  EXTPAT.MRNUM=A.MATCHEDMRN
			", OBJECT );

		//look up by explicit name.
		foreach($mydb as &$aRow){
			$pest = $DB->query(
				"UPDATE ENROLLED_PATIENT_TEMP SET PID=".$aRow->NU.",
					FIRSTNAME='".f($aRow->FIRST_NAME)."',
					LASTNAME='".f($aRow->LAST_NAME)."',
					DOB_DATE=TO_DATE('".f($aRow->DOB)."','YYYYMMDD'),
					SITE='".f($aRow->SITE)."',
					MESSAGES='Matched Successfully'
				 WHERE SYSID = '".intval($aRow->SYSID)."' "
			);
		}
		echo( "<br>Matched by Name/MRN: ". sizeof($mydb) );


		//do database lookup via site, FIRST and LAST name and dob.
		$mydb = $DB->get_results( "
			SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.EXTPAT.MRNUM, A.* FROM
			(SELECT SYSID, PID, SITE, FIRSTNAME, LASTNAME, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB FROM ENROLLED_PATIENT_TEMP WHERE PID IS NULL) A,
			CDWMGR.PATIENT, CDWMGR.EXTPAT
			WHERE PATIENT.PID=EXTPAT.PID AND PATIENT.BIRTH_DATE=A.DOB AND
				UPPER(TRIM(A.FIRSTNAME))=UPPER(TRIM(CDWMGR.PATIENT.FIRST_NAME)) AND
				UPPER(TRIM(A.LASTNAME ))=UPPER(TRIM(CDWMGR.PATIENT.LAST_NAME )) AND
				EXTPAT.EXTAPP_CD=A.SITE", OBJECT );

		foreach($mydb as &$aRow){
			$pest = $DB->query(
				"UPDATE ENROLLED_PATIENT_TEMP SET PID=".f($aRow->NU).",
					MRN='".f($aRow->MRNUM)."',
					MESSAGES='Matched Successfully'
				 WHERE SYSID = '".intval($aRow->SYSID)."' "
			);
		}
		echo( "<br>Matched by First/Last & DOB: ". sizeof($mydb));

		if(current_user_can( 'manage_options' )){
			//allow bypass for administrators to just upload MRN only.
			//do database lookup - via mrn/dob
			$mydb = $DB->get_results( "
				SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.PATIENT.FIRST_NAME AS FNAME, CDWMGR.PATIENT.LAST_NAME AS LNAME, SUBSTR(BIRTH_DATE,0,8) AS DOB, A.SYSID FROM
				(SELECT SYSID, PID, TO_NUMBER(MRN) AS MATCHEDMRN, SITE FROM ENROLLED_PATIENT_TEMP WHERE PID IS NULL) A,
				CDWMGR.PATIENT, CDWMGR.EXTPAT
				WHERE PATIENT.PID=EXTPAT.PID AND EXTPAT.EXTAPP_CD=A.SITE AND EXTPAT.MRNUM=A.MATCHEDMRN
				", OBJECT );


			foreach($mydb as &$aRow){
				$pest = $DB->query(
					"UPDATE ENROLLED_PATIENT_TEMP SET PID=".$aRow->NU.",
						FIRSTNAME='".f($aRow->FNAME)."',
						LASTNAME='".f($aRow->LNAME)."',
						DOB_DATE=TO_DATE('".f($aRow->DOB)."','YYYYMMDD'),
						MESSAGES='Matched Successfully'
					 WHERE SYSID = '".intval($aRow->SYSID)."' "
				);
			}
			echo( "<br>Admin Matched by MRN/SITE: ". sizeof($mydb) );

			//do database lookup via first initial and mrn
			$mydb = $DB->get_results( "
				SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.PATIENT.FIRST_NAME, CDWMGR.PATIENT.LAST_NAME, EXTPAT.EXTAPP_CD AS SITE, A.* FROM
				(
					SELECT SYSID, FIRSTNAME, LASTNAME, PID, TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB
					FROM ENROLLED_PATIENT_TEMP
					WHERE PID IS NULL AND
						NOT FIRSTNAME IS NULL AND
						NOT TO_NUMBER(MRN) IS NULL
				) A,
				CDWMGR.PATIENT, CDWMGR.EXTPAT
				WHERE
					  PATIENT.PID=EXTPAT.PID AND
					  UPPER(SUBSTR(PATIENT.FIRST_NAME,1,3))=UPPER(SUBSTR(A.FIRSTNAME,1,3)) AND
					  EXTPAT.MRNUM=A.MATCHEDMRN
			", OBJECT );

			foreach($mydb as &$aRow){
				$pest = $DB->query(
					"UPDATE ENROLLED_PATIENT_TEMP SET PID=".$aRow->NU.",
						FIRSTNAME='".f($aRow->FNAME)."',
						LASTNAME='".f($aRow->LNAME)."',
						DOB_DATE=TO_DATE('".f($aRow->DOB)."','YYYYMMDD'),
						MESSAGES='Matched Successfully'
					 WHERE SYSID = '".intval($aRow->SYSID)."' "
				);
			}

			echo( "<br>Admin Matched by MRN/LASTNAME: ". sizeof($mydb) );
			//do database lookup via first initial and mrn
			$mydb = $DB->get_results( "
				SELECT NVL(9999999-TO_NUMBER(REPLACE(CDWMGR.EXTPAT.EMPI_NUM,'E','')),10000000+CDWMGR.PATIENT.PID) AS NU, CDWMGR.PATIENT.FIRST_NAME, CDWMGR.PATIENT.LAST_NAME, EXTPAT.EXTAPP_CD AS SITE, A.* FROM
				(
					SELECT SYSID, FIRSTNAME, LASTNAME, PID, TO_NUMBER(MRN) AS MATCHEDMRN, TO_CHAR(DOB_DATE,'YYYYMMDD') AS DOB
					FROM ENROLLED_PATIENT_TEMP
					WHERE PID IS NULL AND
						NOT FIRSTNAME IS NULL AND
						NOT TO_NUMBER(MRN) IS NULL
				) A,
				CDWMGR.PATIENT, CDWMGR.EXTPAT
				WHERE
					  PATIENT.PID=EXTPAT.PID AND
					  UPPER(SUBSTR(PATIENT.LAST_NAME,1,3))=UPPER(SUBSTR(A.LASTNAME,1,3)) AND
					  EXTPAT.MRNUM=A.MATCHEDMRN
			", OBJECT );

			foreach($mydb as &$aRow){
				$pest = $DB->query(
					"UPDATE ENROLLED_PATIENT_TEMP SET PID=".$aRow->NU.",
						FIRSTNAME='".f($aRow->FNAME)."',
						LASTNAME='".f($aRow->LNAME)."',
						DOB_DATE=TO_DATE('".f($aRow->DOB)."','YYYYMMDD'),
						MESSAGES='Matched Successfully'
					 WHERE SYSID = '".intval($aRow->SYSID)."' "
				);
			}
			echo( "<br>Admin Matched by MRN/LASTNAME: ". sizeof($mydb) );

		}

		$mydb = $DB->get_results( "SELECT * FROM ENROLLED_PATIENT_TEMP WHERE PID IS NULL AND PROJECTID=".intval($sysid) );
		echo( "<h2>Known Mismatches</h2>" );
		echo( "<table>" );
		echo( "<tr> <th>#</th><th>SITE</th> <th>MRN</th> <th>FIRSTNAME</th> <th>LASTNAME</th> <th>DOB</th> </tr>" );
		$hadAny = false;
		$count = 0;
		foreach($mydb as &$aRow){
			$hadAny = true;
			$count ++;
			echo( "<tr><td>$count</td><td>".$aRow->SITE."</td> <td>".$aRow->MRN."</td> <td>".$aRow->FIRSTNAME."</td> <td>".$aRow->LASTNAME."</td> <td>".$aRow->DOB_DATE."</td> </tr>" );
		}
		if( !$hadAny ){
			echo( "<tr><td colspan=7>Horray! No Issues Found. All matched.</td></tr>");
		}

		echo( "</table>" );

		$mydb = $DB->get_results( "SELECT SITE, MRN, STUDYID, FIRSTNAME, LASTNAME
			FROM ENROLLED_PATIENT_TEMP
			WHERE PID IN (
				SELECT PID
				FROM ENROLLED_PATIENT_TEMP
				GROUP BY PID, PROJECTID
				HAVING COUNT(*) > 1 AND
					PROJECTID='".intval($sysid)."'
			) ORDER BY LASTNAME
		");
		echo( "<h2>Known Duplicates</h2>" );
		echo( "Below are all records that have been located that are duplicates. This is acceptable, however be in mind that when you run a query that these patients will only be counted once.");
		echo( "<table>" );
		echo( "<tr><th>#</th><th>SITE</th> <th>MRN</th> <th>FIRSTNAME</th> <th>LASTNAME</th> <th>DOB</th> </tr>" );
		$hadAny = false;
		$count = 0;
		foreach($mydb as &$aRow){
			$hadAny = true;
			$count ++;
			echo( "<tr><td>$count</td><td>".$aRow->SITE."</td> <td>".$aRow->MRN."</td> <td>".$aRow->FIRSTNAME."</td> <td>".$aRow->LASTNAME."</td> <td>".$aRow->DOB_DATE."</td> </tr>" );
		}
		if( !$hadAny ){
			echo( "<tr><td colspan=6>Horray! No Issues Found. All matched.</td></tr>");
		}

		echo( "</table>" );

	if( $istraining ){
		$DB->query( "UPDATE ENROLLED_PATIENT_TEMP SET FIRSTNAME='', LASTNAME='' WHERE NOT PID IS NULL AND PROJECTID=".intval($sysid) );
	}
	get_footer();?>

<?php } else { header("Location:login.php");  } ?>