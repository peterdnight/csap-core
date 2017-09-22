

// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
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


define( ["mathjs", "graphPackage", "../../performance/modules/model"], function ( mathjs, ResourceGraph, model ) {

	$windowPanel = $( "#graphDiv" );
	$panelInfo = $( "#panelInfo div", $windowPanel );
	$panelControls = $( "#panelControls", $windowPanel );
	$graphContainer = $( "#graphDiv" );

	window.csapGraphSettings = {
		height: getHeight(),
		width: getWidth(),
		graph: "tbd",
		service: servicePerformanceId
	}

	console.log( "Module  loaded" );

	var _defaultHeight = $graphContainer.outerHeight( true );
	var _defaultWidth = $graphContainer.outerWidth( true );

	var _lastGraph = null;
	var _needsInit = true;
	// var _renderTimer = null;
	var _graphContainers = new Object();

	model.updateLifecycleModel();

	return {
		// peter
		show: function ( event ) {
			//console.log("Showing: " + $(this).attr('class') ) ;
			//$graphContainer.show();
			show( $( this ).parent() );
		},
		hide: function ( event ) {
			//console.log("hiding: " + $(this).attr('class') ) ;
			hide( $( this ) );
		},
		updateHosts: function () {
			updateHosts();
		}

	}

	function getHeight() {
		return $graphContainer.outerHeight( true ) - 190;
	}

	function getWidth() {
		return $graphContainer.outerWidth( true ) - 50;
	}

	function resize( height, width ) {

		$graphContainer.css( "height", height );
		$graphContainer.css( "width", width );

		window.csapGraphSettings.height = getHeight();
		window.csapGraphSettings.width = getWidth();

		_lastGraph.reDraw();
	}

	function updateHosts() {
		$( "#hostDisplay" ).empty();
		$( "#instanceTable *.selected" ).each( function ( index ) {

			var host = $( this ).data( "host" );
			var $label = jQuery( '<input/>', {
				"data-host": host,
				class: "instanceCheck",
				checked: "checked",
				type: "checkbox"
			} ).appendTo( $( "#hostDisplay" ) );
		} );


		for ( var graphName in _graphContainers ) {
			_graphContainers[ graphName ].settingsUpdated();
		}
	}

	function init() {
		$( "#maxPanel" ).show();

		if ( _needsInit ) {
			_needsInit = false;

			updateHosts();
			//$( "#hostDisplay input" ).data( "host", hostName );

//			$windowPanel.hover(
//					function () {
//						// cancel the hide
//
//						clearTimeout( _renderTimer );
//						$panelControls.show( 300 );
//
//					},
//					function () {
//						// use manual close
//					}
//			)

			$( "#closePanel" ).click( function () {
				resize( _defaultHeight, _defaultWidth );
				// $panelControls.hide();
				$windowPanel.hide( 500 );

			} );
			$( "#maxPanel" ).click( function () {
				$( "#maxPanel" ).hide();
				resize( $( window ).outerHeight( true ) - 100, $( window ).outerWidth( true ) - 100 );
			} );

		}
	}

	function hide( $resourceRow ) {

//		if ( $panelControls.is( ":visible" ) ) {
//			// skip if we want to show
//			return;
//		}

//		clearTimeout( _renderTimer );
//
//		_renderTimer = setTimeout( function () {
//			$windowPanel.hide( 300 );
//		}, 1000 );
	}

	function show( $resourceRow ) {

		init();
		renderGraph( $resourceRow );

//		clearTimeout( _renderTimer );
//
//		_renderTimer = setTimeout( function () {
//
//			renderGraph( $resourceRow );
//
//		}, 500 );
	}

	function renderGraph( $resourceRow ) {
		window.csapGraphSettings.graph = $resourceRow.data( "graph" );

		var servicePanelType = $resourceRow.data( "type" );
		//console.log("renderGraph() servicePanelType", servicePanelType) ;
		
		var graphType = servicePanelType ;
		
		// convert new CSAP types to analytics types for queries
		if ( servicePanelType == "app" ) {
			graphType="jmxCustom" ;
		} else if ( servicePanelType == "java" ) {
			graphType="jmx" ;
		}

		window.csapGraphSettings[ "type" ] = graphType;

		var containerId = "#" + graphType + "Container";

		$windowPanel.show( 300 );
		$( ".gpanel", $windowPanel ).hide();
		$( containerId, $windowPanel ).show();

		$panelInfo.hide();
		var message = $( ".resourceWarning", $resourceRow ).attr( "title" );

		if ( message ) {
			$panelInfo.text( message )
					.show();
		}

		_lastGraph = _graphContainers[graphType];
		if ( _lastGraph != undefined ) {
			_lastGraph.reDraw();
			return;
		}


		//$graphContainer.append( $resourceRow.attr( 'class' ) );

		// $( "#numberOfDays", $graphContainer ).val( 2 );
		$( ".useHistorical", $windowPanel ).prop( 'checked',
				true );
		$( '.numDaysSelect option[value="0"]', $windowPanel ).remove();
		$( ".datepicker", $windowPanel ).attr( "placeholder", "Last 24 Hours" );

		// $( ".triggerJmxCustom" ).html( serviceFilter );
		var graphFlag = graphType;
		if ( graphType == "jmxCustom" ) {
			graphFlag = "jmx";
			$( "#jmxCustomWhenClassSet" )
					.addClass( "triggerJmxCustom" )
					.text( serviceFilter );
		} else  {
			$( "#jmxCustomWhenClassSet" )
					.removeClass( "triggerJmxCustom" )
					.text( "" );
		}

		//  resource, service, jmx, jmxCustom
		_lastGraph = buildGraphObject( containerId, graphFlag );
		_graphContainers[graphType] = _lastGraph;
		$.when( _graphContainers[graphType].getGraphLoadedDeferred() ).done( function () {
			$( "#initialMessage" ).hide();
		} );
	}

	function buildGraphObject( containerId, metricType ) {

		console.log( "buildGraphObject() constructing Resource graph for container",  containerId,  "type",  metricType, 
				"window.csapGraphSettings", window.csapGraphSettings );

		var theNewGraph = new ResourceGraph(
				containerId, metricType,
				uiSettings.life, uiSettings.appId, uiSettings.metricsDataUrl,
				model );

		//theNewGraph.settingsUpdated() ;
//		if ( metricType == "jmx" ) {
//			theNewGraph.addCustomViews( customJmxViews );
//		}
		// needed to pull model related information from events DB
//		  console.log( "buildGraphObject() model: " + model.getSelectedProject()) ;
//		  theNewGraph.setModel( model );

		return theNewGraph;
	}


} );


