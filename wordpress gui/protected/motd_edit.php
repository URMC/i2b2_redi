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
	 * This page is designed to display end edit the current message of the day.
 	 */
	require_once("../common.php");

	if( isLoggedIn() && current_user_can( 'manage_options' )  ){

		get_header();

		if( isset( $_REQUEST['ISACTIVE'] ) && isset( $_REQUEST['MOTD'] )){

			if( !isset($_REQUEST['SYSID']) || intval($_REQUEST['SYSID']) == 0 ){
				$pest = $DB->get_row( "SELECT SYSIDS.NEXTVAL AS NV FROM DUAL"  ,OBJECT );
				$_REQUEST['SYSID'] = $pest->NV;
				$pest = $DB->query( "INSERT INTO MOTD (SYSID) VALUES ('".f($_REQUEST['SYSID'])."')" );
			}

			$pest = $DB->query( "UPDATE MOTD SET MOTD='".f($_REQUEST['MOTD'])."', ISACTIVE='".f($_REQUEST['ISACTIVE'])."'  WHERE SYSID='".f($_REQUEST['SYSID'])."' " );

		}

		$pest = $DB->get_row( "SELECT * FROM MOTD WHERE ISACTIVE='Y' ORDER BY SYSID DESC" );

		echo( "<h1>Set Message Of the Day</h1>" );

		echo( "When this message is set, it will page us if there is data access when system maintainance is in effect." );

		echo( "<form method='post'>" );
		echo( "<input type='hidden' name='SYSID' value='".$pest->SYSID."'>" );
		echo( "<textarea rows=20 style='width:95%' name='MOTD'>".$pest->MOTD."</textarea>" );
		echo( "Active? <select name='ISACTIVE'>" );
			echo( "<option value='Y' ". ($pest->ISACTIVE=='Y'?"selected":"").">Active</option>" );
			echo( "<option value='N' ". ($pest->ISACTIVE=='N'?"selected":"").">Not Active</option>" );
		echo( "</select>" );
		echo( "<input type='submit'>" );


		get_footer();

    } else { header("Location:login.php");  }

?>