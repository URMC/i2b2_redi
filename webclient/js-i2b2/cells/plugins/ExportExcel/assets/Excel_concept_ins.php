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
 * This page inserts the items into the storage for excel mappings as well
 * as provide a layer of protection in disallowing users access to data that
 * is not gaurenteed 100% deidentified.
 *
 * This page then includes the table (refresh.php) again, which is replaces the
 * saved settings section as with the delete and update pages in this plugin
 */

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "ExcelExport", $_POST["SESSIONCODE"] );
	}

	if( isset($_ENC["PROJECTID"]) &&
		is_numeric($_ENC["PROJECTID"]) &&
		isset($_POST["CONCEPTPATH"]) &&
		isset($_POST["CONCEPTNAME"])
	){

		$_POST["CONCEPTPATH"] = str_replace( '\\\\','\\',$_POST["CONCEPTPATH"]);

		$allow = true;
		if( $_ENC["PROJECTID"]=='0' ){
			$allow = false;
			$allow |= strpos(strtoupper($_POST["CONCEPTPATH"]), "\\I2B2_DEMO\\" ) > 0;
			$allow |= strpos(strtoupper($_POST["CONCEPTPATH"]), "\\I2B2_DIAG\\" ) > 0;
			$allow |= strpos(strtoupper($_POST["CONCEPTPATH"]), "\\I2B2_PROC\\" ) > 0;
			$allow |= strpos(strtoupper($_POST["CONCEPTPATH"]), "\\I2B2_MEDS\\" ) > 0;
			$allow |= strpos(strtoupper($_POST["CONCEPTPATH"]), "\\I2B2_FLOW\\I2B2\\FLOWSHEETS\\ C" ) > 0;
			$allow |= strpos(strtoupper($_POST["CONCEPTPATH"]), "\\I2B2_URMC\\I2B2\\URMC LABORATORY\\ C" ) > 0;
		}

		if( ( strpos($_POST["CONCEPTPATH"],'\\i2b2') === false ) ){
			$allow = false;
		}

		if(isset($_POST["MODIFIER_CD"]) ){
			$_POST["MODIFIER_CD"] = "'".f($_POST["MODIFIER_CD"])."'";
		} else {
			$_POST["MODIFIER_CD"] = "null";
		}


		if( $allow ){
			$EXP->query("
				INSERT INTO PROJECTS_XLS_EXP_FIELDS (
					SYSID, PROJECTID, USERNAME, CONCEPT_PATH, CONCEPT_NAME, MODIFIER ) VALUES
				(SYSIDS.NEXTVAL,'".f($_ENC["PROJECTID"])."','".f($_ENC["USERNAME"])."', '".f($_POST["CONCEPTPATH"])."','".f($_POST["CONCEPTNAME"])."', ".($_POST["MODIFIER_CD"])." )
			");
		} else {
			echo( "<h1 style='color:red'>I'm sorry, that concept has been administratively disabled for export in the public datamart.</h1>" );
		}

	} else {
		echo( "huh??" );
	}


	include( 'Excel_concept_refresh.php' );
?>