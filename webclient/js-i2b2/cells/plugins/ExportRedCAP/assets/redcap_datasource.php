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
 * This page sets up the UI popup boxes needed for the Excel export module as well
 * as the identifiers needed for the excel mappings to persist.
 */

 require_once("../config.php");

 if( isset($_POST['USERNAME']) && isset($_POST['PROJECTCODE']) && isset($_POST['TOKEN']) ){

	$ans = array();

	//see if your project is extant in the database. If not, you must be the demo project.
 	$ans = $DB->get_row( "
 			SELECT PROJECTID, PROJECTCODE, PROJECTTITLE
 			FROM PROJECTS
 			WHERE TRIM(UPPER(PROJECTCODE))=UPPER(TRIM('".f($_POST['PROJECTCODE'])."'))
 	"  ,ARRAY_A );

 	//now let's see if you're actually logged in.
 	if( strpos( $_POST['TOKEN'], 'SessionKey:' ) ){

 		$key = substr( $_POST['TOKEN'], strpos( $_POST['TOKEN'], 'SessionKey:' ) + 11 );
 		$key = f(str_replace( '</password>', '', $key ));

 		$login = $PM->get_row( "
			SELECT ". getTime( $PM, 'EXPIRED_DATE' )." AS EXPIRES
			FROM PM_USER_SESSION
			WHERE SESSION_ID = '$key' AND USER_ID='".f($_POST['USERNAME'])."'"  ,ARRAY_A
		);

		if( sizeof( $login ) > 0 ){

			$ans["USERNAME"] = filefix($_POST['USERNAME']);
			$ans["EXPIRES"] = filefix($login["EXPIRES"]);
			$ans["SESSIONCODE"] = encryptParameters( "RedCAPExport", http_build_query($ans) );

			echo json_encode( $ans );

		} else {
			echo( '"{ "ERROR" : "No such key '.$key.' in PM_USER_SESSION." }' );
		}

	} else {
		echo( '"{ "ERROR" : "Not Logged In" }' );
	} // if session key is defined.

} else {
	echo( '"{ "ERROR" : "Bad Request" }' );
} //if there are the required parameters
?>