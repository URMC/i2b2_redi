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
 * This is a very monsterously long page displays the study request form. There
 * is skip logic in this form, complying with the URMC approval and approach.
 * The main logic is on the top, in bringing in the queries the end user has
 * queried from i2b2 public sides.
 */

require_once("../common.php");
$_ENC = decryptGetMethod();
if( isLoggedIn() ){

	get_header();

	if( !isset($_ENC["SYSID"]) ){

		$mydb = $DB->get_row( "
			SELECT MAX(PROJECTID) AS MAXER
			FROM PROJECTS
			WHERE
				STATUS is null AND
				ISDELETED IS NULL AND
				LOWER(PROJECTREQUESTER)=lower('".f($_SESSION['USERNAME'])."')"
			,OBJECT
		);



		if( $mydb->MAXER ){

			$_ENC["SYSID"] = $mydb->MAXER;

		} else {

			//make new id
			$mydb = $DB->get_row( "SELECT SYSIDS.NEXTVAL AS IDNO FROM DUAL",OBJECT );

			$_ENC["SYSID"] = $mydb->IDNO;

			$DB->query( "INSERT INTO PROJECTS(PROJECTID,PROJECTREQUESTER) VALUES (".$_ENC["SYSID"].",'".f($_SESSION['USERNAME'])."')" );
		}
	}


	$mydb = $DB->get_row( "
			SELECT PROJECTS.*,
					TO_CHAR(CREATION_DATE,'MM/DD/YYYY') AS CDATE ,
					TO_CHAR(TIME_STAMP,'MM/DD/YYYY') AS EDATE,
					TO_CHAR(SUBMITTED_DATE,'MM/DD/YYYY') AS SUBMITTED
			FROM PROJECTS
			WHERE PROJECTID='".$_ENC["SYSID"]."'
	"  ,OBJECT );

	$mydb_w = $DB->get_row( "SELECT FILENAME FROM PROJECTS_BLOBS WHERE FIELDNAME='WAIVER_FILE' AND PROJECTID='".$_ENC["SYSID"]."' ORDER BY UPDATED DESC "  ,OBJECT );

	$mydb_a = $DB->get_row( "SELECT FILENAME FROM PROJECTS_BLOBS WHERE FIELDNAME='APPROVAL_FILE' AND PROJECTID='".$_ENC["SYSID"]."' ORDER BY UPDATED DESC "  ,OBJECT );

	$mydb_p = $DB->get_row( "SELECT FILENAME FROM PROJECTS_BLOBS WHERE FIELDNAME='PROTOCOL_FILE' AND PROJECTID='".$_ENC["SYSID"]."' ORDER BY UPDATED DESC "  ,OBJECT );


	$myqueries = $DATA->get_results("
		SELECT MAST.USER_ID, RES.START_DATE, RES.SET_SIZE, RES.DESCRIPTION , RES.MESSAGE, RES.RESULT_INSTANCE_ID, INST.QUERY_MASTER_ID
		FROM i2b2demodata.QT_QUERY_MASTER MAST, i2b2demodata.QT_QUERY_INSTANCE INST, i2b2demodata.QT_QUERY_RESULT_INSTANCE RES
		WHERE MAST.QUERY_MASTER_ID=INST.QUERY_MASTER_ID AND
		  INST.QUERY_INSTANCE_ID=RES.QUERY_INSTANCE_ID AND
		  RES.DELETE_FLAG='N' AND
		  RESULT_TYPE_ID IN (1,2) AND
		  REAL_SET_SIZE > 0 AND
		  (trim(LOWER(MAST.USER_ID))=trim(lower('".f($mydb->PROJECTREQUESTER)."')) OR trim(LOWER(MAST.USER_ID))=trim(lower('".f($_SESSION['USERNAME'])."')))
		ORDER BY RES.START_DATE DESC
	");

	$myqueries2 = $DEID->get_results("
		SELECT MAST.USER_ID, RES.START_DATE, RES.SET_SIZE, RES.DESCRIPTION , RES.MESSAGE, RES.RESULT_INSTANCE_ID, INST.QUERY_MASTER_ID
		FROM i2b2datadeid.QT_QUERY_MASTER MAST, i2b2datadeid.QT_QUERY_INSTANCE INST, i2b2datadeid.QT_QUERY_RESULT_INSTANCE RES
		WHERE MAST.QUERY_MASTER_ID=INST.QUERY_MASTER_ID AND
		  INST.QUERY_INSTANCE_ID=RES.QUERY_INSTANCE_ID AND
		  RES.DELETE_FLAG='N' AND
		  RESULT_TYPE_ID IN (1,2) AND
		  REAL_SET_SIZE > 0 AND
		  (trim(LOWER(MAST.USER_ID))=trim(lower('".f($mydb->PROJECTREQUESTER)."')) OR trim(LOWER(MAST.USER_ID))=trim(lower('".f($_SESSION['USERNAME'])."')))
		ORDER BY RES.START_DATE DESC
	");

	$available = $DATA->get_results("SELECT * FROM I2B2METADATA.TABLE_ACCESS WHERE NOT C_VISUALATTRIBUTES LIKE '%H%'");

?>

<script type="text/javascript" src="/js/jquery.validate.min.js"></script>

<style>
	th{ background-color:#e3e3e3; font-weight:bold;}
	table, tr, th, td{ padding:10px; }
	td{ border-bottom:1px solid #DDDDDD; }
	table.selector, tr.selector, th.selector, td.selector{
		padding:2px;
	}
	table.selector {
		width:100%;border:1px solid black;font-weight:bold;
	}
	tr.even { background-color:#f0f0f0; }
	tr.evenwhite { background-color:#ffffff; }
	table.whitebox{ width:100%;background-color:white;border:1px solid black;font-weight:bold;padding:10px; }
	input.wide, textarea.wide{ width:100% }
	#THEFORM .error { background-color:pink; }
</style>

<h1>Setup or View a Project Wizard</h1>

<hr>

<script>
	<!-- This needs to be above the radios that call it so that the function exists in memory... :-P -->
	function projecttype(){
		$('#PROJECT_TYPE_OTHER').hide();
		$('#PROJECT_TYPE_APPROVED').hide();
		$('#PROJECT_TYPE_PREPATORY').hide();

		var FORM = document.getElementById('THEFORM');
		var current = $('#PROJECTTYPE:checked').val();

		if(current == "TRAIN"){
			$('#PROJECT_TYPE_PREPATORY').show('slow');
		}
		else{
			$('#PROJECT_TYPE_' + current).show('slow');
		}

		if( current == 'PREPATORY' || current == 'TRAIN' || current == ''){
			$('#PROJECT_TYPE_SELECT_DATA').hide('slow');
			$('#HIPAA_NON_PREPATORY').hide('slow');
			$('#HIPAA_PREPATORY').show('slow');
			$('#nolist').hide();
			$('#requesteditemsbox').hide();
			$('#selectI2b2Query').hide();
			$('#noi2b2query').hide();
			$('#GENERATIONSQL').hide();
			$('#NEED_REDCAP_CONNECT').hide();
			$('#redcap_project_select').hide();
		} else {
			$('#PROJECT_TYPE_SELECT_DATA').show('slow');
			$('#HIPAA_NON_PREPATORY').show('slow');
			$('#HIPAA_PREPATORY').hide('slow');
		}
	}

	function uploadList(){
		$('#nolist').hide();
		$('#requesteditemsbox').hide();
		$('#NEED_REDCAP_CONNECT').hide();

		var FORM = document.getElementById('THEFORM');
		var uploadList = $('#UPLOADLIST:checked').val();
		if(uploadList == 'N'){
			$('#nolist').show('slow');
			//$('#requesteditemsbox').show('slow');
			$('#GENERATIONSQL').show('slow');
			$('#NEED_REDCAP_CONNECT').hide('slow');
			$('#redcap_project_select').hide('slow');
		}
		else if(uploadList == 'Y'){
			$('#nolist').hide('slow');
			$('#requesteditemsbox').hide('slow');
			$('#selectI2b2Query').hide('slow');
			$('#noi2b2query').hide('slow');
			$('#GENERATIONSQL').hide('slow');
			$('#NEED_REDCAP_CONNECT').show('slow');
		}
	}

	function i2b2Query(){
		$('#selectI2b2Query').hide();
		$('#noi2b2query').hide();

		var FORM = document.getElementById('THEFORM');
		var i2b2query_yn = $('#I2B2QUERY_YN:checked').val()
		if(i2b2query_yn == 'N'){
			$('#noi2b2query').show('slow');
			$('#requesteditemsbox').show('slow');
			$('#GENERATIONSQL').show('slow');
		}
		else if(i2b2query_yn == 'Y'){
			$('#requesteditemsbox').show('slow');
			$('#selectI2b2Query').show('slow');
			$('#GENERATIONSQL').show('slow');
		}
	}

	function redcapProject(){
		$('#redcap_project_select').hide();

		var needRedcap_yn = $('#REDCAP_NEEDED:checked').val();
		if(needRedcap_yn == 'N'){
			$('#redcap_project_select').hide('slow');
		}
		else if(needRedcap_yn == 'Y'){
			$('#redcap_project_select').show('slow');
		}
	}

	$().ready(function () {

	  //http://stackoverflow.com/questions/6212946/jquery-validate-file-upload
	  $.validator.addMethod('filesize', function(value, element, param) {
	      return ((element.files[0].size > 0) || (this.optional(element) || (element.files[0].size <= param)));
	  });

	  //run the function to make sure the logic is consistent after loading.
	  projecttype();
	  uploadList();
	  i2b2Query();
	  redcapProject();

	  $("#THEFORM").validate({

		invalidHandler: function(form, validator) {
			var errors = validator.numberOfInvalids();
			if (errors) {
				var message = errors == 1
				? 'You missed 1 field. It has been highlighted'
				: 'You missed ' + errors + ' fields. They have been highlighted';
				alert(message);
			}
		},

		submitHandler: function(form){
			form.submit();

		},

		ignore: ':hidden'

	  });

	});

</script>


<form name="THEFORM" action="studiessave.php" enctype="multipart/form-data" method="post" id="THEFORM">
	<p>
	You are currently logged in as <b>"<?php echo $_SESSION['USERNAME']; ?>"</b>
	<input name="PROJECTREQUESTER" type="hidden" value="<?php echo htmlentities($mydb->PROJECTREQUESTER); ?>">
	</p>

	<p>

	To begin, Let&#39;s first talk a little bit about your project.

	<h3>Project Name</h3>

	<table class="whitebox">
		<tr class="even"><td colspan=2><b>Name of Project</b>  (required)</td>
		<tr class="even"><td colspan=2 class="even"><input name="PROJECTTITLE" style="width:95%" value="<?php echo htmlentities($mydb->PROJECTTITLE); ?>" class="required" message="You must have a name for this project..."  style="width:75%" ></td>
	</table>


	<input name="ENC" value="<?php echo(encryptParameters('studiessave.php','SYSID='.$mydb->PROJECTID.'&PROJECTID='.$mydb->PROJECTID));?>" type="hidden">

	<h3>Project Contact Information</h3>
	Let&#39;s get some information about you.
	<table class="whitebox">

		<tr><td>Your Full Name (required)</td><td><input name="PROJECTREQUESTERNAME" class="required" value="<?php echo htmlentities($mydb->PROJECTREQUESTERNAME); ?>" type="text"  style="width:75%" ></td>

		<tr class="even"><td>Principal Investigator / Project Chief (required)</td>
		<td width="65%">
			<input name="PROJECTPI" value="<?php echo htmlentities($mydb->PROJECTPI); ?>" class="required" type="text"  style="width:75%" >
		</td>

		<tr><td>Your Department (required)</td><td><input name="DEPARTMENT" class="required" value="<?php echo htmlentities($mydb->DEPARTMENT); ?>" type="text"  style="width:75%" ></td>

		<tr class="even"><td>Your Phone Number</td><td><input name="CONTACTNUMBER" value="<?php echo htmlentities($mydb->CONTACTNUMBER); ?>" type="text"  style="width:75%" ></td>


		<tr><td>Your Email Address (required)</td><td><input name="CONTACTEMAIL" class="required" value="<?php echo htmlentities($mydb->CONTACTEMAIL); ?>" type="text"  style="width:75%" ></td>


	</table>
	<h3>Project Details</h3>


	<table class="whitebox">
		<tr><td><input id="PROJECTTYPE" name="PROJECTTYPE" type="radio" class="required" <?php if( $mydb->PROJECTTYPE=='PREPATORY'){echo('checked');}?> value="PREPATORY" onClick="projecttype()"></td>
			<td><b>This project/request is preparatory to research</b></td></tr>

		<tr  class="even">
		    <td><input id="PROJECTTYPE" name="PROJECTTYPE" type="radio" class="required" <?php if( $mydb->PROJECTTYPE== 'APPROVED' ){echo('checked');}?> value="APPROVED"  onClick="projecttype()"></td>
			<td><b>This is an approved project with an IRB number</b></td></tr>

		<tr><td><input id="PROJECTTYPE" name="PROJECTTYPE" type="radio" class="required" <?php if( $mydb->PROJECTTYPE== 'OTHER' ){echo('checked');}?> value="OTHER"  onClick="projecttype()"></td>
			<td><b>Other - not a IRB project (i.e. case study, Quality Improvement activity)</b></td></tr>

	<?php if(current_user_can( 'manage_options' )){ ?>

		<tr class="even"><td><input id="PROJECTTYPE" name="PROJECTTYPE" type="radio" class="required" <?php if( $mydb->PROJECTTYPE== 'TRAIN' ){echo('checked');}?> value="TRAIN"  onClick="projecttype()"></td>
			<td><b>Training Project</b></td></tr>

	<?php } ?>
	</table>


	</p>

	<br>

	<div id="PROJECT_TYPE_PREPATORY">
		<p>
		<h3>Preparatory To Research</h3>
			<table class="whitebox">
			<tr><td>
			<p><b>Do you have a copy of the preparatory to research from (form 25.3) as required by HIPAA?<br></b>
			<a href="http://intranet.urmc-sh.rochester.edu/apps/HIPAA/apps/disclosure/" target="_blank">
			http://intranet.urmc-sh.rochester.edu/apps/HIPAA/apps/disclosure/
			</a>(clicking will open a new window)
			</p>
			After completing this form (form 25.3 above), you should have received an email with an attachment indicating that you have completed this form. Please upload that attachment below:
			(Max file size:<?php echo(file_upload_max_size());?>)
			</td></tr>
			<tr class="even"><td>

			<?php if( $mydb_w->FILENAME ){ ?>
				<a href="studies_fileopen.php?<?php echo(encryptParameters('studies_fileopen.php','SYSID='.$_ENC["SYSID"].'&TYPE=WAIVER_FILE'));?>" ><b>View Existing File That Was Uploaded :<br> <?php echo htmlentities($mydb_w->FILENAME ); ?></b></a><br>
				<a href="javascript:void(0);" onClick="$('#divWAIVERFILE').show('slow');">Upload Another File (replaces previous waiver)</a>
			<?php } ?>

			<div id="divWAIVERFILE" style="<?php if( $mydb_w->FILENAME ){ ?>display:none;<?php } ?>">
				<input type="file"  style="width:75%"  name="WAIVER_FILE" class="required" >
			</div>


			</td></tr>
			<tr><td colspan=2>Researchers are also responsible for completion of necessary online disclosure logs at: <br>
				<a href="http://intranet.urmc-sh.rochester.edu/apps/HIPAA/apps/disclosure/disclosureEditForm.asp">http://intranet.urmc-sh.rochester.edu/apps/HIPAA/apps/disclosure/disclosureEditForm.asp</a>
			</td></tr>
			</table>
		<p>
	</div>

	<div id="PROJECT_TYPE_OTHER">
		<p>
		<h3>Other Project Types</h3>
			<table class="whitebox">
			<tr><td>
			Tell me why you need this data. (required)
			</td></tr>
			<tr><td><textarea name="REQUESTREASONOTHER"  style="width:95%"  class="required" rows=3 cols=80><?php echo htmlentities($mydb->REQUESTREASONOTHER); ?></textarea></td></tr>
			</table>
		<p>
		<p>
			<h3>Data Usage / Notes</h3>
			<table class="whitebox">
			<tr><td>
			Tell us how you will use the data that you have requested. (required)
			</td></tr>
			<tr><td><textarea name="DATAUSAGE"  style="width:95%"  class="required" rows=3 cols=80><?php echo htmlentities($mydb->DATAUSAGE); ?></textarea></td></tr>
			</table>
		<p>

		<p>
			<h3>Other Data Restrictions</h3>
			<table class="whitebox">
			<tr><td>
			Above we give you the oppurtunity to limit the amount of data that you are receiving. Are there any other data restrictions that we should consider? (Such as Only patients with HTN?)
			</td></tr>
			<tr><td><textarea name="RESTRICTIONS"  style="width:95%"  rows=3 cols=80><?php echo htmlentities($mydb->RESTRICTIONS); ?></textarea></td></tr>
			</table>
		<p>
	</div>

	<div id="PROJECT_TYPE_APPROVED">

		<p>
			<h3>Approved Studies</h3>
			<table class="whitebox">
			<tr class="even"><td colspan=2>
			<b>I&#39;m happy to hear that the IRB has approved of your application. </b>
			</td></tr>

			<tr><td>What is your IRB approval number?  </td><td><input name="IRBAPPROVAL" class="required" value="<?php echo htmlentities($mydb->IRBAPPROVAL); ?>" type="text"></td></tr>
			<tr class="even"><td>When does your IRB approval end? </td><td><input name="IRBENDDATE"  class="required" value="<?php echo htmlentities($mydb->IRBENDDATE); ?>" type="datefield"></td></tr>
			<tr><td>Can you provide some details and supply your approval letter? (Max file size:<?php echo(file_upload_max_size());?>)</td>
			<td>

				<?php if( $mydb_a->FILENAME ){ ?>
					<a href="studies_fileopen.php?<?php echo(encryptParameters('studies_fileopen.php','SYSID='.$_ENC["SYSID"].'&TYPE=APPROVAL_FILE'));?>" ><b>View Existing File That Was Uploaded :<br> <?php echo htmlentities($mydb_a->FILENAME ); ?></b></a><br>
					<a href="javascript:void(0);" onClick="$('#divAPPROVALFILE').show('slow');">Upload Another File (replaces previous approval file)</a>
				<?php } ?>
				<div id="divAPPROVALFILE" style="<?php if( $mydb_a->FILENAME ){ ?> display:none; <?php } ?>">
					<input type="file"  style="width:75%"  name="APPROVAL_FILE"  class="required">
				</div>

			</td></tr>
			<tr class="even"><td>Can you provide me your research plan / protocol? (Max file size:<?php echo(file_upload_max_size());?>)</td>
			<td>

				<?php if( $mydb_p->FILENAME ){ ?>
					<a href="studies_fileopen.php?<?php echo(encryptParameters('studies_fileopen.php','SYSID='.$_ENC["SYSID"].'&TYPE=PROTOCOL_FILE'));?>" ><b>View Existing File That Was Uploaded :<br> <?php echo htmlentities($mydb_p->FILENAME ); ?></b></a><br>
					<a href="javascript:void(0);" onClick="$('#divPROTFILE').show('slow');">Upload Another File (replaces previous protocol file)</a>
				<?php } ?>
				<div id="divPROTFILE" style="<?php if( $mydb_p->FILENAME ){ ?> display:none; <?php } ?>">
					<input type="file"  style="width:75%"  name="PROTOCOL_FILE"  class="required">
				</div>

			</td></tr>
			<tr><td>I acknowledge that the use of the i2b2 tool, either independently or with the assistance of i2b2 staff, in procuring data for published research, must be properly cited as set forth in the "Required Citations" tab on the i2b2 website.</td><td>Initials<input name="WILLDOCITATIONS" class="required" value="<?php echo htmlentities($mydb->WILLDOCITATIONS); ?>" type="text" maxlength=3 class="required"></td></tr>

			</table>
		<p>
	</div>

	<!--- since we will be refering to the selected list with ,ITEM, to determine if we have it, and rely on html to concat with commas, need to shove two commas and somesuch before it. --->
	<div id="project_data_select">
		<p>
			<h3>Requested Data Elements</h3>
			<table class="whitebox" >
				<tr><td>
					Below are possible data elements that you may have to populate your datamart. Please limit to the datatypes that are minimally necessary for your study. You may revisit this section at a later date if you need more data. All checked items will be marked as available in your datamart after review.

					<table width='100%'>
						<thead>
							<tr>
								<th>Use?</th>
								<th>Code</th>
								<th>Description</th>
							</tr>
						</thead>
						<tbody>
						<?php
							$selected_data = $mydb->CONCEPTS_SELECTED;
							if( strpos(trim($selected_data),'<') !== false ){
								$selected_data = simplexml_load_string($selected_data);
							} else {
								$selected_data = new SimpleXMLElement('<selected/>');
								//old style comma seperated -> XML
								foreach( explode(",",$mydb->CONCEPTS_SELECTED ) as $old ){
									$selected_data->addChild($old);
								}
							}
							foreach( $available as &$item ){
								$exists = false;
								$selected_item_options = $selected_data->xpath($item->C_TABLE_CD);
								if( file_exists( "custom/".$item->C_TABLE_CD.".php" ) ){
									$exists = true;
								}
								echo("<tr><td style='padding:2px;'>" );
								if( !$exists ){
									echo("<input name=\"CONCEPTS_SELECTED_$item->C_TABLE_CD\" type='checkbox' ".($selected_item_options?"checked":"")." value='$item->C_TABLE_CD'>");
								} else {
									echo("<input name=\"CONCEPTS_SELECTED_$item->C_TABLE_CD\" type='checkbox' ".($selected_item_options?"checked":"")." value='$item->C_TABLE_CD'
										onClick=\"
											$('#CUSTOM_SECTION_$item->C_TABLE_CD').toggle(this.checked);
										\">"
									);
								}
								echo("</td> <td style='padding:2px;'>$item->C_TABLE_CD</td> <td style='padding:2px;'>$item->C_NAME</td> </tr>\n\t\t\t\t\t\t" );
								if( $exists ){
									echo("<tr><td id='CUSTOM_SECTION_$item->C_TABLE_CD' colspan=3 style='background-color:#f3fff3;".($selected_item_options?"":"display:none;")."'>");
										include( "custom/".$item->C_TABLE_CD.".php" );
									echo("</td></tr>\n\t\t\t\t\t\t");
								}
							}?>
						</tbody>
					</table>
				</td></tr>
			</table>

		</p>
	</div>

	<div id="PROJECT_TYPE_SELECT_DATA">
		<p>
			<h3>Do you have a list of patients you wish to upload?</h3>
			<table class="whitebox">
				<tr><td><input class="required" type='radio' id='UPLOADLIST' name='UPLOADLIST' value='Y' <?php if( $mydb->UPLOADLIST=='Y'){echo('checked');}?> onClick="uploadList()"></td>
					<td><b>Yes, I have a list of patients to upload</b></td></tr>
				<tr  class="even">
					<td><input class="required" type='radio' id='UPLOADLIST' name='UPLOADLIST' value='N' <?php if( $mydb->UPLOADLIST=='N'){echo('checked');}?> onClick="uploadList()"></td>
					<td><b>No, I don&#39;t have a list of patients to upload</b></td></tr>
			</table>

		</p>
	</div>
	<div id ="NEED_REDCAP_CONNECT">
		<p>
			<h3>Will you need to connect this project to a REDCap Project?</h3>
			<table class="whitebox">
				<tr>
					<td>
						<input class="required" type='radio' id='REDCAP_NEEDED' name='REDCAP_NEEDED' value='Y' <?php if( $mydb->REDCAP_NEEDED=='Y'){echo('checked');}?> onClick="redcapProject()">
					</td>
					<td>
						<b>Yes, I will need to connect this I2B2 Project to a REDCap Project</b>
					</td>
				</tr>
				<tr class="even">
					<td>
						<input class="required" type='radio' id='REDCAP_NEEDED' name='REDCAP_NEEDED' value='N' <?php if( $mydb->REDCAP_NEEDED=='N'){echo('checked');}?> onClick="redcapProject()">
					</td>
					<td>
						<b>No, I will <b>NOT</b> need to connect this I2B2 Project to a REDCap Project</b>
					</td>
				</tr>
			</table>

		</p>
	</div>
	<div id="redcap_project_select">
		<p>
			<h3>What REDCap project would you like to connect this project to?</h3>
			<table class="whitebox">
				<tr>
					<td>Project Name</td>
					<td width="65%">
						<input name="REDCAP_PRJ_NAME" value="<?php echo htmlentities($mydb->REDCAP_PRJ_NAME); ?>" type="text"  style="width:95%" >
					</td>
				</tr>
			</table>
		</p>
	</div>

	<div  id='nolist'>
		<p>
			<h3>Do you have an existing I2B2 Query you would like to use?</h3>
			<table class="whitebox">
				<tr><td><input class="required" type='radio' id='I2B2QUERY_YN' name='I2B2QUERY_YN' value='Y' <?php if( $mydb->I2B2QUERY_YN=='Y'){echo('checked');}?> onClick="i2b2Query()"></td>
					<td><b>Yes, I have a I2B2 query I would like to use</b></td></tr>
				<tr  class="even">
					<td><input class="required" type='radio' id='I2B2QUERY_YN' name='I2B2QUERY_YN' value='N' <?php if( $mydb->I2B2QUERY_YN=='N'){echo('checked');}?> onClick="i2b2Query()"></td>
					<td><b>No, I don&#39;t have a I2B2 query I would like to use</b></td></tr>
			</table>
		</p>
	</div>

	<div id='selectI2b2Query'>
		<p>
			<h3>Which I2B2 Query Would You Like To Use?</h3>
			<table class="whitebox">
				<tr><td>
				This query will be run during the datamart identification process weekly to identify your study cohort.
					<select name='QUERY_MASTER_ID_NUM'>
					<?php
						$isdone = false;
						echo( "<option value=''> None</option>" );
						foreach( $myqueries as &$item ){
							echo( "<option value='i2b2demodata.$item->QUERY_MASTER_ID' " );
							if("i2b2demodata.".$item->QUERY_MASTER_ID == $mydb->QUERY_MASTER_ID ){
								echo( "selected" );
								$isdone = true;
							}
							echo(">$item->USER_ID, $item->START_DATE, Sized: $item->SET_SIZE, $item->DESCRIPTION </option>");
						}
						foreach( $myqueries2 as &$item ){
							echo( "<option value='i2b2datadeid.$item->QUERY_MASTER_ID' " );
							if("i2b2datadeid.".$item->QUERY_MASTER_ID == $mydb->QUERY_MASTER_ID ){
								echo( "selected" );
								$isdone = true;
							}
							echo(">$item->USER_ID, $item->START_DATE, Sized: $item->SET_SIZE, $item->DESCRIPTION </option>");
						}
						if( !$isdone ){
							echo("<option value='".$mydb->QUERY_MASTER_ID."' selected>".$mydb->QUERY_MASTER_ID."</option>");
						}
					?>
				</select></td></tr>
			</table>
		</p>
	</div>

	<div id='noi2b2query'>
		<p>
			<h3>What Data Do You Need?</h3>
			<table class="whitebox">
				<tr><td>
					Please tell us the criteria we should use to find the patients you want to have included in your datamart. (e.g. Diabetic Patients)
				</td></tr>
				<tr><td><textarea name="NOLISTDESC"  style="width:95%"  class="required" rows=3 cols=80><?php echo htmlentities($mydb->NOLISTDESC); ?></textarea></td></tr>
			</table>
		</p>
	</div>
	<div id='requesteditemsbox'>
		<p>
			<h3>How Will You Be Using The Data?</h3>
			<table class="whitebox">
				<tr><td>
					Tell us how you will use the data that you have requested. (required)
				</td></tr>
				<tr><td><textarea name="REQUESTEDITEMS"  style="width:95%"  class="required" rows=3 cols=80><?php echo htmlentities($mydb->REQUESTEDITEMS); ?></textarea></td></tr>
			</table>

		</p>
	</div>

	<?php if( $mydb->PROJECTCODE != '' && current_user_can( 'manage_options' ) ){ ?>

		<div id='GENERATIONSQL'>
			<p>
				<h3>SQL Query?</h3>
				<table class="whitebox">
					<tr><td>
						The following query generates the Patient IDS that will populate the database.
					</td></tr>
					<tr><td><?php echo htmlentities($mydb->GENERATION_SQL); ?></td></tr>
				</table>
			</p>
		</div>


		<div id="PROVISIONING">
			<p>
			<h3>User Access</h3>
			<table class="whitebox">
			<tr>
				<td>Below are the usernames that have access to the system, semicolon delimited - thus ;amtatro;irvine; adds adam and carrie to the list. must be semicolon terminated (before and after) and seperated.</td>
				<td width="65%"><input name="ADDITIONALUSERS" value="<?php echo htmlentities($mydb->ADDITIONALUSERS); ?>" type="text"  style="width:95%" ></td>
			</tr>
			</table>
			</p>
		</div>
		<div id="REDCAP_KEY" >
			<p>
				<h3>REDCap Systems Integration</h3>
				<table class="whitebox">
				<tr><td colspan=2>
					Below are the settings used to integrate with REDCap API (Advanced Programming Interface).

					<ol style='margin-left:55px;'>
						<li>First you log into your REDCap Project. You will need to be a study administrator with access to API functions to allows this to work. If you think you should have access and currently do not, please make a request to the CTSI Research Helpdesk to request access.</li>
						<li>On the left hand side of your project welcome screen, there will be an option to request an API key.</li>
						<li>A code will be generated for you, you will receive notification when the REDCap administrator grants access.</li>
						<li>You can copy that information and key below. (red arrow in illustration below)</li>
						<li>To find the REDCap API address, click on the link in green that directs you to the API guide.</li>
						<li>That guide will have the link to the REDCap URL.</li>
					</ol>

				</td></tr>
				<tr>
					<td>REDCap WebAPI URL: <br><a href="javascript:void(0);" onClick="$('#RedCAPURLInstructions').toggle('slow');">Click Here To Show Instructions</a></td>
					<td width="65%"><input name="REDCAP_URL" value="<?php echo htmlentities($mydb->REDCAP_URL); ?>" type="text"  style="width:95%" ></td>
				</tr>
				<tr><td colspan=2 id="RedCAPURLInstructions" style="display:none;">
					<div >
						<h2>Where To Get The URL</h2>
						<img src='/images/redcap_request2.png' width='650' />
						<img src='/images/redcap_url.png' width='450' />
					</div>
				</td>
				<tr>
					<td>REDCap WebAPI Authorization Token/Key  <br><a href="javascript:void(0);" onClick="$('#RedCAPAPIInstructions').toggle('slow');">Click Here To Show Instructions</a></td>
					</td><td><input name="REDCAP_APIKEY" value="<?php echo htmlentities($mydb->REDCAP_APIKEY); ?>" type="text"  style="width:95%" ></td>

				</tr>
				<tr><td colspan=2 id="RedCAPAPIInstructions" style="display:none;">
					<div >
						<h2>Where To Get The Key</h2>
						<img src='/images/redcap_request.png' width='650' />
					</div>
				</td></tr>
				</table>

			</p>
		</div>
	<?php }

	if(current_user_can( 'manage_options' )){ ?>
		<div id='NEWPROJECTCODE'>
			<p>
				<h3>PROJECTCODE For Datamart Creation</h3>
				<table class="whitebox">
					<tr>
						<td>Enter the PROJECTCODE you want to use for the datamart creation process. <span style='color:red'><br/>**MUST BE IN ALL CAPS**<br/>**DO NOT USE <u>DEMO</u> OR <u>DEMODEID</u>**</span></td>
						<td width="65%"><input name="PROJECTCODE" value="<?php echo htmlentities($mydb->PROJECTCODE);?>" type="text"  style="width:95%" ></td>
					</tr>
				</table>
			</p>
		</div>
	<?php }?>
	<div id="PROJECT_HIPAA_IDENTIFIERS">

		<p>
			<h3>HIPAA Identifiers Needed?</h3>
			<table class="whitebox">
			<tr ><td colspan=2> <b>Will your project need HIPAA Identifiers? </b> </td></tr>

			<tr class="even">
				<td><input name="PHI" type="radio" class="required" value="N" <?php if( $mydb->PHI=='N'){echo('checked');}?>></td>
				<td>My project can utilize <b>De-Identified</b> data.<br><br> You may revisit the answer to this question at any time, if you choose to request identified information in the future.</td>
			</tr>

			<tr>
				<td><input name="PHI" type="radio" class="required" value="Y" <?php if( $mydb->PHI=='Y'){echo('checked');}?>></td>
				<td>My project will need PHI information.<br>

					<ul style='background-color:#EEEEEE;border:1px black;font-size:.85em;' id="HIPAA_NON_PREPATORY">
						<li>Names;<br><br></li>
						<li>All geographical subdivisions smaller than a State, including street address, city, county, precinct, zip code, and their equivalent geocodes, except for the initial three digits of a zip code, if according to the current publicly available data from the Bureau of the Census: (1) The geographic unit formed by combining all zip codes with the same three initial digits contains more than 20,000 people; and (2) The initial three digits of a zip code for all such geographic units containing 20,000 or fewer people is changed to 000.<br><br></li>
						<li>All elements of dates (except year) for dates directly related to an individual, including birth date, admission date, discharge date, date of death; and all ages over 89 and all elements of dates (including year) indicative of such age, except that such ages and elements may be aggregated into a single category of age 90 or older;<br><br></li>
						<li>Phone numbers;</li>
						<li>Fax numbers;</li>
						<li>Electronic mail addresses;</li>
						<li>Social Security numbers;</li>
						<li>Medical record numbers;</li>
						<li>Health plan beneficiary numbers;</li>
						<li>Account numbers;</li>
						<li>Certificate/license numbers;</li>
						<li>Web Universal Resource Locators (URLs);</li>
						<li>Internet Protocol (IP) address numbers;</li>
						<li>Any other unique identifying number, characteristic, or code (note this does not mean the unique code assigned by the investigator to code the data)</li>
					</ul>

					<ul style='background-color:#EEEEEE;border:1px black;font-size:.85em;' id="HIPAA_PREPATORY">
						<li>All geographical subdivisions smaller than a State, including street address, city, county, precinct, zip code, and their equivalent geocodes, except for the initial three digits of a zip code, if according to the current publicly available data from the Bureau of the Census: (1) The geographic unit formed by combining all zip codes with the same three initial digits contains more than 20,000 people; and (2) The initial three digits of a zip code for all such geographic units containing 20,000 or fewer people is changed to 000.<br><br></li>
						<li>All elements of dates (except year) for dates directly related to an individual, including birth date, admission date, discharge date, date of death; and all ages over 89 and all elements of dates (including year) indicative of such age, except that such ages and elements may be aggregated into a single category of age 90 or older;<br><br></li>
					</ul>

				</td>
			</tr>


			</table>
		<p>
	</div>

	<p>
		<h3>Do you have any additional comments/concerns?</h3>
		<table class="whitebox">
		<tr><td><textarea name="COMMENTS"  style="width:85%" rows=6 cols=80><?php echo htmlentities($mydb->COMMENTS); ?></textarea></td></tr>
		</table>

	<p>
		<h3>Closing Comments</h3>
		<table class="whitebox"><tr><td>
		This information will allow us to comply with HIPAA privacy regulations for appropriate uses and disclosures of PHI (Protected Health Information).  You must limit your request of PHI per the HIPAA minimum necessary standard to accomplish your task.
		<br><br><b>I understand and agree to comply with all URMC and affiliates HIPAA policies regarding the use and disclosure of PHI.</b>
		<table><tr class="even"><td>Initial Here:</td><td>
			<input value="<?php echo htmlentities($mydb->HIPPAAGREEINITIALS); ?>" name="HIPPAAGREEINITIALS" maxlength=3 class="required" message="Putting your initials here indicates that you are cognizant of HIPPA and you abide by it.">
		</td></tr></table>

		After submitting this form, if your project is preparatory to reseach, then you will be granted access to the deidentified query tool, otherwise, we will send you a confirmation and we will communicate via email.
		<br>
		<input type="hidden" value="SUBMITTED" name="STATUS">
		<input type="hidden" value="#dateformat(now(),'mm/dd/yyyy')#" name="SUBMITTED_DATE">

		<input type="submit" value="  Submit Form For Review!  " name="action1">

		</td></tr></table>
	</p>

	<?php echo($_ENC["SYSID"]); ?>

</form>


	<?php get_footer();?>


<?php } else { header("Location:login.php");  } ?>