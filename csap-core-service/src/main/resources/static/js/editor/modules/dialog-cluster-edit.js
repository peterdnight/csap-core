define( ["./validation-handler", "./json-forms"], function ( validationHandler, jsonForms ) {

	console.log( "Module loaded" );

	var _editPanel = "#clusterEditor";
	var _container = window;
	var _definitionTextAreaId = "#serviceJson";

	var _settingsJson = null;
	var _isJsonEditorActive = false;

	var _currentDialog = null;

	var _dialogId = "clusterEditorDialog";

	var _refreshLifecycleFunction = null;
	var _defaultCluster = {
		"osProcessesList": [],
		"jvmPorts": { },
		"multiVm": {
			"1": {
				"hosts": []
			}
		}
	}


	return {
		setRefresh: function ( refreshFunction ) {
			_refreshLifecycleFunction = refreshFunction;
		},
		//
		show: function ( editDialogHtml ) {
			//when testing standalone, tests without
			if ( !jsonForms.isDialogBuilt( _dialogId ) ) {
				// do it only once.
				_container = ".ajs-dialog "
				_editPanel = _container + _editPanel;
				_definitionTextAreaId = _container + _definitionTextAreaId;
			}

			return showDialog( editDialogHtml );
		},
		getClusterDefinition: function () {
			getClusterDefinition();
		},
		registerDialogButtons: function () {
			registerInputEvents();
		},
		updateClusterDefinition: function () {
			updateClusterDefinition( true );
		},
		validateClusterDefinition: function () {
			updateClusterDefinition( false );
		},
		configureForTest: function () {

			getClusterDefinition(
					$( ".releasePackage" ).val(),
					$( ".lifeSelection" ).val(),
					$( "#dialogClusterSelect" ).val() );
			registerUiComponents();

			jsonForms.registerOperations( updateClusterDefinition );
		}
	}



	function registerUiComponents() {

		console.log( "registerUiComponents(): registering tab events" );
		$( _editPanel ).tabs( {
			beforeActivate: function ( event, ui ) {

				if ( ui.oldTab.text().indexOf( "Editor" ) != -1 ) {
					// refresh ui with edit changes.
					console.log( "registerUiComponents():  parsing serviceJson" );
					_settingsJson = JSON.parse( $( _definitionTextAreaId ).val() );
					getClusterDefnSuccess( _settingsJson );
				}

			},
			activate: function ( event, ui ) {
				console.log( "registerUiComponents(): activating: " + ui.newTab.text() );

				_isJsonEditorActive = false;
				if ( ui.newTab.text().indexOf( "Editor" ) != -1 ) {
					_isJsonEditorActive = true;
					$( _definitionTextAreaId ).val( JSON.stringify( _settingsJson, null, "\t" ) );
					jsonForms.resizeLinedText( $( _definitionTextAreaId ), _container, _editPanel );
				}

				if ( ui.newTab.text().indexOf( "Performance" ) != -1 ) {
					populatePerformanceTable();
				}

				jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );

			}
		} );

		$( "select.dialogClusterSelect" ).sortSelect();
		$( "select.dialogClusterSelect" ).selectmenu( {
			width: "200px",
			change: function () {
				console.log( "registerUiComponents editing env: " + $( this ).val() );
				getClusterDefinition( $( ".releasePackage" ).val(), $( "#lifeEdit" ).val(), $( this ).val() );
			}
		} );

		$( ".jeeAddSelect" ).sortSelect();
		$( ".jeeAddSelect" ).selectmenu( {
			width: "20em",
			change: function () {
				var serviceName = $( this ).val();
				var port = $( 'option:selected', $( this ) ).data( "port" );
				console.log( "registerUiComponents adding service: " + serviceName + " port: " + port );
				if ( !_settingsJson.jvmPorts )
					_settingsJson.jvmPorts = new Object();

				if ( _settingsJson.jvmPorts[ serviceName ] != undefined ) {
					alertify.alert( "Service is already in the cluster: " + serviceName );
					return;
				}

				_settingsJson.jvmPorts[ serviceName ] = new Array();
				_settingsJson.jvmPorts[ serviceName ].push( port );
				jsonForms.loadValues( _editPanel, _settingsJson );
				if ( serviceName != "default" ) {
					$( this ).val( "default" ).selectmenu( 'refresh' );
				}
			}
		} );
		$( ".osAddSelect" ).sortSelect();
		$( ".osAddSelect" ).selectmenu( {
			width: "20em",
			change: function () {
				var serviceName = $( this ).val();
				console.log( "registerUiComponents adding service: " + serviceName );
				if ( !_settingsJson.osProcessesList )
					_settingsJson.osProcessesList = new Array();

				if ( $.inArray( serviceName, _settingsJson.osProcessesList ) != -1 ) {
					alertify.alert( "Service is already in the cluster: " + serviceName );
					return;
				}

				_settingsJson.osProcessesList.push( serviceName );


				jsonForms.loadValues( _editPanel, _settingsJson );
				if ( serviceName != "default" ) {
					$( this ).val( "default" ).selectmenu( 'refresh' );
				}
			}
		} );

		$( ".addHostButton" ).click( function () {

			var message = 'Enter the host name: ';

			if ( addHostUrl.contains( "http" ) ) {
				message += '<a id="hostCatalog" href="' + addHostUrl
						+ '" target="_blank" class="simple" ><img src="' + baseUrl + '/images/16x16/document-new.png">Order</a>';
			}
			alertify.prompt( "New Host", message, "newHostName",
					function ( evt, newHostName ) {

						var hostPath = $( "#hostText" ).data( "path" );
						console.log( 'newHostName: ' + newHostName + " updating path: " + hostPath );

						var currentHosts = jsonForms.getValue( hostPath, _settingsJson );

						if ( $.inArray( newHostName, currentHosts ) != -1 ) {
							alertify.alert( "Host is already in the cluster: " + newHostName );
							return;
						}

						currentHosts.push( newHostName );

						jsonForms.loadValues( _editPanel, _settingsJson );

					},
					function () {
						console.log( "canceled" );
					}
			);
		} );

		$( ".hostAddSelect" ).sortSelect();
		$( ".hostAddSelect" ).selectmenu( {
			width: "20em",
			change: function () {
				var newHostName = $( this ).val();
				var hostPath = $( "#hostText" ).data( "path" );
				console.log( 'newHostName: ' + newHostName + " updating path: " + hostPath );

				var currentHosts = jsonForms.getValue( hostPath, _settingsJson );

				if ( $.inArray( newHostName, currentHosts ) != -1 ) {
					alertify.alert( "Host is already in the cluster: " + newHostName );
					return;
				}

				currentHosts.push( newHostName );


				jsonForms.loadValues( _editPanel, _settingsJson );
				if ( newHostName != "default" ) {
					$( this ).val( "default" ).selectmenu( 'refresh' );
				}
			}
		} );


		registerInputEvents();

		$( _editPanel ).show();

	}


	function showDialog( editDialogHtml ) {
		jsonForms.showDialog(
				_dialogId,
				"Cluster Configuration",
				editDialogHtml,
				updateClusterDefinition );

		registerUiComponents();

		var isAddMode = $( ".addDefButton" ).is( ":visible" );
		if ( !isAddMode ) {
			getClusterDefinition(
					$( ".releasePackage" ).val(),
					$( ".lifeSelection" ).val(),
					$( "#dialogClusterSelect" ).val() );
		} else {
			$( ".serviceLoading" ).hide();
			//getServiceDefinitionSuccess( _defaultService );
			getClusterDefnSuccess( _defaultCluster );
		}


	}


	function getClusterDefinition( releasePackage, lifeToEdit, clusterName ) {

		console.log( "getClusterDefinition() : " + clusterDefUrl + " lifeToEdit: " + lifeToEdit );
//	if ( $( '#serviceJson' ).val() != "loading..." )
//		return;
		//$( ".loading" ).html( "Retrieving capability definition from server" ).show();
		$( ".clusterLoading" ).show();
		$.getJSON( clusterDefUrl, {
			releasePackage: releasePackage,
			lifeToEdit: lifeToEdit,
			clusterName: clusterName
		} )

				.done( function ( serviceJson ) {
					getClusterDefnSuccess( serviceJson );
				} )

				.fail(
						function ( jqXHR, textStatus, errorThrown ) {

							handleConnectionError( "getClusterDefinition ", errorThrown );
						} );
	}

	function getClusterDefnSuccess( clusterDefJson ) {
		_settingsJson = clusterDefJson;

		if ( _settingsJson.lastModifiedBy == undefined ) {
			$( ".lastModified" ).hide();
		} else {
			$( ".lastModified .noteAlt" ).text( _settingsJson.lastModifiedBy );
		}

		$( _definitionTextAreaId ).val( JSON.stringify( clusterDefJson, null, "\t" ) );

		// update form values

		// hook for partitioning styles supported
		var partitionType = "multiVm";
		if ( clusterDefJson.version ) {
			partitionType = "version";
			alertify.alert( "Invalid cluster type - update to correct setting"  );
		}
		if ( clusterDefJson.singleVmPartition )
			partitionType = "singleVmPartition";
		if ( clusterDefJson.multiVmPartition )
			partitionType = "multiVmPartition";

		var version = "sandbox";
		if ( clusterDefJson[ partitionType ]["1"] )
			version = "1";

		var hostTextPath = partitionType + "." + version + ".hosts";

		$( ".clusterHostText" ).data( "path", hostTextPath );
		console.log( "getClusterDefnSuccess() hostTextPath set to: " + hostTextPath );

		$( ".clusterTypeSelect", _editPanel ).val( partitionType );
		$( ".clusterTypeSelect", _editPanel ).data( "last", partitionType );


		jsonForms.loadValues( _editPanel, clusterDefJson );


		//jsonForms.resizeVisibleEditors();
		jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );
		$( ".clusterLoading" ).hide();

	}

	function getDefinition() {
		console.log( "getDefinition()" );
		return _settingsJson;
	}
