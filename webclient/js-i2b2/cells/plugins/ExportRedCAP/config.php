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

	include_once("ezsql/shared/ez_sql_core.php");
	include_once("ezsql/oracle8_9/ez_sql_oracle8_9.php");

	$DB   = new ezSQL_oracle8_9('trials_dev',   '', 'cdw1.urmc.rochester.edu:1521/CDWPROD');
	$EXP  = new ezSQL_oracle8_9('i2b2exports',  '', 'i2b2dev.urmc-sh.rochester.edu:1521/i2b2');
	$PM   = new ezSQL_oracle8_9('i2b2pm',       '', 'i2b2dev.urmc-sh.rochester.edu:1521/i2b2');

	$ENVIRONMENT = "T";
	$CHECKPASSWORDS = false;

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
		$ENCRYPTION_KEY = strtoupper( $destination . date('ymd') );
		$ciphertext = "";
		$mode = "";
		if( function_exists( "mcrypt_decrypt" ) ){
			$mode = "AES";
			$iv_size = mcrypt_get_iv_size(MCRYPT_RIJNDAEL_128, MCRYPT_MODE_CBC);
			$iv = mcrypt_create_iv($iv_size, MCRYPT_DEV_URANDOM);
			$key = pack("H*",md5($ENCRYPTION_KEY));
			$ciphertext = mcrypt_encrypt(MCRYPT_RIJNDAEL_128, $key, $data.$mode, MCRYPT_MODE_CBC, $iv);
			$ciphertext = $iv . $ciphertext;

		} else {
			//better than nothing I suppose. at least you're comparing codes and are obfuscating.
			$mode = "B64";
			$ciphertext = $ENCRYPTION_KEY . $data .$mode;
		}

		$base64String = base64_encode( $ciphertext );
		//make the string into a usable HTML escaped string.
		$base64String = str_replace( "=", "%3D", $base64String);
		$base64String = str_replace( "+", "%2B", $base64String);
		$base64String = str_replace( "/", "%2F", $base64String);

		//echo( "\nKey : $ENCRYPTION_KEY " );
		//echo( "\nbase64 : $base64String " );

		return $mode.$base64String;

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

		$base64String = substr( $base64String, 3 );

		$ciphertext_dec = base64_decode( $base64String );

		//essentially a once use URL. Also uses wordpress' nonce key to further ensure uniqueness.
		$ENCRYPTION_KEY = strtoupper( $receiver . date('ymd') );

		$plaintext_dec = "";
		$mode = "";
		if( function_exists( "mcrypt_decrypt" ) ){
			$mode = "AES";
			$iv_size = mcrypt_get_iv_size(MCRYPT_RIJNDAEL_128, MCRYPT_MODE_CBC);

			//initialize the return value to be nothing with no data to be returned if there isn't enough
			//data in the stream to decode.
			$return = array();
			if( strlen($ciphertext_dec) >= $iv_size ){
				$key = pack("H*",md5($ENCRYPTION_KEY));
				$iv_dec = substr($ciphertext_dec, 0, $iv_size);
				$ciphertext_dec = substr($ciphertext_dec, $iv_size);
				$plaintext_dec = trim(mcrypt_decrypt(MCRYPT_RIJNDAEL_128, $key, $ciphertext_dec, MCRYPT_MODE_CBC, $iv_dec));

				//detect bad data, the end should be intact.
				if( substr($plaintext_dec, -3) != $mode ){
					$plaintext_dec = "";
				} else {
					$plaintext_dec = substr( $plaintext_dec, 0 , strlen($plaintext_dec)-3 );
				}
			}
		} else {
			$mode = "B64";
			//better than nothing I suppose. at least you're comparing codes and are obfuscating.
			if( strpos($ciphertext_dec,$ENCRYPTION_KEY) == 0 && substr($ciphertext_dec, -3) == $mode ){
				$plaintext_dec = substr( $ciphertext_dec, strlen( $ENCRYPTION_KEY ) );
				$plaintext_dec = substr( $plaintext_dec, 0 , strlen($plaintext_dec)-3 );
			} else {
				$plaintext_dec = "";
			}

		}
		//see if you got an array. if not, don't bother.
		if( strpos($plaintext_dec,'=') > 0 ){
			parse_str($plaintext_dec, $return);
		} else {
			$return = $plaintext_dec;
		}

		return $return;

	}

	/**
	 * Most of the Java Services invocation calls are extremely similar. To that end, this function addresses the
	 * Need to create a separate individualized code to start or stop or get the status of a script.
	 * @returns - an associative array with this job's most current statistics.
	 * @param $DB - connection to database
	 * @param $_ENC - the associative array of data representing the study
	 * @param $javaClassName - this is the instance of the java service you're trying to instantiate.
	 * @param $_REQ - the variables passed in, either $_POST or $_GET
	 * @param $ENV - the enviroment to run this on, note $ENVIRONMENT, in this file cannot be scope referenced, it has to be passed in explicitly.
	 */

	function runJavaService( $DB, $_ENC, $javaClassName, $_REQ , $params, $ENV ){
		if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"]) ){
			//look up the last job run.
			$Job_settings = $DB->get_row( "SELECT I2B2_JOBS.*, ".getTime($DB,'LAST_TOUCHED')." AS TIMEOF FROM I2B2_JOBS WHERE PROJECTID=".$_ENC["PROJECTID"]." AND USERNAME='".f($_ENC["USERNAME"])."' AND JOB_TYPE='$javaClassName' ORDER BY JOB_ID DESC"  ,ARRAY_A );

			//if there was some request to start it, then do so.
			if( isset( $_REQ["ACTION"] ) ){
				if( ($Job_settings["STATUS"] == 'Running' || $Job_settings["STATUS"] == 'Queued' || $Job_settings["STATUS"] == 'Created') && $_REQ["ACTION"] == 'Cancel'  ){

					//if the job was running or queued or not yet acknowledged, the cancel it.
					$DB->query( "UPDATE I2B2_JOBS SET STATUS='Cancel' WHERE JOB_ID='".f($Job_settings["JOB_ID"])."'");

				} else {

					//cancel while cancel is silly. So are other statuses. only allow for creation to operate.
					if( $_REQ["ACTION"] == 'Created' ){
						$count = $DB->get_var( "SELECT COUNT(*) AS N FROM I2B2_JOBS WHERE STATUS IN ('Created','Queued','Running') AND JOB_TYPE='$javaClassName' AND PROJECTID=".$_ENC["PROJECTID"]." AND USERNAME='".f($_ENC["USERNAME"])."'");

						if( $count == 0 ){
							$DB->query(
								"INSERT INTO I2B2_JOBS( STATUS, JOB_TYPE, PROJECTID, PROJECTCODE, PARAMS, USERNAME, ENVIRONMENT ) " .
								"VALUES ( 'Created', '$javaClassName', '".$_ENC["PROJECTID"]."', '".$_ENC["PROJECTCODE"]."', '".f($params)."', '".f($_ENC["USERNAME"])."','".$ENV."' )"
							);
						} else {
							echo( "multiple?" );
						}
					} else {
						echo( "Invalid option. current job is ".htmlspecialchars($Job_settings["STATUS"]) );
					}
				}
				$Job_settings = $DB->get_row( "SELECT I2B2_JOBS.*, ".getTime($DB,'LAST_TOUCHED')." AS TIMEOF FROM I2B2_JOBS WHERE PROJECTID=".$_ENC["PROJECTID"]." AND USERNAME='".f($_ENC["USERNAME"])."' AND JOB_TYPE='$javaClassName' ORDER BY JOB_ID DESC"  ,ARRAY_A );
			}
			return $Job_settings;
		} else {
			return array();
		}
	}

	/**
	 * Most of the Java Services invocation calls are extremely similar. To that end, this function addresses the
	 * Need to refresh and to show you the current status as a progress bar.
	 * @returns - a boolean indicating true if done, false if task in progress.
	 * @param $DB - connection to database
	 * @param $_ENC - the associative array of data representing the study
	 * @param $Job_settings - the last known parameters of the job.
	 * @param $taskname - A user friendly name of what this task is accomplishing.
	 * @param $page - what is this job's WS age
	 * @param $_REQ - the variables passed in, either $_POST or $_GET
	 * @param $div - where should the data appear?
	 * @param $supressheader - if the regular progress and boilerplate should be displayed.
	 */

	function showJavaServiceProgressAndRefresh( $DB, $_ENC, $Job_settings, $taskname, $page, $_REQ, $div, $suppressheader ){

		$ans = false;

		if( isset($_ENC["PROJECTID"]) && is_numeric($_ENC["PROJECTID"]) ){

			if( !isset($suppressheader) || !$suppressheader ){
				echo( "<h1>Project # ".htmlspecialchars($_ENC["PROJECTCODE"])."-".htmlspecialchars($_ENC["PROJECTTITLE"])."</h1>" );
				echo( "<h4>Job Started On " . date('m/d/Y', strtotime($Job_settings["ACTUAL_START_TIME"] )) . " With status of ".$Job_settings["STATUS"]. ", last updated " . $Job_settings["TIMEOF"] . "</h4>" );

				$pct = intval($Job_settings["PERCENT_COMPLETE"] );
				echo( "<table width='95%' border=1 CELLPADDING=0 CELLSPACING=0><tr>" );
				echo( "<td width='".$pct."%' style='background-color:#D9ECF0;'>&nbsp;<br>" . $pct."%<br>&nbsp;</td>" );
				if( $pct < 100 ){
					echo( "<td width='".(100-$pct)."%'>&nbsp;</td>" );
				}
				echo("</tr></table><br><br>");
			}
			if( !isset($_REQ['NOTNEW']) ){
				$_REQ['NOTNEW'] = 0;
			} else {
				$_REQ['NOTNEW'] = intval($_REQ['NOTNEW']);
			}
			if( trim($Job_settings["STATUS"] ) == "Created" ||
				trim($Job_settings["STATUS"] ) == "Running" ||
				trim($Job_settings["STATUS"] ) == "Queued" ||
				(intval($_REQ['NOTNEW']) < 3 && trim($Job_settings["STATUS"]) == "Cancel" )){

				$DB->query( "UPDATE I2B2_JOBS SET LAST_UI_TOUCHED=SYSDATE WHERE JOB_ID='".f($Job_settings["JOB_ID"])."'");

				if( trim($Job_settings["STATUS"] ) != "Cancel" ){ ?>
					<input type="button" onClick="
						if(confirm('Cancel Export of Databases Now?')){
							jQuery('<?php echo($div);?>').load(
								'<?php echo($page);?>',
								{SESSIONCODE : '<?php echo($_REQ["SESSIONCODE"]);?>', ACTION : 'Cancel', NOTNEW: 'Y' }
							);
					}" value="Cancel <?php echo($taskname);?>"> <?php

				} else {
					echo( "<b>Cancelling $taskname... Please wait...</b>" );
				} ?>
				<script>
					setTimeout(
						function(){
							jQuery('<?php echo($div);?>').load(
								'<?php echo($page);?>',
								{SESSIONCODE : '<?php echo($_REQ["SESSIONCODE"]);?>', NOTNEW: '<?php echo(intval($_REQ["NOTNEW"])+1); ?>'}
							)
						}, 5000);
				</script>

			<?php } else { ?>

				<input type="button" onClick="
					jQuery('<?php echo($div);?>').load(
						'<?php echo($page);?>',
						{SESSIONCODE : '<?php echo($_REQ["SESSIONCODE"]);?>', ACTION : 'Created' }
					);
				" value="Start <?php echo($taskname);?>">

			<?php $ans = true;

			}
		}
		return $ans;
	}

	function showJavaServiceLog( $DB, $jobid ){

		if( isset($jobid) && is_numeric($jobid) ){

			$logs = $DB->get_results( "
				SELECT I2B2_JOBS_LOG.*, ".getTime($DB,'DATEOF')." AS TIMEOF
				FROM I2B2_JOBS_LOG
				WHERE JOB_ID='".$jobid."'
				ORDER BY DATEOF
			"  ,OBJECT );

			echo( "<br><ul>" );
			$continues = false;
			if( $logs ){
				foreach ( $logs as &$item) {

					if( !$continues ){
						echo( "<li><pre><b>".$item->TIMEOF."</b> " );
					}

					if( strpos($item->DESCR,'Exception: ') > 0 ){
						$item->DESCR = substr( $item->DESCR, strpos($item->DESCR,'Exception: ') + 11);
						$item->DESCR = substr( $item->DESCR, 0, strpos($item->DESCR,"\n"));
						$item->DESCR = ( "<span style='color:red;font-weight:bold'>Error : " . $item->DESCR . "</span>");
					}
					echo (str_replace('<continues/>','',$item->DESCR));
					$continues = strpos($item->DESCR,'<continues/>');

					if( !$continues ){
						echo('</pre></li>');
					}
				}
			}
			echo( "</ul>" );
		}
	}

	function getTime( $DB, $var ){
		if( get_class( $DB ) == "ezSQL_oracle8_9"  || get_class( $DB ) == "ezSQL_postgresql" ){
			return "TO_CHAR($var,'HH24:MI:SS')";
		} elseif ( get_class( $DB ) == "ezSQL_mssql" || get_class( $DB ) == "ezSQL_sqlsrv" ){
			return "CONVERT(VARCHAR(8),$var,108)";
		} elseif ( get_class( $DB ) == "ezSQL_mysql" || get_class( $DB ) == "ezSQL_mysqli" ){
			return "DATE_FORMAT($var, '%H:%i:%s')";
		} else {
			return $var;
		}
	}

?>