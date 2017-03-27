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
 * This top level page shows the list of projects which has data export options.
 * This page then forwards the item to studiespatientlistmgmt.php
 */

require_once("../common.php");
$_ENC = decryptGetMethod();
if( isLoggedIn() ){

	get_header();

	if( current_user_can( 'manage_options' ) && $_ENC["everyone"] ){
		//CRI-170 - minor creature feature to sort by submitted date
		$pest = $DB->get_results( "
				SELECT PROJECTS.*,
						TO_CHAR(CREATION_DATE,'MM/DD/YYYY') AS CDATE ,
						TO_CHAR(TIME_STAMP,'MM/DD/YYYY') AS EDATE,
						TO_CHAR(SUBMITTED_DATE,'MM/DD/YYYY') AS SUBMITTED
				FROM PROJECTS
				WHERE NOT PROJECTCODE IS NULL
				ORDER BY SUBMITTED_DATE DESC NULLS LAST, PROJECTCODE, CREATION_DATE DESC
		"  ,OBJECT );

	} else {
		//CRI-170 - minor creature feature to sort by submitted date
		$pest = $DB->get_results( "
				SELECT PROJECTS.*,
						TO_CHAR(CREATION_DATE,'MM/DD/YYYY') AS CDATE ,
						TO_CHAR(TIME_STAMP,'MM/DD/YYYY') AS EDATE,
						TO_CHAR(SUBMITTED_DATE,'MM/DD/YYYY') AS SUBMITTED
				FROM PROJECTS
				WHERE ((UPPER(TRIM(PROJECTREQUESTER)) = UPPER(TRIM('". f($_SESSION["USERNAME"]) ."')) OR LOWER(ADDITIONALUSERS) LIKE '%;".f(strtolower($_SESSION["USERNAME"])).";%')) AND
					  ISDELETED IS NULL AND NOT STATUS IS NULL AND NOT PROJECTCODE IS NULL
				ORDER BY SUBMITTED_DATE DESC NULLS LAST, PROJECTCODE, CREATION_DATE DESC
		"  ,OBJECT );
	}

?>
<h2>Manage Patient List For Data Extraction</h2>
<hr>


<style>
	table, tr, th, td{ padding:10px; }
</style>


	<?php if( current_user_can( 'manage_options' ) && !$_ENC["everyone"] ){?>
		<table width=100% bgcolor=pink>
			<tr valign=center align=center>
			<td >
				<img src="/images/erred.gif">
			</td>
			<td>
				<b>You are an administrator!</b><br>
				To show all projects, click here: <a href="studiespatientlist.php?<?php echo(encryptParameters('studiespatientlist.php','everyone=Y'));?>">Show Everyone&#039;s Stuff!</a>
			</td></tr>
		</table>
	<?php } ?>


	<div id="boilerplate">
		<p>
			Please select the project you want to analyze.
		</p>
	</div>

	<p>
		<table width="100%" style="cursor:hand;">

			<tr>
				<?php if( current_user_can( 'manage_options' ) ){?>
					<th>User</th>
				<?php } ?>
				<th width=8%>Created</th>
				<th width=8%>Changed</th>
				<th width=70%>Title</th>
				<th width=10%>Status</th>
				<th width=5%>HIPAA?</th>
				<th width=8%>Actions</th>
			</tr>

			<?php $even = false; $hasany = false;?>
			<?php foreach($pest as &$line){ $hasany = true; ?>

				<tr <?php if($line->ISDELETED=='Y'){ ?>bgcolor="pink"<?php } elseif($even){ ?>bgcolor="#e3e3e3"<?php } $even = !$even; ?> onMouseOut="this.style.backgroundColor= '';" onMouseOver="this.style.backgroundColor= 'pink';" title="click to open">

					<?php if( current_user_can( 'manage_options' ) ){?>
						<td><?php echo($line->PROJECTREQUESTER);?></td>
					<?php } ?>
					<td onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>';"><?php echo($line->CDATE);?></td>
					<td onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>';"><?php echo($line->EDATE);?></td>
					<td onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>';"><b><?php if( current_user_can( 'manage_options' ) ){ echo($line->PROJECTCODE."-"); }?> </b> <?php echo($line->PROJECTTITLE);?> </td>
					<td onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>';">
						<?php echo($line->STATUS);?>
						<?php if($line->STATUS=='APPROVED'){?>
							<img src="/images/checkmark.png" width=15 height=15>
						<?php } ?>
					</td>
					<td onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>';"><?php echo($line->PHI);?></td>
					<td onClick="window.location='studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>';"><a href="studiespatientlistmgmt.php?<?php echo(encryptParameters('studiespatientlistmgmt.php','SYSID='.$line->PROJECTID));?>"><b>Analyze Data</b></a></td>
				</tr>
			<?php } ?>

			<?php if(!$pest){ ?>
				<tr><td colspan=11>

				<table width=100% bgcolor=pink>
					<tr valign=center align=center>
					<td >
						<img src="/images/erred.gif">
					</td>
					<td>
						<b>There are no active approved projects to manage patient lists for!</b><br>
						If you believe you should have access to manage patient lists, please contact the research help desk. Please bear in mind that requests take 2-3 business days to review and to set up.
						<br><br><b>Your username is <?php echo(strtolower($_SESSION["USERNAME"])); ?></b>
					</td></tr>
				</table>

				</td></tr>
			<?php } ?>
		</table>

	</p>


	<?php get_footer();?>


<?php } else { header("Location:login.php");  } ?>

