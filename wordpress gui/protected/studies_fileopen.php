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
	 * This page, when provided the project and they type of file, and optionally date, via
	 * GET encrypted data. It then logs in and extracts that blob and feeds it to the end
	 * user. It tries to do a minimal hack at trying to determine the document MIME type
 	 */
	require_once("../common.php");

	$_ENC = decryptGetMethod();

	if( isLoggedIn() ){

		$sql = "SELECT FILENAME, FILECONTENTS
				FROM PROJECTS_BLOBS
				WHERE FIELDNAME='".$_ENC["TYPE"]."' AND
					  PROJECTID='".$_ENC["SYSID"]."'";

		if(isset($_ENC['DATE'])){
			$sql = $sql . "AND UPDATED=to_date('" . $_ENC["DATE"] . "','mm/dd/yyyy hh:mi:ss AM')";
		}

		$sql = $sql . "ORDER BY UPDATED DESC";


		//make a null query just to ensure that the connection is on, so the connection information is in the common.php.
		$DB->get_row( "SELECT SYSDATE FROM DUAL"  ,OBJECT );

		$conn = $DB->dbh;

		$stid = oci_parse($conn, $sql);

		oci_execute($stid);
		$row = oci_fetch_array($stid, OCI_ASSOC+OCI_RETURN_NULLS);
		if (!$row) {
			header('Status: 404 Not Found');
		} else {
			header("Content-Transfer-Encoding: Binary");
			if( substr_compare(strtoupper(trim($row['FILENAME'])), ".PDF", -4, 4) === 0 ){
				header('Content-Type: application/pdf');
			} elseif( substr_compare(strtoupper(trim($row['FILENAME'])), ".TXT", -4, 4) === 0 ){
				header('Content-Type: text/plain');
			} else {
				header('Content-Type: application/octet-stream');
				header("Content-disposition: attachment; filename=\"".str_replace('-',' ',trim($row['FILENAME']))."\"");
			}
			$img = $row['FILECONTENTS']->load();

			header('Content-Length: '.strlen($img) );
			header("Cache-Control: no-Store,no-Cache' ");
			header("Pragma: no-Cache");
			print $img;
		}
	} else {
		echo( "failed." );
	}
?>