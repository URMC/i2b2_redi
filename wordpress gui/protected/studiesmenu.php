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
 * This is a top level page that shows all pending requests to the i2b2 system.
 * It then sends requests to studiesopen.php.
 */

require_once("../common.php");
$_ENC = decryptGetMethod();
if( isLoggedIn() ){

	get_header();

	if( current_user_can( 'manage_options' ) && $_ENC["everyone"] ){
		//CRI-170 - minor creature feature to sort by submitted date
		$pest = $DB->get_results( "
				SELECT PROJECTS.*,
						TO_CHAR(CREATION_DATE,'MM/DD/YY') AS CDATE ,
						TO_CHAR(TIME_STAMP,'MM/DD/YY') AS EDATE,
						TO_CHAR(SUBMITTED_DATE,'MM/DD/YY') AS SUBMITTED
				FROM PROJECTS
				ORDER BY SUBMITTED_DATE DESC NULLS LAST, ISDELETED DESC, UPPER(PROJECTREQUESTER)
		"  ,OBJECT );
	} else {
		//CRI-170 - minor creature feature to sort by submitted date
		$pest = $DB->get_results( "
				SELECT PROJECTS.*,
						TO_CHAR(CREATION_DATE,'MM/DD/YY') AS CDATE ,
						TO_CHAR(TIME_STAMP,'MM/DD/YY') AS EDATE,
						TO_CHAR(SUBMITTED_DATE,'MM/DD/YY') AS SUBMITTED
				FROM PROJECTS
				WHERE ((UPPER(TRIM(PROJECTREQUESTER)) = UPPER(TRIM('". $_SESSION["USERNAME"] ."')) OR ADDITIONALUSERS LIKE '%;".$_SESSION["USERNAME"].";%')) AND
					  ISDELETED IS NULL AND NOT STATUS IS NULL
				ORDER BY SUBMITTED_DATE DESC NULLS LAST
		"  ,OBJECT );
	}

?>
<h2>My Current Active Projects</h2>
<hr>


<style>
	table, tr, th, td{ padding:10px; }
</style>


	<div id="boilerplate">
		<p>
			Below are all of the research studies that you currently have in progress.
			If your approval date is fast approaching done, the system will highlight that project to note that it is almost close to finished
			and that you need to renew your approval with the Research Subjects Review Board. If your project has an expired date, you will
			not get any new data in this research portal until the project is renewed.
		</p>
		<p>
			You will receive emails from this system to remind you that your projects are near the end. The emails will be sent to
			the project contact email as listed in the descriptions below. When a project is approved and verified by a staff member
			at Medical Informatics, we will email you that we are ready to accept a patient list from you.
		</p>
	</div>

	<?php if( current_user_can( 'manage_options' ) && !$_ENC["everyone"] ){?>
		<table width=100% bgcolor=pink>
			<tr valign=center align=center>
			<td >
				<img src="/images/erred.gif">
			</td>
			<td>
				<b>You are an administrator!</b><br>
				To show all projects, click here: <a href='studiesmenu.php?<?php echo(encryptParameters('studiesmenu.php','everyone=y'));?>'>Show Everyone&#039;s Stuff!</a>
			</td></tr>
		</table>
	<?php } ?>

	<p>
		<table width="100%" style="cursor:hand;">

			<tr>
				<th width=7%>Edit</th>
				<?php if( current_user_can( 'manage_options' ) ){?>
					<th>User</th>
				<?php } ?>
				<th width=8%>Created</th>
				<th width=8%>Changed</th>
				<th width=8%>Type</th>
				<th width=70%>Title</th>
				<th width=10%>Status</th>
				<th width=8%>Actions</th>
			</tr>

			<?php $even = false; ?>
			<?php foreach($pest as &$line){ ?>

				<tr <?php if($line->ISDELETED=='Y'){ ?>bgcolor="pink"<?php } elseif($even){ ?>bgcolor="#e3e3e3"<?php } $even = !$even; ?> onMouseOut="this.style.backgroundColor= '';" onMouseOver="this.style.backgroundColor= 'pink';" title="click to open">
					<td><a href="studiesopen.php?<?php echo(encryptParameters('studiesopen.php','SYSID='.$line->PROJECTID));?>">Edit</a></td>
					<?php if( current_user_can( 'manage_options' ) ){?>
						<td><?php echo($line->PROJECTREQUESTER);?></td>
					<?php } ?>
					<td onClick="window.location='studiesopen.php?<?php echo(encryptParameters('studiesopen.php','SYSID='.$line->PROJECTID));?>';"><?php echo($line->CDATE);?></td>
					<td onClick="window.location='studiesopen.php?<?php echo(encryptParameters('studiesopen.php','SYSID='.$line->PROJECTID));?>';"><?php echo($line->EDATE);?></td>
					<td onClick="window.location='studiesopen.php?<?php echo(encryptParameters('studiesopen.php','SYSID='.$line->PROJECTID));?>';"><?php echo($line->PROJECTTYPE);?></td>
					<td onClick="window.location='studiesopen.php?<?php echo(encryptParameters('studiesopen.php','SYSID='.$line->PROJECTID));?>';"><b><?php if( current_user_can( 'manage_options' ) ){ echo($line->PROJECTCODE."-"); }?> </b><?php echo($line->PROJECTTITLE);?></td>
					<td onClick="window.location='studiesopen.php?<?php echo(encryptParameters('studiesopen.php','SYSID='.$line->PROJECTID));?>';">
						<?php echo($line->STATUS);?>
						<?php if($line->ISDELETED=='Y'){ ?>DELETED<?php } ?>
						<?php if($line->STATUS=='APPROVED'){?>
							<img src="/images/checkmark.png" width=15 height=15>
						<?php } ?>
					</td>
					<?php if($line->ISDELETED != 'Y'){?>
						<td><a href="javascript:void(0);" onClick="if( confirm('are you sure?') ){window.location='studiesdel.php?<?php echo(encryptParameters('studiesdel.php','PROJECTID='.$line->PROJECTID));?>';}">Delete</a></td>
					<?php } else { ?>
						<td><a href="javascript:void(0);" onClick="if( confirm('are you sure you want to truely delete?') ){window.location='studiestrulydel.php?<?php echo(encryptParameters('studiestrulydel.php','PROJECTID='.$line->PROJECTID));?>';}">Really Delete</a></td>
					<?php } ?>
				</tr>
			<?php } ?>

			<?php if(count($pest)==0){?>
			<tr>
				<td colspan=9>
					<table width=100% bgcolor=pink>
						<tr valign=center align=center>
						<td >
							<img src="/images/erred.gif">
						</td>
						<td>
							<b>You have no Project Plans Stored.</b><br>
							To start a new one, please click on the button below!
						</td></tr>
					</table>
				</td>
			</tr>
			<?php } ?>

			<form action="studiesopen.php">
			<tr <?php if($even){ ?>bgcolor="#e3e3e3"<?php } $even = !$even; ?>>
				<td colspan=9>
					<input type="submit" value="Add New Project To List / Start Project" style="width:100%">
				</td>
			</tr>
			</form>
		</table>

	</p>


	<?php get_footer();?>


<?php } else { header("Location:login.php");  } ?>

