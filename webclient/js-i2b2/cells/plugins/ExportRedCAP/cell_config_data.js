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
 * This is the entry point for the redcap plugin, it specifies what files to load.
 */

{
	files:[
		"ExportRedCAP_ctrlr.js"
	],
	css:[
		"vwExportRedCAP.css"
	],
	config: {
		// Additional configuration variables that are set by the system
		short_name: "ExportREDCap",
		name: "ExportREDCap",
		description: "This plugin populates a table of selectable concepts/observations and provides the possibility to download the result as a file.",
		icons: { size32x32: "REDCap32x32.png" },
		category: ["celless","plugin","standard"],
		plugin: {
			isolateHtml: false,  // This means do not use an IFRAME
			isolateComm: false,  // This means to expect the plugin to use AJAX communications provided by the framework
			standardTabs: true,  // This means the plugin uses standard tabs at top
			html: {
				source: 'exportlanding.php',
				mainDivId: 'ExportRedCAP-mainDiv'
			}
		}
	}
}
