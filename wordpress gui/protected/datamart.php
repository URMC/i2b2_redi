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
	 * This page is a generic Java Service invocation module. It allows for you to
	 * start, cancel and view statuses and logs from the java services component.
	 * The page creates a refresh timer that updates the middle part of the display showing
	 * the job log as well as the status.
	 */

	require_once("../common.php");
	if( !isset( $_POST["ENC"] ) ){
		$_ENC = decryptGetMethod();
	} else {
		$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
	}

    if( isLoggedIn() && isset($_ENC["SYSID"]) &&  current_user_can( 'manage_options' ) ){

	if( !isset( $_ENC["NOTNEW"] ) ){
		get_header();
	}

	$pest = $DB->get_row( "SELECT PROJECTCODE, PROJECTTITLE FROM PROJECTS WHERE PROJECTID = '".$_ENC["SYSID"]."' "  ,OBJECT );

	echo( "<h2>".$_ENC["JOB_DESC"]." For: ". $pest->PROJECTTITLE. "</h2>" );

	$hive = $HIVE->get_var( "SELECT c_db_fullschema FROM crc_db_lookup WHERE UPPER(c_project_path) =UPPER('/".$pest->PROJECTCODE."/')" );
	$isfat = ("1" == $HIVE->get_var( "select count(*) AS COUNTER from ALL_TABLES WHERE table_name='OBSERVATION_FACT' AND UPPER(OWNER)=UPPER('$hive')" ));

	$Job_settings = $DB->get_row( "SELECT I2B2_JOBS.*, TO_CHAR(LAST_TOUCHED,'HH24:MI:SS') AS TIMEOF FROM I2B2_JOBS WHERE PROJECTID=".$_ENC["SYSID"]." AND USERNAME='".f($_SESSION["USERNAME"])."' AND JOB_TYPE='".$_ENC["JOB_TYPE"]."' ORDER BY JOB_ID DESC"  ,OBJECT );
	$DB->query( "UPDATE I2B2_JOBS SET LAST_UI_TOUCHED=SYSDATE WHERE JOB_ID='".f($Job_settings->JOB_ID)."'");

	if( isset( $_ENC["ACTION"] ) ){
		if( ($Job_settings->STATUS == 'Running' || $Job_settings->STATUS == 'Queued' || $Job_settings->STATUS == 'Created') && $_ENC["ACTION"] == 'Cancel'  ){

			$DB->query( "UPDATE I2B2_JOBS SET STATUS='Cancel' WHERE JOB_ID='".f($Job_settings->JOB_ID)."'");

		} else {

			$Project_settings = $DB->get_row( "SELECT PROJECTCODE FROM PROJECTS WHERE PROJECTID='".f($_ENC["SYSID"])."'" );

			$count = $DB->get_var( "SELECT COUNT(*) AS N FROM I2B2_JOBS WHERE STATUS IN ('Created','Queued','Running') AND JOB_TYPE='".$_ENC["JOB_TYPE"]."' AND PROJECTID='".f($_ENC["SYSID"])."' AND USERNAME='".f($_SESSION["USERNAME"])."'");
			if( $count == 0 && ($_ENC["ACTION"] == 'Queued' || $_ENC["ACTION"] == 'Created') ){
				$DB->query(
					"INSERT INTO I2B2_JOBS( JOB_ID, STATUS, JOB_TYPE, PROJECTID, PROJECTCODE, PARAMS, USERNAME, ENVIRONMENT ) " .
					"VALUES ( -1, 'Created', '".$_ENC["JOB_TYPE"]."', '".$_ENC["SYSID"]."', '".f($Project_settings->PROJECTCODE)."', '".f($Session_settings->SESSIONID)."', '".f($_SESSION["USERNAME"])."','".$ENVIRONMENT."' )"
				);
			}

		}
		$Job_settings = $DB->get_row( "SELECT I2B2_JOBS.*, TO_CHAR(LAST_TOUCHED,'HH24:MI:SS') AS TIMEOF FROM I2B2_JOBS WHERE PROJECTID='".f($_ENC["SYSID"])."' AND USERNAME='".f($_SESSION["USERNAME"])."' AND JOB_TYPE='".$_ENC["JOB_TYPE"]."' ORDER BY JOB_ID DESC"  ,OBJECT );
	}

	echo( "<h4>Job Started On " . date('m/d/Y', strtotime($Job_settings->ACTUAL_START_TIME)) . " With status of ".$Job_settings->STATUS. ", last updated " . $Job_settings->TIMEOF . "</h4>" );

	$pct = intval($Job_settings->PERCENT_COMPLETE);
	echo( "<table width='95%' border=1 CELLPADDING=0 CELLSPACING=0><tr>" );
	echo( "<td width='".$pct."%' style='background-color:#D9ECF0;'>&nbsp;<br>" . $pct."%<br>&nbsp;</td>" );
	if( $pct < 100 ){
		echo( "<td width='".(100-$pct)."%'>&nbsp;</td>" );
	}
	echo("</tr></table><br><br>");

	if( trim($Job_settings->STATUS) == "Created" ||
		trim($Job_settings->STATUS) == "Queued"  ||
		trim($Job_settings->STATUS) == "Running" ||
		(intval($_ENC['NOTNEW']) < 3 && trim($Job_settings->STATUS) == "Cancel" )){ ?>

		<?php if( trim($Job_settings->STATUS) != "Cancel" ){ ?>
			<input type="button" value="Cancel <?php echo($_ENC["JOB_DESC"]);?>" onClick="
				if(confirm('Cancel?')){
					$('#main_content').load(
						'datamart.php',
						{ENC: '<?php echo(encryptParameters('datamart.php','ACTION=Cancel&NOTNEW=Y&SYSID='.$_ENC['SYSID'].'&JOB_TYPE='.$_ENC['JOB_TYPE'].'&JOB_DESC='.$_ENC['JOB_DESC']));?>' }
					);
			}" >
		<?php } else {  ?>
			<b>Cancelling... Please wait...</b>
		<?php } ?>

		<script>
			setTimeout(
				function(){
					$('#main_content').load(
						'datamart.php',
						{ ENC: '<?php echo(encryptParameters("datamart.php","NOTNEW=".(intval($_ENC["NOTNEW"])+1)."&SYSID=".$_ENC["SYSID"].'&JOB_TYPE='.$_ENC['JOB_TYPE'].'&JOB_DESC='.$_ENC['JOB_DESC']));?>' }
					)
				}, 5000);
		</script>

	<?php } else { ?>

		<input type="button" value="Start <?php echo($_ENC["JOB_DESC"]);?>" onClick="
			$('#main_content').load(
				'datamart.php',
				{ENC: '<?php echo(encryptParameters('datamart.php','NOTNEW=Y&ACTION=Created&SYSID='.$_ENC['SYSID'].'&JOB_TYPE='.$_ENC['JOB_TYPE'].'&JOB_DESC='.$_ENC['JOB_DESC']));?>' }
			);
		" >
		<?php
	}

	$logs = $DB->get_results( "
		SELECT I2B2_JOBS_LOG.*, TO_CHAR(DATEOF,'HH24:MI:SS') AS TIMEOF
		FROM I2B2_JOBS_LOG
		WHERE JOB_ID='".f($Job_settings->JOB_ID)."'
		ORDER BY DATEOF
	"  ,OBJECT );

	echo( "<ul>" );
	$continues = false;
	if( is_array( $logs ) ){
		foreach ( $logs as &$item) {

			if( !$continues ){
				echo( "<li><pre><b>".$item->TIMEOF."</b> " );
			}

			echo (str_replace('<continues/>','',$item->DESCR));
			$continues = strpos($item->DESCR,'<continues/>');

			if( !$continues ){
				echo('</pre></li>');
			}
		}
	}
	echo( "</ul>" );



	echo( "<p>Ahoy ". date('m/d/Y h:i:s a', time())."</p>" );


	if( !isset( $_ENC["NOTNEW"] ) ){
		get_footer();
	}


} else {
	echo( "not logged in??? ");
} ?>
