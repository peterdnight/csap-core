define( ["./json-forms"], function ( jsonForms ) {

	console.log( "Module loaded" );

	var $batchTable = $( "#releaseTab table" );
	var $releaseTableBody = $( "#releaseTableBody" );

	var _lastLife = null, _lastRelase = null;


	var _refreshFunction = function () {
		getSummaryView( _lastLife, _lastRelase );
	};

	var $definition = null;
	var _refreshTimer = null ;

	var _initComplete = false;
	function initialize() {
		if ( _initComplete ) {
			return
		}
		_initComplete = true;
		console.log( "initialize() Registering Events" );
		$( "#releaseFilter" ).keyup( function () {
			// 
			clearTimeout(_refreshTimer) ;
			_refreshTimer = setTimeout( function() {
				show();
			}, 500) ;

			
			return false;
		} );

		$( "#batchAttributeSelect" ).change( function () {
			// showReleaseTab();
			console.log( "selected: " + $( this ).val() );
			show();
		} );

		$batchTable.tablesorter( {
			sortList: [[0, 0]],
			theme: 'csapSummary',
			textExtraction: {
				1: function ( node, table, cellIndex ) {
					return $( node ).find( "input" ).val();
				}
			}
			//		headers: { 1: { sorter: 'input' } }
		} );

	}

	return {
		//
		show: function ( $definitionTextArea ) {
			initialize();
			$definition = $definitionTextArea;
			show();
		},
	}

	function show() {



		$releaseTableBody.empty();

		var definitionJson = JSON.parse( $definition.val() );

		var serviceFilter = $( "#releaseFilter" ).val().trim();

		var servicesArray = new Array();
		for ( var serviceContainer in { jvms: "", osProcesses: "" } ) {

			var serviceParentJson = definitionJson[serviceContainer];

			for ( var serviceName in serviceParentJson ) {

//				if ( serviceFilter.length > 0 ) {
//					if ( serviceName.toLowerCase().contains( serviceFilter.toLowerCase() ) )
//						servicesArray.push( serviceName );
//				} else {
				servicesArray.push( serviceName );
//				}
			}
		}

		servicesArray.sort( function ( a, b ) {
			return a.toLowerCase().localeCompare( b.toLowerCase() );
		} );


		for ( var i = 0; i < servicesArray.length; i++ ) {
			var serviceName = servicesArray[i];




			var serviceParentJson = definitionJson.jvms;
			var serviceJson = serviceParentJson[ serviceName ];
			var servicePath = "jvms.";
			if ( serviceJson == undefined ) {
				servicePath = "osProcesses.";
				serviceParentJson = definitionJson.osProcesses;
				serviceJson = serviceParentJson[ serviceName ];
			}
			servicePath += serviceName;


			var selectedAttribute = $( "#batchAttributeSelect" ).val();

			var jsonPath = servicePath + "." + selectedAttribute;
			//console.log( " jsonPath: " + jsonPath) ;

			//console.log( JSON.stringify( serviceJson, null, "\t" ) ) ;


			var row = buildRow( serviceName,
					jsonForms.getValue( selectedAttribute, serviceJson ),
					"(default)",
					jsonPath )

			if ( row != null ) {
				$releaseTableBody.append( row );
			}


			// Add rows for lifecycles

			for ( var versionNumber in serviceJson.version ) {
				for ( var lifeCycle in serviceJson.version[ versionNumber ] ) {
					var lifeJson = serviceJson.version[ versionNumber ][lifeCycle];

					var attributeValue = jsonForms.getValue( selectedAttribute, lifeJson );
					if ( attributeValue != undefined ) {

						var lifePath = servicePath + ".version." + versionNumber + "." + lifeCycle
								+ "." + selectedAttribute;

						var row = buildRow( serviceName,
								attributeValue,
								"(" + lifeCycle + "-" + versionNumber + ")",
								lifePath )

						if ( row != null ) {
							$releaseTableBody.append( row );
						}

					}
				}
			}
		}

		$batchTable.trigger( "update" );

		if ( $( "#batchAttributeSelect" ).val() == "maven.dependency" ) {
			checkForReleaseFile(  );
		}

	}

	function buildRow( serviceName, serviceVersion, lifeVersion, jsonPath ) {

		var serviceFilter = $( "#releaseFilter" ).val().trim();
		if ( serviceFilter.length > 0 &&
				!serviceName.toLowerCase().contains( serviceFilter.toLowerCase() ) &&
				!lifeVersion.toLowerCase().contains( serviceFilter.toLowerCase() ) ) {

			return null;
		}

		var serviceIdentifier = serviceName + '<span class="releaseVersion">' + lifeVersion + '</span>';

		var $userInput;
		if ( jsonPath == null ) {
			$userInput = jQuery( '<div/>', {
				class: "releaseDiv",
				title: "Auto generated using tools. To ignore, use service editor to disable release file",
				html: serviceVersion
			} );
		} else {
			$userInput = jQuery( '<input/>', {
				class: "releaseInput",
				"data-path": jsonPath,
				value: serviceVersion
			} ).change( function () {

				updateDefinition( $( this ) );
			} );
		}

		var $row = $( "<tr></tr>" );
		$row.append( $( "<td/>" ).html( serviceIdentifier ) );
		$row.append( $( "<td/>" ).append( $userInput ) );

		return $row;

	}

	function updateDefinition( $inputThatChanged ) {

		var jsonPath = $( $inputThatChanged ).data( "path" );
		console.log( "modified: " + jsonPath );

		var definitionJson = JSON.parse( $definition.val() );

		jsonForms.updateDefinition( definitionJson, $inputThatChanged );
		$definition.val( JSON.stringify( definitionJson, null, "\t" ) );
	}

	function checkForReleaseFile(  ) {
		$.getJSON( definitionBaseUrl + "/releaseFile", {
			dummy: hostName,
			releasePackage: $( '.releasePackage' ).val()
		} )

				.done( function ( releaseFile ) {

					if ( releaseFile.error ) {
						console.log( "No further processing due to:", releaseFile.error );
					} else {
						addReleaseFileVersions( releaseFile );
					}

				} )

				.fail(
						function ( jqXHR, textStatus, errorThrown ) {

							handleConnectionError( "Retrieving definitionGetSuccess " + hostName, errorThrown );
						} );
	}

	function addReleaseFileVersions( releaseFile ) {
		console.log( "adding release rows" );
		var serviceFilter = $( "#releaseFilter" ).val().trim();

		for ( var serviceName in releaseFile ) {

			var versionItem = releaseFile[serviceName];

			var releaseValue = "";
			for ( var lifecycle in versionItem ) {
				if ( releaseValue != "" ) {
					releaseValue += "<br/>";
				}
				releaseValue += '<span>' + lifecycle + "</span>" + versionItem[lifecycle];
			}

			var row = buildRow( serviceName,
					releaseValue,
					"(releaseFile)",
					null );

			if ( row != null ) {
				$releaseTableBody.append( row );
			}

		}
		$batchTable.trigger( "update" );


	}



} );