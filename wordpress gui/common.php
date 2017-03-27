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
	 * This page is the central configuration page and common functions for the URMC integrations toolset.
	 */

	include_once("lib/dBug.php");
	include_once("lib/ezsql/shared/ez_sql_core.php");
	include_once("lib/ezsql/oracle8_9/ez_sql_oracle8_9.php");
	require_once("wp-blog-header.php");

	$DB   = new ezSQL_oracle8_9('trials',       '', 'cdw1.urmc.rochester.edu:1521/CDWPROD');
	$EXP  = new ezSQL_oracle8_9('i2b2exports',  '', 'i2b202.urmc-sh.rochester.edu:1521/i2b2');
	$HIVE = new ezSQL_oracle8_9('i2b2hive',     '', 'i2b202.urmc-sh.rochester.edu:1521/i2b2');
	$META = new ezSQL_oracle8_9('i2b2metadata', '', 'i2b202.urmc-sh.rochester.edu:1521/i2b2');
	$PM   = new ezSQL_oracle8_9('i2b2pm',       '', 'i2b202.urmc-sh.rochester.edu:1521/i2b2');
	$DATA = new ezSQL_oracle8_9('i2b2demodata', '', 'i2b202.urmc-sh.rochester.edu:1521/i2b2');
	$DEID = new ezSQL_oracle8_9('i2b2datadeid', '', 'i2b202.urmc-sh.rochester.edu:1521/i2b2');

	$ENVIRONMENT="T";

	/**
	 * Curse you security testing. This manually sets the cookie parameters to resolve.
	 */
	if (!isset($_SESSION)) {
		session_start();
		$currentCookieParams = session_get_cookie_params();
		$sidvalue = session_id();
		setcookie(
			'PHPSESSID',//name
			$sidvalue,//value
			0,//expires at end of session
			$currentCookieParams['path'],//path
			$currentCookieParams['domain'],//domain
			isset($_SERVER["HTTPS"]), //secure
			true  //httponly
		);
	}


	/**
	 * This function encrypts data for secure transmissions between sending and receiving pages. Utilizing
	 * this function applies the destination page in the encryption string, so that a link can't be copied between
	 * pages to apply "copy the hash" hack. Each piece of data is encrypted with an environment and a date key,
	 * so that the data cannot be copied cross implementation, or between test or prod and so that a string has a short
	 * lifespan.
	 *
	 * @param $destination - this is the page or string that both sending and receiving pages agree on.
	 * @param $data - this is the data to encrypt
	 * @returns - $data converted to URLSafe(Base64(AES128($data)))
	 */
	function encryptParameters( $destination, $data ){

		//essentially a once use URL. Also uses wordpress' nonce key to further ensure uniqueness.
		$ENCRYPTION_KEY = strtoupper( $ENVIRONMENT . $destination . date('ymd'). NONCE_KEY );

		$iv_size = mcrypt_get_iv_size(MCRYPT_RIJNDAEL_128, MCRYPT_MODE_CBC);
		$iv = mcrypt_create_iv($iv_size, MCRYPT_DEV_URANDOM);
		$key = pack("H*",md5($ENCRYPTION_KEY));
		$ciphertext = mcrypt_encrypt(MCRYPT_RIJNDAEL_128, $key, $data, MCRYPT_MODE_CBC, $iv);
		$ciphertext = $iv . $ciphertext;

		$base64String = base64_encode( $ciphertext );
		//make the string into a usable HTML escaped string.
		$base64String = str_replace( "=", "%3D", $base64String);
		$base64String = str_replace( "+", "%2B", $base64String);
		$base64String = str_replace( "/", "%2F", $base64String);

		//echo( "\nKey : $ENCRYPTION_KEY " );
		//echo( "\nbase64 : $base64String " );

		return $base64String;

	}

	/**
	 * This is a convenience method to just decrypt the parameters from the GET string, assuming
	 * the page is the destination and the data was passed in via the GET parameters.
	 * @returns the decoded $_GET string.
	 */
	function decryptGetMethod(){
		return decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_SERVER['QUERY_STRING']);
	}

	/**
	 * This is the omega to the encrypt alpha. The decodes the data back to raw format.
 	 * @param $receiver - this is the page or string that both sending and receiving pages agree on.
 	 * @param $base64String - $data converted to URLSafe(Base64(AES128($data))) from encode
 	 * @returns array - an associative array that contains the original data
 	 */
	function decryptParameters( $receiver, $base64String ){


		//reverse the URL crap that might have been added.
		$base64String = str_replace("%3D", "=", $base64String);
		$base64String = str_replace("%2B", "+", $base64String);
		$base64String = str_replace("%2F", "/", $base64String);
		$base64String = str_replace(" ", "+", $base64String);

		$ciphertext_dec = base64_decode( $base64String );

		//essentially a once use URL. Also uses wordpress' nonce key to further ensure uniqueness.
		$ENCRYPTION_KEY = strtoupper( $ENVIRONMENT . $receiver . date('ymd') . NONCE_KEY );
		$iv_size = mcrypt_get_iv_size(MCRYPT_RIJNDAEL_128, MCRYPT_MODE_CBC);

		//initialize the return value to be nothing with no data to be returned if there isn't enough
		//data in the stream to decode.
		$return = array();
		if( strlen($ciphertext_dec) >= $iv_size ){
			$key = pack("H*",md5($ENCRYPTION_KEY));
			$iv_dec = substr($ciphertext_dec, 0, $iv_size);
			$ciphertext_dec = substr($ciphertext_dec, $iv_size);
			$plaintext_dec = mcrypt_decrypt(MCRYPT_RIJNDAEL_128, $key, $ciphertext_dec, MCRYPT_MODE_CBC, $iv_dec);
			parse_str($plaintext_dec, $return);
		}
		//echo( "\nKey : $ENCRYPTION_KEY " );
		//echo( "\nplain : $plaintext_dec " );

		return $return;

	}

	/**
	 * This is a deprecated transition function from our old system which had its own authentication system.
	 * It is also used to check the availability of the system for the integrations in the plugins.
	 * This now utilizes the standard wordpress functions.
	 * @returns boolean representing if the user is logged in.
	 */
	function isLoggedIn(){
		$answer = false;

		if( is_user_logged_in() ){
			global $current_user;
			get_currentuserinfo();
			$_SESSION["USERNAME"] = $current_user->user_login;
			$_SESSION['IS_LOGGED_IN'] = 'Y';
			if( current_user_can( 'manage_options' ) ){
			 	$_SESSION['ADMIN'] = 'Y';
			}
			$answer = true;
		} else {
			unset($_SESSION['USERNAME']);
			unset($_SESSION['IS_LOGGED_IN']);
			$_SESSION['USERNAME'] = null;
			$_SESSION['IS_LOGGED_IN'] = null;
		}

		return $answer;
	}


	function logout(){
		unset($_SESSION);
		session_destroy();
		wp_logout();
	}

	/**
	 * Quick field fixup for escaping or simply removing that data from the post fields that are safe to insert.
	 */
	function f( $s ){
		return htmlentities(preg_replace('/[\x7F-\xFF]/','',preg_replace('/[\x00-\x1F]/', '',  str_replace("'","''",$s))),ENT_NOQUOTES,'UTF-8',false);
	}

	/**
	 * this removes things that are nasty from a file name perspective.
	 */
	function filefix( $s ){
		return f(str_replace(array("\\","/","<",">","'",'"','|',':','*','?','%'),"", $s));
	}

	/**
	 * This function returns the maximum upload size for end user knowledge.
	 */
	function file_upload_max_size() {
	  static $max_size = -1;

	  if ($max_size < 0) {
	    // Start with post_max_size.
	    $max_size = (ini_get('post_max_size'));

	    // If upload_max_size is less, then reduce. Except if upload_max_size is
	    // zero, which indicates no limit.
	    $upload_max = (ini_get('upload_max_filesize'));
	    if ($upload_max > 0 && $upload_max < $max_size) {
	      $max_size = $upload_max;
	    }
	  }
	  return $max_size;
	}

?>