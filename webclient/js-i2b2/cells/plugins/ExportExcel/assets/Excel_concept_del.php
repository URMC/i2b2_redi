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
	 * This page deletes the row that had the specific item passed.
	 *
	 * This page then includes the table (refresh.php) again, which is replaces the
	 * saved settings section as with the delete and update pages in this plugin
	 */

	require_once("../config.php");

	$_ENC = array();
	if( isset( $_POST["SESSIONCODE"] ) ){
		$_ENC = decryptParameters( "ExcelExport", $_POST["SESSIONCODE"] );
	}

	if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"]) && isset($_POST["ITEMID"]) && is_numeric($_POST["ITEMID"])){

		$EXP->query("
			DELETE FROM PROJECTS_XLS_EXP_FIELDS WHERE SYSID='".intval($_POST["ITEMID"])."' AND PROJECTID='".f($_ENC["PROJECTID"])."'
		");


	}

	include( 'Excel_concept_refresh.php' );
?>