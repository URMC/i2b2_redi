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
	 * This page is similar to datamart.php in that it creates and controls the ExportREDCap sending to redcap jobs
	 * in the control database's i2b2_jobs table.
	 */

	echo( "<h1>REDCap Sending Results</h2>" );

	echo( "<hr>");

	echo( "Here are the results of what has been previously sent to REDCap and what is awaiting sending. When you click on the button below and confirm the selection,
		 this application will first perform an data integrity download and then upload the data to REDCap in 200 item chunks.
		 You will be updated to the status of what data to be sent. Once this sync process starts, unless the browser is closed, this will continue until completion.<br><Br>"
	);

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"]) ){
		echo( "<h1 style='color:red'>The plugin has not been configured, please contact the i2b2 administrator</h1>" );
	} else {


		$Job_settings = runJavaService( $DB, $_ENC, 'redcap.Sender', $_POST, '', $ENVIRONMENT );

		$mylocation = "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_sendcontroller.php";

		$mydiv = "#ExportRedCAP-Sender";

		if( showJavaServiceProgressAndRefresh( $DB, $_ENC, $Job_settings, "Sending Data To Redcap", $mylocation, $_REQUEST, $mydiv, false ) ){

			$alleav = $EXP->get_var( "SELECT COUNT(*) FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='".f($_ENC["PROJECTID"])."'" );
			$errors = $EXP->get_var( "SELECT COUNT(*) FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND SENDLOG LIKE '<field%'" );
			$unsent = $EXP->get_var( "SELECT COUNT(*) FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND SENDLOG IS NULL" );
			$sentok = $EXP->get_var( "SELECT COUNT(*) FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND SENDLOG='SUCCESS'" );
			$huhwtf = $EXP->get_var( "SELECT COUNT(*) FROM PROJECTS_REDCAP_OUTPUT_EAV WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND SENDLOG='".f($_ENC["PROJECTID"])."'" );

			echo( "<br><br><table border=1>" );
			echo( "<tr><th># Total</th><th># Successfully Sent</th><th>Number of Errors</th><th># to be sent</th>" .($huhwtf ? "<th># in progress</th>" : "" )."</tr>");
			echo( "<tr><th>$alleav</th><th>$sentok            </th><th>$errors         </th><th>$unsent     </th>" .($huhwtf ? "<th>$huhwtf      </th>" : "" )."</tr>");
			echo( "</table><br><br>" );
			$action = "Queued";

		}

		showJavaServiceLog( $DB, $Job_settings["JOB_ID"] );

	}

	echo( "<p>Ahoy ". date('m/d/Y h:i:s a', time())."</p>" );

?>
