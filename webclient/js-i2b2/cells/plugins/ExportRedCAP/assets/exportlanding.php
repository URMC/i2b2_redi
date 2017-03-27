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
 * This page sets up the HTML artifacts that are needed to display the REDCap integration.
 */
?>

<html>
	<body>



		<div id='ExportRedCAP-mainDiv'>

		<!-- ############### <RedCAPLabRange> ############### -->
		<div id="REDCapitemLabRange" style="display:none;">
			<div class="hd" style="background:#6677AA;">Lab Range Constraint</div>
			<div class="bd modLabValues">
				<div style="margin: 0px 5% 12px; text-align: center;" id="REDCapvalueContraintText"></div>
				<div class="mlvBody">
					<div class="mlvtop">
						<div class="mlvModesGroup">
							<div class="mlvMode"><input name="REDCapmlvfrmType" id="REDCapmlvfrmTypeNONE" value="NO_VALUE" type="radio" checked="checked" />As-Is</div>
							<div class="mlvMode"><input name="REDCapmlvfrmType" id="REDCapmlvfrmTypeFLAG" value="BY_FLAG" type="radio" /> By flag</div>
							<div class="mlvMode"><input name="REDCapmlvfrmType" id="REDCapmlvfrmTypeVALUE" value="BY_VALUE" type="radio" /> By value</div>
						</div>
						<div class="mlvInputGroup">
							<div id="REDCapmlvfrmFLAG" style="display:none">
								Please select a range:<br />
								<select id='REDCapmlvfrmFlagValue'><option value="">Loading...</option></select>
							</div>
							<div id="REDCapmlvfrmVALUE" style="display:none">
								<p id="REDCapmlvfrmEnterOperator">
									Please select operator:<br />
									<select id='REDCapmlvfrmOperator'>
										<option value="LT">LESS THAN (&lt;)</option>
										<option value="LE">LESS THAN OR EQUAL TO (&lt;=)</option>
										<option value="EQ">EQUAL (=)</option>
										<option value="BETWEEN">BETWEEN</option>
										<option value="GT">GREATER THAN (&gt;)</option>
										<option value="GE">GREATER THAN OR EQUAL (&gt;=)</option>
									</select>
								</p>
								<p id="REDCapmlvfrmEnterStringOperator">
									Please select operator:<br />
									<select id='REDCapmlvfrmStringOperator'>
										<option value="LIKE[contains]">Contains</option>
										<option value="LIKE[exact]">Exact</option>
										<option value="LIKE[begin]">Starts With</option>
										<option value="LIKE[end]">Ends With</option>
									</select>
								</p>
								<p id="REDCapmlvfrmEnterVal">
									Please enter a value:<br />
									<input id="REDCapmlvfrmNumericValue" class="numInput" />
								</p>
								<p id="REDCapmlvfrmEnterVals" style="display:none">Please enter values:<br />
									<input id="REDCapmlvfrmNumericValueLow" class="numInput" /> &nbsp;-&nbsp; <input id="REDCapmlvfrmNumericValueHigh" class="numInput" />
								</p>

								<p id="REDCapmlvfrmEnterStr">Enter Search Text:<br /><input id="REDCapmlvfrmStrValue" class="strInput" /> </p>

								<p id="REDCapmlvfrmEnterDbOperator"><input id="REDCapmlvfrmDbOperator" type="checkbox"/> Use Database Operators <i>(Advanced Searching)</i><br/></p>

								<p id="REDCapmlvfrmEnterEnum">Please select a value:<br />
									<select id="REDCapmlvfrmEnumValue" class="enumInput" multiple="multiple" size="5" style="overflow: scroll; width: 562px;">
									<option value="">Loading...</option>
									</select>
								</p>
							</div>
						</div>

						<div style="clear:both;height:1px;overflow:hidden;"></div>


					<!-- Units display section -->
					<div id="REDCapmlvfrmUnitsContainer" style="margin: 10px 0px 0px 15px; display:none">
							<div style="float:left; text-align:left; bottom: 0">Units = &nbsp;</div>
							<span><select id='REDCapmlvfrmUnits' class="units" style="width: 500px; float:left;"><option value="0">Loading...</option></select></span>
							<span id="REDCapmlvUnitExcluded" style="color:#900;"><strong>Warning:</strong> There are multiple units available, this mapping tool cannot transform units, you will need to map each unit seperately.</span>
					</div>
					<!-- END snm0 -->
					</div>
				</div>
			</div>
		</div>
		<!-- ############### </RedCAPLabRange> ############### -->

			<div id="ExportRedCAP-TABS" class="yui-navset">
				<ul class="yui-nav">
					<li id="ExportRedCAP-TAB0" class="selected"><a href="#ExportRedCAP-TAB0"><em>REDCap Sync Settings</em></a></li>
					<li id="ExportRedCAP-TAB3"><a href="#ExportRedCAP-TAB3"><em>Map Data</em></a></li>
					<li id="ExportRedCAP-TAB1"><a href="#ExportRedCAP-TAB1"><em>Processing</em></a></li>
					<li id="ExportRedCAP-TAB2"><a href="#ExportRedCAP-TAB2"><em>Test & Validation</em></a></li>
					<li id="ExportRedCAP-TAB4"><a href="#ExportRedCAP-TAB4"><em>Transmit</em></a></li>
				</ul>
				<div class="yui-content" id="ExportRedCAP-CONTENT">

					<div>
						<div class="ExportRedCAP-MainContent">
							<div class="ExportRedCAP-MainContentPad" id="ExportRedCAP-APISync">
								Please hold while the sync information is loaded.
								<div><p></div>
							</div>
						</div>
					</div>

					<div>
						<div class="ExportRedCAP-MainContent">
							<div class="ExportRedCAP-MainContentPad" id="ExportRedCAP-Mapper">
								You should not be able to see this i hope! :-)
								<div><p></div>
							</div>
						</div>
					</div>


					<div>
						<div class="ExportRedCAP-MainContent">
							<div class="ExportRedCAP-MainContentPad" id="ExportRedCAP-Controller">
								You should not be able to see this i hope! :-)
								<div><p></div>
							</div>
						</div>
					</div>


					<div>
						<div class="ExportRedCAP-MainContent">
							<div class="ExportRedCAP-MainContentPad" id="ExportRedCAP-Tester">
								We need to write something here!
							</div>
						</div>
					</div>


					<div>
						<div class="ExportRedCAP-MainContent">
							<div class="ExportRedCAP-MainContentPad" id="ExportRedCAP-Sender">
								We need to write something here!
							</div>
						</div>
					</div>

				</div>
			</div>
		</div>
	</body>
</html>
