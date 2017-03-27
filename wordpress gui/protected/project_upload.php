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
	 * This page is designed to match with the backup_managment page, where a XML file downloaded
	 * can be forwarded to REDCAp for restoring.
 	 */

	require_once("../common.php");
	require_once('../webclient17/js-i2b2/cells/plugins/standard/ExportRedCAP/assets/RestCallRequest.php');

	if( !isset( $_POST["ENC"] ) ){
		$_ENC = decryptGetMethod();
	} else {
		$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
	}

	if( isLoggedIn() && current_user_can( 'manage_options' )  ){

		get_header();


		//Get Projects
		$pest = $DB->get_results('SELECT PROJECTID, PROJECTTITLE FROM PROJECTS WHERE PROJECTCODE IS NOT NULL ORDER BY PROJECTTITLE');

		echo "<h1>Restore A Project</h1>";
		//var_dump($pest);

		//Create Form
		echo "<form method='POST' action='project_upload.php' onsubmit='return confirm(\"Are you sure you want to continue?\")' enctype='multipart/form-data'>";

		//divs to make them line up nicely
		echo "<table width=100%><tr><th>Project:</th><td>";
				echo "<select name='ENC' style='width:80%'>";
				echo "<option value=''>Select A Project</option>";
				foreach($pest as $row){
					echo "<option value='" . encryptParameters('project_upload.php','projectId='.$row->PROJECTID) . "'";

					if (isset($_ENC['projectId']) && $_ENC['projectId'] == $row->PROJECTID){
						echo " selected ='selected'";
					}

					echo ">" . $row->PROJECTTITLE . "</option>";
					//var_dump($row);
					//echo "</br>";
				}
				echo "</select>";

			echo "</td>";
		echo "</tr>";
		echo "<tr><th>Backup File</th><td><input type='file' name='file'></td></tr>";
		echo "<tr><th>Submit:</th><td><input type='submit' name='Upload To Redcap'></td></tr>";
		echo "</table>";
		echo "</form></br>";

		//If file was submitted...
		//print_r($_FILES);
		//var_dump($_FILES);

		if(isset($_FILES['file']) && $_FILES['file']['size'] > 0 && isset($_ENC['projectId']) && $_ENC['projectId'] != ''){
			$redcap_settings = $DB->get_row( "
				SELECT PROJECTID, PROJECTCODE, PROJECTTITLE, REDCAP_URL, REDCAP_APIKEY, REDCAP_SYNCDATE
				FROM PROJECTS
				WHERE PROJECTID='" . $_ENC['projectId'] . "'
			"  ,OBJECT );


			# arrays to contain elements you want to filter results by
			# example: array('item1', 'item2', 'item3');
			$fields = array();
			$forms = array();

			# an array containing all tde elements tdat must be submitted to tde API
			#'content' => 'metadata',
			#file_get_contents($_FILES[$field]['tmp_name']))
			#foreach( $_FILES as $field => $value ){
			#	if( $_FILES[$field]["size"] > 0 ){
			$data = array(
				'token' => $redcap_settings->REDCAP_APIKEY,
				'content' => 'record',
				'format' => 'xml',
				'type'=>'eav',
				'returnFormat' => 'xml',
				'returnContent' => 'ids',
				'data' => file_get_contents($_FILES['file']['tmp_name'])
			);

			$request = new RestCallRequest($redcap_settings->REDCAP_URL, 'POST', $data);
			$request->execute();
			echo( "<pre>" );
			echo( str_replace(array('&','<','>','"','\''), array('&amp;','&lt;','&gt;','&quot;','&apos;'),$request->getResponseBody()) );
			$xml = simplexml_load_string($request->getResponseBody());
			print_r($xml);
			echo( "</pre>" );
		}

		get_footer();
	}  else { header("Location:login.php");  }


?>