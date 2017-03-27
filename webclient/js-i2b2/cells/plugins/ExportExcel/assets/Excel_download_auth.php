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
	 * This page performs an LDAP lookup to see if the passwords are correct.
	 */


	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "ExcelExport", $_POST["SESSIONCODE"] );
	}

	if( !isset($_ENC["PROJECTID"]) || !is_numeric($_ENC["PROJECTID"])){
		echo( "n" );
	} elseif( $CHECKPASSWORDS && isset($_REQUEST["PASSWORD"]) && isset($_ENC["USERNAME"]) ){
		echo( validatePassword( $_ENC["USERNAME"], $_REQUEST["PASSWORD"] ) );
	} elseif( !$CHECKPASSWORDS ) {
		echo("Y");
	} else {
		echo("n");
	}
?>