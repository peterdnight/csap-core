

// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	// needed by graphs
	paths: {
		mathjs: "../../csapLibs/mathjs/modules/math.min"
	},
	packages: [
		{ name: 'graphPackage',
			location: '../../graphs/modules/graphPackage', // default 'packagename'
			main: 'ResourceGraph'                // default 'main' 
		}
	]
} );



require( ["mathjs", "graphPackage", "host-explorer", "explorer-operations"], function ( mathjs, ResourceGraph, explorerTab, explorerOperations ) {

	console.log( "\n\n ************** module hostDash: loaded *** \n\n" );


	// jquery handles. Race condition on load if defined in scope here - placed in init method
	var $loading, $mainLoading, $processFilter, $processTable, $processBody;

	var _graphsNeedInit = true;
	var vmGraph = null;
	var serviceGraph = null;
	var _processFilterTimer = null;

 
	$( document ).ready( function () {
		// initialize jquery here - race condition can cause issues
		console.log( "Main startup4" );
		CsapCommon.configureCsapAlertify();
		$loading = $( "#loadRow" );
		$mainLoading = $( "#initialMessage" );

		$processFilter = $( "#processFilter" );

		$processTable = $( "#processTable" );

		$processBody = $( "tbody.content", $processTable );


		initialize();



		explorerTab.show();
		$mainLoading.hide();


	} );




	this.initialize = initialize;
	function initialize() {


		$processTable.tablesorter( {
			sortList: [[4, 1]],
			theme: 'csap',
			headers: {
				// disable sorting of the first column (we start counting at zero)
				1: {
					// disable it by setting the property sorter to false
					sorter: false
				}
			}
		} );


		$processFilter.keyup( function () {
			// 
			clearTimeout( _processFilterTimer );
			_filterTimer = setTimeout( function () {
				filterProcesses();
			}, 500 );


			return false;
		} );

		$( "#memTableDiv table" ).tablesorter( {
			sortList: [[1, 1]],
			theme: 'csapSummary'
		} );
		$( "#mpTable" ).tablesorter( {
			sortList: [[0, 0]],
			theme: 'csapSummary'
		} );
		$( "#swapTableDiv table" ).tablesorter( {
			sortList: [[2, 1]],
			theme: 'csapSummary'
		} );
		$( "#dfTableDiv table" ).tablesorter( {
			sortList: [[2, 1]],
			theme: 'csapSummary'
		} );

		$( '#filterCsap' ).change( function () {

			$processFilter.val( "" );
			refreshProcessTab();
			//$("#processTable").trigger("update");
			//setTimeout("refreshProcessTable()", 500);
			//alertify.notify( "Refreshing Table" );
			//
		} );


		setTimeout( function () {
			refreshLoad()
		}, 1000 );

		$( '#sysButton' ).click( function () {

			$( "section" ).hide();
			// alert("toggling") ;
			$( "#systemGraphs" ).show();

			return false; // prevents link
		} );

		$( '#hostInfo' ).click( function () {
			$mainLoading.show();
			var host = $( this ).data( "host" );
			var paramObject = {
			};

			function buildAboutLine( label, value ) {
				var $line = jQuery( '<div/>', { class: "aboutline" } );

				jQuery( '<span/>', { class: "label", text: label + ":" } ).appendTo( $line );
				jQuery( '<span/>', { text: value } )
						.css( "font-weight", "bold" )
						.css( "color", "black" )
						.css( "white-space", "pre" )
						.appendTo( $line );

				return $line;
			}

			$.getJSON(
					uiSettings.baseUrl + "/api/agent/hostSummary", paramObject )
					.done(
							function ( loadJson ) {

								var $about = jQuery( '<div/>', { class: "aWrap" } );
								$about.append( buildAboutLine( "Host", uiSettings.hostName ) );
								$about.append( buildAboutLine( "Version", loadJson.redhat ) );
								$about.append( buildAboutLine( "Uptime", loadJson.uptime ) );
								$about.append( buildAboutLine( "uname", loadJson.uname ) );
								$about.append( buildAboutLine( "Disk", "\n" + loadJson.df ) );

								alertify.alert( "About " + uiSettings.hostName, $about.html() );

								$( 'body' ).css( 'cursor', 'default' );

								$mainLoading.hide();

							} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {

						$mainLoading.hide();
						handleConnectionError( "Retrieving service instances", errorThrown );
					} );

			return false; // prevents link
		} );

		$( '#resourceButton' ).click( function () {

			$( "section" ).hide();
			// alert("toggling") ;
			$( "#systemGraphs" ).show();

			return false; // prevents link
		} );

		$( '#fileButton' ).click( function () {

			$( "section" ).hide();
			// alert("toggling") ;
			$( "#fileSystems" ).show();

			return false; // prevents link
		} );

		$( '#memoryButton' ).click( function () {

			$( "section" ).hide();
			// alert("toggling") ;
			$( "#memoryStats" ).show();

			return false; // prevents link
		} );

		$( '#swapButton' ).click( function () {

			$( "section" ).hide();
			// alert("toggling") ;
			$( "#swapStats" ).show();

			return false; // prevents link
		} );

		$( '#processButton' ).click( function () {

			$( "section" ).hide();
			// alert("toggling") ;
			$( "#processStats" ).show();

			return false; // prevents link
		} );

		$( '#allButton' ).click( function () {

			$( "section" ).show();

			return false; // prevents link
		} );

		// alert("peter") ;
		// $("#tabs").tabs();
		$( "#tabs" ).tabs( {
			activate: function ( event, ui ) {

				// stop autorefreshes on tab nav
				clearTimeout( processRefreshTime );

				console.log( "Loading: " + ui.newTab.text() );
				if ( isActivatedTab( ui, "Host Graphs")  ) {
					initializeGraphs()
					vmGraph.reDraw();
				}

				if ( isActivatedTab( ui, "Service Graphs")  ) {
					initializeGraphs()
					serviceGraph.reDraw();
				}


				if ( isActivatedTab( ui, "Memory")  || isActivatedTab( ui, "Swap")  )
					refreshMem();

				if (  isActivatedTab( ui, "File")  )
					refreshDf();

				if ( isActivatedTab( ui, "Process") ) {

					refreshProcessTab();
				}

			}
		} );
		
		$("#tabs").show() ;

		if ( serviceFilterParam != null ) {
			initializeGraphs();
		}

	}
	
	function isActivatedTab( uiEvent, tabName) {
		return  uiEvent.newTab.text().contains( tabName ) ;
	}

	function refreshProcessTab() {
		$( "tbody.content", $processTable ).hide();
		$( "#loadRow" ).show();

		setTimeout( function () {
			// empty can be very slow of large tables. run after a ui delay
			// and do a detach
			$( "tbody.content", $processTable ).children().detach().remove();
			refreshProcessData();
		}, 500 )
	}

	function initializeGraphs() {

		if ( !_graphsNeedInit ) {
			return;
		}
		_graphsNeedInit = false;

		vmGraph = new ResourceGraph(
				"#vmGraphs", "resource",
				uiSettings.life,
				uiSettings.user,
				uiSettings.metricsUrl );

		$.when( vmGraph.getGraphLoadedDeferred() ).done( function () {
			$mainLoading.hide();


			setTimeout( function () {
				serviceGraph = new ResourceGraph(
						"#serviceGraphs", "service",
						uiSettings.life,
						uiSettings.user,
						uiSettings.metricsUrl );

				if ( serviceFilterParam != null ) {
					$.when( serviceGraph.getGraphLoadedDeferred() ).done( function () {
						$( ".serviceCheckbox" ).prop( "checked", false );
						$( "#serviceCheckbox" + serviceFilterParam ).prop( "checked", true );
						serviceGraph.settingsUpdated();

						activateTab( "serviceTab" );
					} );
				}
			}, 200 );
		} );

	}


	function activateTab( tabId ) {
		var tabIndex = $( 'li[data-tab="' + tabId + '"]' ).index();

		console.log( "Activating tab: " + tabIndex );

		// $("#jmx" ).prop("checked", true) ;

		$( "#tabs" ).tabs( "option", "active", tabIndex );

		return;
	}



	$.urlParam = function ( name ) {
		var results = new RegExp( '[\\?&]' + name + '=([^&#]*)' )
				.exec( window.location.href );
		if ( results == null ) {
			return null;
		}
		return results[1] || 0;
	};

	var processRefreshTime = 0;

	$.ajaxSetup( {
		cache: false,
		timeout: 60000,
		error: function ( jqXHR, status, errorThrown ) { // the status

			var message = '<div id="connectionError">Connection timed out while getting latest statistics.<br/> Recommendation: reload page.</div>';

			if ( $( "#connectionError" ).length == 0 ) {
				alertify.csapWarning( message );
			}
		}
	} );


	var currentPriorityForProcess = 22;
	function refreshProcessData() {

		// alert("refreshProcessTable") ;

		var destUrl = "getCpu?filter=no";
		// console.log("clearing" + processRefreshTime) ; 
		clearTimeout( processRefreshTime );
		var prefixId = "";
		if ( $( "#filterCsap" ).prop( 'checked' ) ) {
			destUrl = "getCpu?filter=yes";
		} else {
			$( "tbody.content tr", $processTable ).addClass( "notFound" );
			prefixId = "pid";
		}

		// this comes back extremly quickly with a lot of data- so add a lag to allow for UI update
		$.getJSON(
				destUrl,
				{
					q: "test"
				},
				function ( processInfoJson ) {
					hostProcessResponseHandler( processInfoJson, prefixId )
				} );


	}

	function hostProcessResponseHandler( hostProcessResponse, prefixId ) {
		// var result = "Language code is \"<strong>" +
		// json.responseData.language + "\"";
		// $("#result").html(result);
		// hack for IE9 in compliants mode
		$( 'body' ).css( 'cursor', 'default' );

		updateHostProcessTable( hostProcessResponse.ps, prefixId );

		updateHostCpuTable( hostProcessResponse.mp );

		configurePriorityButtons();

		$( "#processReloadTime" ).html(
				"refreshed: " + hostProcessResponse.timestamp );

		var intervalInSeconds = $( "#cpuIntervalId" ).val();
		// alert("interval" + interval) ;
		processRefreshTime = setTimeout(
				function () {
					refreshProcessData();
				}, intervalInSeconds * 1000 );

	}

	function isArray(o) { return Object.prototype.toString.call(o) == '[object Array]'; }

	function updateHostProcessTable( processJson, prefixId ) {

		console.log( "updateHostProcessTable()", processJson );
		$loading.hide();
		$processBody.show();

		var totalMem = 0;


		for ( var key in processJson ) {
			// if ( ! isInt(threadCount[key].pid) ) continue ;
			var theRowId = prefixId + key;


			var processRow = $( "#" + theRowId, $processBody );
			if ( processRow.length == 0 ) {
				// console.log("Adding: " + theRowId) ;
				processRow = jQuery( '<tr></tr>', {
					id: theRowId,
					html: $( 'tbody.template tr', $processTable ).html()
				} );

				$processBody.append( processRow );

			}

			processRow.removeClass( "notFound" );

			var column = 1;
			var pid = processJson[key].pid;
			if ( isArray(pid) ) {
				pid = pid[0] ;
			}

			//console.log("Updating: ", key, " pid", pid) ;

			if ( pid != "host" )
				processRow.children().each( function () {
					var cellContents = "";
					switch ( column ) {

						case 1 :   // Process name column

							cellContents = processJson[key].serviceName;
							if ( key.contains( "_" ) ) {
								cellContents += "<span class='port'>" + processJson[key].port + "</span>";
							}
							$( this ).html( cellContents );
							break;

						case 2 :   // Controls name column

							if ( $( this ).html().indexOf( pid ) != -1 ) {
								console.log( "Skipping rebuild of link" );
								break;
							}

							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Show process map"'
										+ ' href="' + commandScreen + '?command=script&template=processMemory&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'

										+ '">pmap</a>';


								if ( processJson[key].serviceName
										.indexOf( "CsAgent" ) == -1 ) {
									cellContents += '<a class="simple" title="linux kill -9" target="_blank"'
											+ ' href="killPid?'
											+ 'pid=' + pid + '&'
											+ 'serviceName=' + processJson[key].serviceName + '&'
											+ '">kill</a>';
								}
							}

							$( this ).html( cellContents );
							break;


						case 3 : // Sockets column;

							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Show sockets"'
										+ ' href="' + commandScreen + '?command=script&'
										+ 'template=socketPid&'
										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'
										+ '">'
										+ processJson[key].socketCount
										+ '</a>';

							}

							//console.log("sockets", cellContents)
							$( this ).html( cellContents );
							break;


						case 4 : // ps CPU column;

							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Show process detail"'
										+ ' href="' + commandScreen + '?command=script&template=processDetails&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'

										+ '">'
										+ processJson[key].cpuUtil
										+ '</a>';
							}
							$( this ).html( cellContents );
							break;

						case 5 : // top CPU column;

							if ( pid != "-" ) {
								cellContents = Math.round( processJson[key].topCpu * 100 ) / 100;
							}
							$( this ).html( cellContents );
							break;

						case 6 : // priority column;

							if ( pid != "-" ) {
								cellContents = '<a class="simple promptForNice"  data-pid="' + pid + '" title="linux renice command" '
										+ ' href="#promptForNice">'
										+ processJson[key].currentProcessPriority
										+ '</a>';
							}
							$( this ).html( cellContents );
							break;

						case 7 : // Threads column;


							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="thread tools: jstack, etc."'
										+ ' href="' + commandScreen + '?command=script&template=processThreads&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'

										+ '">'
										+ processJson[key].threadCount
										+ '</a>';
							}

							$( this ).html( cellContents );
							break;

						case 8 : // rss Memory column;


							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Show process detail"'
										+ ' href="' + commandScreen + '?command=script&template=processMemory&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'

										+ '">'
										+ Math.round( (processJson[key].rssMemory / 1024) )
										+ '</a>';
							}
							$( this ).html( cellContents );

							break;

						case 9 : // vsz Memory column;
							if ( pid != "-" ) {
								cellContents = Math.round( (processJson[key].virtualMemory / 1024 / 1024) * 10 ) / 10;
							}
							$( this ).html( cellContents );

							break;

						case 10 : // Open Files column;

							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Show file descriptors"'
										+ ' href="' + commandScreen + '?command=script&template=lsofPid&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'

										+ '">'
										+ processJson[key].fileCount
										+ '</a>';

							}
							$( this ).html( cellContents );
							break;

						case 11 : // Heap or Arguments column;

							if ( pid != "-" ) {
								var viewParams = processJson[key].runHeap;
								if ( viewParams == "" )
									viewParams = "show"
								cellContents = '<a class="simple" target="_blank"'
										+ '" href="getParams?'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'
										+ '">'
										+ viewParams
										+ '</a>';
							}
							$( this ).html( cellContents );
							break;

						case 12 : // Disk space column;

							if ( pid != "-" ) {
								cellContents = processJson[key].diskUtil;
							}
							$( this ).html( cellContents );
							break;

						case 13 : // Disk read column;

							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Run linux pidstat"'
										+ ' href="' + commandScreen + '?command=script&template=processDetails&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'
										+ '">'
										+ processJson[key].diskReadKb
										+ '</a>';

							}

							$( this ).html( cellContents );
							break;

						case 14 : // Disk write column;

							if ( pid != "-" ) {

								cellContents = '<a class="simple" target="_blank" title="Run linux pidstat"'
										+ ' href="' + commandScreen + '?command=script&template=processDetails&'

										+ 'pid=' + pid + '&'
										+ 'serviceName=' + processJson[key].serviceName + '&'
										+ '">'
										+ processJson[key].diskWriteKb
										+ '</a>';

							}

							$( this ).html( cellContents );
							break;

						default :
							$( this ).html( "placeholder" );

					}


					column++;
				} );


			// Need to skip oracle
			if ( processJson[key].serviceName != "oracle"
					&& isInt( processJson[key].rssMemory ) ) {
				totalMem += parseInt( processJson[key].rssMemory );
			}
		}

		$( ".notFound" ).remove(); // get rid of any rows not found

		// hide process controls unless in full mode
		if ( $( "#filterCsap" ).prop( 'checked' ) ) {
			$( ".controls" ).hide();
			$( ".raw" ).show();
		} else {
			$( ".controls" ).show();
			$( ".raw" ).hide();
		}

		filterProcesses();
		$processTable.trigger( "update", "resort" );

