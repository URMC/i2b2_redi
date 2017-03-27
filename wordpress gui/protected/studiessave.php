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

if( isLoggedIn() && isset( $_ENC["PROJECTID"] ) && $_ENC["PROJECTID"] ){

	get_header();

	function checker( $s, $var ){
		$CHECKER['PROJECTTITLE'        ]='512';
		$CHECKER['PROJECTREQUESTER'    ]='120';
		$CHECKER['CONTACTNUMBER'       ]='50';
		$CHECKER['CONTACTEMAIL'        ]='75';
		$CHECKER['PROJECTENDDATE'      ]='DATE';
		$CHECKER['DEPARTMENT'          ]='512';
		$CHECKER['REQUESTREASON'       ]='512';
		$CHECKER['REQUESTREASONOTHER'  ]='512';
		$CHECKER['PROJECTPI'           ]='100';
		$CHECKER['RSRBAPPROVAL'        ]='35';
		$CHECKER['HIPAAAGREE'          ]='1';
		$CHECKER['HIPPAAGREEINITIALS'  ]='4';
		$CHECKER['PROJECTDESC'         ]='2048';
		$CHECKER['PROJECTTYPE'         ]='25';
		$CHECKER['PROTOCOLFILE'        ]='256';
		$CHECKER['APPROVALFILE'        ]='256';
		$CHECKER['WAIVERFILE'          ]='256';
		$CHECKER['RSRBENDDATE'         ]='DATE';
		$CHECKER['STATUS'              ]='50';
		$CHECKER['ISDELETED'           ]='1';
		$CHECKER['CREATION_DATE'       ]='DATE';
		$CHECKER['TIME_STAMP'          ]='DATE';
		$CHECKER['SUBMITTED_DATE'      ]='DATE';
		$CHECKER['PROJECTREQUESTERNAME']='255';
		$CHECKER['REQUESTEDITEMS'      ]='40000';
		$CHECKER['DATAUSAGE'           ]='40000';
		$CHECKER['RESTRICTIONS'        ]='40000';
		$CHECKER['PHI'                 ]='1';
		$CHECKER['SYSUSE'              ]='1';
		$CHECKER['COMMENTS'            ]='40000';
		$CHECKER['IRBENDDATE'          ]='40';
		$CHECKER['IRBAPPROVAL'         ]='40';
		$CHECKER['PROJECTCODE'         ]='25';
		$CHECKER['REDCAP_URL'          ]='512';
		$CHECKER['REDCAP_APIKEY'       ]='64';
		$CHECKER['REDCAP_SYNCDATE'     ]='DATE';
		$CHECKER['SYNCDATE'            ]='DATE';
		$CHECKER['SYNCSTATUS'          ]='15';
		$CHECKER['ENDDATE'             ]='DATE';
		$CHECKER['SYNCFINISHED'        ]='DATE';
		$CHECKER['ADDITIONALUSERS'     ]='512';
		$CHECKER['TESTSTATUS'          ]='15';
		$CHECKER['WILLDOCITATIONS'     ]='3';
		$CHECKER['TESTDATE'            ]='DATE';
		$CHECKER['TESTPATIENT'         ]='NUMBER';
		$CHECKER['GENERATION_SQL'      ]='2000';
		$CHECKER['TESTFIELD'           ]='50';
		$CHECKER['UPLOADLIST'          ]='1';
		$CHECKER['NOLISTDESC'          ]='2000';
		$CHECKER['DM_MADE'             ]='1';
		$CHECKER['I2B2QUERY'           ]='50';
		$CHECKER['I2B2QUERY_YN'        ]='1';
		$CHECKER['QUERY_MASTER_ID'     ]='255';
		$CHECKER['CONCEPTS_SELECTED'   ]='XML';
		$CHECKER['REDCAP_NEEDED'       ]='1';
		$CHECKER['SENDSTATUS'          ]='100';
		$CHECKER['REDCAP_PRJ_NAME'     ]='255';


		$check = $CHECKER[$var];

		if( $check != 'XML' ){
			$ans = htmlentities(preg_replace('/[\x00-\x1F\x7F-\xFF]/', '',str_replace("'","''",str_replace('\\"','"',str_replace("\\'","'",$s)))),ENT_NOQUOTES,'UTF-8',false);
		} else {
			if( simplexml_load_string( $s ) && strlen($s) < 4000 ){
				$ans = $s;
			}
		}

		if( $check == 'NUMBER' ){
			$ans = floatval( $ans );
		}
		if( $check == 'DATE' ){
			$ans = date('Y/m/d',strtotime($ans));
		}
		if( intval($check) > 0 && strlen($ans) > intval($check) ){
			$ans = substr($ans,0, intval($check) );
		}
		return $ans;
	}

	echo("<H1>Submission Successful!</H1> <p>Your submission number was ".$_ENC["PROJECTID"].", titled <b>".f($_POST["PROJECTTITLE"])."</b></p>");

	//Save Data to database.
	$mydb = $DB->get_row( "
		SELECT PROJECTS.*,
				TO_CHAR(CREATION_DATE,'MM/DD/YYYY') AS CDATE ,
				TO_CHAR(TIME_STAMP,'MM/DD/YYYY') AS EDATE,
				TO_CHAR(SUBMITTED_DATE,'MM/DD/YYYY') AS SUBMITTED
		FROM PROJECTS
		WHERE PROJECTID='".$_ENC["PROJECTID"]."'
	"  ,OBJECT );

	foreach( $_FILES as $field => $value ){

		echo("in files");
		new dBug( $_FILES );

		if( $_FILES[$field]["size"] > 0 && $_FILES[$field]["size"] < 2000000){

			$sql = "INSERT INTO PROJECTS_BLOBS (
						projectid,
						fieldname,
						filename,
						updated,
						filecontents
					) VALUES (
						".f($_ENC["PROJECTID"]).",
						'".f($field)."',
						'".filefix($_FILES[$field]["name"])."',
						sysdate,
						empty_blob()
					) RETURNING filecontents INTO :filecontents";


			$conn = $DB->dbh;

			$result = oci_parse($conn, $sql);

			$blob = oci_new_descriptor($conn, OCI_D_LOB);

			oci_bind_by_name($result, ":filecontents", $blob, -1, OCI_B_BLOB);

			if( oci_execute($result, OCI_DEFAULT) ){

				if(!$blob->save(file_get_contents($_FILES[$field]['tmp_name']))) {
					oci_rollback($conn);
					new dBug(oci_error($result));
				} else {
					oci_commit($conn);
					$_POST[$field]=str_replace('\'','`',$_FILES[$field]["name"]);
				}

			} else {
				new dBug(oci_error($result));
			}

			oci_free_statement($result);
			$blob->free();

		} //if there is content
		echo("out of file");
	}


	$recExists = true;
	foreach( $mydb as $key => $value){

		if($key == "PROJECTTITLE" && ($value == "" || $value == null)){
			$recExists = false;
		}
		if($key == "PROJECTREQUESTERNAME" && ($value == "" || $value == null)){
			$recExists = false;
		}
		if($key == "CONTACTEMAIL" && ($value == "" || $value == null)){
			$recExists = false;
		}
	}

	if(($_POST['PROJECTTYPE'] == 'APPROVED' || $_POST['PROJECTTYPE'] == 'OTHER') && $recExists == false){
		$recExists = false;
	} else{
		$recExists = true;
	}


	//custom check to make sure that the project code doesn't get wonked.
	if(isset($_POST["PROJECTCODE"])){
		$_POST["PROJECTCODE"] = preg_replace("/[^A-Za-z0-9 ]/", '', strtoupper($_POST["PROJECTCODE"]));
		if( $_POST["PROJECTCODE"] == 'DEMO' || $_POST["PROJECTCODE"] == 'DEID' || $_POST["PROJECTCODE"] == 'META' ){
			$_POST["PROJECTCODE"] = "";
			echo( " Special Name in project code. " );
		}
	}


	//add in the choices for query grouping, this should overwrite the current query if populated.
	if( isset($_POST['QUERY_MASTER_ID_NUM']) ){

		//this should be in two parts.
		if( strpos($_POST['QUERY_MASTER_ID_NUM'],'.') > 0 ){

			$schema = substr($_POST['QUERY_MASTER_ID_NUM'],0,strpos($_POST['QUERY_MASTER_ID_NUM'],'.'));
			$queryid = intval(substr($_POST['QUERY_MASTER_ID_NUM'],strpos($_POST['QUERY_MASTER_ID_NUM'],'.')+1));

			if( $queryid > 0 ){

				$myqueries = $EXP->get_row("
					SELECT MAST.USER_ID, RES.START_DATE, RES.SET_SIZE, RES.DESCRIPTION , RES.MESSAGE, RES.RESULT_INSTANCE_ID, INST.QUERY_MASTER_ID, RESULT_TYPE_ID
					FROM $schema.QT_QUERY_MASTER MAST, $schema.QT_QUERY_INSTANCE INST, $schema.QT_QUERY_RESULT_INSTANCE RES
					WHERE MAST.QUERY_MASTER_ID=INST.QUERY_MASTER_ID AND
					  INST.QUERY_INSTANCE_ID=RES.QUERY_INSTANCE_ID AND
					  RES.DELETE_FLAG='N' AND
					  RESULT_TYPE_ID IN (1,2) AND
					  REAL_SET_SIZE > 0 AND
					  MAST.QUERY_MASTER_ID='$queryid'
					ORDER BY RES.START_DATE DESC
				");

				$_POST['QUERY_MASTER_ID'] = "$schema.$queryid";
				if( $myqueries->RESULT_TYPE_ID == '1' ){
					$_POST['GENERATION_SQL'] = "SELECT PATIENT_NUM FROM $schema.qt_patient_set_collection where RESULT_INSTANCE_ID=".intval($myqueries->RESULT_INSTANCE_ID);
				} else {
					$_POST['GENERATION_SQL'] = "SELECT PATIENT_NUM FROM $schema.qt_patient_enc_collection where RESULT_INSTANCE_ID=".intval($myqueries->RESULT_INSTANCE_ID);
				}

			} else if( "" != $mydb->QUERY_MASTER_ID ){
				$_POST['GENERATION_SQL'] = "";
				$_POST['QUERY_MASTER_ID'] = "";
			}
		} else if( "" != $mydb->QUERY_MASTER_ID ){
			//no period, i.e. not in two parts.
			$_POST['GENERATION_SQL'] = "";
			$_POST['QUERY_MASTER_ID'] = "";
		}
	}

	//unset if they pick "Y" here, CRC-343: Clear out query if the system is set to upload excel spreadsheet
	if( "Y" == $_POST['UPLOADLIST'] ){
		$_POST['GENERATION_SQL'] = '';
		$_POST['QUERY_MASTER_ID'] = '';
	}

	//fix stupid checkboxes.
	foreach( $_POST as $key => $value) {
		if( strpos($key,"CONCEPTS_SELECTED_") !== false ){
			$item = $_POST[$key];
			unset( $_POST[$key] );
			$_POST[ "CONCEPTS_SELECTED" ] .= " ". $item;
		}
	}
	$_POST[ "CONCEPTS_SELECTED" ] = str_replace(' ',',',f(trim($_POST[ "CONCEPTS_SELECTED" ])));

	//let us make sure data is correct
	foreach( $mydb as $key => $value) {
		if( isset($_POST[$key]) && $_POST[$key] != $value ){
			$_POST[$key] = checker($_POST[$key],$key);
			//echo( "$key = ".$_POST[$key] . "<br>" );
			$sql = "UPDATE PROJECTS SET ".$key."='".$_POST[$key]."' WHERE PROJECTID='".f($_ENC["PROJECTID"])."'";
			$DB->query( $sql );
		}
	}
	$DB->query( "UPDATE PROJECTS SET SUBMITTED_DATE=SYSDATE WHERE PROJECTID='".f($_ENC["PROJECTID"])."'");

	if($_POST["PROJECTTYPE"]=="PREPATORY" || $_POST["PROJECTTYPE"]=="TRAINING" ){?>

			<p>
				You may now go to "Analyse Project Data" On the menu bar to start working on our de-identified dataset!
			</p>

			<img src="/images/analyse.png">

		<?php } else {?>

			<?php
				if(!current_user_can( 'manage_options' )){
					ob_start();
					echo('The following comment was submitted from the i2b2 web pilot.');
					new dBug( $_POST );
					new dBug( $myqueries );
					new dBug( $myqueries2 );
					$export= ob_get_clean();

					$export = str_replace("\"","'",$export);

					$headers = "";//"From: Clinical Research Informatics Group <ClinicalResearchInformaticsGroup@URMC.Rochester.edu>\r\n";

					$message = "Thank you for submitting your I2B2 project (".$_POST["PROJECTTITLE"]."). <br><br>
							This message is to confirm that we have recieved your submission.<br><br>
							If you have questions, feel free to email us at ClinicalResearchInformaticsGroup@URMC.Rochester.edu.<br><br>
							Thank you for your patience,<br><br>
							Clinical Research Informatics Group<br><br><hr/><h1>Details</h1><br>$export";

					//CRI-455 - correcting emails to correct administrators.
					$receipients = "".$_POST["CONTACTEMAIL"].",phillip_ng@urmc.rochester.edu,davidj_pinto@urmc.rochester.edu";

					$subject = "Thank You For Your Submission of I2B2 '".($recExists?"Updated":"New")." project' Page.";

					if( !wp_mail($receipients, $subject, $message, $headers) ){
						//echo("<h2 style='color:red'>Email Failed.</h2>");
						//for ye without functional emailing capability!
						shell_exec(	"java -jar /var/www/html/protected/mailx.jar -s \"$subject\" " . str_replace( ',',' ', $receipients ). " -c \"$message\" 2>&1"	);
					}

				} else {
					echo( "Email not sent, you're an admin" );
				}
			?>

			<p>
				We will contact you in regards to your submission, you will hear from us shortly!

				In the interim. you may now go to "Analyze Project Data" On the menu bar to play with our de-identified dataset!
			</p>
			<br>
			<img src="/images/analyse.png">

		<?php } ?>

	<?php

	get_footer();

} else {

	header("Location:login.php");

} ?>