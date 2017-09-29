define( ["model", "windowEvents", "./dataService", "./utils", "./tableUtils", "./summaryTab", "./table_OS", "./table_JMX", ],
		function ( model, windowEvents, dataService, utils, tableUtils, summaryTab, osTable, jmxTable ) {

			console.log( "Module loaded: reports/table" );

			var csapUser = new CsapUser();

			var resizeDelayForRenderingRaceCondition = 500;

			return {
				//
				loadData: function ( responseJson, reportId, tableId ) {
					buildTable( responseJson, reportId, tableId );
				},
				//
			}

			function buildTable( responseJson, reportId, tableId ) {


				var bodyId = tableId + " tbody";
				var visVal = $( "#visualizeSelect" ).val();

				// For testing graphs
				// visVal = "totalUsrCpu,totalSysCpu";
				// visVal = "cpuCountAvg";



				$( "#visualizeSelect" ).empty();
				$( "#emailText" ).text( "" );

				var optionItem = jQuery( '<option/>', {
					value: "table",
					text: "Summary Table"
				} );
				$( "#visualizeSelect" ).append( optionItem );

				var numSamples = 0

				if ( responseJson.data.length == 0 ) {
					alertify.alert( "No data found. Contact service developers to request they publish productivity data.<br><br>" +
							"<a class='simple' href='https://github.com/csap-platform/csap-core/wiki#updateRefCustom+Metrics'>Reference Guide</a><br><br>" );
				}

				var coresActive = 0;
				numRows = 0;
				var coresTotal = 0;
				for ( var i = 0; i < responseJson.data.length; i++ ) {
					var rowJson = responseJson.data[i];


					if ( i == 0 ) {
						// Fill in view options

						for ( var item in rowJson ) {

							if ( !$.isNumeric( rowJson[item] ) )
								continue;

							var label = model.getServiceLabels( rowJson.serviceName, item );


							//console.log("desc: ", desc);

							var optionItem = jQuery( '<option/>', {
								value: item,
								text: label
							} );

							$( "#visualizeSelect" ).append( optionItem );

							if ( label == "Host Usr Cpu" ) {
								var optionItem = jQuery( '<option/>', {
									value: item + ",totalSysCpu",
									text: "Cpu (usr and sys)"
								} );

								$( "#visualizeSelect" ).append( optionItem );
							}
						}
						$( "#visualizeSelect" ).val( visVal );
					}

					var tableRow = jQuery( '<tr/>', { } );
					if ( reportId == "userid" ) {

						buildUseridRow( responseJson, tableRow, rowJson );

					} else if ( reportId == "service" ) {
						if ( i == 0 )
							console.log( "Keys: " + Object.keys( rowJson ) )

						var serviceName = rowJson.serviceName
						if ( !model.isServiceInSelectedCluster( serviceName ) )
							continue;

						osTable.buildSummaryRow( responseJson, tableRow, rowJson );

					} else if ( reportId == "service/detail" ) {

						osTable.buildServiceDetailRow( responseJson, tableRow, rowJson );

					} else if ( reportId == "jmx" ) {

						jmxTable.buildSummaryRow( responseJson, tableRow, rowJson );

					} else if ( reportId == "jmx/detail" ) {

						jmxTable.buildDetailRow( responseJson, tableRow, rowJson );

					} else if ( reportId == "jmxCustom/detail" ) {

						jmxTable.buildCustomRow( responseJson, tableRow, rowJson );

					} else if ( reportId == "vm" ) {

						var rowHost = rowJson.hostName
						if ( !model.isHostInSelectedCluster( rowHost ) )
							continue;

						var totalCpu = (rowJson.totalUsrCpu + rowJson.totalSysCpu) / rowJson.numberOfSamples;
						var coresUsed = totalCpu * rowJson.cpuCountAvg / 100;
						coresActive += coresUsed;
						coresTotal += rowJson.cpuCountAvg;
						// console.log(" totalCpu " + totalCpu + "  coresUsed: " + coresUsed ) ;

						buildVmRow( responseJson, tableRow, rowJson );

					}

					$( bodyId ).append( tableRow )


					if ( reportId == "userid" ) {
						numSamples += 1;
					} else {
						var metaFields = ["project", "serviceName", "appId", "lifecycle", "numberOfSamples", ];
						var reportArray = Object.keys( rowJson );
						numSamples += rowJson.numberOfSamples * (reportArray.length - metaFields.length);
					}

				}

				if ( reportId == "vm" ) {
					// vm data is used to load summary header
					summaryTab.updateHeader( coresActive, coresTotal );
				}

				$( "#visualizeSelect" ).sortSelect();
				$( "#visualizeSelect" ).selectmenu( "refresh" );

				utils.updateSampleCountDisplayed( numSamples );

				if ( $( "#isAllProjects" ).is( ':checked' ) ) {
					$( " .projectColumn" ).show();
				} else {
					$( " .projectColumn" ).hide();
				}

				$( tableId ).show();
				$.tablesorter.computeColumnIndex( $( "tbody tr", tableId ) );
				$( tableId ).trigger( "tablesorter-initialized" ); // needed for math plugin

				$( ".columnSelector" ).hide();
				var filterdId = reportId.replace( /\//g, "" );
				console.log( "filterdId: " + filterdId );
				$( "#" + filterdId + "ColumnSelector" ).show();

				if ( reportId == "userid" ) {
					var delay = 500;
					csapUser.onHover( $( "#useridTable tbody tr td:nth-child(1)" ), delay );
				}
				$( "#reportsSection" ).show();

				if ( dataService.isCompareSelected() ) {
					var message = "Number of Matches: " + $( ".diffHigh" ).length;
					message += '<span style="font-size: 0.8em; padding-left: 3em">Minimum Value Filter: <label>'
							+ $( "#compareMinimum" ).val();
					message += "</label> Hide non matches: <label>"
							+ $( "#isCompareRemoveRows" ).prop( "checked" )
					message += "</label> Difference percent: <label>"
							+ $( "#compareThreshold" ).val() + "</label>"
					message += "Max Days: <label>" + dataService.getLastReportResults().numDaysAvailable
							+ "</label></span>"

					var msgDiv = jQuery( '<div/>', {
						class: "settings compMessage",
						id: "compareCurrent",
						html: message

					} );

					$( "#reportOptions" ).append( msgDiv );

					// $("#reportOptions").append(msgDiv);

					if ( $( "#isCompareRemoveRows" ).prop( "checked" ) ) {
						$( ".diffHigh" ).parent().parent().addClass( "diffHighRow" );
						$( "tr:not(.diffHighRow)", bodyId ).remove();
					}

					if ( $( "#isCompareEmptyCells" ).prop( "checked" ) ) {
						$( ".diffHigh" ).parent().addClass( "diffHighCell" );
						$( ".col1", bodyId ).addClass( "diffHighCell" );
						$( "td:not(.diffHighCell)", bodyId ).empty();
					}

				}

				if ( dataService.getLastReportResults() != null ) {
					console.log( "_lastReportResults.numDaysAvailable: "
							+ dataService.getLastReportResults().numDaysAvailable );
					$( "#reportStartInput, #compareStartInput" ).datepicker( "option", {
						minDate: 0 - dataService.getLastReportResults().numDaysAvailable
					} );
				}

				// induced by tablesorter?
				setTimeout( function () {
					windowEvents.resizeComponents();
					tableUtils.showHighestInColumn( tableId );

					// fix table corners
					$( "th:visible:first", tableId ).addClass( "tableTopLeft" );
					$( "th:visible:last", tableId ).addClass( "tableTopRight" );
					$( "tbody tr:last td:last", tableId ).css( "border-bottom-right-radius", "0px" );

				}, resizeDelayForRenderingRaceCondition );
				resizeDelayForRenderingRaceCondition = 10;
			}


			function buildVmRow( responseJson, tableRow, rowJson ) {

				tableRow.append( jQuery( '<td/>', {
					class: "projectColumn",
					text: rowJson.project + " - " + rowJson.lifecycle
				} ) );

				tableRow.append( tableUtils.buildHostLinkColumn( rowJson.hostName ) );

				tableUtils.addCell( tableRow, rowJson, "numberOfSamples", 0 );
				tableUtils.addCell( tableRow, rowJson, "memoryInMbAvg", 1, 1024 );
				tableUtils.addCell( tableRow, rowJson, "swapInMbAvg", 1, 1024 );
				tableUtils.addCell( tableRow, rowJson, "totalMemFree", 1, 1024 );

				tableUtils.addCell( tableRow, rowJson, "totalIo", 0, 1, "%" );
				tableUtils.addCell( tableRow, rowJson, "combinedCpu", 1, 1, "%" );
				tableUtils.addCell( tableRow, rowJson, tableUtils.getFieldSummaryAppendix( "totActivity" ), 0 );

				tableUtils.addCell( tableRow, rowJson, "totalUsrCpu", 0, 1, "%" );
				tableUtils.addCell( tableRow, rowJson, "totalSysCpu", 0, 1, "%" );

				tableUtils.addCell( tableRow, rowJson, "cpuCountAvg", 0 );
				tableUtils.addCell( tableRow, rowJson, "totalLoad", 1, 1, "",
						rowJson.cpuCountAvg / 2 );

				var alertSuffix = "%";
				if ( $( "#isUseTotal" ).is( ':checked' ) )
					alertSuffix = "";

				tableUtils.addCell( tableRow, rowJson, "alertsCount", 1, 0.01, alertSuffix );

				tableUtils.addCell( tableRow, rowJson, "csapThreadsTotal", 0, 1, "", 1500 );
				tableUtils.addCell( tableRow, rowJson, "threadsTotal", 0, 1, "", 2000 );

				tableUtils.addCell( tableRow, rowJson, "socketTotal", 0, 1, "", 300 );
				tableUtils.addCell( tableRow, rowJson, "socketWaitTotal", 0, 1, "", 40 );

				tableUtils.addCell( tableRow, rowJson, "csapFdTotal", 0, 1, "", 10000 );
				tableUtils.addCell( tableRow, rowJson, "fdTotal", 0, 1, "", 10000 );
				tableUtils.addCell( tableRow, rowJson, "totalCpuTestTime", 1, 1, "", 5 );
				tableUtils.addCell( tableRow, rowJson, "totalDiskTestTime", 1, 1, "", 5 );
				tableUtils.addCell( tableRow, rowJson, "totalIoReads", 1, 1, "", 5 );
				tableUtils.addCell( tableRow, rowJson, "totalIoWrites", 1, 1, "", 5 );
				//console.log("adding row", rowJson ) ;

			}


			function buildUseridRow( responseJson, tableRow, rowJson ) {
				
				if ( rowJson.uiUser == null ) {
					rowJson.uiUser = "null";
				}
				
				var theUser = rowJson.uiUser.toLowerCase() ;
				if ( theUser.contains("system")  || theUser.contains(".gen")  || theUser.contains("agentuser")  || theUser.contains("null")  || theUser.contains("csagent") ) {
					console.log( "skipping ", theUser) ;
					return ;
				}

				$( "#emailText" ).append( rowJson.uiUser + "@yourcompany.com;" );

				var col1 = jQuery( '<td/>', {
					class: "col1 userids",
					text: rowJson.uiUser
				} );
				// col1.html(userLink) ;

				var eventLink = jQuery( '<a/>', {
					class: "simple",
					target: "_blank",
					href: utils.getUserUrl( "userid=" + rowJson.uiUser ),
					text: rowJson.totActivity
				} );
				var col2 = jQuery( '<td/>', {
					class: "num"
				} );
				col2.html( eventLink );

				// console.log("buildUseridReport: " + col1.text() )

				tableRow.append( col1 ).append( col2 );
			}


		} );