//		setTimeout(() => {
//			
//		}, 1000);
//		$( '[title!=""]', $( "#processTable" ) ).qtip( {
//			content: {
//				attr: 'title',
//				button: true
//			},
//			style: {
//				classes: 'qtip-bootstrap'
//			}
//		} );

		$( "#totalMem" )
				.html( (totalMem / 1000 / 1000).toFixed( 2 ) );

	}


	function filterProcesses() {

		var useServiceName = true;
		if ( !$( "#filterCsap" ).prop( 'checked' ) ) {
			useServiceName = false;
		}

		var filter = $processFilter.val().toLowerCase();

		$( "tr", $processBody ).each( function () {
			$row = $( this );
			var processId = $( "td:nth-child(1)", $row ).text().toLowerCase();
			if ( !useServiceName ) {
				processId = $( "td:nth-child(12)", $row ).text().toLowerCase();
			}
			//console.log("col1: ", col1) ;
			if ( filter.length = 0 || processId.contains( filter ) ) {
				$row.show();
			} else {
				$row.hide()
			}
		} )


	}

	function updateHostCpuTable( mpStatCommandOutput ) {
		var mpTable = $( "#mpTable tbody" );
		mpTable.empty();
		for ( var key in mpStatCommandOutput ) {
			// if ( ! isInt(threadCount[key].pid) ) continue ;

			var mpRow = jQuery( '<tr></tr>', { } ).appendTo( mpTable );

			mpRow.append( jQuery( '<td></td>', {
				text: mpStatCommandOutput[key].cpu
			} ) );

			mpRow.append( jQuery( '<td></td>', {
				class: "num",
				text: mpStatCommandOutput[key].puser
			} ) );

			mpRow.append( jQuery( '<td></td>', {
				class: "num",
				text: mpStatCommandOutput[key].psys
			} ) );

			mpRow.append( jQuery( '<td></td>', {
				class: "num",
				text: mpStatCommandOutput[key].pio
			} ) );

			mpRow.append( jQuery( '<td></td>', {
				class: "num",
				text: mpStatCommandOutput[key].pidle
			} ) );

			mpRow.append( jQuery( '<td></td>', {
				class: "num",
				text: mpStatCommandOutput[key].intr
			} ) );


			mpTable.append( mpRow );
		}
		mpTable.trigger( "update" ); // update table sorter
	}

	function configurePriorityButtons() {
		$( ".promptForNice" ).find( "*" ).off();

		$( ".promptForNice" ).click( function () {


			var curPid = $( this ).data( "pid" );
			currentPriorityForProcess = $( this ).html();

			$( ".ajs-dialog .priorityDesc" ).val( currentPriorityForProcess );


			var newItemDialog = alertify.confirm( $( "#priorityPrompt" ).html() );

			newItemDialog.setting( {
				title: "Caution: Modify linux process priority",
				resizable: false,
				'labels': {
					ok: 'Temporarily Modify',
					cancel: 'Cancel Request'
				},
				'onok': function () {
					var updatedPriority = $( ".ajs-dialog .priorityDesc" ).val();

					if ( updatedPriority == "999" )
						updatedPriority = currentPriorityForProcess;

					alertify.notify( "Sending request to renice pid: " + curPid + " to priority: " + updatedPriority );
					setTimeout( function () {
						updatePriority( curPid, updatedPriority );
					}, 500 );

				},
				'oncancel': function () {
					alertify.warning( "Canceled Request" );
				}

			} );


			// alert( $("#instanceTable *.selected").length ) ;
			return false;
		} );
	}

	function isInt( value ) {
		if ( (parseFloat( value ) == parseInt( value )) && !isNaN( value ) ) {
			return true;
		} else {
			return false;
		}
	}


	function wipeCache() {

		var dt = new Date();
		// window.alert(dt.getTimezoneOffset());
		document.location.href = document.location.href + "?offset="
				+ dt.getTimezoneOffset() + "&" + "emptyCache=emptyCache";
	}


	var refreshDfTimer = 0;
	function refreshDf() {

		clearTimeout( refreshDfTimer );

		var dfTable = $( "#dfTableDiv table tbody" );

		$.getJSON( "getDf", {
			q: "test"
		}, function ( dfJson ) {

			dfTable.empty();

			for ( var key in dfJson ) {

				console.log( "DF adding: " + key );


				var tableRow = jQuery( '<tr/>', { } );
				dfTable.append( tableRow );

				var mountCol = jQuery( '<td/>', { } ).appendTo( tableRow );

				jQuery( '<a/>', {
					class: "simple",
					target: "_blank",
					title: "Explore files on file system",
					href: "../file/FileManager?hostName=" + uiSettings.hostName + "&fromFolder=" + dfJson[key].mount,
					text: dfJson[key].mount
				} ).appendTo( mountCol );


				var commandsCol = jQuery( '<td/>', {
					class: " ",
					text: "",
				} ).appendTo( tableRow );

				var percentUsedColumn = jQuery( '<td/>', {
					class: " num"
				} ).appendTo( tableRow );


				jQuery( '<a/>', {
					class: "simple",
					target: "_blank",
					title: "Test throughput to disk",
					href: commandScreen + "?command=script&template=diskPerf&fromFolder=__root__" + dfJson[key].mount,
					text: dfJson[key].usedp
				} ).appendTo( percentUsedColumn );



				jQuery( '<td/>', {
					class: " num",
					text: dfJson[key].used + ' / ' + dfJson[key].avail
				} ).appendTo( tableRow );

				var deviceColumn = jQuery( '<td/>', {
					class: " ",
					text: dfJson[key].dev
				} ).appendTo( tableRow );

				if ( dfJson[key].dev == "shmfs" || dfJson[key].dev == "tmpfs" )
					deviceColumn.css( "color", "red" ).css( "font-weight", "bold" );





				jQuery( '<a/>', {
					class: "promptButton",
					target: "_blank",
					title: "Show inode and file counts",
					href: commandScreen + "?command=script&template=df&fromFolder=__root__" + dfJson[key].mount,
					text: "st"
				} ).appendTo( commandsCol );


				jQuery( '<a/>', {
					class: "promptButton",
					target: "_blank",
					title: "Show storage per directory",
					href: commandScreen + "?command=script&template=du&fromFolder=__root__" + dfJson[key].mount,
					text: "us"
				} ).appendTo( commandsCol );


			}

			dfTable.trigger( "update", "resort" );
			$( '[title!=""]', dfTable ).qtip( {
				content: {
					attr: 'title',
					button: true
				},
				style: {
					classes: 'qtip-bootstrap'
				}
			} );
		} )

		refreshDfTimer =
				setTimeout( function () {
					refreshDf()
				}, 60000 );
	}


	var memoryRefreshTimer = 0;

	function refreshMem() {

		console.log( "Updating memory stats: " + memoryRefreshTimer );

		clearTimeout( memoryRefreshTimer );


		$.getJSON( "getMem", {
			q: "test"
		} )
				.done( renderMemoryUi );


		memoryRefreshTimer =
				setTimeout( function () {
					refreshMem()
				}, 20000 );
	}

	function renderMemoryUi( memoryMetrics ) {

		// update Swap table
		var swapTableBody = $( "#swapTableDiv table tbody" );
		swapTableBody.empty();

		for ( var key in memoryMetrics ) {

			if ( (key.indexOf( "swapon" ) == -1)
					|| (key == "timestamp") ) {
				continue;
			}

			var tableRow = jQuery( '<tr/>', { } );

			swapTableBody.append( tableRow );

			var swapArray = memoryMetrics[key];
			for ( var i = 0; i < swapArray.length; i++ ) {

				jQuery( '<td/>', {
					class: " num",
					text: swapArray[i]
				} ).appendTo( tableRow );
			}


		}


		// Update memory table
		var memTableBody = $( "#memTableDiv table tbody" );
		memTableBody.empty();

		// newer kernels combine buffer data into first line, so skip
		if ( !memoryMetrics.isFreeAvailable ) {
			// Used hashed values
			var bufferArray = memoryMetrics["buffer"];
			var memRow = jQuery( '<tr/>', { } );
			memRow.appendTo( memTableBody );
			jQuery( '<td/>', {
				class: "",
				text: bufferArray[0] + " " + bufferArray[1]
			} ).appendTo( memRow );

			jQuery( '<td/>', { } ).appendTo( memRow );

			jQuery( '<td/>', { text: "Used: " + bufferArray[2] } ).appendTo( memRow );


			jQuery( '<td/>', { text: "Free: " + bufferArray[3] } ).css( "color", "red" ).appendTo( memRow );
		}



		// Used hashed values
		var ramArray = memoryMetrics["ram"];

		var usage = Math.round( parseInt( ramArray[2] )
				/ parseInt(ramArray[1]) * 100);

		memRow = jQuery( '<tr/>', { } );
		memRow.appendTo( memTableBody );

		jQuery( '<td/>', { text: ramArray[0] } ).appendTo( memRow );

		jQuery( '<td/>', { text: usage + "%" } ).appendTo( memRow );

		jQuery( '<td/>', { text: ramArray[2] + " / " + ramArray[1] } ).appendTo( memRow );

		var $bufCacheAvailable = jQuery( '<span/>', { text: ramArray[4] + " / " + ramArray[5] } );
		if ( memoryMetrics.isFreeAvailable ) {
			var available = jQuery( '<span/>', { text: ramArray[6] } ).css( "color", "red" );
			$bufCacheAvailable.append( "  available: " );
			$bufCacheAvailable.append( available );
		} else {
			$bufCacheAvailable.append( " / " + ramArray[6] );
		}
		var $memoryCol = jQuery( '<td/>', { } );
		$memoryCol.append( $bufCacheAvailable );
		memRow.append( $memoryCol );




		// Used hashed values
		var swapArray = memoryMetrics["swap"];

		var usage = 0;
		if ( swapArray[1] != 0 ) {

			usage = Math.round( parseInt( swapArray[2] )
					/ parseInt(swapArray[1]) * 100);
		}


		memRow = jQuery( '<tr/>', { } );
		memRow.appendTo( memTableBody );

		jQuery( '<td/>', { text: swapArray[0] } ).appendTo( memRow );

		jQuery( '<td/>', { text: usage + "%" } ).appendTo( memRow );

		jQuery( '<td/>', { text: swapArray[2] + " / " + swapArray[1] } ).appendTo( memRow );


		jQuery( '<td/>', { text: "" } ).appendTo( memRow );



		memTableBody.trigger( "update", "resort" );

		swapTableBody.trigger( "update", "resort" );

		$( "#memReloadTime" ).html(
				"last refresh:" + memoryMetrics["timestamp"] );


	}

	function refreshLoad() {

		$.getJSON( "getLoad", {
			q: "test"
		}, function ( loadJson ) {

			var cpuCountInt = parseInt( loadJson["cpuCount"] );
			$( "#cpuCount" ).html( cpuCountInt );

			var cpuLoadFloat = parseFloat( loadJson["cpuLoad"] );
			$( "#cpuLoad" ).html( cpuLoadFloat );


			$( "#cpuLoad" ).css( 'color', 'black' );
			if ( cpuLoadFloat >= cpuCountInt ) {
				$( "#cpuLoad" ).css( 'color', 'red' );
			}
			$( "#cpuTimestamp" ).html( "(refreshed: " + loadJson["timeStamp"] + ")" );

			explorerOperations.buildTreeSummary( loadJson );
		} );


		var intervalInSeconds = $( "#cpuIntervalId" ).val();
		setTimeout( function () {
			refreshLoad()
		}, intervalInSeconds * 1000 );
	}

	function updatePriority( pid, priority ) {

		$.getJSON( "updatePriority", {
			pid: pid,
			priority: priority
		}, function ( resultsJson ) {

			alertify.alert( "Results: " + resultsJson.results, function ( e ) {
				$( 'body' ).css( 'cursor', 'wait' );
				refreshProcessData();
			} );



		} );

	}

} );