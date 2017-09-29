define( ["./service-edit-config", "./validation-handler", "./json-forms"], function ( configuration, validationHandler, jsonForms ) {

	console.log( "Service Edit Module loaded" );

	var _resultPanel = "#serviceEditorResult";
	var _editPanel = "#serviceEditor";
	var _container = window;
	var _definitionTextAreaId = "#serviceJson";
	var _isShowIdWarning = true;

	var _hostName = "*";

	var _serviceJson = null;
	var _jsonParseTimer = null;
	var _isJsonEditorActive = false;

	var _currentDialog = null;
	var _dialogId = "serviceDialog"

	var _refreshLifecycleFunction = null;

	var $propFileContainer = $( "#fileContainer" );
	var $propName = $( "#propFileName" );
	var $propLifecycle = $( "#propLifecycle" );
	var $propExternal = $( "#propExternal" );
	var $propContents = $( "#propFileText" );
	var $propSelect = $( "#propertyFileSelect" );
	var _propSelectTimer = null;

	return {
		setRefresh: function ( refreshFunction ) {
			_refreshLifecycleFunction = refreshFunction;
		},
		setSpecificHost: function ( specificHostName ) {
			_hostName = specificHostName;
		},
		//
		showServiceDialog: function ( editDialogHtml ) {
			//when testing standalone, tests without
			if ( !jsonForms.isDialogBuilt( _dialogId ) ) {
				// do it only once.
				_container = ".ajs-dialog "
				_resultPanel = _container + _resultPanel;
				_editPanel = _container + _editPanel;
				_definitionTextAreaId = _container + _definitionTextAreaId;
			}

			return showServiceDialog( editDialogHtml );
		},
		getServiceDefinition: function () {
			getServiceDefinition();
		},
		registerDialogButtons: function () {
			registerInputEvents();
		},
		updateServiceDefinition: function () {
			updateServiceDefinition( true );
		},
		validateServiceDefinition: function () {
			updateServiceDefinition( false );
		},
		configureForTest: function () {

			getServiceDefinition( serviceName );
			registerUiComponents();
			jsonForms.registerOperations( updateServiceDefinition );
		}
	}

	function registerUiComponents() {


		$( ".serviceContainer" ).change( function () {
			var selection = $( this ).val();

			if ( !selection.contains( "SpringBoot" ) && !selection.contains( "tomcat" ) && !selection.contains( "cssp" ) ) {
				$( ".tomcatTab" ).hide();
			} else {
				$( ".tomcatTab" ).show();
			}
//
//			if ( !selection.contains( "docker" ) ) {
//				$( ".dockerTab" ).hide();
//				$( ".deployTab" ).show();
//			} else {
//				$( ".dockerTab" ).show();
//				$( ".deployTab" ).hide();
//			}
		} );


//		$( ".useDockerJavaContainer" ).change( function () {
//			var selection = $( this ).val();
//			$( ".dockerTab" ).show();
//		} );

		$( "#dockerTemplateSelect" ).change( function () {

			var templateName = $( this ).val();
			$( this ).val( "default" );

			$.getJSON( serviceDefUrl + "DockerTemplate", {
				"templateName": templateName
			} )

					.done( function ( templateDefinition ) {

//						var dockerTemplate = JSON.stringify( serviceJson, null, "\t" );
//						$( ".dockerSettings" ).val( dockerTemplate ).trigger( "change" );
						_serviceJson.docker = templateDefinition ;
						getServiceDefinitionSuccess( _serviceJson ) ;
					} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {

						$( ".serviceLoading" ).hide();
						failedToGetService( "retrieving: docker template host: " + _hostName + " from: " + serviceDefUrl, errorThrown );
					} );
		} );

		console.log( "registerUiComponents(): registering tab events 2" );
		$( _editPanel ).tabs( {
			beforeActivate: function ( event, ui ) {

				if ( ui.oldTab.text().indexOf( "Editor" ) != -1 ) {
					// refresh ui with edit changes.
					console.log( "registerUiComponents():  parsing serviceJson" );
					_serviceJson = JSON.parse( $( _definitionTextAreaId ).val() );
					getServiceDefinitionSuccess( _serviceJson );
				}

			},
			activate: function ( event, ui ) {
				console.log( "registerUiComponents(): activating: " + ui.newTab.text() );

				_isJsonEditorActive = false;
				if ( ui.newTab.text().indexOf( "Editor" ) != -1 ) {
					activateJsonEditor();
				}

				if ( ui.newTab.text().indexOf( "Performance" ) != -1 ) {
					populatePerformanceTable();
				}


				if ( ui.newTab.text().indexOf( "Property" ) != -1 ) {
					syncPropertyFileSelect();
				}

				//resizeVisibleEditors();

				jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );

			}

		} );


		registerInputEvents();


		$( "select.dialogServiceSelect" ).selectmenu( {
			width: "200px",
			change: function () {
				console.log( "registerUiComponents editing service: " + $( this ).val() );
				serviceName = $( this ).val();
				getServiceDefinition( $( this ).val() );
			}
		} );

		$( _editPanel ).show();
		// activateTab( "docker" );

	}
	function activateTab( tabId ) {
		var tabIndex = $( 'li[data-tab="' + tabId + '"]' ).index();

		console.log( "Activating tab: " + tabIndex );

		// $("#jmx" ).prop("checked", true) ;

		$( _editPanel ).tabs( "option", "active", tabIndex );

		return;
	}


	function activateJsonEditor() {
		_isJsonEditorActive = true;
		$( _definitionTextAreaId ).val( JSON.stringify( _serviceJson, null, "\t" ) );
		//resizeLinedText( $( _definitionTextAreaId ) );
		jsonForms.resizeLinedText( $( _definitionTextAreaId ), _container, _editPanel );
	}


	function showServiceDialog( editDialogHtml ) {
		jsonForms.showDialog( _dialogId, "Service Configuration", editDialogHtml, updateServiceDefinition );
		registerUiComponents();

		configuration.defaultService().description = $( ".serviceDesc" ).val();
		//configuration.defaultService().docUrl = $( ".serviceHelp" ).val();

		var isAddMode = $( ".addDefButton" ).is( ":visible" );
		if ( !isAddMode ) {
			getServiceDefinition( serviceName );
		} else {
			$( ".serviceLoading" ).hide();
			getServiceDefinitionSuccess( configuration.defaultService() );
		}



	}

	function getServiceDefinition( selectedService ) {

		console.log( "getServiceDefinition(): ", serviceDefUrl, " service: ", selectedService );
		$( ".serviceLoading" ).show();
//	if ( $( '#serviceJson' ).val() != "loading..." )
//		return;
		//$( ".loading" ).html( "Retrieving capability definition from server" ).show();
		var paramObject = {
			releasePackage: $( ".releasePackage" ).val(),
			serviceName: selectedService,
			hostName: _hostName
		};

		$.getJSON( serviceDefUrl, paramObject )

				.done( function ( serviceJson ) {

					$( ".serviceLoading" ).hide();
					getServiceDefinitionSuccess( serviceJson );
					syncPropertyFileSelect();
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					$( ".serviceLoading" ).hide();
					failedToGetService( "retrieving: " + selectedService + " host: " + _hostName + " from: " + serviceDefUrl, errorThrown );
				} );
	}


	function getServiceDefinitionSuccess( serviceJson ) {
		_serviceJson = serviceJson;



		if ( _serviceJson.lastModifiedBy == undefined ) {
			$( ".lastModified" ).hide();
		} else {
			$( ".lastModified .noteAlt" ).text( _serviceJson.lastModifiedBy );
		}

		$( _definitionTextAreaId ).val( JSON.stringify( serviceJson, null, "\t" ) );

		// update form values

		jsonForms.loadValues( _editPanel, serviceJson, configuration.defaultFields() );

		var serverType = _serviceJson.server;
		var isAddMode = $( ".addDefButton" ).is( ":visible" );
		if ( isAddMode ) {
			$( ".tomcatTab" ).show();
			$( ".jeeOptions" ).show();
			$( ".osPackagesTab" ).show();
			$( ".osOptions" ).show();
		} else if ( serverType.contains( "wrapper" ) || serverType.contains( "os" ) || serverType.contains( "docker" ) ) {
			$( ".tomcatTab" ).hide();
			$( ".jeeOptions" ).hide();
			$( ".osPackagesTab" ).show();
			$( ".osOptions" ).show();
		} else {
			$( ".tomcatTab" ).show();
			if ( serverType.contains( "Boot" ) ) {
				$( ".tomcatWar" ).hide();
				$( ".bootTomcat" ).show();
			} else {
				$( ".tomcatWar" ).show();
				$( ".bootTomcat" ).hide();
			}
			$( ".jeeOptions" ).show();
			$( ".osPackagesTab" ).hide();
			$( ".osOptions" ).hide();
		}

		if ( serverType.contains( "docker" ) ) {
			$( ".deployTab" ).hide();
			$( "#dockerContainerSelect" ).hide();
		}
//		if ( serverType.contains( "docker" ) ) {
//			$( ".dockerTab" ).show();
//			$( ".deployTab" ).hide();
//		} else if ( _serviceJson.docker ) {
//			$( ".dockerTab" ).show();
//		} else {
//			$( ".dockerTab" ).hide();
//			$( ".deployTab" ).show();
//		}


		if ( _serviceJson.standardJmx == undefined ) {
			console.log( "getServiceDefinitionSuccess(): javaLimitsTab Hiding element: " + $( ".javaLimitsTab" ).attr( "id" ) );
			$( ".javaLimitsTab" ).parent().hide();
		}


		jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );

		if ( $( "#jsonEditor" ).is( ":visible" ) ) {
			activateJsonEditor();
		}
		if ( $( "#performance" ).is( ":visible" ) ) {
			populatePerformanceTable();
		}


	}


	function getDefinition() {
		console.log( "getDefinition()" );
		return _serviceJson;
	}

	function getPropertyFileCount() {
		if ( _serviceJson.files ) {
			return _serviceJson.files.length;
		}
		return 0;
	}
	function pad( targetString, paddingArray, isPadLeft ) {

		//console.log("targetString.length", targetString.length, "paddingArray.length", paddingArray.length )
		if ( targetString.length > paddingArray.length )
			return targetString;

		if ( typeof targetString === 'undefined' )
			return paddingArray;
		if ( isPadLeft ) {
			return (paddingArray + targetString).slice( -paddingArray.length );
		} else {
			return (targetString + paddingArray).substring( 0, paddingArray.length );
		}
	}
	function getPropertyKey( life, name, addPadding ) {
		console.log( "getPropertyKey", life, name );

		if ( !addPadding ) {
			return life + "-" + name;
		}
		var padding = Array( addPadding ).join( ' ' );
		var paddedString = pad( life + ":", padding ) + name;
		return paddedString.replace( / /g, '&nbsp;' );
	}

	function refreshGlobalVariables() {
		// needed for lazy loads in parent apps
		$propFileContainer = $( "#fileContainer" );
		$propName = $( "#propFileName" );
		$propLifecycle = $( "#propLifecycle" );
		$propExternal = $( "#propExternal" );
		$propContents = $( "#propFileText" );
		$propSelect = $( "#propertyFileSelect" );
	}

	function syncPropertyFileSelect( isNewItem ) {

		if ( _propSelectTimer ) {
			clearTimeout( _propSelectTimer );
		}
		_propSelectTimer = setTimeout( function () {

			if ( _serviceJson.files && _serviceJson.files.length > 0 ) {
				$propSelect.empty();
				console.log( "$propSelect", $propSelect.parent().html() );

				var lastItemAdded = "";
				for ( var i = 0; i < _serviceJson.files.length; i++ ) {
					lastItemAdded = getPropertyKey( _serviceJson.files[i].lifecycle, _serviceJson.files[i].name );
					console.log( "adding option", i );
					jQuery( '<option/>', {
						html: getPropertyKey( _serviceJson.files[i].lifecycle, _serviceJson.files[i].name, 10 ),
						value: lastItemAdded
					} ).appendTo( $propSelect );
				}

				if ( isNewItem ) {
					$propSelect.val( lastItemAdded );
					$propSelect.trigger( "change" );
				} else {
					$propSelect.val( getPropertyKey( $propLifecycle.val(), $propName.val() ) );
				}

				$propSelect.sortSelect();
				// initial rendering - infinite loop occurs so only apply on invisible
				if ( $propName.val() != "" && !$propFileContainer.is( "visible" ) ) {
					$propFileContainer.show();
					$propSelect.trigger( "change" );
				}
			} else {
				$propSelect.empty();
				jQuery( '<option/>', {
					text: "Add File(s)"
				} ).appendTo( $propSelect );
			}

		}, 100 )
	}

	function registerPropertyEvents() {

		console.log( "registering property events" );

		$( "#addFileButton" ).click( function () {

			var fileCount = getPropertyFileCount();
			$propFileContainer.show();

			if ( !_serviceJson.files ) {
				_serviceJson.files = new Array();
			}

			var newFile = new Object();
			_serviceJson.files.push( newFile );
			newFile.name = "fileName" + fileCount;
			var contentLines = new Array();
			contentLines.push( "content" + fileCount );
			newFile.content = contentLines;
			newFile.lifecycle = "common";
			newFile.external = true;
			newFile.newFile = true;

			syncPropertyFileSelect( true );


		} );

		$propName.change( syncPropertyFileSelect );
		$propLifecycle.change( syncPropertyFileSelect );


		$propContents.change( function () {
			_serviceJson.files[ $propContents.data( "file-index" ) ].contentUpdated = true;
		} );

		$( "#deleteFileButton" ).click( function () {

			for ( var i = 0; i < _serviceJson.files.length; i++ ) {

				var currentFile = getPropertyKey( _serviceJson.files[i].lifecycle, _serviceJson.files[i].name );
				if ( $propSelect.val() == currentFile ) {
					_serviceJson.files[i].deleteFile = true;
					//_serviceJson.files[i].content ="File is marked for delete"
					$propContents.val( "File is marked for delete" );
					// _serviceJson.files.splice( i, 1 );
				}
			}
			//$propName.val( "" );
			//syncPropertyFileSelect();
			//$propFileContainer.hide();
		} );

		$propSelect.change( function () {
			for ( var fileIndex = 0; fileIndex < _serviceJson.files.length; fileIndex++ ) {
				var item = _serviceJson.files[fileIndex];

				var lifeName = getPropertyKey( _serviceJson.files[fileIndex].lifecycle, _serviceJson.files[fileIndex].name );
				if ( lifeName == $propSelect.val() ) {
					$propName.val( item.name );
					$propName.data( "path", "files[" + fileIndex + "].name" );

					$propLifecycle.val( item.lifecycle );
					$propLifecycle.data( "path", "files[" + fileIndex + "].lifecycle" );

					$propExternal.val( item.external );
					$propExternal.data( "path", "files[" + fileIndex + "].external" );

					$propContents.data( "path", "files[" + fileIndex + "].content" );
					var textLines = "";
					for ( var textLineCount = 0; textLineCount < item.content.length; textLineCount++ ) {
						textLines += item.content[ textLineCount ] + "\n";
					}

					$propContents.val( textLines );
					$propContents.css( "white-space", "pre" );
					$propContents.data( "file-index", fileIndex );
				}
			}
			$propFileContainer.show();
		} );
	}

