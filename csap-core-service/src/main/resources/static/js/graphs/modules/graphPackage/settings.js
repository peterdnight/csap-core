define( ["./graphLayout"], function ( graphLayout ) {

	console.log( "Module loaded: graphPackage/settings" );

	var startButtonUrl = uiSettings.baseUrl + "images/16x16/start.jpg";
	var pauseButtonUrl = uiSettings.baseUrl + "images/16x16/pause.png";
	
	return {
		//
		uiComponentsRegistration: function ( resourceGraph ) {

			miscSetup( resourceGraph );

			zoomSetup( resourceGraph );

			layoutSetup( resourceGraph );

			datePickerSetup( resourceGraph );

			numberOfDaysSetup( resourceGraph );
		},
		//
		addCustomViews: function ( resourceGraph, customViews ) {
			addCustomViews( resourceGraph, customViews );
		},
		flashDialogButton: function ( isFlash, $container ) {
			flashDialogButton( isFlash, $container );
		},
		//
		dialogSetup: function ( settingsChangedCallback, $GRAPH_INSTANCE ) {

			dialogSetup( settingsChangedCallback, $GRAPH_INSTANCE );
		},
		//
		modifyTimeSlider: function ( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) {
			modifyTimeSlider( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph );
		},
		//
		addToolsEvents: function () {
			addToolsEvents();
		},
		//
		addContainerEvents: function ( resourceGraph, container ) {
			addContainerEvents( resourceGraph, container );
		},
		postDrawEvents: function ( $newGraphContainer, $GRAPH_INSTANCE, numDays ) {
			postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, numDays );
		}
	};

	function miscSetup( resourceGraph ) {


		var resizeTimer = 0;
		$( window ).resize( function () {

			if ( !resourceGraph.getCurrentGraph().is( ':visible' ) )
				return;
			clearTimeout( resizeTimer );
			resizeTimer = setTimeout( function () {
				console.log( "window Resized" );
				resourceGraph.reDraw();
			}, 300 );

		} );

		jQuery( '.numbersOnly' ).keyup( function () {
			this.value = this.value.replace( /[^0-9\.]/g, '' );
		} );

		// refreshButton
		$( ".refreshGraphs", resourceGraph.getCurrentGraph() ).click( function () {
			resourceGraph.settingsUpdated();
			return false; // prevents link
		} );

		$( ".graphTimeZone", resourceGraph.getCurrentGraph() ).change( function () {
			console.log( "graphTimeZone" );
			resourceGraph.reDraw();
			return false; // prevents link
		} );

		$( ".useLineGraph", resourceGraph.getCurrentGraph() ).change( function () {
			console.log( "useLineGraph" );
			resourceGraph.reDraw();
			return false; // prevents link
		} );

	}

	function layoutSetup( resourceGraph ) {

		var $graphContainer = resourceGraph.getCurrentGraph();

		$( ".savePreferencesButton", $graphContainer ).click( function () {
			showSavePreferencesDialog( resourceGraph );
		} ).hide();

		$( ".layoutSelect", $graphContainer ).selectmenu( {
			width: "10em",
			change: function () {
				layoutChanged( resourceGraph )
			}
		} );
	}

	function layoutChanged( resourceGraph ) {

		var $graphContainer = resourceGraph.getCurrentGraph();
		var selectedLayout = $( ".layoutSelect", $graphContainer ).val();
		console.log( "layout selected: " + selectedLayout );

		$( ".savePreferencesButton", $graphContainer ).show();
		switch ( selectedLayout ) {
			case "spotlight1":
				setGraphsSize( "18%", "15%", $graphContainer, 1 );
				break;
			case "spotlight2":
				setGraphsSize( "18%", "15%", $graphContainer, 2 );
				break;
			case "small":
				setGraphsSize( "20%", "15%", $graphContainer );
				break;
			case "smallWide":
				setGraphsSize( "100%", "15%", $graphContainer );
				break;
			case "medium":
				setGraphsSize( "30%", "25%", $graphContainer );
				break;
			case "mediumWide":
				setGraphsSize( "100%", "30%", $graphContainer );
				break;
		}

		resourceGraph.reDraw();
	}

	function addCustomViews( resourceGraph, customViews ) {

		var $graphContainer = resourceGraph.getCurrentGraph();
		var $customSelect = $( ".customViews", $graphContainer );
		$customSelect.parent().show();
		for ( var viewKeys in customViews ) {
			var optionItem = jQuery( '<option/>', {
				value: viewKeys,
				text: viewKeys
			} );
			$customSelect.append( optionItem );
		}
		$( ".customViews", $graphContainer ).selectmenu( {
			width: "10em",
			change: function () {

				var selectedView = $( this ).val();
				console.log( "customViews selected: " + selectedView );

				var layouts = "default";
				if ( selectedView != "all" )
					layouts = "mediumWide";
				$( ".layoutSelect", $graphContainer ).val( layouts );
				$( ".layoutSelect", $graphContainer ).selectmenu( "refresh" );
				layoutChanged( resourceGraph );
				// _currentResourceGraphInstance.reDraw();
			}
		} );
	}

	function setGraphsSize( width, height, $graphContainer, spotIndex ) {
		// save all sizes
		$( ".plotContainer > div.plotPanel", $graphContainer ).each( function ( index ) {
			var graphName = $( this ).data( "graphname" );

			// console.log("setGraphsSize(): " + spotIndex + " graph: " + graphName + " index: " + index) ;
			var sizeObject = {
				width: width,
				height: height
			};

			if ( spotIndex == 1 && index == 0 ) {
				sizeObject.width = "50%";
				sizeObject.height = "50%";
			}
			if ( spotIndex == 2 && index <= 1 ) {
				sizeObject.width = "45%";
				sizeObject.height = "50%";
			}
			graphLayout.setSize( graphName, sizeObject, $graphContainer, $( this ).parent() )
		} );
	}

	function showSavePreferencesDialog( resourceGraph ) {
		// 

		if ( !alertify.graphPreferences ) {
			var message = "Saving current settings will enable these to be used on all labs in all lifecycles"
			var startDialogFactory = function factory() {
				return{
					build: function () {
						// Move content from template
						this.setContent( message );
						this.setting( {
							'onok': function () {
								var $graphContainer = resourceGraph.getCurrentGraph();

								var resetResource = false;
								if ( $( ".layoutSelect", $graphContainer ).val() == "default" ) {
									resetResource = $graphContainer.data( "preference" );
								}
								graphLayout.publishPreferences( resetResource );
							},
							'oncancel': function () {
								alertify.warning( "Cancelled Request" );
							}
						} );
					},
					setup: function () {
						return {
							buttons: [{ text: "Save current settings", className: alertify.defaults.theme.ok },
								{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
							],
							options: {
								title: "Save Current Layout :", resizable: false, movable: false, maximizable: false,
							}
						};
					}

				};
			};
			alertify.dialog( 'graphPreferences', startDialogFactory, false, 'confirm' );
		}

		alertify.graphPreferences().show();
	}

	function zoomSetup( resourceGraph ) {
		var $graphContainer = resourceGraph.getCurrentGraph();
		var max = $( "#numSamples > option", $graphContainer ).length;
		var current = $( "#numSamples", $graphContainer ).prop(
				"selectedIndex" );

		$( ".zoomSelect", $graphContainer ).empty();

		// each instance is copied from the base definition
		for ( var i = 0; i < max; i++ ) {
			var itemInZoom = $( "#numSamples > option:nth-child(" + (i + 1) + ")", $graphContainer ).text();
			var sel = "";
			if ( i == current )
				sel = 'selected="selected"';
			$( ".zoomSelect", $graphContainer ).append(
					"<option " + sel + " >" + itemInZoom + "</option>" );
		}

		var zoomChange = function () {
			console.log( "Zoom changed" );
			$( "#numSamples", $graphContainer ).prop(
					"selectedIndex",
					$( ".zoomSelect", $graphContainer ).prop(
					"selectedIndex" ) );
			console.log( "Zoom changed: " + $( "#numSamples", $graphContainer ).val() );
			// alertify.notify(" Selected: " + $( this ).val())
			if ( $( "#numSamples", $graphContainer ).val() != "99999" ) {
				$( ".sliderContainer", $graphContainer ).show();
			} else {
				$(".useLineGraph", $graphContainer ).prop("checked","checked") ; 
				
				$( ".sliderContainer", $graphContainer ).hide();
			}

			resourceGraph.reDraw();
		}

		$( ".zoomSelect", $graphContainer ).selectmenu( {
			width: "11em",
			change: zoomChange
		} );
		
		$( ".meanFilteringSelect", $graphContainer ).selectmenu( {
			width: "4em",
			change: function() {
				resourceGraph.reDraw();
			}
		} );




	}

	function datePickerSetup( resourceGraph ) {

		var $graphContainer = resourceGraph.getCurrentGraph();

		console.log( "Registering datepicker: local time: " + new Date() + ",  Us Central: " + getUsCentralTime( new Date() ) );

		$( ".datepicker", $graphContainer ).datepicker( {
			defaultDate: getUsCentralTime( new Date() ),
			maxDate: '0',
			minDate: '-120'
		} );

		$( ".datepicker", $graphContainer ).css( "width", "7em" );

		$( ".datepicker", $graphContainer ).change( function () {
			// $(".daySelect", $GRAPH_INSTANCE).val("...");
			if ( $( ".numDaysSelect", $graphContainer ).val() == 0 )
				$( ".numDaysSelect", $graphContainer ).val( 1 );

			var dayOffset = calculateUsCentralDays( $( this ).datepicker(
					"getDate" ).getTime() );

			console.log( "dayOffset: " + dayOffset );

			$( ".useHistorical", $graphContainer ).prop( 'checked',
					true );
			$( ".historicalOptions", $graphContainer ).css(
					"display", "inline-block" );

			$( "#dayOffset", $graphContainer ).val( dayOffset );

			resourceGraph.settingsUpdated();

			return false; // prevents link
		} );
	}


	// binds the select
	function numberOfDaysSetup( resourceGraph ) {

		var $graphContainer = resourceGraph.getCurrentGraph();

		uiSetupForNumberOfDays( $graphContainer );

		// Handle change events
		$( ".numDaysSelect", $graphContainer ).change(
				function () {
					var numberOfDaysSelected = $( ".numDaysSelect",
							$graphContainer ).val();

					console.log( "setupNumberOfDaysChanged(): " + numberOfDaysSelected );

					if ( numberOfDaysSelected == 0 ) {
						$( ".useHistorical", $graphContainer ).prop(
								'checked', false );
					} else {
						$( ".useHistorical", $graphContainer ).prop(
								'checked', true );
					}

					// updates the dropDown
					$( "#numberOfDays", $graphContainer )
							.val( numberOfDaysSelected );

					resourceGraph.settingsUpdated();

					return false; // prevents link
				} );
	}

	function uiSetupForNumberOfDays( $graphContainer ) {
		if ( $( ".numDaysSelect", $graphContainer ).val() == 0 ) {
			$( ".useHistorical", $graphContainer ).prop( 'checked', false );
			$( ".historicalOptions", $graphContainer ).hide();
		} else {
			$( ".useHistorical", $graphContainer ).prop( 'checked', true );
			$( ".historicalOptions", $graphContainer ).css( "display",
					"inline-block" );
		}

		if ( $( ".numDaysSelect", $graphContainer ).length < 5 ) {
			for ( var i = 2; i <= 14; i++ ) {
				$( ".numDaysSelect", $graphContainer ).append(
						'<option value="' + i + '" >' + i + " days</option>" );
			}

			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="21" >3 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="28" >4 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="42" >6 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="56" >8 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="112" >16 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="256" >32 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="336" >48 Weeks</option>' );
			$( ".numDaysSelect", $graphContainer ).append(
					'<option value="999" >All</option>' );
		}
	}

	function flashDialogButton( isFlash, $container ) {
		console.log( "flashDialogButton() " + isFlash );
		if ( isFlash ) {
			$( ".settingsText", $container ).text( "Settings*" )
					.css( "font-size", "0.7em" );
			$( ".showSettingsDialogButton", $container ).animate( {
				"background-color": "#F7C0BE"
			}, 1000 );
			$( ".showSettingsDialogButton", $container ).fadeOut( "fast" );
			$( ".showSettingsDialogButton", $container ).fadeIn( "fast" );
		} else {
			$( ".settingsText", $container ).text( "Settings" );
			$( ".showSettingsDialogButton" ).css( "background-color", "none" ).css(
					"font-size", "1em" );
		}
	}

	function dialogSetup( settingsChangedCallback, $GRAPH_INSTANCE ) {


		// $('.padLatest', $GRAPH_INSTANCE).prop("checked", false) ;

		$( ".resourceConfigDialog .sampleIntervals, .padLatest" ).click(
				function () {
					$( ".resourceConfigDialog .useAutoInterval" )
							.prop( 'checked', false )
							.parent().css( "background-color", "yellow" );
				} );

		$( '.showSettingsDialogButton', $GRAPH_INSTANCE ).click( function () {

			try {
				dialogShow( settingsChangedCallback, $GRAPH_INSTANCE );
			} catch ( e ) {
				console.log( e );
			}

			setTimeout( function () {
				$( '[title!=""]', "#graphSettingsDialog" ).qtip( {
					content: {
						attr: 'title',
						button: true
					},
					style: {
						classes: 'qtip-bootstrap'
					}
				} );
			}, "1000" );

			$( ".pointToolTip" ).hide();


			return false; // prevents link
		} );
	}

	/**
	 * Static function:  Alertify usage is non-trivial as scope for function
	 * is inside ResourceGraph instances, and there are multple instances of Graph.
	 * Unlike typical usage, Resource Graph content is pulled in when launched, and moved
	 * back to original DOM location in order for 
	 */
	function dialogShow( settingsChangedCallback, $GRAPH_INSTANCE ) {

		var dialogId = "graphSettingsDialog";

		if ( !alertify.graphSettings ) {

			console.log( "Building: dialogId: " + dialogId );
			var settingsFactory = function factory() {
				return{
					build: function () {
						// Move content from template
						this.setContent( '<div id="' + dialogId + '"></div>' );

					},
					setup: function () {
						return {
							buttons: [{ text: "Refresh Graphs", className: alertify.defaults.theme.ok, key: 27/* Esc */ }],
							options: {
								title: "Graph Settings :", resizable: true, movable: false, maximizable: false
							}
						};
					}

				};
			};

			alertify.dialog( 'graphSettings', settingsFactory, false, 'alert' );
		}

		var settingsDialog = alertify.graphSettings();

		// Settings from associated ResourceGraph moved into dialog
		$( "#" + dialogId ).append( $( ".resourceConfigDialog", $GRAPH_INSTANCE ) );

		settingsDialog.setting( {
			'onclose': function () {
				// Settings moved back to original location
				$( ".resourceConfig", $GRAPH_INSTANCE ).append( $( ".resourceConfigDialog", "#" + dialogId ) );
				settingsChangedCallback();
			}
		} );


		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;
		settingsDialog.resizeTo( targetWidth, targetHeight );
		
		var numColumns = Math.round( targetWidth / 240 ) ;
		$(".graphCheckboxes").css("column-count", numColumns ) ;
		$(".serviceCheckboxes").css("column-count", numColumns ) ;
		$("#jmxCustomServicesDiv").css("column-count", numColumns ) ;
		

		settingsDialog.show();
	}

	function modifyTimeSlider( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) {

		if ( sampleTimeArray.length <= 0 ) {

			alertify
					.alert( "No data available in selected range. Select another range or try again later." );
			return;
		}
		var maxItems = sampleTimeArray.length - 1;

		var d = new Date( parseInt( descTimeArray[maxItems] ) );
		var mins = d.getMinutes();
		if ( mins <= 9 )
			mins = "0" + mins;
		var formatedDate = d.getHours() + ":" + mins + " "
				+ $.datepicker.formatDate( 'M d', d );

		$( ".sliderTimeStart", $newGraphContainer ).val( formatedDate );

		// alert (maxItems + " timerArray[0]: " + timerArray[0] + "
		// timerArray[maxItems]:" + timerArray[maxItems]) ;
		// alert (new Date(parseInt(sampleTimeArray[0]))) ;

		var hostAuto = $( ".autoRefresh", $newGraphContainer );

		var minSlider = 0;
		var numSamples = $( "#numSamples", resourceGraph.getCurrentGraph() ).val();
		if ( sampleTimeArray.length > numSamples ) {
			minSlider = numSamples - 5;
		} else if ( sampleTimeArray.length > 10 ) {
			minSlider = 10;
		}

		var sliderConfig = {
			value: maxItems,
			min: minSlider,
			max: maxItems,
			step: 1,
			slide: function ( event, ui ) {
//					 setSliderLabel( $newGraphContainer,
//								descTimeArray[maxItems - ui.value] );

				resourceGraph.clearRefreshTimers();
				sliderUpdatePosition( $slider, ui.value, resourceGraph, sampleTimeArray, descTimeArray );
			},
			stop: function ( event, ui ) {
				resourceGraph.clearRefreshTimers();
				sliderUpdatePosition( $slider, ui.value, resourceGraph, sampleTimeArray, descTimeArray );
			}
		}


		var $slider = $( ".resourceSlider", $newGraphContainer ).slider( sliderConfig );


		$( ".playTimelineButton", $newGraphContainer ).off().click( function () {

			var $buttonImage = $( "img", $( this ) );
			resourceGraph.clearRefreshTimers();

			if ( $buttonImage.attr( "src" ) == startButtonUrl ) {
				isKeepPlaying = false;
				$buttonImage.attr( "src", pauseButtonUrl );
				var currentLocation = $slider.slider( "value" );
				;
				if ( currentLocation > (maxItems - 10) )
					$slider.slider( "value", 0 ); // restart or resume
				setTimeout( function () {
					isKeepPlaying = true;
					playSlider( 1, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray );
				}, 500 )
			} else {
				isKeepPlaying = false;
				$buttonImage.attr( "src", startButtonUrl );
			}


		} );
		$( ".playTimelineBackButton", $newGraphContainer ).off().click( function () {

			var $buttonImage = $( "img", $( this ) );
			resourceGraph.clearRefreshTimers();

			if ( $buttonImage.attr( "src" ) == startButtonUrl ) {
				isKeepPlaying = false;
				$buttonImage.attr( "src", pauseButtonUrl );
				var currentLocation = $slider.slider( "value" );
				var zoomSetting = $( "#numSamples", resourceGraph.getCurrentGraph() ).val();
				if ( (currentLocation - zoomSetting) <= 10 )
					$slider.slider( "value", maxItems ); // restart or resume
				setTimeout( function () {
					isKeepPlaying = true;
					playSlider( -1, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray );
				}, 500 )
			} else {
				isKeepPlaying = false;
				$buttonImage.attr( "src", startButtonUrl );
			}


		} );

		sliderUpdatePosition( $slider, maxItems, resourceGraph, sampleTimeArray, descTimeArray );
	}


	var isKeepPlaying = true;
	function playSlider( offset, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray ) {

		var $graphContainer = resourceGraph.getCurrentGraph();
		var newPosition = $slider.slider( "value" ) + offset;

		var zoomSetting = $( "#numSamples", $graphContainer ).val();
//		  console.log( "Starting to play: " + newPosition + " max:" + descTimeArray.length
//					 + " samples: " + zoomSetting );

		if ( offset > 0 && newPosition >= descTimeArray.length )
			isKeepPlaying = false;
		if ( offset < 0 && (newPosition - zoomSetting) <= 0 )
			isKeepPlaying = false;
		if ( !isKeepPlaying ) {
			$buttonImage.attr( "src", startButtonUrl );
			return;
		}


		sliderUpdatePosition( $slider, newPosition, resourceGraph, sampleTimeArray, descTimeArray );

		// do it again
		var delay = 5000 / zoomSetting;

		setTimeout( function () {
			playSlider( offset, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray );
		}, delay );

	}

	function sliderUpdatePosition( $slider, position, resourceGraph, sampleTimeArray, descTimeArray ) {

		var reversePosition = sampleTimeArray.length - position;
		// set the label
		setSliderLabel( resourceGraph.getCurrentGraph(), descTimeArray[ reversePosition ] );

		// move the slider
		$slider.slider( "value", position );
		var host = $slider.parent().parent().data( "host" );

		// console.log("host: " + host ) ;
		// move the grapsh 
		var graphsArray = resourceGraph.getDataManager().getHostGraph( host );

		if ( graphsArray == undefined )
			return; // initial rendering
		for ( var i = 0; i < graphsArray.length; i++ ) {
			// Custom Hook into FLOT nav plugin.
			var currPlot = graphsArray[i];

			currPlot.jumpX( {
				// sample times are in reverse order
				x: parseInt( sampleTimeArray[ reversePosition ] )
			} );
		}
	}

	function setSliderLabel( $newGraphContainer, newTime ) {

		var d = new Date( parseInt( newTime ) );
		var mins = d.getMinutes();
		if ( mins <= 9 )
			mins = "0" + mins;
		var formatedDate = d.getHours() + ":" + mins + " "
				+ $.datepicker.formatDate( 'M d', d );

		// alert( formatedDate) ;
		$( ".sliderTimeCurrent", $newGraphContainer ).val( formatedDate );

	}

	function addToolsEvents() {

		$( '.hostLaunch' ).click(
				function () {
					

					var linkHost = $( this ).parent().parent().parent().data(
							"host" );
					var baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, linkHost );
					var theUrl = baseUrl + "/os/HostDashboard?u=1";
					openWindowSafely( theUrl, linkHost + "Stats" );

					return false; // prevents link
				} );
	}

	function addContainerEvents( resourceGraph, container ) {
		if ( resourceGraph.isCurrentModePerformancePortal() ) {
			$( '.clearMetrics', container ).hide();
			$( ".autoRefresh" ).hide();
		}
		$( '.clearMetrics', container ).click(
				function () {

					var linkHost = $( this ).parent().parent()
							.parent().data( "host" );

					var message = "Clear metrics only clears the UI history. Reloading the page will display entire timeline. Host: "
							+ linkHost;
					alertify.notify( message );

					resourceGraph.getDataManager().clearHostData( linkHost );
					//  _metricsJsonCache[linkHost] = undefined;

					setTimeout( function () {
						resourceGraph.getMetrics( 2, container, linkHost );
					}, "1000" );

					return false; // prevents link
				} );

		var hostAuto = $( ".autoRefresh", container );
		hostAuto.change( function () {
			// console.log( "hostAuto.is(':checked')" + hostAuto.is( ':checked' ) );
			if ( hostAuto.is( ':checked' ) ) {
				resourceGraph.getMetrics( 1, container, container
						.data( "host" ) );
			} else {
				// alert("Clearing timer" + newHostContainerJQ.data("host")
				// ) ;
				resourceGraph.clearRefreshTimers( container.data( "host" ) );
			}
			return false; // prevents link
		} );
	}

	function postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, numDays ) {
		var d = new Date();

		// ugly - but prefix single digits with a 0 consistent with times
		var curr_hour = ("0" + d.getHours()).slice( -2 );
		var curr_min = ("0" + d.getMinutes()).slice( -2 );
		var curr_sec = ("0" + d.getSeconds()).slice( -2 );
		$( ".refresh", $newGraphContainer ).html(
				"refreshed: " + curr_hour + ":" + curr_min + ":" + curr_sec );

		$( '.useHistorical', $GRAPH_INSTANCE ).off( 'change' ).change(
				function () {
					// console.log("Toggling Historical") ;
					$( '.historicalContainer', $GRAPH_INSTANCE ).toggle();
				} );


		// Finally Update the calendar based on available days
		$( ".datepicker", $GRAPH_INSTANCE ).datepicker( "option", "minDate",
				1 - numDays );

		$( ".graphHelp" ).qtip( {
			content: {
				text: "1. Y-Axis: Click and Drag on graph to adjust each graph individually.<br>"
						+ "2. X-Axis: Use the Zoom option (top right) to adjust all graphs. Use the timeline to scoll after selecting.<br>"
						+ "3. Use the customize button to switch to line graphs, enable panning/zooming via mouse, etc.",
				button: true
			},
			style: {
				classes: 'qtip-bootstrap'
			}
		} );

	}
} );