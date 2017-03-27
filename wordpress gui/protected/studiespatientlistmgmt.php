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
 * This page utilizes the datatable plugin for jquery to provide excel-like entry
 * into the tracking table for these patients for the study. It then utilizes JSON
 * data source to populate the data, there's also an editor page to allow for data
 * saving.
 *
 * This page is downstream from studiespatientlist.php
 */

require_once("../common.php");
$_ENC = decryptGetMethod();
if( isLoggedIn() && isset($_ENC["SYSID"]) ){

	get_header();

	$pest = $DB->get_row( "
			SELECT PROJECTS.*
			FROM PROJECTS
			WHERE PROJECTID = '".f($_ENC["SYSID"])."'
	"  ,OBJECT );

 	if( $pest->UPLOADLIST=='Y' ){ ?>
	<style type="text/css" title="currentStyle">
		@import "<?php echo get_template_directory_uri(); ?>/css/datatable/demo_table.css";
	</style>
	<style>
		.dataTables_info {
			width:45%;
		}
	</style>
	<script type="text/javascript" language="javascript" src="<?php echo get_template_directory_uri(); ?>/js/datatable/jquery.jeditable.js"></script>
	<script type="text/javascript" language="javascript" src="<?php echo get_template_directory_uri(); ?>/js/datatable/jquery.dataTables.js"></script>
	<script type="text/javascript" charset="utf-8">
		var oTable = "";
		$(document).ready(function() {
			 oTable = $('#example').dataTable( {
				"iDisplayLength" : 25,
				"bProcessing": true,
				"bServerSide": true,
				"sAjaxSource": "studiespatientlistjson.php",
				"fnServerParams": function ( aoData ) {
					aoData.push( { "name": "PROJECTID", "value": "<?php echo($_ENC["SYSID"]);?>" } );
				},
				"tooltip"    : "Click to edit...",
				"placeholder": "a",
				"fnDrawCallback": function () {
					$('#example tbody td').editable( 'studiespatientlistedit.php', {
						"placeholder":"...",
						"callback": function( sValue, y ) {
							var aPos = oTable.fnGetPosition( this );
							oTable.fnUpdate( sValue, aPos[0], aPos[1] );

						},
						"submitdata": function ( value, settings ) {
							return {
								"row_id": this.parentNode.getAttribute('id'),
								"column": oTable.fnGetPosition( this )[2]
							};
						},
						"onerror": function (settings, original, xhr) {
							alert( xhr.responseText );
						}
					} );
				}
			} );
		} );
	</script>
<?php } ?>

<h2><?php echo($pest->PROJECTTITLE); ?></h2>
<hr>

<?php if( $pest->UPLOADLIST=='Y' ){ ?>
	<div id="dynamic">
		<table cellpadding="0" cellspacing="0" border="0" class="display" id="example">
			<thead>
				<tr>
					<th width="4%">ID#</th>
					<th width="6%">Study#</th>
					<th width="6%">MRN</th>
					<th width="5%">Site </th>
					<th width="15%">First Name</th>
					<th width="15%">Last Name</th>
					<th width="10%">DOB</th>
					<th width="10%">Enrollment</th>
					<th width="4%">Arm</th>
					<th width="19%">Messages</th>
				</tr>
			</thead>
			<tbody>
				<tr>
					<td colspan="5" class="dataTables_empty">Loading data from server</td>
				</tr>
			</tbody>
			<tfoot>
				<tr>
					<th width="4%">ID#</th>
					<th width="6%">Study#</th>
					<th width="6%">MRN</th>
					<th width="5%">Site </th>
					<th width="15%">First Name</th>
					<th width="15%">Last Name</th>
					<th width="10%">DOB</th>
					<th width="10%">Enrollment</th>
					<th width="4%">Arm</th>
					<th width="19%">Messages</th>
				</tr>
			</tfoot>
		</table>
	</div>

	<?php if( current_user_can( 'manage_options' ) ){ ?>
		<input type="button" onClick="window.location='datamart.php?<?php echo(encryptParameters('datamart.php','SYSID='.$_ENC["SYSID"].'&JOB_TYPE=DatamartCreator&JOB_DESC=Create Datamart'));?>'" value="Create DM">
		<input type="button" onClick="window.location='datamart.php?<?php echo(encryptParameters('datamart.php','SYSID='.$_ENC["SYSID"].'&JOB_TYPE=DatamartDeleter&JOB_DESC=Delete Datamart'));?>'" value="Delete DM">
		<input type="button" onClick="window.location='datamart.php?<?php echo(encryptParameters('datamart.php','SYSID='.$_ENC["SYSID"].'&JOB_TYPE=DatamartLoader&JOB_DESC=Load Datamart'));?>';" value="Load DM">
		<input type="button" onClick='$.ajax({url:"studiespatientlistadd.php?<?php echo(encryptParameters('studiespatientlistadd.php','SYSID='.$_ENC["SYSID"]));?>",success:function(result){ oTable.fnDraw(); }});' value="New Pt">
		<input type="button" onClick="window.location='studiespatientlistexcelupload.php?<?php echo(encryptParameters('studiespatientlistexcelupload.php','SYSID='.$_ENC["SYSID"]));?>';" value="Upload">
	<?php } else { ?>
		<input type="button" onClick='$.ajax({url:"studiespatientlistadd.php?<?php echo(encryptParameters('studiespatientlistadd.php','SYSID='.$_ENC["SYSID"]));?>",success:function(result){ oTable.fnDraw(); }});' value="New Patient">
		<input type="button" onClick="window.location='studiespatientlistexcelupload.php?<?php echo(encryptParameters('studiespatientlistexcelupload.php','SYSID='.$_ENC["SYSID"]));?>';" value="Upload Excel">
	<?php } ?>

<?php } else { ?>
	<table width=100% bgcolor=pink>
		<tr valign=center align=center>
		<td >
			<img src="/images/erred.gif">
		</td>
		<td>
			<b style='size:2em;'>Not A Upload Project</b><br>
			This project is populated from an i2b2 query, and you should not upload
		</td></tr>
	</table>
	<?php if( current_user_can( 'manage_options' ) ){ ?>
		<input type="button" onClick="window.location='datamart.php?<?php echo(encryptParameters('datamart.php','SYSID='.$_ENC["SYSID"].'&JOB_TYPE=DatamartCreator&JOB_DESC=Create Datamart'));?>'" value="Create DM">
		<input type="button" onClick="window.location='datamart.php?<?php echo(encryptParameters('datamart.php','SYSID='.$_ENC["SYSID"].'&JOB_TYPE=DatamartDeleter&JOB_DESC=Delete Datamart'));?>'" value="Delete DM">
		<input type="button" onClick="window.location='datamart.php?<?php echo(encryptParameters('datamart.php','SYSID='.$_ENC["SYSID"].'&JOB_TYPE=DatamartLoader&JOB_DESC=Load Datamart'));?>';" value="Load DM">
	<?php } ?>
<?php } ?>


	<?php get_footer();?>


<?php } else { header("Location:login.php");  } ?>