// Need to register events after dialog is loaded
	function registerInputEvents() {
		refreshGlobalVariables();
		registerPropertyEvents();

		$( "input[name=updateOp]" ).click( function () {
			var operation = $( "input[name=updateOp]:checked" ).val();
			if ( operation == "add" || operation == "rename" ) {
				$( "#opsNewName" ).show();
			} else {
				$( "#opsNewName" ).hide();
			}
		} );

		//configureJsonEditors();
		jsonForms.configureJsonEditors( getDefinition, _container, _editPanel, _definitionTextAreaId );

		console.log( "registerInputEvents(): register events" );
		$( ".toggleAppCollect" ).click( function () {
			console.log( "registerInputEvents toggling display" );
			$( ".appCollectToggle" ).toggle();
			if ( $( "table.appCollect" ).is( ":visible" ) ) {
				//parseAndUpdateJsonEdits( $( ".appCollectToggle textarea" ) );
				jsonForms.parseAndUpdateJsonEdits( _serviceJson, $( ".appCollectToggle textarea" ) )
				populatePerformanceTable();
				$( ".toggleAppCollect" ).text( "Show Editor" );
			} else {
				// update text contents
				getServiceDefinitionSuccess( _serviceJson );
				// resizeVisibleEditors();

				jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );
				$( ".toggleAppCollect" ).text( "Show Summary" );
			}
		} );

		// set default placeHolder on Alerts tab
		$( _editPanel + " #alerts label.alerts input" ).attr( "placeholder", " *" );

		$( _editPanel + " input," + _editPanel + " select" ).change( function () {
			jsonForms.updateDefinition( _serviceJson, $( this ) );
		} );

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
		for ( var metricId in _serviceJson.customMetrics ) {
			numRows++;
			var metricData = _serviceJson.customMetrics[ metricId ];

			var $metricRow = jQuery( '<tr/>', { 'data-order': metricId } );
			$metricRow.appendTo( $tbody );



			var $labelCell = jQuery( '<td/>', { } ).appendTo( $metricRow );
			$labelCell.append( jQuery( '<img/>', {
				title: "Click/Drag to re-order", class: "moveRow", src: baseUrl + "/images/16x16/mail-send-receive.png"
			} ) );
			$labelCell.append( jQuery( '<img/>', {
				title: "Add a new row", class: "newRow", src: baseUrl + "/images/16x16/document-new.png"
			} ) );
			$labelCell.append( jQuery( '<img/>', {
				title: "Delete row", class: "deleteRow", src: baseUrl + "/images/16x16/process-stop.png"
			} ) );

			var $titleDiv = jQuery( '<div/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".title",
				text: getValueOrDefault( metricData.title )
			} ).appendTo( $labelCell );

			var $idCell = jQuery( '<td/>', { } ).appendTo( $metricRow );
			var $idDiv = jQuery( '<div/>', {
				class: "tedit",
				title: "WARNING: renaming ID looses all history. Only alpha chars are permitted",
				"data-id": metricId,
				text: metricId
			} ).appendTo( $idCell );


			var $typeCell = jQuery( '<td/>', { class: "" } );
			$typeCell.appendTo( $metricRow );

			var type = getMetricType( metricId, metricData );
			$typeCell.append( buildSourceCell( type, metricId, metricData ) );

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

		registerPerformanceTableEvents( $tbody );

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

	function registerPerformanceTableEvents( $tbody ) {
		$tbody.sortable( {
			handle: ".moveRow",
			update: function ( event, ui ) {
				//console.log( "reorderd rows: ", ui );
				//saveLayout( $graphContainer, $plotContainer );
				var currentOrderItems = _serviceJson.customMetrics;
				//var currentItems = definitionContainer[ attribute ];
				var updatedItemIndexOrder = new Array();
				$( "tr", $tbody ).each( function () {
					updatedItemIndexOrder.push( $( this ).data( "order" ) );
				} );
				console.log( "row order: ", updatedItemIndexOrder );
				var itemsInNewOrder = new Object();
				for ( var i = 0; i < updatedItemIndexOrder.length; i++ ) {
					var field = updatedItemIndexOrder[i];
					itemsInNewOrder[ field ] = currentOrderItems[ field ];
				}
//				;
				_serviceJson.customMetrics = itemsInNewOrder;

				//console.log( "itemsInNewOrder: ", itemsInNewOrder );
			}
		} );

		$( ".deleteRow", $tbody ).click( function () {
			var attributeToDelete = $( this ).parent().parent().data( "order" );

			var currentItems = _serviceJson.customMetrics;
			var updatedItems = new Object();
			for ( var attribute in currentItems ) {
				if ( attribute != attributeToDelete )
					updatedItems[attribute] = currentItems[attribute];
			}

			_serviceJson.customMetrics = updatedItems;

			console.log( "updatedItems: ", updatedItems );
			populatePerformanceTable();
		} );

		$( ".newRow", $tbody ).click( function () {
			var attributeToDuplicate = $( this ).parent().parent().data( "order" );

			var currentItems = _serviceJson.customMetrics;
			var updatedItems = new Object();
			for ( var attribute in currentItems ) {
				updatedItems[attribute] = currentItems[attribute];
				if ( attribute == attributeToDuplicate )
					var newObject = jQuery.extend( { }, currentItems[attribute] );
				updatedItems["updateThisId"] = newObject;
			}

			_serviceJson.customMetrics = updatedItems;

			console.log( "updatedItems: ", updatedItems );
			populatePerformanceTable();
		} );


		$( ".tedit", $tbody ).click( function () {
			var content = $( this ).text();
			if ( content == "---" )
				content = "";
			var actual = $( this ).data( "actual" );
			if ( actual )
				content = actual;
			var path = $( this ).data( "path" );
			var id = $( this ).data( "id" );
			var type = $( this ).data( "type" );
			var truefalse = $( this ).data( "truefalse" );
			if ( truefalse ) {
				if ( content == "false" ) {
					$( this ).text( "true" );
				} else {
					$( this ).text( "false" );
				}
				jsonForms.updateDefinition( _serviceJson, $( this ) );
				return;
			}
			$( this ).off();
			$( this ).empty();

			var $editValueContainer;

			if ( id && _isShowIdWarning && !type ) {
				_isShowIdWarning = false;
				alertify.alert( "Modifying the collection id will remove correlation with previously collected results" );
			}
			if ( type ) {
				$editValueContainer = jQuery( '<select/>', {
					class: "",
					"data-type": type,
					"data-id": id,
					"data-path": path
				} );
				var options = $( "#serviceTemplates select.attType" ).html();
				$editValueContainer.html( options );
				$editValueContainer.sortSelect();
			} else {
				var $editValueContainer = jQuery( '<input/>', {
					class: "",
					"data-id": id,
					"data-path": path,
					value: content
				} );
			}

			$editValueContainer.change( function () {
				var oldId = $( this ).data( "id" );
				var oldType = $( this ).data( "type" );
				//console.log("Updating performance:", $( this ).data("id")) ;
				if ( oldType ) {
					// new ID needs to be used
					var newType = $( this ).val();
					// updating field value
					console.log( "id: ", oldId, " update collection type from: ", oldType, " to: ", newType );
					var metricToUpdate = _serviceJson.customMetrics[oldId];
					var setting = metricToUpdate[oldType];
					delete metricToUpdate[oldType];
					metricToUpdate[newType] = setting;
					populatePerformanceTable();

				} else if ( oldId ) {
					// new ID needs to be used
					var newId = $( this ).val();
					console.log( "Updating id: ", oldId, " to: ", newId );

					var currentItems = _serviceJson.customMetrics;
					var updatedItems = new Object();
					for ( var attribute in currentItems ) {

						if ( attribute == oldId ) {
							var newObject = jQuery.extend( { }, currentItems[attribute] );
							updatedItems[newId] = newObject;
						} else {
							updatedItems[attribute] = currentItems[attribute];
						}
					}

					_serviceJson.customMetrics = updatedItems;

					console.log( "updatedItems: ", updatedItems );
					populatePerformanceTable();

				} else {
					// updating field value
					jsonForms.updateDefinition( _serviceJson, $( this ) );
				}
			} );
			$( this ).append( $editValueContainer );

		} );
	}
	function getMetricType( metricId, metricData ) {
		if ( metricData.mbean )
			return "mbean";
		if ( metricData.simonMedianTime )
			return "simonMedianTime";
		if ( metricData.simonMaxTime )
			return "simonMaxTime";
		if ( metricData.simonCounter )
			return "simonCounter";
		if ( metricId == "config" )
			return "config";

		return "http";
	}

	function buildSourceCell( typeText, metricId, metricData ) {

		var $sourceDiv = jQuery( '<div/>', { class: "" } );

		var $typeSelect = $( "#serviceTemplates select.attType" );
		$typeSelect.val( typeText );
		var typeLabel = typeText;
		//console.log("trendType",  $("#settingsTemplates select.trendType").val()) ;
		if ( $typeSelect.val() != "" ) {
			var optionText = $( "option:selected", $typeSelect ).text();
			if ( optionText.length > 0 ) {
				typeLabel = optionText;
			}
		}

		var $idDiv = jQuery( '<div/>', {
			class: "tedit",
			title: "Use browser or jvisualvm to confirm",
			"data-type": typeText,
			"data-id": metricId,
			text: typeLabel
		} ).appendTo( $sourceDiv );


		if ( metricData.divideBy ) {
			var divideLabel = getValueOrDefault( metricData.divideBy );
			if ( metricData.divideBy && metricData.divideBy == "1000000" ) {
				divideLabel = "(ms)";
			}
			var $divideBy = jQuery( '<div/>', {
				class: "tedit",
				title: "Divide collected result to simplify reporting. For per second, specify 'interval'. Java Simon uses micro - specify 1000000 for ms",
				"data-actual": getValueOrDefault( metricData.divideBy ),
				"data-path": "customMetrics." + metricId + ".divideBy",
				text: divideLabel
			} ).appendTo( $sourceDiv );
		}

		var customizations = "";

		if ( metricData.decimals )
			customizations += ".*(" + metricData.decimals + ")";

		// console.log("metricData.isHourlyAverage: " + metricData.isHourlyAverage) ;
		if ( metricData.reportRate ) {
			jQuery( '<div/>', { text: metricData.reportRate } )
					.appendTo( $sourceDiv );
		}

		$sourceDiv.append( customizations );
		return $sourceDiv;
	}

	function buildCollectorDiv( metricId, type, metricData ) {

		console.log( "buildCollectorDiv() type: ", type );

		var $collectorDiv = jQuery( '<div/>', { } );

		if ( type == "mbean" ) {

			var $mbean = jQuery( '<div/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".mbean",
				text: metricData[ type ]
			} ).appendTo( $collectorDiv );

			var $attDiv = jQuery( '<div/>', {
				html: "attribute: "
			} ).css( "padding-top", "0.5em" ).appendTo( $collectorDiv );


			jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".attribute",
				text: metricData.attribute
			} ).appendTo( $attDiv );


			var $ignoreSpan = jQuery( '<span/>', {
				html: "ignore errors: ",
				title: "set to true to ignore collection errors for alerts"
			} ).css( "float", "right" ).css( "margin-left", "10px" );
			$ignoreSpan.appendTo( $attDiv );

			var ignoreErrors = metricData.ignoreErrors;
			if ( !ignoreErrors )
				ignoreErrors = "false";
			jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".ignoreErrors",
				"data-truefalse": true,
				text: ignoreErrors
			} ).appendTo( $ignoreSpan );


			$attDiv.append( buildDeltaInput( metricId, metricData ) );

		} else if ( type == "http" ) {

			var $container = jQuery( '<div/>', {
			} ).appendTo( $collectorDiv );

			jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".attribute",
				text: metricData.attribute
			} ).appendTo( $container );

			$container.append( buildDeltaInput( metricId, metricData ) );


		} else if ( type == "config" ) {

			$collectorDiv.append( "Collect URL:" );
			jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".httpCollectionUrl",
				text: metricData.httpCollectionUrl
			} ).css( "margin-left", "1em" ).appendTo( $collectorDiv );

			var $matchDiv = jQuery( '<div/>', {
				html: "Match Using: "
			} ).appendTo( $collectorDiv );

			jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + ".patternMatch",
				text: metricData.patternMatch
			} ).css( "margin-left", "1em" ).appendTo( $matchDiv );

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


		} else {
			// javasimon

			var $simon = jQuery( '<span/>', {
				class: "tedit",
				"data-path": "customMetrics." + metricId + "." + type,
				text: metricData[ type ]
			} ).appendTo( $collectorDiv );

//			var $attDiv = jQuery( '<span/>', {
//			} ).appendTo( $collectorDiv );

			$collectorDiv.append( buildDeltaInput( metricId, metricData, true ) );
		}

		return $collectorDiv;
	}

	function buildDeltaInput( metricId, metricData, isSimon ) {

		console.log( "metricData:", metricData );
		var isDelta = false;
		if ( isSimon ) {
			isDelta = true;
		}
		if ( metricData.delta != undefined ) {
			if ( metricData.delta == "delta" ) {
				isDelta = true;
			} else {
				isDelta = metricData.delta;
			}
		}
		var $deltaDiv = jQuery( '<span/>', {
			text: "delta: ",
			title: "set to true to record the difference between subsequent collections"
		} ).css( "margin-left", "1em" ).css( "float", "right" );
		jQuery( '<span/>', {
			class: "tedit",
			"data-path": "customMetrics." + metricId + ".delta",
			"data-truefalse": true,
			text: isDelta
		} ).appendTo( $deltaDiv );

		return $deltaDiv;

	}






	function updateServiceDefinition( operation, isUpdate, globalDefinitionUpdateFunction, newName, message ) {


		if ( jsonForms.areThereErrors() ) {
			return;
		}
		// need sync _serviceJson with editors
		if ( _isJsonEditorActive ) {
			// only submit if parsing is passing
			// if ( !parseAndUpdateJsonEdits( $( _definitionTextAreaId ) ) ) {


			if ( !jsonForms.parseAndUpdateJsonEdits( _serviceJson, $( _definitionTextAreaId ), _definitionTextAreaId ) ) {
				alertify.alert( "Parsing errors must be corrected prior to further processing" );
				return;
			} else {
				_serviceJson = JSON.parse( $( _definitionTextAreaId ).val() );
			}
		}


		var paramObject = {
			operation: operation,
			newName: newName,
			releasePackage: $( ".releasePackage" ).val(),
			serviceName: $( "#dialogServiceSelect" ).val(),
			hostName: _hostName,
			definition: JSON.stringify( _serviceJson, null, "\t" ),
			message: "Service Settings: " + message
		};
		console.log( "updateServiceDefinition(): ", paramObject );



		if ( operation == "notify" ) {

			$.extend( paramObject, {
				itemName: paramObject.serviceName
			} )
			$.post( serviceDefUrl + "/../notify", paramObject )
					.done( function ( updatesResult ) {
						alertify.alert( "Changes Submitted For Review", JSON.stringify( updatesResult, null, "\t" ) );
					} )
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

		$.post( serviceDefUrl, paramObject )

				.done( function ( updatesResult ) {

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
								jsonForms.closeDialog();
							}
						}


						if ( _refreshLifecycleFunction != null ) {
							_refreshLifecycleFunction();
						} else {
							console.log( "Skipping refreshFunction" );
						}
					}


					if ( !isUpdate ) {
						$userMessage.append( '<div class="info">Uncheck validation only to commit changes.</div>' );
					}

					if ( updatesResult.message ) {
						$userMessage.append( "<br/><br/>" + updatesResult.message );
					}


					if ( globalDefinitionUpdateFunction != null ) {
						globalDefinitionUpdateFunction( true );
					}

					jsonForms.showUpateResponseDialog( resultsTitle, $userMessage.html(), okFunction );


				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					failedToGetService( "updating" + serviceDefUrl, errorThrown );
					//alertify.error( "Unable to retreive service", "Url: " + serviceDefUrl + "<br>" );
				} );
	}
	;



	function failedToGetService( command, errorThrown ) {

		if ( errorThrown == "abort" ) {
			console.log( "Request was aborted: " + command );
			return;
		}
		var message = "Failed to get service: ";
		message += "<br><br>Command: " + command
		message += '<br><br>Server Response:<pre class="error" >' + errorThrown + "</pre>";

		var errorDialog = alertify.alert( message );

		errorDialog.setting( {
			title: "Unable to get/update service instance",
			resizable: false,
			'labels': {
				ok: 'Close'
			},
			'onok': function () {
				// document.location.reload( true );
			}

		} );

		$( 'body' ).css( 'cursor', 'default' );
	}


} );
