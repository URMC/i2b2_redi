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
 * This is a file checker for checking whether or not the excel spreadsheet import
 * has finished, this is to be replaced soon with a new Java Service that does the
 * same with the protections that service grants.
 *
 * This is downstream and ajax invoked from studiespatientexcelupload.php.
 */

require_once("../common.php");
if( !isset( $_POST["ENC"] ) ){
	$_ENC = decryptGetMethod();
} else {
	$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
}
?>
<pre>
Processing...
<?php
	if( isLoggedIn() && isset($_ENC["LOCATION"]) ){

		$location = $DB->get_var("SELECT LOC FROM PROJECTS_UPLOADS WHERE ID='".intval($_ENC["LOCATION"])."'");
		readfile("/var/www/html/".$location.".log");
	} else {
		echo ("?");
	}
?>
</pre>