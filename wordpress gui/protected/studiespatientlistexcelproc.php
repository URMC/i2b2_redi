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
	 * This starts off a Java Service to store the uploaded spreadsheet. This should
	 * wait until it is complete and then it returns and lets the system finish, which
	 * causes the merge to carry off.
	 *
	 * This is downstream and ajax invoked from studiespatientexcelupload.php.
	 */


	require_once("../common.php");
	if( !isset( $_ENC["ENC"] ) ){
		$_ENC = decryptGetMethod();
	} else {
		$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_ENC["ENC"]);
	}
	if( isLoggedIn() && isset($_ENC["LOCATION"]) && isset($_ENC["SYSID"])){

		$location = $DB->get_var("SELECT LOC FROM PROJECTS_UPLOADS WHERE ID='".intval($_ENC["LOCATION"])."'");

		if( !file_exists ( "/var/www/html/". $location.".log" ) ){

			// Outputs all the result of shellcommand "ls", and returns
			// the last output line into $last_line. Stores the return value
			// of the shell command in $retval.
			$DB->query( "UPDATE PROJECTS_UPLOADS SET STATUS='Start' WHERE ID='".intval($_ENC["LOCATION"])."'");
			$last_line = system("java -jar -Xmx512M -Xms512M ExcelImport.jar /var/www/html/". $location. " ". $DB->dbname . " " .$DB->dbuser . " " . $DB->dbpassword . " " . $_ENC["SYSID"]." > /var/www/html/". $location.".log 2>&1", $retval);
			$DB->query( "UPDATE PROJECTS_UPLOADS SET STATUS='Done' WHERE ID='".intval($_ENC["LOCATION"])."'");
			unlink("/var/www/html/".$aRow->LOC);
		}
	} else {
		echo( "Not logged in?!" );
		print_r( $_ENC );
	}
?>



