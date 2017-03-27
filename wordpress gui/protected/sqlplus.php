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
	 * This page is a basic SQL access tool for managing the wordpress side of the database.
 	 */

	require('../wp-blog-header.php');
	get_header();
	global $current_user;
	get_currentuserinfo();
	if( is_user_logged_in()  && current_user_can( 'manage_options' ) ) {

		if($_POST["SQL"]){
			$sql = preg_replace('/[\x7F-\xFF]/','',preg_replace('/[\x00-\x1F]/', '', str_replace( "\'","'", $_POST["SQL"] )));
		} else {
			$sql = preg_replace('/[\x7F-\xFF]/','',preg_replace('/[\x00-\x1F]/', '', str_replace( "\'","'", $_GET["SQL"] )));
		}
		$wpdb->show_errors();



		echo("<h2>Do It!!!</h2>");

		echo("<form method='POST'><textarea name='SQL' style='width:100%;height:200px;'>". $sql ."</textarea><br><input type='submit'></form>");

		$hmm = array();
		$hmm["HMM"]="Not an allowed command.";
		$createtable = array($hmm);

		if( strpos(strtoupper($sql),'SELECT') === 0 ||
			strpos(strtoupper($sql),'UPDATE') === 0 ||
			strpos(strtoupper($sql),'INSERT') === 0 ||
			strpos(strtoupper($sql),'SHOW') === 0 ||
			strpos(strtoupper($sql),'DESC') === 0 ){
			$createtable = $wpdb->get_results( $sql , ARRAY_A );
		}
		$i = 0;
		print "<table>";
		foreach ($createtable as &$values) {

		    if( $i == 0 ){
		    	print "<tr><th>N</th>";
				foreach ($values as $key => $value) {
					print "<th>$key</th>";
				}
				print "</tr>";
		    }

		    print "<tr><th>$i</th>";
			foreach ($values as $key => $value) {
				print "<td>$value</td>";
			}
		    print "</tr>";

		    $i++;
		}

		if( $i == 0 ){
			echo "executed";
		}
		print "</table>";


		$wpdb->query(
			$wpdb->prepare("INSERT INTO `wp_wpsyslog`( severity, user, module, message ) values ( '5', %s, 'sql', %s )", $current_user->id, $sql )
		);

		echo("<h2>Last 25 queries</h2>");
		$issues = $wpdb->get_results( $wpdb->prepare("SELECT time, message FROM wp_wpsyslog WHERE module='sql' ORDER BY time DESC LIMIT 50"));
		echo("<ol>");
		foreach ($issues as &$issue) {
			echo("<li>".$issue->time." - <a href='sqlplus.php?SQL=".urlencode($issue->message)."'>". $issue->message ."</a></li>");
		}
		echo("</ol>");


	} else {
		echo( "not logged in or not admin");
	}
	get_footer();
?>