// Need to register events after dialog is loaded
	function registerInputEvents() {


		jsonForms.configureJsonEditors( getDefinition, _container, _editPanel, _definitionTextAreaId );

		console.log( "registerInputEvents(): register events" );

		$( _editPanel + " input," + _editPanel + " select" ).change( function () {
			jsonForms.updateDefinition( _settingsJson, $( this ) );
		} );
		
		$( ".clusterTypeSelect", _editPanel ).change( function() {
			
			var newCluster = $(this).val( ) ;
			var oldCluster = $(this).data( "last" ) ;
		
			console.log("Changing the cluster to", 
					newCluster , " from last: ", oldCluster ) ;
			_settingsJson[newCluster] = _settingsJson[oldCluster];
			delete _settingsJson[oldCluster] ;
			getClusterDefnSuccess( _settingsJson ) 
		}) ;

	}



	function getValueOrDefault( value ) {
		if ( value == undefined || value == "" ) {
			return "---"
		}
		return value;
	}

	function populatePerformanceTable() {
		console.log( "populatePerformanceTable Refresh" );
		var $tbody = $( ".appCollect tbody" );
		$tbody.empty();
		var numRows = 0;
		for ( var metricId in _settingsJson.customMetrics ) {
			numRows++;
			var metricData = _settingsJson.customMetrics[ metricId ];

			var $metricRow = jQuery( '<tr/>', { } );
			$metricRow.appendTo( $tbody );

			jQuery( '<td/>', {
				class: "",
				text: metricId
			} ).appendTo( $metricRow );

			var $titleCell = jQuery( '<td/>', { } ).appendTo( $metricRow );

			var $titleDiv = jQuery( '<div/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".title",
				text: getValueOrDefault( metricData.title )
			} ).appendTo( $titleCell );

			var type = getMeticType( metricData );
			jQuery( '<td/>', {
				class: "",
				html: type + getMetricCustomization( type, metricData )
			} ).appendTo( $metricRow );




			var $collectCell = jQuery( '<td/>', { } ).appendTo( $metricRow );

			buildCollectorDiv( metricId, type, metricData )
					.appendTo( $collectCell );




			var $maxCell = jQuery( '<td/>', { } ).appendTo( $metricRow );
			var $maxDiv = jQuery( '<div/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".max",
				text: getValueOrDefault( metricData.max )
			} ).appendTo( $maxCell );


		}


		$( ".tedit", $tbody ).click( function () {
			var content = $( this ).text();
			if ( content == "---" )
				content = "";
			var path = $( this ).data( "path" );
			$( this ).off();
			$( this ).empty();
			var $editInput = jQuery( '<input/>', {
				class: "",
				"data-path": path,
				value: content
			} ).change( function () {
				jsonForms.updateDefinition( _settingsJson, $( this ) );
			} );
			$( this ).append( $editInput );

		} );

		if ( numRows == 0 ) {
			var $metricRow = jQuery( '<tr/>', { } );
			$metricRow.appendTo( $tbody );

			var $missingMsg = jQuery( '<td/>', {
				class: "",
				colspan: 99,
				html: $( ".missingMetrics" ).html()
			} ).appendTo( $metricRow );
			$missingMsg
					.css( "max-width", "50em" )
					.css( "word-break", "normal" )
					.css( "font-size", "1.5em" );
		}
	}

	function getMeticType( metricData ) {
		if ( metricData.mbean )
			return "mbean";
		if ( metricData.simonMedianTime )
			return "simonMedianTime";
		if ( metricData.simonMaxTime )
			return "simonMaxTime";
		if ( metricData.simonCounter )
			return "simonCounter";

		return "http";
	}

	function getMetricCustomization( type, metricData ) {

		var customizations = "";
		if ( metricData.delta )
			customizations += "<br/>" + "delta";
		if ( metricData.decimals )
			customizations += "<br/>" + ".*(" + metricData.decimals + ")";
		if ( metricData.divideBy )
			customizations += "<br/>" + "/" + metricData.divideBy;

		// console.log("metricData.isHourlyAverage: " + metricData.isHourlyAverage) ;
		if ( metricData.reportRate )
			customizations += "<br/>" + metricData.reportRate;


		return customizations;
	}

	function buildCollectorDiv( metricId, type, metricData ) {


		var $collectorDiv = jQuery( '<div/>', { } );



		if ( type == "mbean" ) {

			var $mbean = jQuery( '<div/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".mbean",
				text: metricData[ type ]
			} ).appendTo( $collectorDiv );

			var $attDiv = jQuery( '<div/>', {
				html: " attribute: "
			} ).css( "padding-top", "0.5em" ).appendTo( $collectorDiv );


			jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".attribute",
				text: metricData.attribute
			} ).appendTo( $attDiv );

		} else if ( type == "http" ) {

			if ( metricId != "config" ) {
				jQuery( '<div/>', {
					class: "tedit",
					"data-path": "customMetrics." + metricId + ".attribute",
					text: metricData.attribute
				} ).appendTo( $collectorDiv );

			} else {

				var $configDiv = jQuery( '<div/>', {
					html: "Collection Url: "
				} ).css( "padding-bottom", "0.5em" ).appendTo( $collectorDiv );


				jQuery( '<div/>', {
					class: "tedit",
					"data-path": "customMetrics." + metricId + ".httpCollectionUrl",
					text: metricData.httpCollectionUrl
				} ).appendTo( $configDiv );

				if ( metricData.user ) {
					var $credDiv = jQuery( '<div/>', {
						html: "Credentials: "
					} ).appendTo( $collectorDiv );


					jQuery( '<span/>', {
						class: "tedit",
						"data-path": "customMetrics." + metricId + ".user",
						text: metricData.user
					} ).css( "margin-left", "1em" ).appendTo( $credDiv );

					jQuery( '<span/>', {
						class: "tedit",
						"data-path": "customMetrics." + metricId + ".pass",
						text: metricData.pass
					} ).css( "margin-left", "1em" ).appendTo( $credDiv );
				}

			}


		} else {
			// javasimon

			var $simon = jQuery( '<div/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + "." + type,
				text: metricData[ type ]
			} ).appendTo( $collectorDiv );
		}

		return $collectorDiv;
	}



	function updateClusterDefinition( operation, isUpdate, globalDefinitionUpdateFunction, newName, message ) {


		if ( jsonForms.areThereErrors() ) {
			return;
		}
		// need sync _serviceJson with editors
		if ( _isJsonEditorActive ) {
			// only submit if parsing is passing
			// definitionJson, $jsonTextArea, definitionDomId
			if ( !jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( _definitionTextAreaId ), _definitionTextAreaId ) ) {
				alertify.alert( "Parsing errors must be corrected prior to further processing" );
				return;
			}
			console.log( "updateClusterDefinition() - setting json" );
			_settingsJson = JSON.parse( $( _definitionTextAreaId ).val() );

		}


		var paramObject = {
			operation: operation,
			newName: newName,
			releasePackage: $( ".releasePackage" ).val(),
			lifeToEdit: $( ".lifeSelection" ).val(),
			clusterName: $( "#dialogClusterSelect" ).val(),
			definition: JSON.stringify( _settingsJson, null, "\t" ),
			message: "Cluster Settings: " + message
		};

		console.log( "updateClusterDefinition(): ", paramObject );
		
		if ( operation == "notify" ) {
			$.extend( paramObject, {
				itemName: paramObject.clusterName,
				hostName: "*" 
			} )
			$.post( clusterDefUrl + "/../notify", paramObject )
				.done( function ( updatesResult ) {
					alertify.alert("Changes Submitted For Review", JSON.stringify( updatesResult, null, "\t" ));
				})
				.fail( function ( jqXHR, textStatus, errorThrown ) {
					alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
				} );
			return false;
		}

		var resultsTitle = "Results for Operation: " + operation;
		if ( isUpdate ) {
			$.extend( paramObject, {
				isUpdate: isUpdate
			} );
		} else {
			resultsTitle += "- Validation Only";
		}

		$.post( clusterDefUrl, paramObject )
				.done(
						function ( updatesResult ) {

							var $userMessage = validationHandler.processValidationResults( updatesResult.validationResults );

							var $debugInfo = jQuery( '<div/>', {
								class: "debugInfo",
								text: "*details...." + JSON.stringify( updatesResult, null, "\t" )
							} );

							var okFunction = function () {
								console.log( "Closed results" );
							}

							$userMessage.append( $debugInfo );
							if ( updatesResult.updatedHost ) {
								var $moreInfo = $( "#dialogResult" ).clone().css( "display", "block" );
								$( ".noteAlt", $moreInfo ).text( updatesResult.updatedHost );
								$userMessage.append( $moreInfo );



								if ( operation != "modify" ) {
									// $userMessage.append( "<br/><br/>Remember to kill/clean services before applying changes to cluster" );
									okFunction = function () {
										console.log( "Closed results" );
										if ( _refreshLifecycleFunction != null )
											_refreshLifecycleFunction();
										jsonForms.closeDialog();
									}
								} else {
									okFunction = function () {
										console.log( "Closed results" );
										if ( _refreshLifecycleFunction != null )
											_refreshLifecycleFunction();
									}
								}
							}

							if ( globalDefinitionUpdateFunction != null ) {
								globalDefinitionUpdateFunction( true );
							}

							// alertify.alert( resultsTitle, $userMessage.html(), okFunction );
							jsonForms.showUpateResponseDialog( resultsTitle, $userMessage.html(), okFunction );



						} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "updating" + clusterDefUrl, errorThrown );
					alertify.error( "Failed" );
				} );
	}
	;


} );
/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


