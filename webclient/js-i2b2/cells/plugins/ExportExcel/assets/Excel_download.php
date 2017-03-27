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
	 * This page fetches the blob and sends it as an excel file,
	 */

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "ExcelExport", $_POST["SESSIONCODE"] );
	}

	if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"])){

		$sql = "SELECT LASTMOST_SHEET FROM PROJECTS_XLS_EXP_JOBS
				WHERE PROJECTID='".f($_ENC["PROJECTID"])."' AND
					  USERNAME='".f($_ENC["USERNAME"])."'
				";

		//make a null query just to ensure that the connection is on, so the connection information is in the common.php.
		$EXP->get_row( "SELECT SYSDATE FROM DUAL"  ,OBJECT );
		$conn = $EXP->dbh;

		$stid = oci_parse($conn, $sql);
		$img = "";
		oci_execute($stid);
		$row = oci_fetch_array($stid, OCI_ASSOC+OCI_RETURN_NULLS);
		if (!$row || !$row['LASTMOST_SHEET']) {
			header('Status: 404 Not Found');
		} else {
			$img = $row['LASTMOST_SHEET']->load();
		}

		$filename =  "Excel Report For Project ".$_REQUEST["SYSID"]." - " . date("YmdHi");

		header("Content-type: application/vnd.ms-excel; name='excel'");
		header("Content-disposition: attachment; filename=$filename.xlsx");
		header('Content-Length: '.strlen($img) );
		header("Cache-Control: no-Store,no-Cache' ");
		header("Pragma: no-Cache");

		print $img;
	} else {
		echo( "not logged in" );
	}
?>