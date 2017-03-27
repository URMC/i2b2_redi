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
	 * This page marks is downstream from the studiesmenu.php page and marks a project
	 * and this time really delete it.
	 */

	require_once("../common.php");

	$_ENC = decryptGetMethod();

	if( is_numeric($_ENC["PROJECTID"]) ){
		$DB->query( "DELETE FROM PROJECTS WHERE PROJECTID='".$_ENC["PROJECTID"]."'");
		header( "Location:studiesmenu.php" );
	} else {
		echo( $_ENC["PROJECTID"] );
	}



?>