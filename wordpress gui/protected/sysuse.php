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
 * This is called from studiesanalyse.php to check permissions and checks conditions that we don't want end users to forget.
 * This is a last check in the data governanace system.
 *
 * This then forwards to the webclient's login.php page.
 */

require_once("../common.php");
if( !isset( $_POST["ENC"] ) ){
	$_ENC = decryptGetMethod();
} else {
	$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
}
?>

<?php if( isLoggedIn() && isset($_ENC["SYSID"]) && is_numeric($_ENC["SYSID"])){

	if( !current_user_can( 'manage_options' ) ){
		if( isset($_ENC["SYSUSE"]) ){
			$DB->query( "UPDATE PROJECTS SET SYSUSE='Y' WHERE PROJECTID='".intval($_ENC["SYSID"])."'" );
		}
	}
	$mydb = $DB->get_row( "
		SELECT SYSUSE, PROJECTTITLE, PROJECTCODE, IRBENDDATE, WILLDOCITATIONS
		FROM PROJECTS
		WHERE PROJECTID=".intval($_ENC["SYSID"])."
	"  ,OBJECT );

	$mydb = $DB->get_row( "
			SELECT SYSUSE, PROJECTTITLE, PROJECTCODE, IRBENDDATE, WILLDOCITATIONS, PROJECTTYPE
			FROM PROJECTS
			WHERE PROJECTID=".intval($_ENC["SYSID"])."
	"  ,OBJECT );

	//CRI-472: attempting to block users from doing silly things.
	$jobs = $DB->get_results( "SELECT (SYSDATE-ACTUAL_END_TIME) AS DAYS_SINCE, JOB_TYPE, ACTUAL_END_TIME FROM i2B2_jobs WHERE ACTUAL_END_TIME IS NOT NULL AND STATUS LIKE 'Complete%' AND PROJECTID=".intval($_ENC["SYSID"])." ORDER BY ACTUAL_END_TIME DESC",OBJECT );
	$xlsx = $DB->get_row( "SELECT LASTER, SYSDATE-LASTER AS LASTMOST FROM (SELECT MAX(UPDATED) AS LASTER FROM ENROLLED_PATIENT WHERE PROJECTID=".intval($_ENC["SYSID"])." ) A"  ,OBJECT );

	$done = $xlsx->LASTMOST;
	$donedate = $xlsx->LASTER;
	$loaded = 0 ;
	$created = 0 ;
	$deleted = 0;
	$ready = ($done == ''); //blank data should be allowed a free pass for now.
	$lasttime = 0;

	foreach($jobs as &$line){
		if( $line->JOB_TYPE =='DatamartLoader' ){
			$loaded = $line->DAYS_SINCE ;
			if( $line->DAYS_SINCE < $done ){
				$ready = $line->DAYS_SINCE ;
			} else {
				$lasttime = $line->ACTUAL_END_TIME;
			}
		}
		if( $line->JOB_TYPE =='DatamartCreator' ){
			$created = $line->DAYS_SINCE;
		}
		if( $line->JOB_TYPE =='DatamartDeleter' ){
			$deleted = $line->DAYS_SINCE ;
		}
	}

	//CRI-498 - adding code to block expired projects
	$dateofexp = strtotime($mydb->IRBENDDATE);
	if( strpos( $mydb->IRBENDDATE, "/" ) <= 0 ){
		$dateofexp = 0;
	}

	if( $mydb->PROJECTCODE == '' || $mydb->PROJECTTYPE != 'APPROVED' ){
		$dateofexp = time(); //to bypass the next few logics
	}

	if( $mydb->PROJECTTYPE != 'APPROVED' ){
		$mydb->WILLDOCITATIONS = 'XXX';
	}

	if( ( ($mydb->SYSUSE == "Y" || current_user_can( 'manage_options' )) && $dateofexp > 0 && $mydb->WILLDOCITATIONS != '' && $created && $ready && $loaded && ($ready <= $loaded) ) ){

		//CRI-172 - better systems usage tracking.
		$DB->query( "UPDATE PROJECTS SET SUBMITTED_DATE=SYSDATE WHERE PROJECTID='".f($_ENC["SYSID"])."'");

		$motd = $DB->get_row( "SELECT * FROM MOTD WHERE ISACTIVE='Y' ORDER BY SYSID DESC" );
		?>
		<script type="text/javascript" src="/js/jquery-1.9.0.min.js"></script>

		<form method="post" action="/webclient17/login.php" name="SUBMITIT" id="SUBMITIT" >
			<input type="hidden" name="ENC" value='<?php echo(encryptParameters("login.php","SYSID=".$_ENC["SYSID"]."&MESSAGE=$motd->MOTD&VALIDFOR=".(time()+60)));?>'>
		</form>

		All is well.
		<script type="text/javascript" >
		$(document).ready(
			function(data) {
				$('form').submit();
			}
		);
		</script>

		<?php

	} else {

		$hardstop = false;
		get_header();

		if( $dateofexp < time() ){

			echo("<h2>Project Expired</h2>");
			echo("<h4>The expiration date of $mydb->IRBENDDATE is either expired or an invalid date (formatted m/d/yy).<h4>");
			echo("<br>");
			new dBug( $mydb );
			echo("<br>");
			echo("<input type='button' value='Edit Project Request To Update' onClick=\"window.location='studiesopen.php?".encryptParameters('studiesopen.php','SYSID='.$_ENC["SYSID"])."';\">");
			$hardstop = true;
		}
		if( $mydb->WILLDOCITATIONS == '' ){

			echo("<h2>Will You Cite Us?</h2>");
			echo("<h4>The required field stating you will cite the CTSI has not been filled in.<h4>");
			echo("<input type='button' value='Edit Project Request To Update' onClick=\"window.location='studiesopen.php?".encryptParameters('studiesopen.php','SYSID='.$_ENC["SYSID"])."';\">");
			$hardstop = true;

		}
		if( $mydb->SYSUSE != "Y" && !current_user_can( 'manage_options' )  ){
			$hardstop = true;
			?>
				<h1>SYSTEMS ACCESS AND CONFIDENTIALITY AGREEMENT</h1>
				<h3>University of Rochester Medical Center and Affiliates (collectively, URMC)</h3>

				<ol>
					<li>I understand that  this Agreement and its terms will govern my use and access of medical records and information systems maintained by  the University of Rochester Medical Center and its off-site  subsidiaries and affiliates (together:  "URMC").  <br></li>
					<li>URMC creates and maintains demographic, insurance and health information relating to its patients ("Confidential Information" or "PHI").  This Confidential Information is located in various computer  information systems as well as paper charts and records.  URMC computerized data systems  ("Information Systems" or "Systems") include, but are not limited to:  </li>
						<ul style="margin-left:25px;">
							<li>clinical information systems,</li>
							<li>medical records data retrieval systems,  </li>
							<li>patient registration, scheduling and accounting systems,  </li>
							<li>laboratory, radiology and pharmacy information systems, and  </li>
							<li>physician billing systems.   </li>
						</ul>
						<br>
						<br>
					</li>
					<li>By electronically signing this Agreement (clicking "I accept" below), I understand that I am requesting that URMC permit me access to the Information Systems as an Authorized User, and I  agree to be bound by the terms of this Agreement.  <br><br> </li>
				</ol>

				<hr>

				<ol>
					<li>I understand that my password and user ID are my unique identifiers for the Information Systems that I am authorized to use. I will not share my User ID or password, nor will I allow any other individual to use  my password.  I will safeguard and will not disclose my password or any other authorization I have  that allows me access to PHI. I accept responsibility for all activities undertaken using my password. I  agree that I will  change my password when requested or my access will be suspended.   I understand  that my access to systems containing PHI may be audited (random or focused) at any time. <br><br></li>
					<li>I will use PHI information as needed by me only to perform my legitimate patient-related or job-related duties. This means, among other things, that: </li>
					<ul style="margin-left:25px;">
						<li>I will not access PHI for which I have no legitimate clinical or business related purpose. </li>
						<li>I will not in any way divulge, copy, release, sell, loan, revise, alter, or destroy any PHI except as properly authorized within the scope of my patient-related or job-related responsibilities. </li>
						<li>I will not misuse or carelessly care for PHI and/or Confidential Information. </li>
					</ul>
					<br>
					<br>
					<li>I will adhere to the federal HIPAA privacy and security regulations in my use of URMC clinical systems. In addition,  if I am an employee, medical staff or faculty member of URMC or its affiliates, I  agree to follow any Strong Memorial or Highland Hospital  policies relating to confidentiality of patient  information.<br><br> </li>
					<li>I understand that medical record confidentiality is required by law, and I agree to fully comply with federal and state statutes mandating the  confidentiality of medical records, including, but not limited  to, special provisions regarding mental health, HIV and drug and alcohol-related treatment records. I  understand that  URMC has incorporated the requirements of such statutes into its policies and  procedures for access to and treatment  of such specialized medical record information and that it is  my responsibility to be familiar with and adhere to such statutes and policies. Any  fraudulent  application, violation  of confidentiality or any violation of the above provisions may result in  disciplinary action,  including termination of access to the system, appropriate medical staff or URMC  disciplinary measures, up to and including termination of my affiliation or employment with URMC, or  dismissal from school for URMC students.  URMC may also be obligated to report breaches of  confidentiality to appropriate state licensing authorities.  <br><br> </li>
					<li>I am aware that, in addition to disciplinary actions, I may be subject to legal sanctions if I improperly disclose or permit the disclosure of PHI contained in any patient records.  I have been advised that  improper disclosure by me of confidential HIV  patient information is a  criminal misdemeanor  under  New York State law that could result in a fine or jail sentence or both. I have also been advised that if I  improperly disclose or permit the disclosure of information relative to a patient&#39;s treatment for drug and  alcohol abuse, I may be subject to criminal penalties including payment of a fine ranging from $500 to  $5,000 or more, as stipulated in the laws and regulations then in effect. I have also been advised that if I  improperly use or disclose PHI in violation of the Health  Insurance Portability and Accountability Act  (HIPAA), that I may be subject to criminal or civil penalties. <br><br></li>
					<li>I agree to indemnify, protect, save and hold harmless URMC, its officers, employees and agents from and against any and all losses, damages, injuries, claims, demands and expenses (including  attorney&#39;s fees and legal expenses) of whatsoever any kind and nature (including fines and  penalties) arising on account of my acts or omissions, or the acts of omissions of those under my  control.  <br><br> </li>
					<li>I understand that URMC make no representations/warranties about the availability or accuracy of the Information Systems or the data contained therein.<br> <br></li>
					<li>I understand that I have no right or ownership interest in my password or any Confidential Information or PHI referred to in this  agreement. URMC may, at its sole discretion  and at any time, revoke my  password and access to the Information Systems, or otherwise limit or restrict such access.<br> <br></li>
					<li>I understand that there may be additional terms governing my access to the Information Systems, and if so, these terms will be found on  the log in page of the System.  I understand that by logging into a  System, I agree be bound by these additional Terms and Conditions (as amended from time to time),  and to the incorporation of these Terms and Conditions into this Agreement by reference.  <br> <br></li>
					<li>I agree that this Agreement will apply to my use of any Information Systems currently in use at URMC and affiliates, and any Information Systems which may be added in the future by URMC. I may terminate  this Agreement at any time upon written notice, provided, however, that the obligations contained herein  shall survive the termination of this Agreement.  <br> <br></li>
				 </ol>



				<form method="POST">
					<input type="hidden" name="ENC" value='<?php echo(encryptParameters("sysuse.php","SYSID=".$_ENC["SYSID"]."&SYSUSE=Y"));?>'>
					<input type="submit" style="width:100%" name="ACCEPT_BUTTON" value="I Accept">
				</form>

			<?php
		} // sysuse

		if( !$created || ($deleted > 0 && $deleted < $created) ){
			echo("<h2>Your Datamart Has Not Been Created Yet</h2>");
			echo("<h4>If you proceed, you will be in the public deidentifed datamart. Our staff will inform you when your datamart has been loaded.<h4>");
			//new dBug( $jobs );
			if( $deleted < $created ){
				echo("<br><b>Datamart was deleted ($deleted days ago) after creation ($created days ago)</b>");
			}
			if( !$hardstop ){ ?>
				<form method="post" action="/webclient17/login.php" name="SUBMITIT" id="SUBMITIT" >
					<input type="hidden" name="ENC" value='<?php echo(encryptParameters("login.php","SYSID=".$_ENC["SYSID"]."&MESSAGE=Not Yet Created Datamart&VALIDFOR=".(time()+60)));?>'>
					<input type="submit" name="PRO" value='Enter the PUBLIC DEIDENTIFIED datamart'>
				</form>
			<?php } else {
				echo( "<br><b>There are other issues above that prevent access to the public datamart</b>" );
			}
		}

		if( !$loaded || $loaded > $created ){
			echo("<h2>Your Datamart Has Not Been Loaded Yet</h2>");
			echo("<h4>There is no data yet loaded into your datamart.<h4>");
			//new dBug( $jobs );
			if( $loaded > $created ){
				echo("<br><b>Datamart was created ($created days ago) after loaded ($loaded days ago)</b>");
			}
			$hardstop = true;
		}

		if( !$ready ){
			echo("<h2>You Have Uploaded Excel Data, However The Datamart Hasn't Yet Been Loaded</h2>");
			echo("<h4>The excel was last uploaded on $donedate, the datamart was loaded $lasttime. <h4>");
			echo("If you proceed, the newly entered patients and data in the excel upload will not be in the datamart until loaded" );
			//new dBug( $jobs );
			if( !$hardstop ){ ?>
				<form method="post" action="/webclient17/login.php" name="SUBMITIT" id="SUBMITIT" >
					<input type="hidden" name="ENC" value='<?php echo(encryptParameters("login.php","SYSID=".$_ENC["SYSID"]."&MESSAGE=Uploaded Excel, Not loaded&VALIDFOR=".(time()+60)));?>'>
					<input type="submit" name="PRO" value='Enter your project datamart, Data may not be loaded.'>
				</form>
			<?php } else {
				echo( "<br><b>There are other issues above that prevent access to the public datamart</b>" );
			}
		}

		if( current_user_can( 'manage_options' ) ){
			echo("<h2>You Are the Administrator!</h2>");
			echo("<h4>Eh... Whatever... <h4>");
			?>
			<form method="post" action="/webclient17/login.php" name="SUBMITIT" id="SUBMITIT" >
				<input type="hidden" name="ENC" value='<?php echo(encryptParameters("login.php","SYSID=".$_ENC["SYSID"]."&VALIDFOR=".(time()+60)));?>'>
				<input type="submit" name="PRO" value='Administrative Override' style='background-color:pink'>
			</form><?php
		}

		get_footer();
	}
} else {
	header("Location:login.php");
}?>


