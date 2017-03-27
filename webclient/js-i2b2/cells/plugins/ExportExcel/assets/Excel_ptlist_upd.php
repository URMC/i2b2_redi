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
	 * This page updates the patient list.
	 */

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "ExcelExport", $_POST["SESSIONCODE"] );
	}

	//if you get here - you must have been configured, and logged in, yay!
	if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"]) && isset($_POST["CONCEPTNAME"]) && isset($_POST["CONCEPTPATH"]) ){

		$EXP->query("
			UPDATE PROJECTS_XLS_EXP_JOBS
			SET
				RESULT_INSTANCE_ID='".intval($_POST["CONCEPTPATH"])."',
				PATIENT_LIST_DESC='".f($_POST["CONCEPTNAME"])."'
			WHERE
				USERNAME='".f($_SESSION["USERNAME"])."' AND
				PROJECTID='".f($_ENC["PROJECTID"])."'
		");

		echo("k.");

	} else {
		echo( "huh?" );
	}
?>