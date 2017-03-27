// this file contains a list of all files that need to be loaded dynamically for this plugin
// every file in this list will be loaded after the plugin's Init function is called
{
	files:[ "DataDictionary_ctrlr.js" ],
	css:[ "vwDataDictionary.css" ],
	config: {
		// additional configuration variables that are set by the system
		short_name: "Data Dictionary",
		name: "Data Dictionary",
		description: "This plugin shows summary information and aggregate statistics for an i2b2 concept.",
		category: ["celless","plugin","statistics"],
		plugin: {
			isolateHtml: false,  // this means do not use an IFRAME
			isolateComm: false,  // this means to expect the plugin to use AJAX communications provided by the framework
			standardTabs: true, // this means the plugin uses standard tabs at top
			html: {
				source: 'injected_screens.html',
				mainDivId: 'DataDictionary-mainDiv'
			}
		}
	}
}