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
	 * This page is similar to datamart.php in that it creates and controls the ExportREDCap jobs
	 * in the control database's i2b2_jobs table.
	 */

	echo( "<h1>REDCap File Generation Status</h1>");

	echo( "<p>Use the following screen to control the REDCap file generation process. Below is the current status of the sync,
		followed by control buttons. You may start / cancel a current operation. If the process is started,
		it will continue to run until the merge process is complete or failed. If you get logged out of the system,
		you can log back into the system and return to this page to see where the status of the process is.</p>" );


	echo( "<p>An XML file will be generated on the server, you will have the option to download that XML file in Excel format to preview your changes to REDCap, and then you can upload that data from the screen below..</p>" );

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "RedCAPExport", $_POST["SESSIONCODE"] );
	}

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"]) ){
		echo( "<h1 style='color:red'>The plugin has not been configured, please contact the i2b2 administrator</h1>" );
	} else {


		$Job_settings = runJavaService( $DB, $_ENC, 'redcap.RedcapSync', $_POST, '', $ENVIRONMENT );

		$mylocation = "js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_servicecontroller.php";

		$mydiv = "#ExportRedCAP-Controller";

		if( showJavaServiceProgressAndRefresh( $DB, $_ENC, $Job_settings, "Generate REDCap Values", $mylocation, $_REQUEST, $mydiv, false ) ){ ?>

			<input type="button" onClick="
				window.location='js-i2b2/cells/plugins/standard/ExportRedCAP/assets/redcap_previewdata.php?SESSIONCODE=<?php echo($_POST["SESSIONCODE"]);?>';
			" value="Preview Data">

			<input type="button" onClick="i2b2.ExportRedCAP.yuiTabs.set('activeIndex',4);" value="Send Data Generated To REDCap">


		<?php }
		showJavaServiceLog( $DB, $Job_settings["JOB_ID"] );

	}

	echo( "<p>Ahoy ". date('m/d/Y h:i:s a', time())."</p>" );

?>