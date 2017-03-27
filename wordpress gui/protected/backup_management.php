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
	 * This page shows any backup files from REDCap. This allows for a URL to allow for
	 * downloading. A seperate page is generated to upload the data to REDCap to restore
	 * data to a overwritten file. Bear in mind the files can be edited to only have
	 * one patient's data or one variable via external text editor.
	 */

	require_once("../common.php");

	if( !isset( $_POST["ENC"] ) ){
		$_ENC = decryptGetMethod();
	} else {
		$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
	}

	if( isLoggedIn() && current_user_can( 'manage_options' )  ){

		get_header();

		$pest = $DB->get_results('SELECT PROJECTID, PROJECTTITLE FROM PROJECTS
								WHERE PROJECTCODE IS NOT NULL AND PROJECTID IN (
								SELECT PROJECTID FROM PROJECTS_BLOBS WHERE FIELDNAME LIKE \'BACKUP%\'
								) ORDER BY PROJECTTITLE');



		echo "<h1>Project Backups</h1>";
		//var_dump($pest);
		echo "<form method='POST' action='backup_management.php'>";
		echo "Project: <select name='ENC'>";
		echo "<option value=''>Select A Project</option>";
		//loop
		foreach($pest as $row){
			echo "<option value='" . encryptParameters('backup_management.php','projectId='.$row->PROJECTID) . "'";

			if (isset($_ENC['projectId']) && $_ENC['projectId'] == $row->PROJECTID){
				echo " selected ='selected'";
			}

			echo ">" . $row->PROJECTTITLE . "</option>";
			//var_dump($row);
			//echo "</br>";
		}

		echo "</select></br>";

		echo "<input type='submit' value='Submit'></input>";
		echo "</form></br>";

		if( isset($_ENC['projectId']) && $_ENC['projectId'] != ''){
			$pest = $DB->get_results("SELECT trim(FIELDNAME) FIELDNAME, FILENAME, to_char(UPDATED, 'mm/dd/yyyy hh:mi:ss AM') UPDATED FROM PROJECTS_BLOBS WHERE PROJECTID = " . f($_ENC['projectId']) . " AND FIELDNAME LIKE 'BACKUP%' ORDER BY UPDATED");

			echo "<table width='100%'><tr><th>File Type</th><th>File Name</th><th>Date</th><th>Link</th></tr>";

			$count = 1;
			foreach($pest as $row){
				if($count%2 == 0){
					echo "<tr bgcolor='#e3e3e3' onmouseout='this.style.backgroundColor = \"\";' onmouseover='this.style.backgroundColor = \"pink\";'>";
				}
				else{
					echo "<tr onmouseout='this.style.backgroundColor = \"\";' onmouseover='this.style.backgroundColor = \"pink\";'>";
				}

				echo "<td>" . $row->FIELDNAME . "</td>";
				echo "<td>" . $row->FILENAME . "</td>";
				echo "<td>" . $row->UPDATED . "</td>";
				echo "<td><a href='studies_fileopen.php?". encryptParameters('studies_fileopen.php',"TYPE=".$row->FIELDNAME."&SYSID=".$_ENC['projectId']."&DATE=".$row->UPDATED) . "'>Download</a></td>";
				echo "</tr>";

				$count++;
			}
			echo "</table>";
		}



		get_footer();

	}  else { header("Location:login.php");  }
?>