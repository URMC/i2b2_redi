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
 * This performs final merging of the excel data into the main loading table.
 * This then returns to the patient list page.
 */
	require_once("../common.php");

	if( !isset( $_POST["ENC"] ) ){
		$_ENC = decryptGetMethod();
	} else {
		$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
	}

	if( isLoggedIn() && isset($_ENC["SYSID"]) ){

		$sysid = intval($_ENC["SYSID"]);

		$DB->query( "DELETE FROM ENROLLED_PATIENT WHERE PROJECTID=".$sysid );
		$DB->query( "DELETE FROM ENROLLED_PATIENT_XLS_DATA WHERE PROJECTID=".$sysid );

		$DB->query( "INSERT INTO ENROLLED_PATIENT          SELECT * FROM ENROLLED_PATIENT_TEMP          WHERE PROJECTID=".$sysid );
		$DB->query( "INSERT INTO ENROLLED_PATIENT_XLS_DATA SELECT * FROM ENROLLED_PATIENT_XLS_DATA_TEMP WHERE PROJECTID=".$sysid );

		//CRI-170: upon save, set the updated date so we know it was touched, as well as the patient list.
		$DB->query( "UPDATE ENROLLED_PATIENT SET UPDATED       =SYSDATE WHERE PROJECTID=".$sysid );
		$DB->query( "UPDATE PROJECTS         SET SUBMITTED_DATE=SYSDATE WHERE PROJECTID=".$sysid );

		$DB->query( "DELETE FROM ENROLLED_PATIENT_TEMP WHERE PROJECTID=".$sysid );
		$DB->query( "DELETE FROM ENROLLED_PATIENT_XLS_DATA_TEMP WHERE PROJECTID=".$sysid );

		//CRI-441 - fetch data to email title.
		$mydb = $DB->get_row( "SELECT PROJECTCODE, PROJECTTITLE, CONTACTEMAIL FROM PROJECTS WHERE PROJECTID=".$sysid );

		$headers = "";

		$message = "The I2B2 project $mydb->PROJECTCODE ($mydb->PROJECTTITLE). <br><br> Has had Excel Sheet finalized.";

		$receipients = "$mydb->CONTACTEMAIL,phillip_ng@urmc.rochester.edu,davidj_pinto@urmc.rochester.edu";

		$subject = "Excel Upload of data to I2B2 $mydb->PROJECTCODE ( $mydb->PROJECTTITLE ) ";

		if( !wp_mail($receipients, $subject, $message, $headers) ){
			//for ye without functional emailing capability!
			shell_exec(	"java -jar /var/www/html/protected/mailx.jar -s \"$subject\" " . str_replace( ',',' ', $receipients ). " -c \"$message\" 2>&1"	);
		}

		header("Location:studiespatientlistmgmt.php?".encryptParameters('studiespatientlistmgmt.php','SYSID='.$sysid));
		echo("done");
	}
?>