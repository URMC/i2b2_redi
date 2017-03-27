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
	 * This page is similar to datamart.php in that it creates and controls the ExportExcel jobs
	 * in the control database's i2b2_jobs table.
	 */


	require_once("../config.php");

	echo( "<h1>Excel Sync Status</h1>");

	echo( "<p>Use the following screen to control the Excel syncing process. Below is the current status of the sync,
		followed by control buttons. You may start / cancel a current operation. If the process is started,
		it will continue to run until the merge process is complete or failed. If you get logged out of the system,
		you can log back into the system and return to this page to see where the status of the process is.</p>" );


	echo( "<p>An XML file will be generated on the server, you will have the option to download that XML file in Excel format to preview your changes to Excel, and then you can upload that data from the screen below..</p>" );


	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "ExcelExport", $_POST["SESSIONCODE"] );
	}

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"])){
		echo( "<h1 style='color:red'>The plugin has not been configured, please contact the i2b2 administrator</h1>" );
	} else {

		$Session_settings = $EXP->get_row( "SELECT SESSIONID FROM PROJECTS_XLS_EXP_JOBS WHERE USERNAME='".f($_ENC["USERNAME"])."' AND PROJECTID='".f($_ENC["PROJECTID"])."'" ,ARRAY_A );
		$params = $Session_settings["SESSIONID"];


		$Job_settings = runJavaService( $DB, $_ENC, 'excel.ExcelSync', $_REQUEST, $params, $ENVIRONMENT );

		$mylocation = "js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_servicecontroller.php";

		$mydiv = "#ExportExcel-Controller";

		if( showJavaServiceProgressAndRefresh( $DB, $_ENC, $Job_settings, "Generate Excel File", $mylocation, $_REQUEST, $mydiv, false ) ){ ?>

			<form action="js-i2b2/cells/plugins/standard/ExportExcel/assets/Excel_download.php" method="post" id='ExportExcelDownload'>
				<input type="hidden" name="SESSIONCODE" value='<?php echo($_POST["SESSIONCODE"]);?>'>
				<input type="hidden" name="PASSWORD" value='' id='ExportExcelRealPasswordField'>
				<input type="button" onClick="i2b2.ExportExcel.showDownload()" value="Download Data">
			</form>


		<?php }

		showJavaServiceLog( $DB, $Job_settings["JOB_ID"] );
	}

	echo( "<p>Ahoy ". date('m/d/Y h:i:s a', time())."</p>" );
?>