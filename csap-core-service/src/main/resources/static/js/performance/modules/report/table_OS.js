define( ["model", "./dataService", "./tableUtils", "./utils"], function (model, dataService, tableUtils, utils) {

	 console.log( "Module loaded: reports/table_OS" );


	 return {
		  //
		  buildSummaryRow: function (rowJson, tableRow, rowJson) {
				buildSummaryRow( rowJson, tableRow, rowJson );
		  },
		  //
		  buildServiceDetailRow: function(rowJson, tableRow, rowJson) {
				buildServiceDetailRow(rowJson, tableRow, rowJson);
		  },
		  //
	 }

	 function getProjectInstancesLength() {

		  if ( model.getPackages() == null )
				return 0; // hack for start

		  return model.getPackageDetails( model.getSelectedProject() ).instances.instanceCount.length;
	 }

	 function buildSummaryRow(rowJson, tableRow, rowJson) {

		  // console.log( JSON.stringify(rowJson, null,"\t") )

		  tableRow.append( jQuery( '<td/>', {
				class: "projectColumn",
				text: rowJson.project + " - " + rowJson.lifecycle
		  } ) );

		  $( "#serviceLabel" ).html( "OS  Resource Report" );
		  var serviceLink = jQuery(
					 '<a/>',
					 {
						  class: "simple",
						  target: "_blank",
						  href: uiSettings.analyticsUiUrl + "?service=" + rowJson.serviceName
									 + "&life=" + $( "#lifeSelect" ).val() + "&project="
									 + model.getSelectedProject(),
						  text: rowJson.serviceName
					 } ).click( function () {
				utils.launchServiceOsReport( rowJson.serviceName )
				return false;
		  } );

		  var col1 = jQuery( '<td/>', {
				class: "col1"
		  } );
		  col1.html( serviceLink );
		  tableRow.append( col1 );

		  var numInstances = "-";
		  for ( var i = 0; i < getProjectInstancesLength(); i++ ) {
				var instanceCount = model.getPackageDetails( model.getSelectedProject() ).instances.instanceCount[i];
				// console.log( JSON.stringify(rowJson, null,"\t") )
				if ( instanceCount.serviceName == rowJson.serviceName ) {
					 numInstances = instanceCount.count;
					 break;
				}
		  }

		  tableRow.append( jQuery( '<td/>', {
				class: "num ",
				text: numInstances
		  } ) );

		  tableUtils.addCell( tableRow, rowJson, "numberOfSamples", 0 );

		  tableUtils.addCell( tableRow, rowJson, "topCpu", 1, 1, "%", 40 );

		  tableUtils.addCell( tableRow, rowJson, "threadCount", 0, 1, "", 100 );

		  tableUtils.addCell( tableRow, rowJson, "rssMemory", 0, 1, "", 1000 );

		  tableUtils.addCell( tableRow, rowJson, "diskUtil", 0, 1, "", 350 );

		  tableUtils.addCell( tableRow, rowJson, "diskWriteKb", 0, 1, "", 350 );
		  tableUtils.addCell( tableRow, rowJson, "diskReadKb", 0, 1, "", 350 );

		  tableUtils.addCell( tableRow, rowJson, "fileCount", 0, 1, "", 500 );

		  tableUtils.addCell( tableRow, rowJson, "socketCount", 0, 1, "", 30 );

	 }
	 
	 
	 
	 // Service
	 function buildServiceDetailRow(rowJson, tableRow, rowJson) {

		  // console.log( JSON.stringify(rowJson, null,"\t") )

		  $( "#serviceLabel" ).html( rowJson.serviceName + " OS Resources " );

		  tableRow.append( jQuery( '<td/>', {
				class: "projectColumn",
				text: rowJson.project + " - " + rowJson.lifecycle
		  } ) );

		  tableRow.append( tableUtils.buildHostLinkColumn( rowJson.host ) );

		  tableUtils.addCell( tableRow, rowJson, "numberOfSamples", 0 );

		  tableUtils.addCell( tableRow, rowJson, "topCpu", 1, 1, "%", 40 );

		  tableUtils.addCell( tableRow, rowJson, "threadCount", 0, 1, "", 100 );

		  tableUtils.addCell( tableRow, rowJson, "rssMemory", 0, 1, "", 1000 );

		  tableUtils.addCell( tableRow, rowJson, "diskUtil", 0, 1, "", 350 );

		  tableUtils.addCell( tableRow, rowJson, "diskWriteKb", 0, 1, "", 350 );
		  tableUtils.addCell( tableRow, rowJson, "diskReadKb", 0, 1, "", 350 );

		  tableUtils.addCell( tableRow, rowJson, "fileCount", 0, 1, "", 500 );

		  tableUtils.addCell( tableRow, rowJson, "socketCount", 0, 1, "", 30 );

	 }


} );