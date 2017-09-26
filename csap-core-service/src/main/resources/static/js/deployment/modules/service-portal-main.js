// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	waitSeconds: 15,
	packages: [
		{ name: 'editPackage',
			location: '../../editor/modules', // default 'packagename'
			main: 'service-edit'                // default 'main' 
		}
	]

} );



require( ["editPackage", "service-os-panel", "service-app-panel", "./service-graph"], function ( serviceEdit, osPanel, appPanel, serviceGraph ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	var _deploySuccess = false;

	var _resultsDialog = null;
	var $resultsPre = $( "#resultPre" ) ;
	
	var $serviceParameters ;
	$( document ).ready( function () {
		initialize();
	} );


	this.initialize = initialize;
	function initialize() {
		CsapCommon.configureCsapAlertify();
		console.log( "ServiceAdmin::initialize" );
		
		$serviceParameters=$("#serviceParameters") ;

		register_lifeCycleEvents();

		register_Buttons();

		register_ToolTips();

		register_ReportActions();

		initialize_ServiceSettings()

		serviceInstancesGet( false );
		addServiceContext( '#instanceTable', getLast );
	}


	function getLast() {
		return lastInstanceRollOver;
	}


	/** @memberOf ServiceAdmin */
	function register_lifeCycleEvents() {
		$( '#lcSelect' ).change( function () {

			var lcSelected = $( this ).val();
			if ( lcSelected == "none" )
				return false;

			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', false );
			$( "#instanceTable tbody tr" ).removeClass( "selected" );
			$( currentInstance ).addClass( "selected" );

			$( "#instanceTable tbody tr" ).each( function () {

				var rowLc = $( this ).column( 1 ).text();
				// alert( "lcSelected: " + lcSelected + " rowLc: " +
				// rowLc ) ;

				if ( lcSelected == rowLc ) {
					$( this ).addClass( "selected" );
				}
			} );

			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', true );
			// reset the select drop down
			$( "option:eq(0)", this ).attr( "selected", "selected" );
			return false; // prevents link
		} );


		$( '.selectAllHosts' ).click( function () {
			// $( "#meters" ).hide();
			//$( "#toggleMeters" ).trigger( "click" );
			$( "#instanceTable tbody tr" ).addClass( "selected" );
			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', true );
			// alert( $("#instanceTable *.selected").length ) ;
			serviceGraph.updateHosts();
			getLatestServiceStats();
			return false;
		} );

		$( '#deselectAllHosts' ).click( function () {
			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', false );
			$( "#instanceTable tbody tr" ).removeClass( "selected" );
			$( currentInstance ).addClass( "selected" );
			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', true );

			serviceGraph.updateHosts();
			getLatestServiceStats();
			return false;
		} );


		$( '#isGarbageLogs' ).click( function () {
			if ( $( "#isGarbageLogs" ).is( ':checked' ) )
				alertify.notify( "Adding params to start: " + gcParams );
			else
				alertify.notify( "GC params will not be added" );

			return true;
		} );


		$( '#isInlineGarbageLogs' ).click( function () {
			if ( $( "#isInlineGarbageLogs" ).is( ':checked' ) )
				alertify.notify( "Adding params to start: " + gcInlineParams );
			else
				alertify.notify( "GC params will not be added" );

			return true;
		} );
	}


	/** @memberOf ServiceAdmin */
	function register_Buttons() {



		var message = "Use checkbox to select service instances to be included in start/stop/deploy operations."
				+ "\n\nRight mouse click to run commands on individual instances. "
				+ "\n\nThe checkbox that is disabled is the primary instance for deployments and builds. Click other a different host name to change.\n\n";

		$( '#instanceHelpButton' ).attr( "title", message );
		$( '#instanceHelpButton' ).click( function () {
			alertify.alert( message, function ( result ) {
				// do nothing
			} );

			$( ".alertify-inner" ).css( "text-align", "left" );
			$( ".alertify-inner" ).css( "white-space", "pre-wrap" );

			return false;
		} );

		$( "#showFilteredMetersButton" ).click( function () {
			$( ".hiddenMeter" ).toggle( "slow" );
		} );

		$( '.uncheckAll' ).click( function () {

			$( 'input', $( this ).parent().parent() ).prop( "checked", false ).trigger( "change" );
			return false; // prevents link
		} );

		$( '.checkAll' ).click( function () {

			$( 'input', $( this ).parent().parent() ).prop( "checked", true );
			return false; // prevents link
		} );
		$( '.multipleServicesButton' ).click( function () {
			console.log( "Adding: " + $( this ).data( "target" ) + " services: " + servicesOnHostForward.length );
			var operation = $( this ).data( "target" );
			var $servicesContainer = $( "#" + operation );
			$servicesContainer.toggle();

			var $serviceCheckboxes = $( ".serviceCheckboxes", $servicesContainer );
			$serviceCheckboxes.empty();

			var serviceOrder = servicesOnHostForward;
			if ( operation == "killServerServices" )
				serviceOrder = servicesOnHostReverse;

			for ( var i = 0; i < serviceOrder.length; i++ ) {

				var label = serviceOrder[i];
				if ( label.contains( "_" ) )
					label = label.substring( 0, label.indexOf( "_" ) );
				var serviceLabel = jQuery( '<label/>', {
					class: "serviceLabels",
					text: label
				} ).appendTo( $serviceCheckboxes );

				var optionItem = jQuery( '<input/>', {
					name: serviceOrder[i],
					type: "checkbox"
				} ).prependTo( serviceLabel );

				if ( serviceOrder[i] == serviceName ) {
					optionItem.prop( 'checked', true );
				}

			}
		} );

		$( '#editServiceButton' ).click( function () {
			serviceEdit.setSpecificHost( hostName );
			$.get( serviceEditUrl,
					serviceEdit.showServiceDialog,
					'html' );
			return false;
		} );


		$( '#deployOptionsButton' ).click( showDeployDialog );

		// Do start bindings
		$( '#startOptionsButton' ).click( showStartDialog );


		$( '#killOptionsButton' ).click( showKillDialog );

		$( '#jobsButton' ).click( showJobsDialog );


		// Do event bindings
		$( '#launchService' ).click( launchServiceDialog );



		$( '#runtimeSelect' ).change( showRuntimeConfirmDialog );

		$( '#jdkSelect' ).change( showJdkConfirmDialog );


		$( '#motdButton' ).click( showHostMessageDialog );




		$( '#undeploySelect' ).change( function () {
			unDeployService( true );
			return false; // prevents link
		} );

		$( '#showFiles' ).click( function () {
			launchFiles( $( "#instanceTable *.selected" ) );
			return false; // prevents link
		} );

		$( '#showLogs' ).click( function () {
			launchLogs( $( "#instanceTable *.selected" ) );
			return false; // prevents link
		} );


		$( '#searchLogs' ).click(
				function () {
					var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName );

					urlAction += commandScreen +
							"?command=logSearch&fromFolder=defaultLog&serviceName=" + serviceName + "&hostName=" + hostName + "&";

					openWindowSafely( urlAction, "_blank" );

					return false;
				} );

		$( '#hostDashboardButton' ).click( function () {
			launchHostDash( $( "#instanceTable *.selected" ) );
			return false; // prevents link
		} );

		$( '#jmxDashboardButton' ).click( function () {
			launchJmxDash( $( "#instanceTable *.selected" ) );
			return false; // prevents link
		} );

		$( '#jmxCustomDashboardButton, #httpCollectionButton' ).click( function () {
			launchCustomJmxDash( $( "#instanceTable *.selected" ) );
			return false; // prevents link
		} );


		$( '#historyButton' ).click( function () {
			launchHistory( $( "#instanceTable *.selected" ), null );
			return false; // prevents link
		} );

		$( '#jmcButton' ).click( function () {
			launchProfiler( $( "#instanceTable *.selected" ), false );
			return false; // prevents link
		} );

		$( '#jmeterButton' ).click( function () {
			launchJMeter( false );
			return false; // prevents link
		} );

		$( '#jvisualvmButton' ).click( function () {
			launchProfiler( $( "#instanceTable *.selected" ), true );
			return false; // prevents link
		} );


		$( '#hostCompareButton, #jmxCompareButton, #appCompareButton, #osCompareButton' ).click( function () {
			// launchPerfCompare( $("#instanceTable *.selected"), true);


			// alert (" Num: " +jqueryRows.length) ;
			var jqueryRows = $( "#instanceTable *.selected" )
			var hosts = "";

			jqueryRows.each( function ( index ) {
				var host = $( this ).data( "host" );
				if ( hosts != "" )
					hosts += ",";
				hosts += host;
			} );

			var urlAction = analyticsUrl + "&report=" + $( this ).data( "report" ) + "&project=" + selectedProject + "&host=" + hosts
					+ "&service=" + serviceShortName + "&appId=" + appId + "&";

			if ( $( this ).attr( "id" ) == "appCompareButton" ) {
				urlAction += "appGraph=appGraph";
			}


			openWindowSafely( urlAction, "_blank" );


			return false; // prevents link
		} );



		$( '#serviceHelpButton' ).click( function () {
			launchHelp( $( this ) );
			return false; // prevents link
		} );

		$( '#hostHelpButton' ).click( function () {
			var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName ); 
			urlAction += "/csap/health?pattern=" + serviceShortName + "&u=1";

			// console.log("hostHelp: " + urlAction) ;
			openWindowSafely( urlAction, "_blank" );

			return false; // prevents link
		} );


		$( '#launchOptions a' ).each( function ( index ) {

			// alert(index + ': ' + $(this).text());

			$( this ).click( function () {
				launchService( $( this ) );
				return false; // prevents link
			} );

		} );

	}


	/** @memberOf ServiceAdmin */
	function initialize_ServiceSettings() {
		if ( serviceName == "CsAgent_8011" || serviceName.indexOf( "admin" ) == 0 ) {
			$( "#deployStart" ).prop( 'checked', false );
			$( "#isScmUpload" ).prop( 'checked', true );
			$( "#stopButton" ).hide();
		}

		// jvmSelection
		for ( var i = 0; i < jvms.available.length; i++ ) {

			var optionItem = jQuery( '<option/>', {
				text: jvms.available[i]
			} );
			$( "#jdkSelect" ).append( optionItem );

		}
		$( "#jdkSelect" ).val( jvms.serviceSelected );

		// Update load
		// serviceDataGet() ;

		if ( isSkipJmx ) {
			$( ".jmxClassifier" ).hide();
		}

	}


	/** @memberOf ServiceAdmin */
	function register_ReportActions() {

		var triggerReports = function () {

			if ( $( this ).attr( "id" ) == "compareStartInput" )
				return;
			getLatestServiceStats();
		}

		$( '#filterThreshold' ).selectmenu( {
			width: "6em",
			change: triggerReports
		} );
		$( '#numAppSamples' ).selectmenu( {
			width: "6em",
			change: triggerReports
		} );

		$( '#rateSelect' ).selectmenu( {
			width: "10em",
			change: triggerReports
		} );


		$( '#jmxAnalyticsLaunch' ).click( function () {

			var urlAction = analyticsUrl + "&project=" + selectedProject + "&report=jmx/detail"
					+ "&service=" + serviceShortName + "&appId=" + appId + "&";

			openWindowSafely( urlAction, "_blank" );
			return false;
		} );

		$( '#applicationLaunch' ).click( function () {

			var urlAction = analyticsUrl + "&project=" + selectedProject + "&report=jmxCustom/detail"
					+ "&service=" + serviceShortName + "&appId=" + appId + "&";

			openWindowSafely( urlAction, "_blank" );
			return false;
		} );


		$( "#compareStartInput" ).datepicker( { maxDate: '-1' } );
		$( "#compareStartInput" ).change( function () {
			var days = calculateUsCentralDays( $( this ).datepicker( "getDate" ).getTime() );

			// $("#dayOffset", resourceRootContainer).val(days) ;
			console.log( "Num days offset: " + days + " id " + $( this ).attr( "id" ) );


			if ( days == 0 ) {
				days = 1;
			}

			osPanel.updateOffset( days );
			appPanel.updateOffset( days );
			getLatestServiceStats();
		} );




		$( "#refreshStats" ).click( function () {
			getLatestServiceStats();

			$( 'body , a' ).css( 'cursor', 'wait' );
			refreshServiceData();

			return false; // prevents link
		} );

		$.jqplot.config.enablePlugins = true;
		if ( isScript ) {
			$( "#meters" ).hide();
			$( "#osChart" ).hide();
		} else {
			getLatestServiceStats();
		}

	}


	/** @memberOf ServiceAdmin */
	function register_ToolTips() {

//	return;
//		$( '[data-qtipLeft!=""]' ).qtip( {
//			content: {
//				attr: 'data-qtipLeft',
//				button: true
//			},
//			style: {
//				classes: 'qtip-bootstrap'
//			},
//			position: {
//				my: 'top left',
//				at: 'bottom left'
//			}
//		} );
//		$( '[data-qtipRight!=""]' ).qtip( {
//			content: {
//				attr: 'data-qtipRight',
//				button: true
//			},
//			style: {
//				classes: 'qtip-bootstrap'
//			},
//			position: {
//				my: 'top right',
//				at: 'bottom right',
//				adjust: { x: 0, y: 10 }
//			}
//		} );
//
//		$( '[title != ""]' ).qtip( {
//			content: {
//				attr: 'title',
//				button: true
//			},
//			style: {
//				classes: 'qtip-bootstrap'
//			},
//			position: {
//				my: 'top left', // Position my top left...
//				at: 'bottom left',
//				adjust: { x: 5, y: 10 }
//			}
//		} );
	}

	/** @memberOf ServiceAdmin */
	function getLatestServiceStats() {


		var hostNameArray = new Array();
		$( "#instanceTable *.selected" ).each( function () {
			var serviceNamePort = $( this ).data( "instance" );
			var serviceHost = $( this ).data( "host" );
			hostNameArray.push( serviceHost );
		} );

		if ( hostNameArray.length == 0 )
			hostNameArray.push( hostName );

		if ( $( "#serviceStats" ).is( ":visible" ) ) {
			osPanel.show( hostNameArray );
		}
		appPanel.show( hostNameArray );



	}


	/** @memberOf ServiceAdmin */
	function launchServiceDialog() {
		if ( isOsOrWrapper ) {
			launchDefaultService( $( "#instanceTable *.selected" ) );
		} else {
			// Lazy create
			if ( !alertify.launch ) {
				var launchDialogFactory = function factory() {
					return{
						build: function () {
							// Move content from template
							this.setContent( $( "#launchOptions" ).show()[0] );
						},
						setup: function () {
							return {
								buttons: [{ text: "Close", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }],
								options: {
									title: "Service Launch :", resizable: false, movable: false, maximizable: false
								}
							};
						}

					};
				};

				alertify.dialog( 'launch', launchDialogFactory, false, 'alert' );
			}

			var alertsDialog = alertify.launch().show();

		}

		return;
	}


	/** @memberOf ServiceAdmin */
	function showJdkConfirmDialog() {


		var message = "<div style='margin: 2em'>Review the logs to verify JDK selection.";
		message += " Use the edit configuration button to make the change permanent on all instances</div>";


		var applyDialog = alertify.confirm( message );
		applyDialog.setting( {
			title: "Caution Advised",
			'labels': {
				ok: 'Proceed Anyway',
				cancel: 'Cancel request'
			},
			'onok': function () {
				var javaOpts = $serviceParameters.val();
				javaOpts = javaOpts.replace( "-DcsapJava8", " " );
				javaOpts = javaOpts.replace( "-DcsapJava7", " " );

				var csapJdkParam = "-DcsapJava7";

				if ( $( '#jdkSelect' ).val().indexOf( "8" ) != -1 )
					csapJdkParam = "-DcsapJava8";

				$serviceParameters.val( csapJdkParam + " " + javaOpts );
			},
			'oncancel': function () {
				alertify.warning( "Reverting java to version in Definition" );
				$( "#jdkSelect" ).val( jvms.serviceSelected );
			}

		} );


	}


	/** @memberOf ServiceAdmin */
	function showRuntimeConfirmDialog() {


		var message = "<div style='margin: 2em'>Changing the application runtime can cause the application to fail to start.<br><br> Review the logs to verify.";
		message += '<br><br><span class="selected">' +
				'Use the edit configuration button to make the change permanent on all instances</span></div>';


		var applyDialog = alertify.confirm( message );
		applyDialog.setting( {
			title: "Caution Advised",
			'labels': {
				ok: 'Proceed Anyway',
				cancel: 'Cancel request'
			},
			'onok': function () {

				// alertify.notify("Made the change") ;
			},
			'oncancel': function () {
				alertify.warning( "Operation Cancelled" );
				$( '#runtimeSelect' ).val( serverType )
			}

		} );


	}
	function showJobsDialog() {
		// Lazy create
		if ( !alertify.jobs ) {
			var jobsDialogFactory = function factory() { 
				return{
					build: function () {
						// Move content from template
						if ( serviceJobDefinition != null ) {
							$( "#jobDetails" ).text( JSON.stringify( serviceJobDefinition, null, "\t" ) );
						}
						$( "#jobSelect" ).empty();
						jQuery( '<option/>', {
								text: "Log Rotation"
							} ).appendTo( $( "#jobSelect" ) );
						for ( var i = 0; i < serviceJobs.length; i++ ) {
							jQuery( '<option/>', {
								text: serviceJobs[i].description
							} ).appendTo( $( "#jobSelect" ) );
						}
						this.setContent( $( "#jobOptions" ).show()[0] );
					},
					setup: function () {
						return {
							buttons: [{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }],
							options: {
								title: "Dummy :", resizable: false, movable: false, maximizable: false
							}
						};
					}

				};
			};

			alertify.dialog( 'jobs', jobsDialogFactory, false, 'alert' );

			$( '#doJobsButton' ).click( function () {
				alertify.closeAll();
				//runServiceJobs();
				var paramObject = {
					jobToRun: $( "#jobSelect" ).val()
				}
				executeOnSelectedHosts( "runServiceJob", paramObject );
			} );

		}

		setAlertifyTitle( "Run Jobs for ", alertify.jobs().show() );

	}




	function showKillDialog() {
		// Lazy create
		if ( !alertify.kill ) {
			var killDialogFactory = function factory() {
				return{
					build: function () {
						// Move content from template
						this.setContent( $( "#killOptions" ).show()[0] );
					},
					setup: function () {
						return {
							buttons: [{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }],
							options: {
								title: "Service Stop :", resizable: false, movable: false, maximizable: false
							}
						};
					}

				};
			};

			alertify.dialog( 'kill', killDialogFactory, false, 'alert' );

			$( '#killButton' ).click( function () {
				alertify.closeAll();
				killService();
			} );

			$( '#stopButton' ).click( function () {
				alertify.closeAll();
				if ( isOsOrWrapper || serverType == "SpringBoot" ) {
					stopService();
					return;
				}
				var message = "Warning: JEE service stops can take a while, and may never terminate the OS process.<br><br>"
						+ "Use the CSAP Host Dashboard  and log viewer to monitor progress; use kill if needed.<br><br>"
						+ 'Unless specifically requested by service owner: <br>'
						+ '<div class="news"><span class="stopWarn">kill option is preferred as it is an immediate termination</span></div>';

				var applyDialog = alertify.confirm( message );
				applyDialog.setting( {
					title: "Caution Advised",
					'labels': {
						ok: 'Proceed Anyway',
						cancel: 'Cancel request'
					},
					'onok': function () {
						stopService();
					},
					'oncancel': function () {
						alertify.warning( "Operation Cancelled" );
					}

				} );


			} );

		}

		setAlertifyTitle( "Stopping", alertify.kill().show() );

	}


	/** @memberOf ServiceAdmin */
	function showStartDialog() {

		// Lazy create
		if ( !alertify.start ) {
			var startDialogFactory = function factory() {
				return{
					build: function () {
						// Move content from template
						this.setContent( $( "#startOptions" ).show()[0] );
						this.setting( {
							'onok': startService, 
							'oncancel': function () {
								alertify.warning( "Cancelled Request" );
							}
						} );
					},
					setup: function () {
						return {
							buttons: [{ text: "Start Service", className: alertify.defaults.theme.ok },
								{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
							],
							options: {
								title: "Service Start :", resizable: false, movable: false, maximizable: true,
							}
						};
					}

				};
			};
			alertify.dialog( 'start', startDialogFactory, false, 'confirm' );
		}

		
		if ( serverType == "docker" ) {
			$serviceParameters.val( JSON.stringify( __dockerConfiguration, "\n", "\t" )) ;
			$serviceParameters.css("min-height", "25em") ;
		}
		var startDialog = alertify.start().show() ;
		setAlertifyTitle( "Starting",  startDialog);

		if ( serviceName.indexOf( "CsAgent" ) != -1 ) {
			var message = "Warning - CsAgent should be killed which will trigger auto restart.<br> Do not issue start on CsAgent unless you have confirmed with CSAP team.";

			alertify.alert( message );

		}

	}

	var _lastOperation = "";

	/** @memberOf ServiceAdmin */
	function setAlertifyTitle( operation, dialog ) {
		var target = $( "#instanceTable *.selected" ).length;
		if ( target === 1 )
			target = hostName;
		else
			target += " hosts";
		_lastOperation = operation + " Service: " + serviceShortName + " on " + target;
		dialog.setting( {
			title: _lastOperation
		} );
	}


	/** @memberOf ServiceAdmin */
	function showDeployDialog() {

		// Lazy create
		if ( !alertify.deploy ) {
			
			createDeployDialog();
		}

		setAlertifyTitle( "Deploying", alertify.deploy().show() );
		// $("#sourceOptions").fadeTo( "slow" , 0.5) ;

		if ( serviceName.indexOf( "CsAgent" ) != -1 ) {
			var message = "Please confirm deployment of CsAgent, including  update of CS-AP application runtimes and scripts in STAGING/bin";

			alertify.confirm( message,
					function () {
						alertify.notify( "CsAgent will be updated" );
					},
					function () {
						alertify.closeAll()
					}
			);
		}
	}


	/** @memberOf ServiceAdmin */
	function createDeployDialog() {

		console.log( "\n\n Creating dialog" );

		var deployTitle = 'Service Deploy: <span title="After build/maven deploy on build host, artifact is deployed to other selected instances">'
				+ hostName + "</span>"

		var okFunction = function () {
			var deployChoice = $( 'input[name=deployRadio]:checked' ).val();
			// alertify.success("Deployment using: "
			// +
			// deployChoice);
			alertify.closeAll();
			_resultsDialog=null ;
			showResultsDialog( "Deploy" );

			$resultsPre.html( "Deploy Request is being processed\n" );
			$( 'body' ).css( 'cursor', 'wait' );
			displayResults( "Initiating build" );
			
			switch ( deployChoice ) {

				case "maven":
					if ( $( "#deployServerServices input:checked" ).length == 0 ) {
						deployService( true, serviceName );
					} else {
						$( "#deployServerServices input:checked" ).each( function () {
							var curName = $( this ).attr( "name" );
							deployService( true, curName );
						} );
//													 var msg = "Multiple Services selected for deployment, only the status of the final build will be shown"
//																+ " Once the final deployment has completed, then the start can be issued."
//													 alertify.csapWarning( msg );
					}
					break;

				case "source":
					deployService( false, serviceName );
					break;

				case "upload":
					uploadWar(  );
					break;

			}

		}

		var deployDialogFactory = function factory() {
			return{
				build: function () {
					// Move content from template
					this.setContent( $( "#deployDialog" ).show()[0] );
					this.setting( {
						'onok': okFunction,
						'oncancel': function () {
							alertify.warning( "Cancelled Request" );
						}
					} );
				},
				setup: function () {
					return {
						buttons: [{ text: "Deploy Service", className: alertify.defaults.theme.ok },
							{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
						],
						options: {
							title: deployTitle, resizable: false, movable: false, maximizable: false

						}
					};
				}

			};
		};

		alertify.dialog( 'deploy', deployDialogFactory, false, 'confirm' );

		if ( serverType == "docker" ) {
			$("#dockerImageVersion").val( __dockerConfiguration.image ) ;
			$("#osDeployOptions").hide() ;
		} else {
			$("#dockerDeployOptions").hide() ;
		}

		$( 'input[name=deployRadio]' ).change( function () {

			$( "#osDeployOptions >div" ).hide();

			var $selectedDiv = $( "#" + $( this ).val() + "Options" );
			$selectedDiv.show();
		} );

		$( 'input[name=deployRadio]:checked' ).trigger( "change" );



		$( '#scmPass' ).keypress( function ( e ) {
			if ( e.which == 13 ) {
				$( '.ajs-buttons button:first',
						$( "#deployDialog" ).parent().parent().parent() )
						.trigger( "click" );
			}
		} );
		
		
		


		$( '#cleanServiceBuild' ).click( function () {
			cleanServiceBuild( false );
			return false; // prevents link
		} );

		$( '#cleanGlobalBuild' ).click( function () {
			cleanServiceBuild( true );
			return false; // prevents link
		} );


	}


	/** @memberOf ServiceAdmin */
	function displayResults( resultsJson, append ) {

		console.log("results", resultsJson) ;
		var results = JSON.stringify( resultsJson, null, "\t" );
		results = results.replace( /\\n/g, "<br />" );

		if ( !append ) {
			$resultsPre.html( "" );
			$( 'body' ).css( 'cursor', 'default' );
			refreshServiceData();
		} 
		$resultsPre.append( results );

		// needed when results is small
		showResultsDialog( "Updating" );


	}


	/** @memberOf ServiceAdmin */
	function displayHostResults( host, serviceInstance, command, resultsJson, currentCount, totalCount ) {

		var results = JSON.stringify( resultsJson, null, "\t" );
		results = results.replace( /\\n/g, "<br />" );
		results = results.replace( /\\t/g, '&#9;' );


		var isDetailsShown = false;
		if ( results.indexOf( "__ERROR" ) != -1 || results.indexOf( "__WARN" ) != -1 ) {
			isDetailsShown = true;
		}

		var resultProgressMessage = "";
		if ( currentCount != 0 )
			resultProgressMessage = currentCount + " of " + totalCount;

		if ( command == "startServer" || command == "killServer" ) {

			var $console = $resultsPre;

			if ( isDetailsShown ) {

				var $commandOutput = jQuery( '<div/>', {
					id: serviceInstance + "Output",
					class: "note",
					html: results
				} ).css( "font-size", "0.9em" ).appendTo( $console );
				$commandOutput.css( "display", "block" );

			}

			var progress = '\n' + resultProgressMessage + " : " + host + ' : ' + command + " on " + serviceInstance + ' has been queued.';
			$console.append( progress );

			//var launchResponse="Start"
			var $scriptLink = jQuery( '<a/>', {
				href: "#scriptOutput",
				class: "simple ",
				text: "Command Output"
			} ).appendTo( $console );

			$scriptLink.click( function () {

				var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );

				var logname = serviceInstance + "_start.log&";
				if ( command == "killServer" ) {
					logname = serviceInstance + "_kill.log&"
				}

				urlAction += "/file/FileMonitor?serviceName=" + serviceInstance
						+ "&hostName=" + host + "&ts=1&fileName=//" + logname + "isLogFile=false&";
				openWindowSafely( urlAction, "_blank" );
				return false;
			} );

			var $serviceTail = jQuery( '<a/>', {
				href: "#tailLogs",
				class: "simple",
				text: "Service Logs"
			} ).appendTo( $console );

			$serviceTail.click( function () {
				var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );

				urlAction += "/file/FileMonitor?serviceName=" + serviceInstance
						+ "&hostName=" + host + "&isLogFile=true&";

				openWindowSafely( urlAction, "_blank" );

				return false;
			} );

		} else {

			var $console = $resultsPre;
			var progress = '\n' + resultProgressMessage + " : " + host + ' : ' + command + " on " + serviceInstance + ' has completed.';
			$console.append( progress );
			var $commandLink = jQuery( '<a/>', {
				href: "#scriptOutput",
				class: "simple ",
				text: "Results"
			} ).appendTo( $console );

			var $commandOutput = jQuery( '<div/>', {
				id: serviceInstance + "Output",
				class: "note",
				html: results
			} ).css( "font-size", "0.9em" ).appendTo( $console );

			if ( isDetailsShown ) {
				$commandOutput.css( "display", "block" );
			} else {
				$commandOutput.css( "display", "none" );
			}

			$commandLink.click( function () {

				if ( $commandOutput.is( ":visible" ) ) {
					$commandOutput.hide();
					restoreResultsDialog();
				} else {
					$commandOutput.css( "display", "block" );
					maximizeResultsDialog();
				}
				return false; // prevents link
			} );

		}



		showResultsDialog( command );
		if ( !isDetailsShown && currentCount == totalCount ) {
			restoreResultsDialog();
			showResultsDialog( command );
		}


	}



	/** @memberOf ServiceAdmin */
	function maximizeResultsDialog() {
		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;
		_resultsDialog.resizeTo( targetWidth, targetHeight )
	}


	/** @memberOf ServiceAdmin */
	function restoreResultsDialog() {
		_resultsDialog.show();
	}


	/** @memberOf ServiceAdmin */
	function showResultsDialog( commandName ) {

		// if dialog is active, just updated scroll and return
		if ( _resultsDialog != null ) {
			var heightToScroll = $resultsPre[0].scrollHeight;
			// console.log("Scrolling to bottom: " + heightToScroll) ;
			$( ".ajs-content" ).scrollTop( heightToScroll );
			return;
		}



		if ( !alertify.results ) {
			alertify.dialog( 'results', function factory() {
				return{
					build: function () {
						this.setContent( $( "#resultsSection pre" ).show()[0] );
					}
				};
			}, false, 'alert' );
		}

		_resultsDialog = alertify.results().show();

		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;

		var dialogTitle = commandName;

		if ( _lastOperation != "" ) {
			dialogTitle = _lastOperation;
			_lastOperation = "";
		}

		_resultsDialog.setting( {
			title: "Results: " + dialogTitle,
			resizable: true,
			movable: false,
			'label': "Close after reviewing output",
			'onok': function () {
				//$( "#resultsSection" ).append( $resultsPre );
				//_resultsDialog = null;
			}


		} );

		// resultsDialog.resizeTo(targetWidth, targetHeight)

		


	}



	/** @memberOf ServiceAdmin */
	function deployService( isMavenDeploy, deployServiceName ) {

		var targetScpHosts = new Array();
		// $("#instanceTable tbody tr").addClass("selected") ;
		$( "#instanceTable *.selected" ).each( function () {
			// alert( Checking )
			if ( $( this ).data( "host" ) != hostName )
				targetScpHosts.push( $( this ).data( "host" ) );
		} );


		var paramObject = {
			scmUserid: $( "#scmUserid" ).val(),
			scmPass: $( "#scmPass" ).val(),
			scmBranch: $( "#scmBranch" ).val(),
			commandArguments: $serviceParameters.val(),
			runtime: $( "#runtimeSelect" ).val(),
			targetScpHosts: targetScpHosts,
			serviceName: deployServiceName,
			hostName: hostName
		};
		
		if ( serverType == "docker" ) {
			$.extend( paramObject, {
				dockerImage: $("#dockerImageVersion").val(),
				mavenDeployArtifact: $("#dockerImageVersion").val()
			} );
		} else if ( isMavenDeploy ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;
			var artifact = $( "#mavenArtifact" ).val();
			$.extend( paramObject, {
				mavenDeployArtifact: artifact
			} );
			console.log( "Number of ':' in artifact", artifact.split( ":" ).length );
			if ( artifact.split( ":" ).length != 4 ) {
				$( "#mavenArtifact" ).css( "background-color", "#f5bfbf" );
				alertify.csapWarning( "Unexpected format of artifact. Typical is a:b:c:d  eg. org.csap:BootEnterprise:1.0.27:jar" );
				//return ;
			} else {
				$( "#mavenArtifact" ).css( "background-color", "#CCFFE0" );
			}
		} else {

			var scmCommand = $( "#scmCommand" ).val();

			if ( scmCommand.indexOf( "deploy" ) == -1
					&& $( "#isScmUpload" ).is( ':checked' ) ) {
				scmCommand += " deploy";
			}

			$.extend( paramObject, {
				scmCommand: scmCommand
			} );
		}


		if ( $( "#deployServerServices input:checked" ).length > 0 ) {
			$( "#deployStart" ).prop( 'checked', false );
			// default params are used when multistarts

			delete paramObject.commandArguments;
			delete paramObject.runtime;
			delete paramObject.scmCommand;
			delete paramObject.mavenDeployArtifact;
			$.extend( paramObject, {
				mavenDeployArtifact: "default"
			} );

		}


		if ( $( "#isHotDeploy" ).is( ':checked' ) ) {
			$.extend( paramObject, {
				hotDeploy: "hotDeploy"
			} );
		}

		var buildUrl = serviceBaseUrl + "/rebuildServer";
		// buildUrl = "http://yourlb.yourcompany.com/admin/services" +
		// "/rebuildServer" ;
		$.post( buildUrl, paramObject )
				.done( function ( results ) {
					displayHostResults( hostName, deployServiceName, "Build Started", results, 0, 0 );

					// $("#resultPre div").first().show() ;
					$( "#resultPre div" ).first().css( "display", "block" );

					fileOffset = "-1";

					_deploySuccess = false;

					getUpdatedBuildOutput( deployServiceName );
				} )
				.fail( function ( jqXHR, textStatus, errorThrown ) {

					if ( deployServiceName.indexOf( "CsAgent" ) != -1 ) {
						alert( "CsAgent can get into race conditions...." );
						var numHosts = $( "#instanceTable *.selected" ).length;

						if ( numHosts > 1 && results.indexOf( "BUILD__SUCCESS" ) != -1 ) {
							isBuild = true; // rebuild autostarts
							startService();
						} else {
							$( 'body' ).css( 'cursor', 'default' );
						}
					} else {
						handleConnectionError( hostName + ":" + rebuild, errorThrown );
					}
				} );

	}



	var fileOffset = "-1";

	/** @memberOf ServiceAdmin */
	function getUpdatedBuildOutput( nameOfService ) {

		clearTimeout( checkForChangesTimer[nameOfService] );
		// $('#serviceOps').css("display", "inline-block") ;


		// console.log("Hitting Offset: " + fileOffset) ;
		var requestParms = {
			serviceName: nameOfService,
			hostName: hostName,
			logFileOffset: fileOffset
		};

		$.getJSON(
				deployProgressUrl,
				requestParms )

				.done( function ( hostJson ) {
					buildOutputSuccess( hostJson, nameOfService );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
				} );
	}


	var checkForChangesTimer = new Object();
	var warnRe = new RegExp( "warning", 'gi' );
	var errorRe = new RegExp( "error", 'gi' );
	var infoRe = new RegExp( "info", 'gi' );
	var debugRe = new RegExp( "debug", 'gi' );

	var winHackRegEx = new RegExp( "\r", 'g' );
	var newLineRegEx = new RegExp( "\n", 'g' );


	var refreshTimer = 2 * 1000;

	/** @memberOf ServiceAdmin */
	function  buildOutputSuccess( changesJson, nameOfService ) {
		
		//$resultsPre.parent().append( $("#resultsLoading") ) ;
		$("#resultsLoading").show()
		
		if ( changesJson.error || changesJson.contents == undefined ) {
			console.log( "No results found, rescheduling" );
			checkForChangesTimer[nameOfService] = setTimeout( function () {
				getUpdatedBuildOutput( nameOfService );
			},
					refreshTimer );
			return;
		}
		// $("#"+ hostName + "Result").append("<br>peter") ;
		// console.log( JSON.stringify( changesJson ) ) ;
		// console.log("Number of changes :" + changesJson.contents.length);

		for ( var i = 0; i < changesJson.contents.length; i++ ) {
			var fileChanges = changesJson.contents[i];
			var htmlFormated = fileChanges.replace( warnRe, '<span class="warn">WARNING</span>' );
			htmlFormated = htmlFormated.replace( errorRe, '<span class="error">ERROR</span>' );
			htmlFormated = htmlFormated.replace( debugRe, '<span class="debug">DEBUG</span>' );
			htmlFormated = htmlFormated.replace( infoRe, '<span class="info">INFO</span>' );
			htmlFormated = htmlFormated.replace( /\\n/g, "<br />" );
			// htmlFormated = htmlFormated.replace(winHackRegEx, '') ;
			// htmlFormated = htmlFormated.replace(newLineRegEx, '<br>') ;
			// displayResults( '<span class="chunk">' + htmlFormated +
			// "</span>", true);

			var $consoleOutput = $( "#" + nameOfService + "Output" );
			$consoleOutput.append( '<span class="chunk">' + htmlFormated + "</span>" );

			// TOKEN may get split in lines, so check text results for success and complete tokens
			var resultsSofar = $consoleOutput.text();
			if ( resultsSofar.length > 100 ) {
				resultsSofar = resultsSofar.substring( resultsSofar.length - 100 );
			}

			if ( fileChanges.contains( "BUILD__SUCCESS" ) || resultsSofar.contains( "BUILD__SUCCESS" ) ) {
				_deploySuccess = true;
			}
			
			if ( fileChanges.contains( "__COMPLETED__" ) ||
					resultsSofar.contains( "__COMPLETED__" ) ) {
				$("#resultsLoading").hide() ;
			
				if ( _deploySuccess ) {
					isBuild = true; // only going to restart the other
					// VMs

					$consoleOutput.hide();

					if ( $( "#deployStart" ).is( ':checked' ) ) {
						startService();
					} else {
						$resultsPre.append( '\n<img class="but" height="14"  src="images/16x16/note.png">'
								+ nameOfService
								+ " Deploy autostart is not selected. Service may now be started from console\n" );
					}

				} else {
					$resultsPre.append( '\n<img class="but" height="14"  src="images/16x16/warning.png">'
							+ nameOfService
							+ " Warning - did not find BUILD__SUCCESS in output\n" );
					// displayResults( "\n\n", true) ;
					// alertify.alert("Warning - did not find
					// BUILD__SUCCESS in
					// output") ;
				}
				showResultsDialog( "Updating" );
				return;
			}
		}
		showResultsDialog( "Updating" );
		maximizeResultsDialog();


		fileOffset = changesJson.newOffset;
		// $("#fileSize").html("File Size:" + changesJson.currLength) ;


		checkForChangesTimer[nameOfService] = setTimeout( function () {
			getUpdatedBuildOutput( nameOfService );
		}, refreshTimer );


	}




	/**
	 * 
	 * Ajax call to kill service
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function killService() {

		var paramObject = new Object();

		if ( $( "#isSuperClean" ).is( ':checked' ) ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;
			$.extend( paramObject, {
				clean: "super"
			} );
		} else {
			if ( $( "#isClean" ).is( ':checked' ) ) {
				// paramObject.push({noDeploy: "noDeploy"}) ;
				$.extend( paramObject, {
					clean: "clean"
				} );
			}
			if ( $( "#isSaveLogs" ).is( ':checked' ) ) {
				// paramObject.push({noDeploy: "noDeploy"}) ;
				$.extend( paramObject, {
					keepLogs: "keepLogs"
				} );
			}
		}


		var message = '<div class="myAlerts">' + serviceName + " is configured with warnings to prevent corruption caused by sending:\n\t kill -9";
		message += "<br><br>It is strongly recommended to issue a stop rather then a kill in order for graceful shutdown to occur.";
		message += "<br><br> Failing to stop the service gracefully may lead to corruption.";
		message += "<br><br> Click OK to proceed anyway, or cancel to use the stop button.</div>";

		if ( isShowWarning ) {

			var applyDialog = alertify.confirm( message );
			applyDialog.setting( {
				title: "Caution Advised",
				'labels': {
					ok: 'Proceed Anyway',
					cancel: 'Cancel request'
				},
				'onok': function () {
					executeOnSelectedHosts( "killServer", paramObject );
				},
				'oncancel': function () {
					alertify.warning( "Operation Cancelled" );
				}

			} );


		} else {
			executeOnSelectedHosts( "killServer", paramObject );
		}


	}


	var isBuild = false;

	/** @memberOf ServiceAdmin */
	function executeOnSelectedHosts( command, paramObject ) {


		// alert("numSelected: " + numHosts) ;
		// nothing to do on a single node build
		if ( !isBuild )
			$resultsPre.html( "" );

		$resultsPre.append( command + " Request initiated\n" );
		$( 'body' ).css( 'cursor', 'wait' );

		var numResults = 0;

		// Now run through the additional hosts selected
		var postCommandToServerFunction = function ( serviceInstance, serviceHost ) {

			var hostParamObject = new Object();

			if ( $( "#isHotDeploy" ).is( ':checked' ) ) {
				$.extend( paramObject, {
					hotDeploy: "hotDeploy"
				} );
			}

			$.extend( hostParamObject, paramObject, {
				hostName: serviceHost,
				serviceName: serviceInstance
			} );

			$.post( serviceBaseUrl + "/" + command, hostParamObject, totalCommandsToRun )
					.done(
							function ( results ) {
								// displayResults(results);
								numResults++;
								displayHostResults( serviceHost, serviceInstance, command, results, numResults, totalCommandsToRun );
								isBuild = false;
								// console.log("numResults: " +
								// numResults + "
								// numHosts:" + numHosts) ;

								if ( numResults >= totalCommandsToRun ) {
									$( 'body' ).css( 'cursor', 'default' );

									refreshServiceData();
								}
							} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {
						//console.log( JSON.stringify( jqXHR, null, "\t" ));
						//console.log( JSON.stringify( errorThrown, null, "\t" ));

						handleConnectionError( serviceHost + ":" + command, errorThrown );
					} );


		}
		// 
		var $multiServiceContainer = $( "#" + command + "Services" );
		var numServices = $( "input:checked", $multiServiceContainer ).length;
		if ( numServices == 0 ) {

			var totalCommandsToRun = $( "#instanceTable *.selected" ).length;
			$( "#instanceTable *.selected" ).each( function () {
				var serviceNamePort = $( this ).data( "instance" );
				var serviceHost = $( this ).data( "host" );
				postCommandToServerFunction( serviceNamePort, serviceHost, totalCommandsToRun );
			} );
		} else {

			var totalCommandsToRun = $( "#instanceTable *.selected" ).length * numServices;
			$( "#instanceTable *.selected" ).each( function () {
				var serviceHost = $( this ).data( "host" );
				$( "input:checked", $multiServiceContainer ).each( function () {
					var serviceMulti = $( this ).attr( "name" );
					// default params are used when multistarts
					delete paramObject.runtime;
					delete paramObject.commandArguments;
					postCommandToServerFunction( serviceMulti, serviceHost, totalCommandsToRun );
				} );
			} );
		}
	}

	var t1 = null, t2 = null, t3 = null, t4 = null;

	/** @memberOf ServiceAdmin */
	function refreshServiceData() {
		// alertify.notify("Refreshing Data - Note that Sockets and Disk are
		// only
		// checked every few minutes due to OS cost. Use CSAP dashboard for
		// direct
		// access") ;
		clearTimeout( t1 );
		clearTimeout( t2 );
		clearTimeout( t3 );
		clearTimeout( t4 );
		t1 = setTimeout( function () {
			serviceInstancesGet( true );
		}, 1 * 1000 );
		t2 = setTimeout( function () {
			serviceInstancesGet( true );
		}, 10 * 1000 );
		t3 = setTimeout( function () {
			serviceInstancesGet( true );
		}, 20 * 1000 );
		t4 = setTimeout( function () {
			serviceInstancesGet( true );
		}, 30 * 1000 );

	}

	/**
	 * 
	 * Ajax call to stop service
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function stopService() {


		var paramObject = {
			serviceName: serviceName
		};

		executeOnSelectedHosts( "stopServer", paramObject );

	}

	/**
	 * 
	 * Ajax call to stop service
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function showHostMessageDialog() {

		var message = "<div style='margin: 2em'>Enter the updated message of the day:<div>";
		var motdDialog = alertify.prompt( message, $( "#motd" ).text() );
		motdDialog.setting( {
			title: "Message Of the Day",
			'labels': {
				ok: 'Change Message',
				cancel: 'Cancel'
			},
			'onok': function ( evt, value ) {
				var paramObject = {
					motd: value,
					hostName: hostName
				};

				$.post( serviceBaseUrl + "/updateMotd", paramObject,
						function ( results ) {

							displayResults( results );

						} );
			},
			'oncancel': function () {
				alertify.warning( "Update cancelled" );
			}

		} );


	}

	/**
	 * 
	 * Ajax call to stop service
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function unDeployService() {

		$resultsPre.html( "Request is being processed\n" );
		$( 'body' ).css( 'cursor', 'wait' );
		var paramObject = {
			serviceName: serviceName,
			warSelect: $( "#undeploySelect" ).val()
		};


		executeOnSelectedHosts( "undeploy", paramObject );


		$( '#undeploySelect' ).val( "select" );

//		  $.post( serviceBaseUrl + "/undeploy", paramObject,
//					 function (results) {
//
//						  displayResults( results );
//
//					 } );

	}


	/** @memberOf ServiceAdmin */
	function launchHelp( helpButtonObj ) {

		var url = helpButtonObj.attr( "href" );

		if ( url == "" ) {
			alertMessage = "No help url configured for service. Request service team contact cluster admin with help url.";
			alertify.alert( alertMessage );
			return;
		}

		openWindowSafely( url, serviceName + "Help" );
	}


	/**
	 * Context menu also launches urls - but only the default LB. This
	 * function enables user to select different LBs. Eg. the embedded http
	 * in tomcat, or via different httpd apache configurations.
	 * 
	 * @param buttonObject
	 */

	/** @memberOf ServiceAdmin */
	function launchService( buttonObject ) {

		var buttonUrl = buttonObject.attr( 'href' );
		console.log( " buttonUrl: " + buttonUrl )
		if ( buttonUrl == "default" ) {
			// use default launch code
			launchDefaultService( $( "#instanceTable *.selected" ) );
			return
		}

		var jqueryRows = $( "#instanceTable *.selected" );
		if ( !confirmMaxItem( jqueryRows ) )
			return;

		jqueryRows.each( function ( index ) {

			var host = $( this ).data( "host" );
			var port = $( this ).data( "port" );
			var context = $( this ).data( "context" );

			var urlAction = buttonUrl + "/" + context;
			if ( context.charAt( serviceContext.length - 1 ) != "/" )
				urlAction += "/";

			openWindowSafely( urlAction, context + host + "Launch" );
		} );

	}



	/**
	 * 
	 * Ajax call to trigger jmeter
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function launchJMeter() {

		_resultsDialog=null ;
		showResultsDialog( "Jmeter launch" );
		var message = "\nRequest is being processed\n";
		displayResults( message, false );
		$( 'body' ).css( 'cursor', 'wait' );
		var paramObject = {
			serviceName: serviceName,
			hostName: hostName
		};

		$.post( serviceBaseUrl + "/jmeter", paramObject,
				function ( results ) {
					displayResults( results );
					showResultsDialog();
				} );

	}



	/**
	 * 
	 * Ajax call to clean build and maven caches
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function cleanServiceBuild( isGlobal ) {

		displayResults( "Cleaning service sources files" );
		$( 'body' ).css( 'cursor', 'wait' );
		var paramObject = {
			serviceName: serviceName,
			hostName: hostName
		};

		if ( isGlobal ) {
			$.extend( paramObject, {
				global: "GLOBAL"
			} );
		}

		$.post( serviceBaseUrl + "/purgeDeployCache", paramObject,
				function ( results ) {

					displayResults( results, false );

				} );

	}

	/**
	 * 
	 * This sends an ajax http get to server to start the service
	 * 
	 */

	/** @memberOf ServiceAdmin */
	function startService() {


		var paramObject = new Object();

		if ( $( "#noDeploy" ).is( ':checked' ) ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;
			$.extend( paramObject, {
				noDeploy: "noDeploy"
			} );
		}

		if ( $( "#isDebug" ).is( ':checked' )
				&& $serviceParameters.val().indexOf( "agentlib" ) == -1 ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;

			$serviceParameters.val( $serviceParameters.val()
					+ " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + debugPort );

		}

		if ( $( "#isJmc" ).is( ':checked' )
				&& $serviceParameters.val().indexOf( "FlightRecorder" ) == -1 ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;

			$serviceParameters.val( $serviceParameters.val()
					+ " -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints" );

		}


		if ( $( "#isGarbageLogs" ).is( ':checked' )
				&& $serviceParameters.val().indexOf( "PrintGCDetails" ) == -1 ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;

			$serviceParameters.val( $serviceParameters.val()
					+ gcParams );

		}
		if ( $( "#isInlineGarbageLogs" ).is( ':checked' )
				&& $serviceParameters.val().indexOf( "PrintGCDetails" ) == -1 ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;

			$serviceParameters.val( $serviceParameters.val()
					+ gcInlineParams );

		}

		$.extend( paramObject, {
			commandArguments: $serviceParameters.val(),
			runtime: $( "#runtimeSelect" ).val()
		} );

		if ( $serviceParameters.val().indexOf( "agentlib" ) != -1 ) {
			alertify.alert( 'Service started in debug mode, configure your ide with host: <div class="note">' + hostName
					+ '</div> debug port: <div class="note">' + debugPort
					+ '</div><br><br> Jvm started using options: <br><div class="note">' + $serviceParameters.val() + '</div>' );
			$( ".alertify-inner" ).css( "text-align", "left" );
			$( ".alertify" ).css( "width", "800px" );
			$( ".alertify" ).css( "margin-left", "-400px" );
		}


		executeOnSelectedHosts( "startServer", paramObject );

	}

	var gcParams = " -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:logs/garbageCollect.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=2 -XX:GCLogFileSize=5M ";
	var gcInlineParams = " -XX:+PrintGCDetails -XX:+PrintGCDateStamps";


	/** @memberOf ServiceAdmin */
	function uploadWar() {
		_resultsDialog=null ;
		showResultsDialog( "Uploading artifact: " + $( "#uploadOptions :file" ).val() );


		displayResults( "" );
		$resultsPre.append( '<div class="progress"><div class="bar"></div ><div class="percent">0%</div ></div>' );

		$( "#upService" ).val( serviceName );
		// <input type="hidden " name="hostName" value="" />
		$( "#upHosts" ).empty();
		$( "#instanceTable *.selected" ).each( function () {

			var reqHost = $( this ).data( "host" ); // (this).data("host")
			$( "#upHosts" ).append( '<input type="hidden" name="hostName" value="' + reqHost + '" />' );
// $.extend(formParams, {
// hostName : reqHost
// });
		} );

		var bar = $( '.bar' );
		var percent = $( '.percent' );
		var status = $( '#status' );

		var formOptions = {
			beforeSend: function () {
				$( 'body' ).css( 'cursor', 'wait' );

				status.empty();
				var percentVal = '0%';
				bar.width( percentVal );
				percent.html( "Upload Progress: " + percentVal );

			},
			uploadProgress: function ( event, position, total, percentComplete ) {
				var percentVal = percentComplete + '%';
				bar.width( percentVal );
				percent.html( "Upload Progress: " + percentVal );
			},
			success: function () {
				var percentVal = '100%';
				bar.width( percentVal );
				percent.html( "Upload Progress: " + percentVal );

				$( ".progress" ).hide();
			},
			complete: function ( xhr ) {
				var percentVal = '100%';
				bar.width( percentVal );
				percent.html( "Upload Progress: " + percentVal );
				$( ".progress" ).hide();
				// status.html(xhr.responseText);
				// $("#resultPre").html( xhr.responseText ) ;
				displayResults( xhr.responseText );
			}
		};

		$( '#uploadOptions form' ).ajaxSubmit( formOptions );



	}


	/** @memberOf ServiceAdmin */
	function toggleResultsButton( toggleButton ) {

		// $("#resultPre").css('height', 'auto');

		alertify.success( "toggleResults" );
	}


	var serviceTimer = 0;


	/** @memberOf ServiceAdmin */
	function serviceInstancesGet( blocking ) {

		clearTimeout( serviceTimer );

		var serviceNameStrippedOfPort = serviceName.substr( 0, serviceName
				.indexOf( "_" ) );

		$.getJSON(
				serviceBaseUrl + "/getServiceInstances",
				{
					"serviceName": serviceNameStrippedOfPort,
					"blocking": blocking,
					"releasePackage": releasePackage
				} )
				.done(
						function ( loadJson ) {

							serviceInstanceSuccess( loadJson );
							if ( blocking ) {
								setTimeout( function () {
									$( 'body, a' ).css( 'cursor', 'default' );
									getLatestServiceStats();
								}, 2000 );
							}
							serviceTimer = setTimeout( function () {
								serviceInstancesGet( false );
							}, 60000 );

						} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving service instances", errorThrown );
				} );
	}

	var hostInit = false; // hostSelect is stateful, only inited once

	/** @memberOf ServiceAdmin */
	function serviceInstanceSuccess( serviceInstancesJson ) {
		var tableHtml = "";
		// for (var serviceInstance in loadJson.instances ) {
		// alert( loadJson.instances.length ) ;

		$( "#primaryHost" ).html( hostName );
		// $( "#primaryInstance" ).html( serviceShortName );
		$( "#instanceTableDiv" ).show();

		serviceInstancesJson.serviceStatus.sort( function ( a, b ) {

			if ( a.host < b.host )
				return -1;
			else
				return 1;


		} );


		var $instanceTableBody = $( "#instanceTable tbody" );

		$( 'tr input', $instanceTableBody ).unbind( 'click' );
		$( 'tr input', $instanceTableBody ).unbind( 'hover' );


		$( "#undeploySelect" ).empty();
		var optionItem = jQuery( '<option/>', {
			value: "select",
			text: "Select Version to remove"
		} );
		$( "#undeploySelect" ).append( optionItem );


		for ( var i = 0; i < serviceInstancesJson.serviceStatus.length; i++ ) {
			var serviceOnHost = serviceInstancesJson.serviceStatus[i];

			$instanceTableBody.append(
					buildServiceInstanceRow( serviceOnHost )
					);

		}

		checkForServiceWarnings( serviceInstancesJson );

		hostInit = true;

// $('#instanceTable tbody tr').each( function( index ) {
// registerRowEvents( $(this)); }) ;


		$( '#instanceTable tbody tr input' ).click( function () {
			if ( $( this ).prop( 'checked' ) == true ) {
				$( this ).parent().parent().addClass( "selected" );
			} else {
				$( this ).parent().parent().removeClass( "selected" );
			}
		} );
		// alert("setting selected");
		$( currentInstance ).addClass( "selected" );
		$( '#instanceTable tbody tr.selected input' ).prop( 'checked', true );
		$( '#instanceTable tbody tr' ).hover( function () {
			lastInstanceRollOver = $( this );
		} );

		$( 'input', currentInstance ).each( function ( index ) {
			$( this ).prop( 'disabled', true );
			$( this ).prop( 'title', "Click another service to make it the master" );
		} );

		$( '#instanceTable .simple' ).click( function () {

			var rowObject = $( this ).parent().parent();
			var host = rowObject.data( 'host' );
			var serviceName = rowObject.data( 'instance' );

			var url = "admin?serviceName="
					+ serviceName
					+ "&hostName="
					+ host
					+ "&releasePackage="
					+ releasePackage;
			document.location.href = url;

			return false; // prevents link
		} );
	}

	var _isShowServiceWarningOnce = true;

	/** @memberOf ServiceAdmin */
	function checkForServiceWarnings( serviceInstancesJson ) {


		if ( isScript ) {
			$( "#serviceCpu" ).hide();
			$( "#osChart" ).hide();
			$( "#osLearnMore" ).show();




		} else if ( isOs ) {
			$( "#opsButtons" ).html( '<div class="note">Server type is OS, operations may be executed via the VM Admin.</div>' );
			$( "#meters" ).hide();
		} else if ( _isShowServiceWarningOnce ) {

			_isShowServiceWarningOnce = false;

			showTechnologyScore( serviceInstancesJson );

			var limitsExceeded = "";
			var maxDiskInMb = resourceLimits[ "diskUtil" ];
			for ( var i = 0; i < serviceInstancesJson.serviceStatus.length; i++ ) {
				var serviceInstance = serviceInstancesJson.serviceStatus[i];

				if ( parseInt( serviceInstance.diskUtil ) > maxDiskInMb ) {
					limitsExceeded += " " + serviceInstance.host + ": " + serviceInstance.diskUtil + " MB";
				}
			}


			if ( limitsExceeded != "" ) {
				// console.log("serviceInstance.diskUtil: " +
				// serviceInstance.diskUtil +
				// " maxDisk " + maxDisk) ;
				var $warnContainer = jQuery( '<div/>', { } );
				var $warning = jQuery( '<div/>', { } )
						.css( "font-size", "0.8em" )
						.appendTo( $warnContainer );


				$warning.append( '<img style="vertical-align: middle" src="images/error.gif">' );
				$warning.append( 'One or more of the ' + serviceShortName
						+ ' instances exceeds disk limit of: ' + maxDiskInMb + " MB<br>" );

				if ( svcUser == "null" ) {
					$warning.append( '<div class="error" style="text-align: left"> Run away disk usage may '
							+ 'trigger a cascading failure of other services. Auto shutdown will be initiated'
							+ ' once breach threshold is exceeded on the following:<br>' + limitsExceeded
							+ ' </div>' );
				} else {

					$warning.append( '<div class="error" style="text-align: left"> This could case VM disk to fill causing service interruption: <br>'
							+ limitsExceeded + ' </div>' );
				}
				$warning.append( '<br>Verify application is working, log rotations configured, etc. If neededed, update the limits, or kill/clean the service. ' );

				$warning.append( jQuery( '<a/>', {
					class: "simple",
					title: "Click to learn more about monitoring configuration",
					target: "_blank",
					href: "https://github.com/csap-platform/csap-core/wiki#updateRefCS-AP+Monitoring",
					text: "Visit CS-AP Monitoring"
				} ).css( "display", "inline" ) );

				alertify.csapWarning( $warnContainer.html() );



			}
		}
	}

	function addTechnologyError( description ) {

		console.log( "Adding: " + description );

		$( ".eolItems" ).append( jQuery( '<div/>', {
			class: "noteHighlight",
			text: description
		} ) );
	}

	function showTechnologyScore( serviceInstancesJson ) {
		var numEol = 0;

		if ( customMetrics == null ) {
			numEol += 2;
			addTechnologyError( "Performance Metrics Not Configured" );
		}

		if ( (!isSkipJmx) && jvms.serviceSelected.indexOf( "7" ) != -1 ) {
			numEol += 2;
			addTechnologyError( "Java 7" );
		}
		if ( serverType.contains( "cssp-" ) ) {
			numEol += 3;
		}
		//if ( serverType.contains( "cssp-" ) || serverType == "tomcat7.x"|| serverType == "tomcat8.x" ) {
		if ( isTomcatEol ) {
			numEol += 2;
			addTechnologyError( serverType );
		}

		for ( var i = 0; i < serviceInstancesJson.serviceStatus.length; i++ ) {
			var serviceInstance = serviceInstancesJson.serviceStatus[i];
			if ( serviceInstance.host == hostName && serviceInstance.port == httpPort ) {

				if ( serviceInstance.eolJars && serviceInstance.eolJars.length > 0 ) {
					numEol += serviceInstance.eolJars.length;
					addTechnologyError( JSON.stringify( serviceInstance.eolJars, null, "\t" ) );

				}
				break;
			}
		}

		var $statusButton = $( "#eolWarningButton" );
		var $statusImages = $( "span", $statusButton );

		if ( numEol >= 4 ) {
			$statusButton.show().click( function () {
				alertify.csapWarning( $( "#eolWarningsMessage" ).html() );
			} );
			$statusButton.removeClass( "ok" ).addClass( "warning" );
			$( "span", $statusButton ).text( "Warnings" );
			jQuery( '<img/>', {
				src: baseUrl + "images/16x16/warning.png"
			} ).appendTo( $( "span", $statusButton ) );
			$( "span", $statusButton ).addClass( "attention" );
		} else if ( numEol > 0 ) {
			var maxStars = $( "img", $statusImages ).size();
			console.log( "maxStars: " + maxStars );

			if ( numEol > maxStars )
				numEol = maxStars;

			for ( var i = 0; i < numEol; i++ ) {
				console.log( "updating: " + i );

				$( ":nth-child(" + (maxStars - i) + ")", $statusImages ).attr( "src", baseUrl + "images/starBlack.png" );
			}
			//$statusImages.empty() ;
			// $( "#eolSoftware" ).show();
			$statusButton.show().click( function () {
				alertify.csapWarning( $( "#eolWarningsMessage" ).html() );
			} );
			$statusButton.removeClass( "ok" ).addClass( "warning" );
			// var
		} else {
			$statusButton.show();
			$statusButton.removeClass( "warning" ).addClass( "ok" );
		}
	}


	/** @memberOf ServiceAdmin */
	function buildServiceInstanceRow( serviceInstance ) {

		var instancePort = serviceInstance.serviceName
				+ "_" + serviceInstance.port;

		var rowClass = "";

		var currentId = serviceInstance.host + "_" + instancePort;
		if ( $( "#" + currentId ).first().hasClass( "selected" ) ) {
			rowClass = "selected";
		}

		$( "#" + currentId ).remove(); // get rid of previous

		if ( instancePort == serviceName
				&& serviceInstance.host == hostName ) {
			updateUIForInstance( serviceInstance );
		}

		var $instanceRow = jQuery( '<tr/>', {
			id: currentId,
			class: rowClass,
			title: serviceInstance.scmVersion,
			"data-host": serviceInstance.host,
			"data-instance": instancePort,
			"data-context": serviceInstance.context,
			"data-launchurl": serviceInstance.launchUrl,
			"data-jmx": serviceInstance.jmx,
			"data-jmxrmi": serviceInstance.jmxrmi,
			"data-port": serviceInstance.port

		} );

//		$instanceRow.qtip( {
//			content: {
//				attr: 'title',
//				title: serviceInstance.serviceName + " Deployment Status",
//				button: 'Close'
//			},
//			style: {
//				classes: 'qtip-bootstrap versionTip'
//			},
//			position: {
//				my: 'bottom left', // Position my top left...
//				at: 'bottom right',
//				adjust: {
//					x: +5
//				}
//			}
//		} );
//		.qtip( {
//			content: {
//				attr: 'title',
//				button: true
//			},
//			style: {
//				classes: 'qtip-bootstrap'
//			},
//			position: {
//				my: 'bottom left',
//				at: 'bottom right'
//			}
//		} );

		var $checkCol = jQuery( '<td/>', { } );
		$instanceRow.append( $checkCol );

		var $instanceCheck = jQuery( '<input/>', {
			class: "instanceCheck",
			type: "checkbox",
			title: "Select to include in operations"
		} );

		$instanceCheck.change( function () {

			serviceGraph.updateHosts();
			getLatestServiceStats();
			// seems to be a race
		} );


		$instanceCheck.css( "margin-right", "0.5em" );
		$checkCol.append( $instanceCheck );

		$checkCol.append( jQuery( '<a/>', {
			class: "simple",
			title: "Click to switch primary host",
			href: "#switchPrimary",
			text: serviceInstance.host
		} ).css( "display", "inline" ) );
		// + ":" + serviceInstance.port

		if ( serviceInstance.port != 0 ) {
			$checkCol.append( jQuery( '<span/>', {
				text: serviceInstance.port
			} ) );
		}


		var lc = serviceInstance.lc;
		if ( lc && lc.indexOf( "-" ) != -1 )
			lc = lc.substr( lc.indexOf( "-" ) + 1 );

		$instanceRow.append( jQuery( '<td/>', { text: lc } ) );


		if ( !hostInit ) {
			var found = false;
			$( "#lcSelect option" ).each( function () {
				if ( $( this ).text() == lc )
					found = true;
			} );

			if ( !found ) {
				$( "#lcSelect" ).append( '<option value="' + lc + '">' + lc + '</option>' );
			}
		}

		var regex = new RegExp( ".*##", "g" );
		var ver = serviceInstance.deployedArtifacts;

		// alert(ver) ;
		if ( serviceInstance.deployedArtifacts ) {

			ver = "";
			var verArray = serviceInstance.deployedArtifacts
					.split( ',' );
			for ( var j = 0; j < verArray.length; j++ ) {


				if ( serviceInstance.host == hostName ) {
					//console.log("Adding: " + verArray[j]) ;
					optionItem = jQuery( '<option/>', {
						value: verArray[j],
						text: verArray[j]
					} );
					$( "#undeploySelect" ).append( optionItem );
				}


				if ( ver != "" )
					ver += ", ";
				ver += verArray[j].replace( regex, "" );
				// ver += verArray[j] + " ";
			}

			if ( serviceInstance.scmVersion.indexOf( "Custom artifact uploaded" ) != -1 )
				ver += "upload";
		}


		$instanceRow.append( jQuery( '<td/>', { text: ver } ) );

		if ( serviceInstance.cpuUtil ) {


			$instanceRow.append( jQuery( '<td/>', {
				text: serviceInstance.cpuLoad + ' / ' + serviceInstance.cpuCount
			} ) );



			var $cpuCol = jQuery( '<td/>', { } );
			$instanceRow.append( $cpuCol );

			if ( serviceInstance.cpuUtil == "-" ) {
				if ( serviceInstance.serverType == "script" ) {
					$cpuCol.append( jQuery( '<span/>', {
						text: 'script'
					} ) ).css( "font-size", "0.8em" );

				} else {
					$cpuCol.append( jQuery( '<img/>', {
						src: "images/16x16/process-stop.png"
					} ).css( "height", "12px" ) );

				}
			} else {
				var cpu = serviceInstance.cpuUtil;

				if ( cpu == "AUTO" ) {

					$cpuCol.append( jQuery( '<img/>', {
						src: "images/32x32/appointment-new.png"
					} ).css( "height", "12px" ) );

				} else if ( cpu == "CLEAN" ) {
					$cpuCol.append( jQuery( '<img/>', {
						src: "images/16x16/clean.png"
					} ).css( "height", "12px" ) );

				} else {
					if ( cpu.length > 4 )
						cpu = cpu.substr( 0, 4 );
					$cpuCol.text( cpu );
				}
			}
			var disk = serviceInstance.diskUtil;
			if ( disk > 1000 ) {
				disk = (disk / 1000).toFixed( 1 ) + " Gb"
			} else {
				disk += " Mb"
			}
			$instanceRow.append( jQuery( '<td/>', { text: disk } ) );

		} else {

			// Service is not active - use placeholders
			$instanceRow.append( jQuery( '<td/>', { text: "-" } ) );
			$instanceRow.append( jQuery( '<td/>', { text: "-" } ) );
			$instanceRow.append( jQuery( '<td/>', { text: "-" } ) );

		}

		return $instanceRow;

	}


	var _isServiceActive = false;
	/** @memberOf ServiceAdmin */
	function updateUIForInstance( serviceInstance ) {

		$( "#motd" ).html( serviceInstance.motd );

		$( "#lastOp" ).html( serviceInstance.lastOp );
		$( "#users" ).html(
				"Users: " + serviceInstance.users );
		// alert( Number(serviceInstance.cpuCount) + 1) ;
// var cpuCountInt = Number( serviceInstance.cpuCount );
// $( "#cpuCount" ).html(
// Number( serviceInstance.cpuCount ) );
//
// var cpuLoadFloat = parseFloat( Number( serviceInstance.cpuLoad ) );
// $( "#cpuLoad" ).html( cpuLoadFloat );
// if ( cpuLoadFloat >= cpuCountInt ) {
// $( "#cpuLoad" )
// .html(
// cpuLoadFloat
// + '<img class="but" height="14" src="images/16x16/warning.png">' );
// }
//
// $( "#du" ).html( serviceInstance.du );
// if ( serviceInstance.du >= 80 ) {
// $( "#du" ).html(
// '<img class="but" height="14" src="images/16x16/warning.png">' +
// serviceInstance.du );
// }
//
//
// if ( serviceInstance.diskUtil != null ) {
// $( "#serviceDisk" ).html(
// '<span style="font-weight:bold">disk: </span>'
// + serviceInstance.diskUtil
// + "M" );
// if ( serviceInstance.diskUtil == "-1" ) {
// $( "#serviceDisk" )
// .html(
// '<span style="font-weight:bold">disk: </span>...' );
// ;
// }
// }
//
		_isServiceActive = serviceInstance.cpuUtil != null && serviceInstance.cpuUtil != "-";

		if ( !_isServiceActive ) {
			$( "#serviceCpu" )
					.html(
							'<img src="images/16x16/process-stop.png">'
							+ '<span style="font-weight:bold">Stopped </span>' );
		} else {
			$( "#serviceCpu" )
					.html(
							'<img src="images/16x16/ok.png">'
							+ '<span style="font-weight:bold">Relative Cpu: </span>'
							+ serviceInstance.topCpu + "%" );
		}



		$( "#serviceVersion" ).html( serviceInstance.scmVersion );
		$( "#serviceDate" ).html(
				'<span style="font-weight:bold">Date: </span>'
				+ serviceInstance.warDate );
	}


} );