define( ["./validation-handler", "./json-forms"], function ( validationHandler, jsonForms ) {

	console.log( "Module loaded" );

	var _editPanel = "#settingsEditor";
	var _container = window;
	var _definitionTextAreaId = "#serviceJson";
	var _performanceLabels = null;
	var HOST_REAL_TIME_LABEL="HostRealTime";

	var _settingsJson = null;
	var _isJsonEditorActive = false;

	var _currentDialog = null;

	var _dialogId = "settingsDialog";


	var _defaultValues = {
		"references": {
			"variableName": "ValueToBeSet"
		},
		"metricsCollectionInSeconds": {
			"realTimeMeters": [
				{
					"label": "VM coresActive",
					"id": "vm.coresActive",
					"intervals": [
						3,
						5,
						10
					],
					"min": 0
				},
				{
					"label": "CsAgent Cpu (Total)",
					"id": "process.topCpu_CsAgent",
					"intervals": [
						10,
						30,
						100
					]
				}
			],
			"trending": [
				{
					"label": "Cores Active",
					"report": "custom/core",
					"metric": "coresUsed",
					"divideBy": "1"
				},
				{
					"label": "Vm Threads",
					"report": "vm",
					"metric": "threadsTotal",
					"divideBy": "numberOfSamples"
				},
				{
					"label": "CsAgent Socket Count",
					"report": "service/detail",
					"metric": "socketCount",
					"serviceName": "CsAgent",
					"divideBy": "numberOfSamples"
				},
				{
					"label": "CsAgent OS Commands",
					"report": "jmxCustom/detail",
					"metric": "OsCommandsCounter",
					"serviceName": "CsAgent",
					"divideBy": "numberOfSamples"
				}
			]
		}

	}

	return {
		//
		showSettingsDialog: function ( editDialogHtml ) {
			//when testing standalone, tests without
			if ( !jsonForms.isDialogBuilt( _dialogId ) ) {
				// do it only once.
				_container = ".ajs-dialog "
				_editPanel = _container + _editPanel;
				_definitionTextAreaId = _container + _definitionTextAreaId;
			}

			return showSettingsDialog( editDialogHtml );
		},
		getSettingsDefinition: function () {
			getSettingsDefinition();
		},
		registerDialogButtons: function () {
			registerInputEvents();
		},
		updateSettingsDefinition: function () {
			updateSettingsDefinition( true );
		},
		validateSettingsDefinition: function () {
			updateSettingsDefinition( false );
		},
		configureForTest: function () {

			getSettingsDefinition();
			registerUiComponents();
			jsonForms.registerOperations( updateSettingsDefinition );
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
					getSettingsDefinitionSuccess( _settingsJson );
				}

			},
			activate: function ( event, ui ) {
				console.log( "registerUiComponents(): activating: " + ui.newTab.text() );

				_isJsonEditorActive = false;
				if ( ui.newTab.text().indexOf( "Editor" ) != -1 ) {
					activateJsonEditor();
				}

				if ( ui.newTab.text().indexOf( "Real Time" ) != -1 ) {
					populateRealTimeTable();
				}
				if ( ui.newTab.text().indexOf( "Trends" ) != -1 ) {
					populateTrendingTable();
				}


				jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );

			}
		} );

		$( ".dialogLifeSelect" ).selectmenu( {
			width: "200px",
			change: function () {
				console.log( "showSettingsDialog editing env: " + $( this ).val() );
				getSettingsDefinition( $( this ).val() )
			}
		} );


		registerInputEvents();

		$( _editPanel ).show();

	}

	function activateJsonEditor() {

		_isJsonEditorActive = true;
		$( _definitionTextAreaId ).val( JSON.stringify( _settingsJson, null, "\t" ) );
		jsonForms.resizeLinedText( $( _definitionTextAreaId ), _container, _editPanel );
	}


	function showSettingsDialog( editDialogHtml ) {
		jsonForms.showDialog(
				_dialogId,
				"Lifecycle Configuration",
				editDialogHtml,
				updateSettingsDefinition );

		registerUiComponents();
		getSettingsDefinition( $( "#dialogLifeSelect" ).val() );

	}

	function getSettingsConfiguration( lifeToEdit ) {

		console.log( "getServices(): " + settingsDefUrl + " lifeToEdit: " + lifeToEdit );


		$.getJSON( settingsDefUrl + "/config", {
			lifeToEdit: lifeToEdit

		} ).done( function ( settingsConfig ) {
			var $servicesSelect = $( "#settingsTemplates select.services" );
			$servicesSelect.empty();

			jQuery( '<option/>', {
				text: "All Services",
				value: "all"
			} ).appendTo( $servicesSelect );

			for ( var i = 0; i < settingsConfig.services.length; i++ ) {
				jQuery( '<option/>', {
					text: settingsConfig.services[ i ]
				} ).appendTo( $servicesSelect );
			}

			$servicesSelect.sortSelect();

			_performanceLabels = settingsConfig.performanceLabels;



		} ).fail( function ( jqXHR, textStatus, errorThrown ) {

			handleConnectionError( "getSettingsDefinition ", errorThrown );

		} );
	}

	function getSettingsDefinition( lifeToEdit ) {

		console.log( "getSettingsDefinition(): " + settingsDefUrl + " lifeToEdit: " + lifeToEdit );

		$( ".settingsLoading" ).show();
		$.getJSON( settingsDefUrl, {
			lifeToEdit: lifeToEdit


		} ).done( function ( settingsJson ) {
			getSettingsDefinitionSuccess( settingsJson );

			getSettingsConfiguration( lifeToEdit );

		} ).fail( function ( jqXHR, textStatus, errorThrown ) {

			handleConnectionError( "getSettingsDefinition ", errorThrown );
		} );
	}

	function getSettingsDefinitionSuccess( settingsJson ) {
		_settingsJson = settingsJson;

		if ( _settingsJson.lastModifiedBy == undefined ) {
			$( ".lastModified" ).hide();
		} else {
			$( ".lastModified .noteAlt" ).text( _settingsJson.lastModifiedBy );
		}

		$( _definitionTextAreaId ).val( JSON.stringify( settingsJson, null, "\t" ) );

		// update form values

		jsonForms.loadValues( _editPanel, settingsJson, _defaultValues );


		//jsonForms.resizeVisibleEditors();
		jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );
		$( ".settingsLoading" ).hide();
		if ( $( "#jsonEditor" ).is( ":visible" ) ) {
			activateJsonEditor();
		}
		if ( $( "#realtime" ).is( ":visible" ) ) {
			populateRealTimeTable();
		}
		if ( $( "#trending" ).is( ":visible" ) ) {
			populateTrendingTable();
		}
	}

	function getDefinition() {
		console.log( "getDefinition()" );
		return _settingsJson;
	}
