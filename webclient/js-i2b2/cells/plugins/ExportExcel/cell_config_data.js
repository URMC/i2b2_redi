/**
 * @projectDescription	i2b2 CSV Export Plugin
 * @inherits			i2b2
 * @namespace			i2b2.ExportXLS
 * @author				Axel Newe
 * ----------------------------------------------------------------------------------------
 * updated 2013-01-09: Initial Launch [Axel Newe, FAU Erlangen-Nuremberg]
 */

// This file contains a list of all files that need to be loaded dynamically for this i2b2 Cell
// Every file in this list will be loaded after the cell's Init function is called

{
	files:[
		"ExportExcel_ctrlr.js"
	],
	css:[
		"vwExportExcel.css"
	],
	config: {
		// Additional configuration variables that are set by the system
		short_name: "ExportExcel",
		name: "ExportExcel",
		description: "This plugin populates a table of selectable concepts/observations and provides the possibility to download the result as a file.",
		icons: { size32x32: "excel32x32.png" },
		category: ["celless","plugin","standard"],
		plugin: {
			isolateHtml: false,  // This means do not use an IFRAME
			isolateComm: false,  // This means to expect the plugin to use AJAX communications provided by the framework
			standardTabs: true,  // This means the plugin uses standard tabs at top
			html: {
				source: 'exportlanding.php',
				mainDivId: 'ExportExcel-mainDiv'
			}
		}
	}
}
