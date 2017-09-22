define( ["./service-edit", "./settings-edit", "./dialog-cluster-edit", "./json-forms","./validation-handler"], function ( serviceEdit, settingsEdit, clusterEditor, jsonForms, validationHandler ) {

	console.log( "Module loaded" );

	var $targetContainer = $( "#lifeEditor" );

	var $packageSelected = $( '.releasePackage' ) ;

	var _lastLife = null, _lastRelase = null;

	var _refreshFunction = function () {
		getSummaryView( _lastLife, _lastRelase );
	};

	var _lastSummary = null;

	return {
		//
		showSummaryView: function ( life, container, releasePackage ) {


			$targetContainer = container;

			getSummaryView( life, releasePackage );
		},
	}

	function getSummaryView( life, releasePackage ) {
		// alertify.notify( "Loading: " + life );

		$( ".loadingLargePanel", $targetContainer.parent() ).show();

		_lastLife = life, _lastRelase = releasePackage;

		var params = {
			lifeToEdit: life,
			releasePackage: releasePackage
		}
		$.get( lifeEditUrl,
				params,
				showSummaryView,
				'html' );
	}

	function showSummaryView( lifeEditView ) {

		_lastSummary = lifeEditView;



		var $batchContent = $( "<html/>" ).html( lifeEditView ).find( '#lifeEditorWrapper' );
		console.log( "showSummaryView(): content Length: " + $batchContent.text().length );

		$targetContainer.html( $batchContent );


		console.log( "initialize() Registering Events" );
		$( "#clusterFilter" ).keyup( function () {
			var filter = $( "#clusterFilter" ).val();


			console.log( "Need to filter: " + filter );
			$( "#editLifeTable tbody tr" ).each( function () {
				var numberMatches = 0;
				$( "a", $( this ) ).each( function () {
					if ( $( this ).text().toLowerCase().contains( filter.toLowerCase() ) ) {
						numberMatches += 1;
						$( this ).addClass( "svcHighlight" );
					} else {
						$( this ).removeClass( "svcHighlight" );
					}
				} );
				console.log( "Filter matches: " + numberMatches + " filter.length: " + filter.length );
				if ( numberMatches == 0 ) {
					$( this ).hide();
				} else {
					$( this ).show();
				}
				if ( filter.length == 0 )
					$( "a", $( this ) ).removeClass( "svcHighlight" );
			} );


			//showSummaryView( _lastSummary ) ;
			return true;
		} );

		$( ".loadingLargePanel", $targetContainer.parent() ).hide();

		$( ".lifeSelection" ).selectmenu( {
			width: "10em",
			change: function () {
				console.log( "showLifeEditor editing env: " + $( this ).val() );
				getSummaryView( $( this ).val(), $packageSelected.val() );
			}
		} );

		registerLifeOperations();

		// console.log("showSummaryView: hostName: " + hostName + " serviceName: " + serviceName);
		serviceEdit.setRefresh( _refreshFunction );
		$( '.editServiceButton' ).click( function () {
			serviceName = $( this ).text().trim();
			console.log( "\n\nshowSummaryView(): launching: " + serviceEditUrl + " hostName: " + hostName + " serviceName: " + serviceName );



			var params = {
				releasePackage: $packageSelected.val(),
				serviceName: serviceName,
				hostName: "*"
			}
			$.get( serviceEditUrl,
					params,
					serviceEdit.showServiceDialog,
					'html' );
			return false;
		} );



		$( ".addServiceClusterButton" ).click( function () {
			console.log( "Adding service" );
			alertify.prompt( "Add a new service", 'Enter the name', "newServiceToBeAdded",
					function ( evt, newServiceName ) {
						console.log( 'newServiceName: ' + newServiceName );
						var params = {
							releasePackage: $packageSelected.val(),
							serviceName: newServiceName,
							"newService": "newService",
							hostName: "*"
						}
						$.get( serviceEditUrl,
								params,
								serviceEdit.showServiceDialog,
								'html' );
					},
					function () {
						console.log( "canceled" );
					}
			);
			return false;
		} );


		$( '#editSettingsButton' ).click( function () {

			if ( $packageSelected.val() != rootPackageName ) {
				alertify.alert( "Operation not available", "Life Cycle settings can only be modified when root package is selected: " + rootPackageName );
			} else {


				var lifeEdit = $( "#lifeEdit" ).val();

				console.log( "\n\nshowSummaryView(): launching: " + settingsEditUrl + " lifeEdit: " + lifeEdit );

				var params = {
					lifeToEdit: lifeEdit
				}

				$.get( settingsEditUrl,
						params,
						settingsEdit.showSettingsDialog,
						'html' );

			}
		} );


		$( '.editClusterButton' ).click( function () {

			var lifeEdit = $( "#lifeEdit" ).val();
			var clusterName = $( this ).text().trim();
			var relPkg = $packageSelected.val();

			console.log( "\n\nshowSummaryView(): launching: " + clusterEditUrl
					+ " lifeEdit: " + lifeEdit + " clusterName: " + clusterName + " releasePackage: " + relPkg );

			var params = {
				releasePackage: relPkg,
				lifeToEdit: lifeEdit,
				clusterName: clusterName
			}

			clusterEditor.setRefresh( _refreshFunction )


			$.get( clusterEditUrl,
					params,
					clusterEditor.show,
					'html' );


		} );



		$( ".addNewClusterButton" ).click( function () {
			console.log( "Adding cluster" );
			alertify.prompt( "New service cluster", 'Enter the name', "newClusterToBeAdded",
					function ( evt, newClusterName ) {

						var lifeEdit = $( "#lifeEdit" ).val();
						var clusterName = $( this ).text().trim();
						var relPkg = $packageSelected.val();

						console.log( "\n\n new Cluster(): launching: " + clusterEditUrl
								+ " lifeEdit: " + lifeEdit + " clusterName: " + newClusterName + " releasePackage: " + relPkg );

						var params = {
							releasePackage: relPkg,
							lifeToEdit: lifeEdit,
							"newService": "newService",
							clusterName: newClusterName
						}


						$.get( clusterEditUrl,
								params,
								clusterEditor.show,
								'html' );
					},
					function () {
						console.log( "canceled" );
					}
			);
			return false;
		} );
	}

	function registerLifeOperations() {

		$( ".addNewLifeButton" ).click( function () {
			//alertify.notify( "Adding life" );
			alertify.prompt( "Add a new lifecycle", 'Enter the name. It must be alpha numeric only. Eg. dev, dev1, stage, lt, prod', "stage",
					function ( evt, newName ) {
						console.log( 'newName: ' + newName );
						var params = {
							releasePackage: $packageSelected.val(),
							newName: newName,
							lifeToEdit: $( ".lifeSelection" ).val(),
							"operation": "add"
						}
						
						_lastLife = newName ;
						
						$.post( lifeUpdateUrl,
								params,
								showLifeOperationResults,
								'json' );
					},
					function () {
						console.log( "canceled" );
					}
			);
			return false;

		} );
		
		$( ".removeLifeButton" ).click( function () {
			alertify.confirm( "Life cycle Removal", 'Click ok to continue',
					function ( evt, newName ) {
						console.log( 'newName: ' + newName );
						var params = {
							releasePackage: $packageSelected.val(),
							lifeToEdit: $( ".lifeSelection" ).val(),
							"operation": "delete"
						}
						$(".lifeSelection option[value='" + _lastLife + "']").remove() ;
						_lastLife = $(".lifeSelection").val() ;
						$.post( lifeUpdateUrl,
								params,
								showLifeOperationResults,
								'json' );
					},
					function () {
						console.log( "canceled" );
					}
			);
			return false;
		} );

	}

	function showLifeOperationResults( updatesResult ) {

		console.log("showLifeOperationResults()", updatesResult) ;
		
		var $userMessage = validationHandler.processValidationResults( updatesResult.validationResults );

		var $debugInfo = jQuery( '<div/>', {
			class: "debugInfo",
			text: "*details...." + JSON.stringify( updatesResult, null, "\t" )
		} );

		var okFunction = function () {
			console.log( "Closed results" );
			_refreshFunction() ;
		}

		$userMessage.append( $debugInfo );
		if ( updatesResult.updatedHost ) {
			var $moreInfo = $( "#dialogResult" ).clone().css( "display", "block" );
			$( ".noteAlt", $moreInfo ).text( updatesResult.updatedHost );
			$userMessage.append( $moreInfo );

		}


		if ( updatesResult.message ) {
			$userMessage.append( "<br/><br/>" + updatesResult.message );
		}

		jsonForms.showUpateResponseDialog( "Life cycle Results", $userMessage.html(), okFunction );

		if ( updatesResult.validationResults.success ) {
			console.log("showLifeOperationResults() triggering definition reload") ;
			jsonForms.runUpdateFunction() ;
		}
	}
	;
} );