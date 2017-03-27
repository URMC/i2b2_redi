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
 * This is an upload page hander that shows the upload screen and instructions
 * and then whence a file is uploaded, it saves the file to a temporary space and
 * then runs the excel import process to load the data to temporary table. We are
 * changing this to utilize the standard Java service method and transfer a blob
 * entry to file storage; this page will hopefully be changing soon.
 *
 * This is downstream from studiespatientlistmgmt.php.
 */


require_once("../common.php");
if( !isset( $_POST["ENC"] ) ){
	$_ENC = decryptGetMethod();
} else {
	$_ENC = decryptParameters(basename($_SERVER['SCRIPT_FILENAME']),$_POST["ENC"]);
}

if( isLoggedIn() && isset($_ENC["SYSID"]) ){

	get_header();

	//new dBug( $DB );

	$sysid = $_ENC["SYSID"];

	$pest = $DB->get_row( "SELECT PROJECTS.* FROM PROJECTS WHERE PROJECTID = '".intval($sysid)."' "  ,OBJECT );

	$location = "";

	$nextval = "";

	if( isset( $_FILES ) && sizeof($_FILES) > 0 ){

		$allowedExts = array("xlsx","xls");
		$extension = strtolower(end(explode(".", $_FILES["file"]["name"])));

		if (in_array($extension, $allowedExts)){

			if ($_FILES["file"]["error"] > 0){
				if( $_FILES["file"]["error"] == "1" ){
					echo "<h2 style='color:red'>File is too big.</h2>";
				} else {
					echo "Return Code: " . $_FILES["file"]["error"] . "<br>";
				}
			} else {
				if ($_FILES["file"]["size"] < 2048000 ){
					$location = "uploads/" . $sysid . "_" . date("YmdHis"). "." . $extension;
					if( move_uploaded_file($_FILES["file"]["tmp_name"], "/var/www/html/".$location) ){
					} else {
						$location = "";
						echo("hmm - couldn't upload?");
					}
				} else {
					echo "<h2 style='color:red'>File is too big.</h2>";
				}
			}

			$nextval = $DB->get_var("SELECT SYSIDS.NEXTVAL FROM DUAL");
			$DB->query( "INSERT INTO PROJECTS_UPLOADS(LOC, STATUS, ID) VALUES ('$location','New','$nextval')");

		} else {
			echo "<h2 style='color:red'>Invalid file, file must be an excel XLS or XLSX file.</h2>";
		}

	}


?>
	<style>
		#main_content ol,ul{ margin:auto;padding-left: 20px; }
		form table,td,tr,th{ padding:5px; border:1px solid black;}
	</style>

	<h2><?php echo($pest->PROJECTTITLE); ?></h2>

		<?php if( $location == "" ){ ?>
		<form method="post" enctype="multipart/form-data">


		<p>
			<input type="hidden" name="ENC" value="<?php echo(encryptParameters('studiespatientlistexcelupload.php','SYSID='.$_ENC['SYSID'])); ?>"><br>
			<table><tr><td>
			<b>Select an excel file (Max file size:<?php echo(file_upload_max_size());?>)</b>
			</td><td>
			<input type="file" name="file" id="file"><br>
			</td><td>
			<input type="submit" name="submit" value="Submit">
			</td>
			</tr></table>
		</p>

		<p>
		<b>This system will let you populate the list of patients using an existing excel (XLS,XLSX) document.</b>
		<br><br>
		<ol>
			<li>
				First select and upload an excel file. This program will <b>only</b> look at the first sheet, and the file cannot exceed 2MB. <br>
				The excel file <u><b>must contain</b></u> the following set of <span style="color:red;"><b>required red columns</b></span> and columns must start in row 1:
				<br><br>
				<table style="background-color:white;" ><tr>
					<th style='background-color:red;color:white;'><b>MRN</b></th>
					<th><b>SITE</b></th>
					<th style='background-color:red;color:white;'><b>DOB</b></th>
					<th>StudyID</th>
					<th>Additional</th>
					<th>StudyGroup</th>
				</tr><tr>
					<td style='background-color:pink'>123456</td>
					<td>HH</td>
					<td style='background-color:pink'>4/1/1993</td>
					<td>A00001</td>
					<td>This optional column will be stored in database, additional data, like study group will be stored if you want to query by it.</td>
					<td>This patient is in Group A</td>
				</tr><tr>
					<td style='background-color:pink'>1234567</td>
					<td>SMH</td>
					<td style='background-color:pink'></td>
					<td>B00002</td>
					<td></td>
					<td>This patient is in Group B</td>
				</tr>
				</table>

				<br><b>or</b><br><br>

				<table style="background-color:white;" ><tr>
					<th style='background-color:red;color:white;'>SITE</th>
					<th style='background-color:red;color:white;'>FIRSTNAME</th>
					<th style='background-color:red;color:white;'>LASTNAME</th>
					<th style='background-color:red;color:white;'>DOB</th>
					<th>Additional</th>
				</tr><tr>
					<td style='background-color:pink'>HH</td>
					<td style='background-color:pink'>FirstNom</td>
					<td style='background-color:pink'>TestLastNom</td>
					<td style='background-color:pink'>4/1/1993</td>
					<td>This column will be stored in database</td>
				</tr><tr>
					<td style='background-color:pink'>SMH</td>
					<td style='background-color:pink'>Imaginary</td>
					<td style='background-color:pink'>Person</td>
					<td style='background-color:pink'>1/1/1983</td>
					<td></td>
				</tr>
				</table>

				<br><b>or</b><br><br>

					<table style="background-color:white;" ><tr>
						<th style='background-color:red;color:white;'>FIRSTNAME</th>
						<th style='background-color:red;color:white;'>LASTNAME</th>
						<th style='background-color:red;color:white;'>MRN</th>
						<th>Additional</th>
					</tr><tr>
						<td style='background-color:pink'>FirstNom</td>
						<td style='background-color:pink'>TestLastNom</td>
						<td style='background-color:pink'>2293800</td>
						<td>This column will be stored in database</td>
					</tr><tr>
						<td style='background-color:pink'>Imaginary</td>
						<td style='background-color:pink'>Person</td>
						<td style='background-color:pink'>12345673</td>
						<td></td>
					</tr>
				</table>


				<ul>

				</ul><br><br>

			</li>
			<li>The system will then merge the list with the current list of patients into a temporary space</li>
			<li>You will be shown the number of non matches or issues.</li>
			<li>If you plan to use the RedCAP integration module, you will need to supply two additional fields, the Visit 0 / Enrollment in a field labelled "ENROLLED" and if your study has differing arms, a field labelled "ARM".</li>
			<li>This system will accept STUDYID as a column as well.</li>
			<li>MRNs should not have "dashes" and dates should be in the format M/D/YYYY</li>
			<li>If you accept the list as is, you can press "final import" which will allow you to save the data to the main list.</li>
		</ol>
		<br>
		If you have additional columns in your excel file, they will be stored and they will be added to your data mart as selectable columns.
		You may use this tool at any time to upload new columns to your dataset that are not provided by your i2b2 data mart.
		</p>


		</form>

	<?php } else { ?>

		<script>
			$( document ).ready( function(){

				// refresh every 3000 milliseconds
				var auto_refresh = setInterval(
					function (){
						$('#waiter').load('studiespatientlistexcelchecker.php?' ,
							{ENC:"<?php echo(encryptParameters('studiespatientlistexcelchecker.php','SYSID='.$_ENC['SYSID'].'&LOCATION='.$nextval)); ?>" }
						);
						$('#waiter').scrollTop($('#waiter')[0].scrollHeight);
					}
					, 3000
				);

				$.ajax( {
					url: "studiespatientlistexcelproc.php",
					data: "<?php echo(encryptParameters('studiespatientlistexcelproc.php','SYSID='.$_ENC['SYSID'].'&LOCATION='.$nextval)); ?>"
				} ).done(
					function(){
						$("#nextscreen").removeAttr("disabled");
					}
				);

			} );
		</script>
		<form action="studiespatientlistexcelmerge.php" method="POST">
			<input type="hidden" name="ENC" value="<?php echo(encryptParameters('studiespatientlistexcelmerge.php','SYSID='.$_ENC['SYSID'])); ?>" >
			<div id="waiter">
				Upload A Data File!
			</div>
			<input type="submit" id="nextscreen" value = "  Continue To Merge Page  " disabled >
		</form>

	<?php } ?>
	<?php get_footer();?>

<?php } else { header("Location:login.php");  } ?>