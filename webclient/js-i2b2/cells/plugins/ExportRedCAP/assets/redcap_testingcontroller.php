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
	 * This page is similar to datamart.php in that it creates and controls the ExportREDCap testing jobs
	 * in the control database's i2b2_jobs table.
	 */

	echo( "<h1>Mapping Testing Screen</h1>");

	echo( "<p>Below, you can select a patient from your patient list that you can utilize to do a complete and detailed diagnostic/validation of what data is in the system.</p>" );

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"]) ){
		echo( "<h1 style='color:red'>The plugin has not been configured, please contact the i2b2 administrator</h1>" );
	} else {

		if( isset( $_REQUEST["SETFIELD"] ) ){
			if( strlen( $_REQUEST["SETFIELD"] ) < 50 ){
				$DB->query( "UPDATE PROJECTS SET TESTFIELD='".filefix($_REQUEST["SETFIELD"])."' WHERE PROJECTID='".f($_ENC["PROJECTID"])."'");
			}
		}
		if( isset( $_REQUEST["SETPATIENT"] ) && strlen($_REQUEST["SETPATIENT"] )>0){
			$DB->query( "UPDATE PROJECTS SET TESTPATIENT='".intval($_REQUEST["SETPATIENT"])."' WHERE PROJECTID='".f($_ENC["PROJECTID"])."'");
		}

		$redcap_settings = $DB->get_row( "SELECT * FROM PROJECTS WHERE PROJECTID='".f($_ENC["PROJECTID"])."'", ARRAY_A);

		$Job_settings = runJavaService( $DB, $_ENC, 'redcap.RedcapSync', $_POST, '', $ENVIRONMENT );

		$mylocation = "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_testingcontroller.php";

		$mydiv = "#ExportRedCAP-Tester";

		$tester_settings = $DB->get_row( "SELECT * FROM PROJECTS_REDCAP_FIELDS_MAPPING WHERE FIELD_NAME='".f($redcap_settings["TESTFIELD"])."' AND PROJECTID='".f($_ENC["PROJECTID"])."' " ,ARRAY_A );
		echo( "<h4>Testing Field To Work On: " . htmlentities($tester_settings["FORM_NAME"]).".".htmlentities($tester_settings["FIELD_NAME"]). " With aggregation of ".htmlentities($tester_settings["AGGR_TYPE"]). ". </h4>" );

		$patients = $DB->get_results( "SELECT * FROM ENROLLED_PATIENT WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND NOT PID IS NULL AND ROWNUM < 100"  ,ARRAY_A );
		echo("<table width='100%'><tr><td width='10%'>Patient Select</td><td width='80%'>");
		echo("<select name='SELECTAPT' id='SELECTAPT' >");
		echo("<option value='0'> Select A Patient From Below: </option>" );
		if( $patients ){
			foreach ( $patients as $item) {
				echo("<option value='".$item["PID"]."' ".($item["PID"]==$redcap_settings["TESTPATIENT"]?"selected":"").">".htmlentities($item["STUDYID"]). ", ".htmlentities($item["LASTNAME"]).", ".htmlentities($item["FIRSTNAME"])." - ".htmlentities($item["DOB_DATE"])."</option>");
			}
		}
		echo("</select>");
		echo("</td><td width='10%'>");
		showJavaServiceProgressAndRefresh( $DB, $_ENC, $Job_settings, "", $mylocation, $_REQUEST, $mydiv , true );
		echo("</td></tr></table>");

		showJavaServiceLog( $DB, $Job_settings["JOB_ID"] );

	}

	echo( "<p>Ahoy ". date('m/d/Y h:i:s a', time())."</p>" );
?>