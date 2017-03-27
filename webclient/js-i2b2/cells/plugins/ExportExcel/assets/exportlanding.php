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
 * This page sets up the UI popup boxes needed for the Excel export module as well
 * as the identifiers needed for the excel mappings to persist.
 */

 require_once("../config.php");

?>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<!--
/**
 * @projectDescription	i2b2 Excel Export Plugin
 * @inherits			i2b2
 * @namespace			i2b2.ExportExcel
 * @author				Axel Newe
 * ----------------------------------------------------------------------------------------
 * updated 2013-01-09: Initial Launch [Axel Newe, FAU Erlangen-Nuremberg]
 */
-->
<html>
	<body>

		<div id='ExportExcel-mainDiv'>

		<!-- ############### Excel Popup Box ############### -->
			<div id="ExcelPassword" style="display:none;">
				<div class="hd" style="background:#6677AA;">Friendly Reminder...</div>
				<div class="bd modLabValues" id="ExportExcel-Password">
					<div style="margin: 0px 5% 12px; text-align: center;" id="REDCapvalueContraintText">
					I acknowledge my obligation to secure all Protected Health Information (PHI) and to limit
					access to data in accordance with URMC and Affiliates Privacy and Security Policies and Procedures.
					<br><br>
					I am aware that any use or disclosure of PHI for research activity must be done in accordance
					with a research protocol approved by the RSRB and
					<a href="http://intranet.urmc-sh.rochester.edu/app/HIPAA/apps/disclosure/" style="font-weight:strong;">
					HIPAA Procedure 0P25 Uses and Disclosures of PHI for Research Activities.
					</a>
					<br><br>
					<?php if( $CHECKPASSWORDS ){ ?>
						Please enter your URMC password:<br>
						<strong>Account: <span id="ExportExcel-Username">bob smith</span></strong><br>
						<input type="password" style="font-size:1.5em;" id="ExcelPasswordField">
					<?php } else { ?>
						<input type="hidden" value="nopassword" id="ExcelPasswordField">
					<?php } ?>
					</div>
				</div>
			</div>

			<div id="ExportExcel-TABS" class="yui-navset">
				<ul class="yui-nav">
					<li id="ExportExcel-TAB0" class="selected"><a href="#ExportExcel-TAB0"><em>Excel Sync Settings</em></a></li>
					<li id="ExportExcel-TAB1"><a href="#ExportExcel-TAB1"><em>Processing</em></a></li>
				</ul>
				<div class="yui-content" id="ExportExcel-CONTENT">

					<div>
						<div class="ExportExcel-MainContent">
							<div class="ExportExcel-MainContentPad" id="ExportExcel-Landing">
								<?php echo( "<h1><span id='ExportExcel-Name'>Loading....</span></h1>" ); ?>
								<p>
								Drop a patient set and at least one concept (ontology term) onto the approriate input boxes below, then click the "View Results" tab to retrieve the respectiv observations in the selected patient set.
								<hr>

								<h2>Patient List To Report On</h2>

								If a patient list is selected, it will utilize all data with the patients, If an encounter list is selected, the data will be limited to the encounters selected in the list.
								<?php

									echo("<div id='ExportExcel_PRSDROP' class='droptrgt SDX-PRS' >");
										echo("<div class='concptItem'>This tool has not been setup yet.</div>");
									echo("</div>");

									echo("<h2>Add New Concept</h2>");
									echo("<div id='ExportExcel_CONCPT' class='droptrgt SDX-CONCPT' >");
									echo("<div class='concptItem'>This tool has not been setup yet.</div>");
									echo("</div>");
									echo("<br><h2>Current Excel Columns</h2>");
									echo("<div id='ExportExcel_CONCPTS' >");
									echo("</div>");
								echo("<div id='ExportExcel_CONCPT_status'></div>");

								?>
							</div>
						</div>
					</div>

					<div>
						<div class="ExportExcel-MainContent">
							<div class="ExportExcel-MainContentPad" id="ExportExcel-Controller">
								You should not be able to see this i hope! :-)
								<div><p></div>
							</div>
						</div>
					</div>


				</div> <!-- ExportExcel-CONTENT-->
			</div><!-- ExportExcel-TABS -->
		</div><!-- ExportExcel-mainDiv -->


	</body>
</html>
