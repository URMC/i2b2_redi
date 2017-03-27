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

	require_once("../config.php");

	echo("<h1>REDCap Database Data Dictionary Synchronization</h1>");

	echo( '<p>Your REDCap project is set up to organize data via visits (Events) and CRFS per these events. Clicking on the button below will sync the data between this portal and your REDCap Project. You will not need to do this if you have not made changes in your REDCap project in a while.</p>');
	echo( '<p>Here is your current REDCap sync settings (as they set up in the "My Projects" section of the i2b2 data portal.</p>' );

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"]) ){
		echo( "<h1 style='color:red'>The plugin has not been configured, please contact the i2b2 administrator</h1>" );
	} else {


		$Job_settings = runJavaService( $DB, $_ENC, 'redcap.DataDictionarySync', $_POST, '', $ENVIRONMENT );

		$mylocation = "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_syncstatus.php";

		$mydiv = "#ExportRedCAP-APISync";

		showJavaServiceProgressAndRefresh( $DB, $_ENC, $Job_settings, "Sync Data Dictionary", $mylocation, $_REQUEST, $mydiv, false );
		showJavaServiceLog( $DB, $Job_settings["JOB_ID"] );

	}

	echo( "<p>Ahoy ". date('m/d/Y h:i:s a', time())."</p>" );
?>