// Need to register events after dialog is loaded
	function registerInputEvents() {


		jsonForms.configureJsonEditors( getDefinition, _container, _editPanel, _definitionTextAreaId );

		$( ".toggleRealTimeButton" ).click( function () {
			console.log( "toggleRealTimeButton toggling display" );
			$( ".realTimeViewContainer" ).toggle();
			if ( $( "table.realTimeTable" ).is( ":visible" ) ) {
				//parseAndUpdateJsonEdits( $( ".realTimeViewContainer textarea" ) );
				jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( ".realTimeViewContainer textarea" ) )
				$( ".toggleRealTimeButton" ).text( "Show Editor" );
				populateRealTimeTable();
			} else {
				// update text contents
				getSettingsDefinitionSuccess( _settingsJson );
				// resizeVisibleEditors();

				jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );
				$( ".toggleRealTimeButton" ).text( "Show Summary" );
			}
		} );
		$( ".toggleTrendingButton" ).click( function () {
			console.log( "toggleTrendingButton toggling display" );
			$( ".trendingViewContainer" ).toggle();
			if ( $( "table.trendingTable" ).is( ":visible" ) ) {
				//parseAndUpdateJsonEdits( $( ".realTimeViewContainer textarea" ) );
				jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( ".trendingViewContainer textarea" ) )
				$( ".toggleTrendingButton" ).text( "Show Editor" );
				populateTrendingTable();
			} else {
				// update text contents
				getSettingsDefinitionSuccess( _settingsJson );
				// resizeVisibleEditors();

				jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel );
				$( ".toggleTrendingButton" ).text( "Show Summary" );
			}
		} );

		console.log( "registerInputEvents(): register events" );

		$( _editPanel + " input," + _editPanel + " select" ).change( function () {
			jsonForms.updateDefinition( _settingsJson, $( this ) );
		} );

	}

	function populateRealTimeTable() {
		console.log( "populateRealTimeTable Refresh" );
		var $tbody = $( "table.realTimeTable tbody" );
		$tbody.empty();

		var meters = _settingsJson.metricsCollectionInSeconds.realTimeMeters;
		var numRows = 0;
		for ( var i = 0; i < meters.length; i++ ) {

			var meter = meters[ i ];
			if ( !meter.id ) {
				alertify.alert( "Missing id field in item: " + i + " Use JSON Editor to correct." );
				continue;
			}

			var $meterRow = jQuery( '<tr/>', { 'data-order': i } );
			$meterRow.appendTo( $tbody );




			var $labelCell = jQuery( '<td/>', { } );
			$labelCell.appendTo( $meterRow );
			$labelCell.append( jQuery( '<img/>', {
				title: "Click/Drag to re-order", class: "moveRow", src: baseUrl + "/images/16x16/mail-send-receive.png"
			} ) );

			$labelCell.append( jQuery( '<img/>', {
				title: "Add a new row", class: "newRow", src: baseUrl + "/images/16x16/document-new.png"
			} ) );
			$labelCell.append( jQuery( '<img/>', {
				title: "Delete row", class: "deleteRow", src: baseUrl + "/images/16x16/process-stop.png"
			} ) );

			jQuery( '<div/>', {
				class: "tedit",
				"data-path": "metricsCollectionInSeconds.realTimeMeters[" + i + "].label",
				"data-iwidth": 150,
				text: meter.label
			} ).appendTo( $labelCell );


			// real time has complicated ID
			var ids = meter.id.split( "." );

			var performanceCategory = ids[0];
			$( "#settingsTemplates select.realtimeType" ).val( performanceCategory );
//			console.log( "found realtime: ", performanceCategory, " select:",
//					$( "#settingsTemplates select.realtimeType" ).val() );

			if ( $( "#settingsTemplates select.realtimeType" ).val() != "" ) {
				var optionText = $( "#settingsTemplates select.realtimeType option:selected" ).text();
				if ( optionText.length > 0 ) {
					performanceCategory = optionText;
				}
			}
			var attributes = ids[1].split( "_" );
			var serviceName = "---";
			var collectItem = "update";
			switch ( performanceCategory ) {
				case "Java" :
					serviceName = attributes[1];
					collectItem = attributes[0];
					break;

				case "App" :
					serviceName = ids[1];
					collectItem = ids[2];
					break;

				case "Host" :
					collectItem = ids[1];
					break;

				case "OS" :
					serviceName = attributes[1];
					collectItem = attributes[0];
					break;

				default:
					alertify.alert( "Invalid id field in item: " + i + " Use JSON Editor to correct: " + performanceCategory );
					continue;

			}

			if ( collectItem == undefined ) {
				collectItem = "update";
			}
			addEditableCell( $meterRow, "Service name for meter", serviceName, {
				path: "metricsCollectionInSeconds.realTimeMeters[" + i + "].id",
				iwidth: 100,
				servicename: serviceName,
				editvalue: serviceName,
				realtimefield: "service",
				realtimeid: meter.id,
				selectclass: "services",
			} );

			var label = findAttributeLabel( collectItem, performanceCategory, collectItem, serviceName );
			addEditableCell( $meterRow, "Performance Attribute", label, {
				path: "metricsCollectionInSeconds.realTimeMeters[" + i + "].id",
				iwidth: 200,
				perfcategory: performanceCategory,
				perfservice: serviceName,
				editvalue: collectItem,
				realtimefield: "attribute",
				realtimeid: meter.id,
			} );

			addEditableCell( $meterRow, "Performance Category", performanceCategory, {
				path: "metricsCollectionInSeconds.realTimeMeters[" + i + "].id",
				iwidth: 100,
				servicename: serviceName,
				editvalue: ids[0],
				realtimefield: "type",
				realtimeid: meter.id,
				selectclass: "realtimeType",
			} );


			var $intervalsCell = jQuery( '<td/>', { } );
			$intervalsCell.appendTo( $meterRow );

			jQuery( '<div/>', {
				class: "tedit",
				"data-json": true,
				"data-path": "metricsCollectionInSeconds.realTimeMeters[" + i + "].intervals",
				text: JSON.stringify( meter.intervals )
			} ).appendTo( $intervalsCell );


			var $reverseCell = jQuery( '<td/>', { } );
			$reverseCell.appendTo( $meterRow );

			var isReverse = "---";
			if ( meter.reverseColors )
				isReverse = meter.reverseColors;
			jQuery( '<div/>', {
				class: "tedit",
				"data-path": "metricsCollectionInSeconds.realTimeMeters[" + i + "].reverseColors",
				html: isReverse
			} ).appendTo( $reverseCell );



			var $divideCell = jQuery( '<td/>', { } );
			$divideCell.appendTo( $meterRow );

			var divideBy = "---";
			if ( meter.divideBy )
				divideBy = meter.divideBy;
			jQuery( '<div/>', {
				class: "tedit",
				"data-path": "metricsCollectionInSeconds.realTimeMeters[" + i + "].divideBy",
				html: divideBy
			} ).appendTo( $divideCell );




			var $multiplyCell = jQuery( '<td/>', { } );
			$multiplyCell.appendTo( $meterRow );

			var multiplyBy = "---";
			if ( meter.multiplyBy )
				multiplyBy = meter.multiplyBy;
			jQuery( '<div/>', {
				class: "tedit",
				"data-path": "metricsCollectionInSeconds.realTimeMeters[" + i + "].multiplyBy",
				html: multiplyBy
			} ).appendTo( $multiplyCell );


		}

		registerTableEditEvents( $tbody, _settingsJson.metricsCollectionInSeconds, "realTimeMeters", populateRealTimeTable );
	}

	function addEditableCell( $meterRow, title, label, attributes ) {

		var $cell = jQuery( '<td/>', { } );
		$cell.appendTo( $meterRow );

		var $editableDiv = jQuery( '<div/>', {
			class: "tedit",
			title: title,
			html: label
		} );

		/**
		 * path: used to update definition model
		 * iwidth = input width, editvalue = value of input select
		 * realtimeid = used for ID generation
		 * selectclass: use template for generating select
		 * perfcategory: generate select from performance labels
		 * servicename: only needed if perfcategory == App
		 * 
		 */
		for ( var attribute in attributes ) {
			// $editableDiv.data( attribute, attributes[attribute] );
			$editableDiv.attr( "data-" + attribute, attributes[attribute] );
		}

		$editableDiv.appendTo( $cell );

	}

	function populateTrendingTable() {
		console.log( "populateTrendingTable Refresh" );
		var $tbody = $( "table.trendingTable tbody" );
		$tbody.empty();

		var meters = _settingsJson.metricsCollectionInSeconds.trending;
		var numRows = 0;
		for ( var i = 0; i < meters.length; i++ ) {

			var meter = meters[ i ];

			var $meterRow = jQuery( '<tr/>', { 'data-order': i } );
			$meterRow.appendTo( $tbody );




			var $labelCell = jQuery( '<td/>', { } );
			$labelCell.appendTo( $meterRow );
			$labelCell.append( jQuery( '<img/>', {
				title: "Click/Drag to re-order", class: "moveRow", src: baseUrl + "/images/16x16/mail-send-receive.png"
			} ) );

			$labelCell.append( jQuery( '<img/>', {
				title: "Add a new row", class: "newRow", src: baseUrl + "/images/16x16/document-new.png"
			} ) );
			$labelCell.append( jQuery( '<img/>', {
				title: "Delete row", class: "deleteRow", src: baseUrl + "/images/16x16/process-stop.png"
			} ) );

			jQuery( '<div/>', {
				class: "tedit",
				title: "Keep to a reasonable width for UI",
				"data-path": "metricsCollectionInSeconds.trending[" + i + "].label",
				"data-iwidth": 250,
				text: meter.label
			} ).appendTo( $labelCell );



			var $nameCell = jQuery( '<td/>', { } );
			$nameCell.appendTo( $meterRow );

			var serviceName = "---";
			if ( meter.serviceName )
				serviceName = meter.serviceName;
			jQuery( '<div/>', {
				class: "tedit",
				title: "name of service(s), comma separated",
				"data-selectclass": "services",
				"data-iwidth": 150,
				"data-path": "metricsCollectionInSeconds.trending[" + i + "].serviceName",
				html: serviceName
			} ).appendTo( $nameCell );


			var $attributeCell = jQuery( '<td/>', { } );
			$attributeCell.appendTo( $meterRow );

			var attributeName = "---";
			if ( meter.metric ) {
				attributeName = meter.metric;
			}

			var reportName = meter.report, report = meter.report;

			$( "#settingsTemplates .trendType" ).val( report );
			//console.log("trendType",  $("#settingsTemplates select.trendType").val()) ;
			if ( $( "#settingsTemplates select.trendType" ).val() != "" ) {
				var optionText = $( "#settingsTemplates select.trendType option:selected" ).text();
				if ( optionText.length > 0 ) {
					reportName = optionText;
				}
			}

			// attribute Names
			attributeName = findAttributeLabel( attributeName, reportName, meter.metric, serviceName );

			jQuery( '<div/>', {
				class: "tedit",
				title: "name of attribute to trend",
				"data-perfservice": serviceName,
				"data-perfcategory": reportName,
				"data-editvalue": meter.metric,
				"data-iwidth": 150,
				"data-path": "metricsCollectionInSeconds.trending[" + i + "].metric",
				html: attributeName
			} ).appendTo( $attributeCell );


			var $reportCell = jQuery( '<td/>', { } );
			$reportCell.appendTo( $meterRow );

			jQuery( '<div/>', {
				class: "tedit",
				title: "report id",
				"data-selectclass": "trendType",
				"data-editvalue": report,
				"data-path": "metricsCollectionInSeconds.trending[" + i + "].report",
				html: reportName
			} ).appendTo( $reportCell );


//
//
			var $totalCell = jQuery( '<td/>', { } );
			$totalCell.appendTo( $meterRow );

			var isTotal = "---";
			if ( meter.allVmTotal )
				isTotal = meter.allVmTotal;
			jQuery( '<div/>', {
				class: "tedit",
				title: "if true,  trend value will be the sum of all instances",
				"data-path": "metricsCollectionInSeconds.trending[" + i + "].allVmTotal",
				html: isTotal
			} ).appendTo( $totalCell );
//
//
//
			var $divideCell = jQuery( '<td/>', { } );
			$divideCell.appendTo( $meterRow );

			var divideBy = "---";
			if ( meter.divideBy )
				divideBy = meter.divideBy;
			jQuery( '<div/>', {
				class: "tedit",
				title: "numberOfSamples can be used to show the average collected value. Multiple values can be comma separated."
						+ "0.5 can be used to multiple",
				"data-path": "metricsCollectionInSeconds.trending[" + i + "].divideBy",
				html: divideBy
			} ).appendTo( $divideCell );


		}

		registerTableEditEvents( $tbody, _settingsJson.metricsCollectionInSeconds, "trending", populateTrendingTable );

	}

	function registerTableEditEvents( $tbody, definitionContainer, attribute, populateFunction ) {
		$tbody.sortable( {
			handle: ".moveRow",
			update: function ( event, ui ) {
				//console.log( "reorderd rows: ", ui );
				//saveLayout( $graphContainer, $plotContainer );
				var currentItems = definitionContainer[ attribute ];
				var updatedItemIndexOrder = new Array();
				$( "tr", $tbody ).each( function () {
					updatedItemIndexOrder.push( $( this ).data( "order" ) );
				} );
				console.log( "row order: ", updatedItemIndexOrder );
				var itemsInNewOrder = new Array();
				for ( var i = 0; i < updatedItemIndexOrder.length; i++ ) {
					itemsInNewOrder.push( currentItems[ updatedItemIndexOrder[i] ] );
				}
				;
				definitionContainer[ attribute ] = itemsInNewOrder;

				console.log( "itemsInNewOrder: ", itemsInNewOrder );
				// need to redraw - because moves use the table row number
				populateFunction();
			}
		} );

		$( ".deleteRow", $tbody ).click( function () {
			var indexToDelete = $( this ).parent().parent().data( "order" );

			var currentItems = definitionContainer[ attribute ];
			var itemsInNewOrder = new Array();
			for ( var i = 0; i < currentItems.length; i++ ) {

				if ( i != indexToDelete )
					itemsInNewOrder.push( currentItems[ i ] );
			}
			;
			definitionContainer[ attribute ] = itemsInNewOrder;

			console.log( "itemsInNewOrder: ", itemsInNewOrder );
			populateFunction();
		} );


		$( ".newRow", $tbody ).click( function () {
			var indexToCopy = $( this ).parent().parent().data( "order" );

			var currentItems = definitionContainer[ attribute ];
			var itemsInNewOrder = new Array();
			for ( var i = 0; i < currentItems.length; i++ ) {

				itemsInNewOrder.push( currentItems[ i ] );
				if ( i == indexToCopy ) {
					// need a deep copy or same reference is in list twice.
					var newObject = jQuery.extend( { }, currentItems[ i ] );
					itemsInNewOrder.push( newObject );
				}
			}
			;
			definitionContainer[ attribute ] = itemsInNewOrder;

			console.log( "itemsInNewOrder: ", itemsInNewOrder );
			populateFunction();

		} );

		$( ".tedit", $tbody ).click( function () {
			makeItemEditable( $( this ), populateFunction );
		} );
	}

	function findAttributeLabel( labelDefault, reportName, metric, serviceName ) {
		var label = labelDefault;

		if ( reportName == "App" ) {
			if ( _performanceLabels[reportName]
					&& _performanceLabels[reportName][serviceName ]
					&& _performanceLabels[reportName][serviceName ][metric] ) {
				label = _performanceLabels[reportName][serviceName ][metric];
			}
		} else if ( _performanceLabels[reportName] && _performanceLabels[reportName][metric] ) {
			label = _performanceLabels[reportName][metric];

		} else if ( reportName == "Host" 
				&& _performanceLabels[HOST_REAL_TIME_LABEL] 
				&& _performanceLabels[HOST_REAL_TIME_LABEL][metric] ) {
			// alternal labels for real time graphs
			label = _performanceLabels[HOST_REAL_TIME_LABEL][metric];
		}
		
		if ( metric == "diskTest") {
			console.log("found label:", label, " list",  _performanceLabels[HOST_REAL_TIME_LABEL], metric) ;
		}

		return label
	}

	function makeItemEditable( $itemSelected, populateFunction ) {

		console.log( "makeItemEditable all data: ", $itemSelected.data() );
		var content = $itemSelected.text();
		if ( content == "---" )
			content = "---";
		var editValue = $itemSelected.data( "editvalue" );
		if ( editValue != undefined ) {
			content = editValue;
		}
		var path = $itemSelected.data( "path" );
		$itemSelected.off();
		$itemSelected.empty();


		var selectClass = $itemSelected.data( "selectclass" );
		var perfCategory = $itemSelected.data( "perfcategory" );
		var isRealTime = $itemSelected.data( "realtimefield" ) != undefined;
		var $editValueContainer = null;

		if ( perfCategory != undefined && _performanceLabels[perfCategory] ) {
			// generate select

			console.log( "isRealTime now:", isRealTime, "Generating select using performance", perfCategory
					, " current value: ", editValue );
			$editValueContainer = jQuery( '<select/>', {
				class: "",
				"data-json": $itemSelected.data( "json" ),
				"data-path": path
			} )

			var labelKey = perfCategory;
			if ( isRealTime && perfCategory == "Host") {
				labelKey=HOST_REAL_TIME_LABEL ;
			}
			// var options = $( "#settingsTemplates select." + selectClass ).html();
			var attributes = _performanceLabels[labelKey];
			console.log( "attributes", attributes );
			if ( perfCategory == "App" ) {
				attributes = attributes [ $itemSelected.data( "perfservice" ) ];
			}
			for ( var attributeId in attributes ) {
				jQuery( '<option/>', {
					value: attributeId,
					text: attributes[attributeId]
				} ).appendTo( $editValueContainer );
			}

			$editValueContainer.val( content );
			$editValueContainer.change( function () {

				setTimeout( function () {
					populateFunction();
				}, 1000 );
			} );
			$editValueContainer.sortSelect();

		} else if ( selectClass != undefined && selectClass != "none" ) {
			console.log( "Generating select using selectclass", selectClass, editValue );
			$editValueContainer = jQuery( '<select/>', {
				class: "",
				"data-json": $itemSelected.data( "json" ),
				"data-path": path
			} )

			var options = $( "#settingsTemplates select." + selectClass ).html();
			$editValueContainer.html( options );


			// console.log( options );

			$editValueContainer.val( content );
			$editValueContainer.change( function () {

				setTimeout( function () {
					populateFunction();
				}, 1000 );
			} );
		} else {
			// default to input
			$editValueContainer = jQuery( '<input/>', {
				class: "",
				"data-json": $itemSelected.data( "json" ),
				"data-path": path,
				value: content
			} )
		}
		$editValueContainer.change( function () {
			var realTimeId = $itemSelected.data( "realtimeid" );
			var oldValue = $itemSelected.data( "editvalue" );
			var field = $itemSelected.data( "realtimefield" );
			var newValue = $( this ).val();

			if ( realTimeId != undefined && oldValue != undefined ) {

				var newId = realTimeId.replace( oldValue, newValue );
				if ( field == "type" ) {
					var serviceName = $itemSelected.data( "servicename" );
					// need to reorder based on type
					switch ( newValue ) {
						case "process" :
							newId = "process.UpdateThis_" + serviceName;
							break;

						case "jmxCustom" :
							newId = "jmxCustom." + serviceName + ".UpdateThis";
							break;

						case "jmxCommon" :
							newId = "jmxCommon.UpdateThis_" + serviceName;
							break;

						case "vm" :
							newId = "vm.UpdateThis";
							break;
					}
				}
				var $realTimeInput = $editValueContainer = jQuery( '<input/>', {
					"data-path": path,
					value: newId
				} );
				jsonForms.updateDefinition( _settingsJson, $realTimeInput );

			} else {
				jsonForms.updateDefinition( _settingsJson, $( this ) );
			}
		} );

		var inputWidth = Math.round( $itemSelected.parent().outerWidth() - 30 );
		if ( editValue != undefined ) {
			inputWidth = content.length * 7;
		}
		var iwidth = $itemSelected.data( "iwidth" );
		if ( iwidth != undefined ) {
			inputWidth = iwidth;
		}
		$editValueContainer.css( "width", inputWidth );
		$editValueContainer.css( "margin-right", 0 );
		$itemSelected.append( $editValueContainer );

	}



	function updateSettingsDefinition( operation, isUpdate, globalDefinitionUpdateFunction, newName, message ) {

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
			console.log( "updateSettingsDefinition() - setting json" );
			_settingsJson = JSON.parse( $( _definitionTextAreaId ).val() );

		}

		var lifeToEdit = $( "#dialogLifeSelect" ).val();
		var paramObject = {
			lifeToEdit: lifeToEdit,
			definition: JSON.stringify( _settingsJson, null, "\t" ),
			message: "Lifecycle Settings: " + message
		};

		console.log( "updateSettingsDefinition(): ", paramObject );

		if ( operation == "notify" ) {
			$.extend( paramObject, {
				itemName: paramObject.lifeToEdit,
				hostName: "*"
			} )
			$.post( clusterDefUrl + "/../notify", paramObject )
					.done( function ( updatesResult ) {
						alertify.alert( "Changes Submitted For Review", JSON.stringify( updatesResult, null, "\t" ) );
					} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {
						alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
					} );
			return false;
		}

		var resultsTitle = "Results for Operation: " + operation + ", Lifecycle: " + lifeToEdit;
		if ( isUpdate ) {
			$.extend( paramObject, {
				isUpdate: isUpdate
			} );
		} else {
			resultsTitle += " - Validation Only";
		}

		$.post( settingsDefUrl, paramObject )

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
						okFunction = function () {
							console.log( "Closed results" );
							jsonForms.closeDialog();
						}
						$userMessage.append( $moreInfo );
					}

					if ( globalDefinitionUpdateFunction != null ) {
						globalDefinitionUpdateFunction( true );
					}

					//alertify.alert( resultsTitle, $userMessage.html(), okFunction );
					jsonForms.showUpateResponseDialog( resultsTitle, $userMessage.html(), okFunction );


				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "updating" + settingsDefUrl, errorThrown );
					alertify.error( "Failed" );
				} );
	}
	;


} );
