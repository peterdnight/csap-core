define( ["./graphLayout", "mathjs"], function ( graphLayout, mathjs ) {

	console.log( "Module loaded: graphPackage/flotUtils" );

	var hackLengthForTesting = -1;

	var bindTimer, tipTimer;


	var STATS_FORMAT = { notation: 'fixed', precision: 2 };


	return {
		//
		buildHostStatsPanel: function ( $statsPanel, graphTitle ) {
			return buildHostStatsPanel( $statsPanel, graphTitle )
		},
		//
		buildPlotPanel: function ( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder ) {
			return buildPlotPanel( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder )
		},
		//
		getPlotOptionsAndXaxis: function ( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost ) {
			return getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost );
		},
		//
		addPlotAndLegend: function ( resourceGraph, numGraphs, graphName, host, plotPanel, plotContainer ) {
			return addPlotAndLegend( resourceGraph, numGraphs, graphName, host, plotPanel, plotContainer )
		},
		//
		configurePanelEvents: function ( $targetPanel, $panelContainer, graphsArray ) {
			configurePanelEvents( $targetPanel, $panelContainer, graphsArray )
		}
	}

	/**
	 * http://www.flotcharts.org/, http://flot.googlecode.com/svn/trunk/API.txt
	 * 
	 * @param newHostContainerJQ
	 * @param graphMap
	 */

	function buildPlotPanel( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder ) {

		// console.log( "title: " + title );

		var $graphContainer = resourceGraph.getCurrentGraph();

		var $plotPanel = jQuery( '<div/>', {
			id: host + "_plot_" + graphName,
			class: "plotPanel " + graphName,
			"data-graphname": graphName
		} );

		var $graphTitle = jQuery( '<div/>', {
			id: "Plot" + host + graphName,
			title: "Click and drag graph title to re order",
			class: "graphTitle",
			html: '<span>' + title + '</span>'
		} );



		$plotPanel.append( $graphTitle );

		var sampleLabel = jQuery( '<label/>', {
			text: "",
			class: "graphNotes"
		} ).appendTo( $graphTitle );


		var $minMaxButton = jQuery(
				'<a/>',
				{
					title: "Click to toggle size",
					class: "plotMinMaxButton",
					on: {
						click: function ( event ) {

							if ( !graphLayout.isAGraphMaximized( $graphContainer ) ) {
								$( ".graphCheckboxes .customCheck", $graphContainer ).prop(
										"checked", false );
								$( "#" + $( this ).data( "graphType" ),
										".graphCheckboxes" ).prop( "checked",
										true );
							} else {

								$( ".graphCheckboxes .customCheck", $graphContainer ).prop(
										"checked", true );
							}
							console.log( "$minMaxButton" );
							resourceGraph.reDraw();
						}
					}
				} ).data( "graphType", checkId ).css( "cursor", "pointer" );

		if ( !graphLayout.isAGraphMaximized( $graphContainer ) ) {
			$minMaxButton
					.html( '<img id="toggleResultsImage" src="' + uiSettings.baseUrl + 'images/maxWindow.gif">' );
		} else {
			$minMaxButton
					.html( '<img id="toggleResultsImage" src="' + uiSettings.baseUrl + 'images/restoreWindow.gif">' );
		}
		$graphTitle.append( $minMaxButton );


		//console.log("Adding About for: " + host) ;
		var $aboutButton = jQuery(
				'<a/>',
				{
					title: "View Summary Information",
					class: "plotAboutButton",
					on: {
						click: function ( event ) {
							var content = "";
							for ( var i = 0; i < statsBuilder.lines.length; i++ ) {
								content += statsBuilder.function( statsBuilder.lines[i], statsBuilder.title[i] );
							}
							// console.log("$aboutButton: " + content);
							$( "#graphToolTip" ).hide();
							alertify.alert( "Summary Statistics: " + title, content );
						}
					}
				} ).data( "graphType", checkId ).css( "cursor", "pointer" );
		$aboutButton
				.html( '<img id="toggleResultsImage" src="' + uiSettings.baseUrl + 'images/16x16/x-office-spreadsheet.png">' );
		$graphTitle.append( $aboutButton );

		return $plotPanel;
	}

	function buildHostStatsPanel( linesOnGraphArray, graphTitle ) {
		console.log( "buildHostStatsPanel() graphTitle: " + graphTitle
				+ " numLines: " + linesOnGraphArray.length )

		var $statsPanel = jQuery( '<div/>', { class: "statsPanel " } );
		for ( var i = 0; i < linesOnGraphArray.length; i++ ) {
			var graphDefinition = linesOnGraphArray[i];
			// if ( i == (linesOnGraphArray.length-1))  console.log( JSON.stringify( graphDefinition, null, "\t" ) )
			$seriesContainer = jQuery( '<div/>', { class: "statsContainer" } );
			$seriesContainer.append( jQuery( '<div/>', { class: "statsLabel", html: graphDefinition.label } ) );



			var stats = graphStats( graphDefinition.rawData );

			if ( !stats ) {
				continue;
			}

			addStatsItems( graphTitle, $seriesContainer, stats.all, "All" );
			addStatsItems( graphTitle, $seriesContainer, stats.nonZero, "values > 0" );

			//$statsLabel.append ( JSON.stringify( graphDefinition.stats, null, "<br/>" ) );
			$statsPanel.append( $seriesContainer );
		}
		return $statsPanel.html();
	}

	function addStatsItems( graphTitle, $seriesContainer, samples, categoryTitle ) {

		if ( !samples || !samples.Collected ) {
			return;
		}
		$seriesContainer.append( jQuery( '<div/>', { class: "statsType", text: categoryTitle + " : " } ) );

		for ( var type in samples ) {
			// console.log( "type: " + type );

			$itemLabel = jQuery( '<div/>', { text: type + " : " } );
			var rawVal = samples[type];
			var suffix = "";
			if ( type != "Collected" && graphTitle.contains( "ms" ) && rawVal > 2000 ) {
				rawVal = (rawVal / 1000).toFixed( 1 );

				if ( rawVal > 60 ) {
					rawVal = Math.round( rawVal );
				}
				suffix = '<span class="statsUnits">s</span>'
			}

			var val = numberWithCommas( rawVal );
			var theStyles = "";
			if ( type.contains( "2x" ) )
				theStyles = "red";
			$itemLabel.append( jQuery( '<span/>', { class: theStyles, html: val + suffix } ) )
			$seriesContainer.append( $itemLabel );
		}

	}


	function graphStats( graphData ) {

		//console.log( "graphStats(): " + JSON.stringify( graphData ) );

		var allStats = new Object();

		try {

			allStats.all = calculateStats( graphData );

			var non0Values = new Array();
			for ( var i = 0; i < graphData.length; i++ ) {
				if ( graphData[i] != 0 ) {
					non0Values.push( graphData[i] )
				}

			}

			var percentZeros = (graphData.length - non0Values.length) / graphData.length;

			if ( percentZeros > 0.1 ) {
				// for low hit services eliminate the 0's to avoid weighting the diffs
				allStats.nonZero = calculateStats( non0Values );
			}

		} catch ( err ) {
			console.log( "failed:" + err );
		}

		//console.log( "graphStats(): " + JSON.stringify( allStats ) );

		return allStats;
	}

	function calculateStats( dataArray ) {

		var stats = new Object();
		stats["Collected"] = dataArray.length;
		var mean = mathjs.mean( dataArray );
		stats["mean"] = mathjs.format( mean, STATS_FORMAT );
		stats["std. deviation"] = mathjs.format( mathjs.std( dataArray ), STATS_FORMAT );

		stats["samples > 2x mean"] = countItemsGreaterThan( dataArray, mean, 2 );
		stats["median"] = mathjs.format( mathjs.median( dataArray ) );
		stats["min"] = mathjs.min( dataArray );
		stats["max"] = mathjs.max( dataArray );

		return stats;
	}

	function countItemsGreaterThan( dataArray, mean, scale ) {

		var threshhold = mathjs.multiply( mean, scale )
		var numOverThreshold = 0;

		for ( var i = 0; i < dataArray.length; i++ ) {
			if ( mathjs.larger( dataArray[i], threshhold ) ) {
				numOverThreshold++;
			}
		}

		return numOverThreshold;
	}

	function numberWithCommas( x ) {
		return x.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," );
	}


	var doOnce = true;
	function getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost ) {

		var isStack = !$( '.useLineGraph', $GRAPH_INSTANCE ).prop( "checked" );

		if ( graphName == "OS_Load" )
			isStack = false;


		var isMouseNav = $( '.zoomAndPan', $GRAPH_INSTANCE ).prop( "checked" );

		var mouseSelect = "y";

		if ( isMouseNav )
			mouseSelect = "xy";

		var plotWidth = $( ".plotPanel ." + graphName, $GRAPH_INSTANCE ).outerWidth( true );
		var numLegendColumns = Math.floor( plotWidth / 120 );
		// console.log( "graphName: " + graphName + " width: "  + plotWidth + " numLegendColumns" + numLegendColumns) ;

		var plotOptions = {
			series: {
				stack: isStack
			},
			legend: {
				position: "nw",
				noColumns: numLegendColumns
			},
			lines: {
				show: true,
				fill: isStack
			},
			points: {
				show: false
			},
			selection: {
				// "xy"
				mode: mouseSelect
			},
			grid: {
				hoverable: true,
				clickable: false
			},
			yaxis: {
				panRange: false,
				zoomRange: false
			},
			zoom: {
				interactive: isMouseNav
			},
			pan: {
				interactive: isMouseNav
			},
			xaxis: buildTimeAxis(
					flotTimeOffsetArray,
					sampleInterval,
					$( "#numSamples", $GRAPH_INSTANCE ).val()
					, isSampling, plotWidth )
		};

		if ( isOutsideLegend( $GRAPH_INSTANCE ) ) {
			plotOptions.legend.container = $( ".Legend" + graphName, $GRAPH_INSTANCE );
		}




		// 
		try {
			scaleLabels( $plotPanel, linesOnGraphArray, plotOptions )
		} catch ( e ) {
			console.log( "Failed to scaleLabel: ", e );
		}


		//for ( var i = graphSeries.data.length - 5; )


		return plotOptions;
	}

	function scaleLabels( $plotPanel, linesOnGraphArray, plotOptions ) {
		
		var $titleSpan = $(".graphTitle span" , $plotPanel) ;
		var title = $titleSpan.text() ;
		console.log("scaleLabels() title", title) ;
		// big assumption - use last value of  first series.
		var graphSeries = linesOnGraphArray[0];
		// var firstY = (graphSeries.data[ 1 ])[1] ;
		//plotOptions.yaxis.min = firstY ; this defaults in line mode
		var lastPointInSeries = graphSeries.data[ graphSeries.data.length - 1 ];
		var lastY = lastPointInSeries[1];

		// + " firstY: " + firstY
//				console.log( "graphName: " + graphName 
//						   + " last y: " + lastY )

		if ( lastY > 1000 ) {
			console.log( title + " lasty: " + lastY );

			if ( title.toLowerCase().contains( "(mb)" ) ) {
				$titleSpan.text( title.substring(0, title.length -4) ) ;

				plotOptions.yaxis.tickFormatter =
						function ( val, axis ) {
							val = val / 1024;
							return val.toFixed( 1 ) + "Gb";
						}
			} else if ( title.toLowerCase().contains( "(kb)" ) ) {

				$titleSpan.text( title.substring(0, title.length -4) ) ;
				plotOptions.yaxis.tickFormatter =
						function ( val, axis ) {
							val = val / 1024;
							return val.toFixed( 1 ) + "Mb";
						}
			} else if ( title.toLowerCase().contains( "(ms)" ) ) {

				$titleSpan.text( title.substring(0, title.length -4) ) ;
				plotOptions.yaxis.tickFormatter =
						function ( val, axis ) {
							val = val / 1000;
							return val.toFixed( 1 ) + "s";
						}
			} else {
				plotOptions.yaxis.tickFormatter =
						function ( val, axis ) {
							val = val / 1000;
							return val.toFixed( 1 ) + "K";
						}
			}
		}
		if ( lastY > 1000000 ) {
			// console.log( "OsMpStat: " + jsonToString( graphSeries.data ) + " lasty: " + lastY );
			plotOptions.yaxis.tickFormatter =
					function ( val, axis ) {
						val = val / 1000000;
						return val.toFixed( 2 ) + "M";
					}
		}
	}


	/**
	 * http://www.flotcharts.org/
	 * http://flot.googlecode.com/svn/trunk/API.txt
	 * @param newHostContainerJQ
	 * @param graphMap
	 */

	function buildTimeAxis( flotTimeOffsetArray, sampleIntervalInSeconds, numSamples, isSampled, plotWidth ) {

		var flotXaxisConfig = new Object();

		flotXaxisConfig.mode = "time";
		flotXaxisConfig.timeformat = "%H:%M<br>%b %d";

//		  console.log( "buildTimeAxis() time array size: " + flotTimeOffsetArray.length + " isSampled:" + isSampled
//					 + " plotWidth: " + plotWidth);
		var numItems = flotTimeOffsetArray.length - 1;



		var curDisplay = numSamples;
		if ( numSamples > numItems )
			curDisplay = numItems;

		// handle time scrolling
		if ( !isSampled ) {
			flotXaxisConfig.min = flotTimeOffsetArray[curDisplay];
			flotXaxisConfig.max = flotTimeOffsetArray[flotTimeOffsetArray.length];
			// flotXaxisConfig.zoomRange = [graphMap.usrArray[numItems][0] ,
			// graphMap.usrArray[0][0] ] ;
			flotXaxisConfig.panRange = [flotTimeOffsetArray[numItems],
				flotTimeOffsetArray[flotTimeOffsetArray.length]];

		}
		if ( plotWidth < 270 ) {
			flotXaxisConfig.ticks = 3;
		}

		return flotXaxisConfig;
	}



	function addPlotAndLegend( resourceGraph, numGraphs, graphName, host, plotPanel, $plotContainer ) {

		var $graphContainer = resourceGraph.getCurrentGraph();

		var plotDiv = jQuery( '<div/>', {
			id: "Plot" + host + graphName,
			class: "graphPlot " + graphName
		} );
		plotPanel.append( plotDiv );

		// Support panel resizing
		plotPanel.resizable( {
			stop: function ( event, ui ) {
				// console.log("width: " + ui.size.width + " height: " + ui.size.height) ;
				graphLayout.setSize( graphName, ui.size, $graphContainer, $plotContainer );
				resourceGraph.reDraw();
			},
			start: function ( event, ui ) {
				$( "div.graphPlot", plotPanel ).remove();
			}
		} );
		plotPanel.on( 'resize', function ( e ) {
			// otherwise resize window will be continuosly called
			e.stopPropagation();
		} );

		$plotContainer.append( plotPanel );
		plotDiv.css( "height", "100%" );
		plotDiv.css( "width", "100%" );



		var fullHeight = $( window ).outerHeight( true )
				- $( "header" ).outerHeight( true ) - 150;
		
		if (  $( "#hostSelection" ).length != 0 ) {
			fullHeight = fullHeight - $( "#hostSelection" ).outerHeight( true )
		}
	

		console.log("addPlotAndLegend() $( window ).outerHeight( true ): " + fullHeight) ;
		var targetHeight = fullHeight;

		if ( graphLayout.getHeight( graphName, $graphContainer ) != null ) {
			targetHeight = graphLayout.getHeight( graphName, $graphContainer );
			//console.log("addPlotAndLegend() targetHeight: " + targetHeight  + " type: " + (typeof targetHeight) )   ;
			
			if ( typeof targetHeight == "string" && targetHeight.contains( "%" ) ) {
				var percent = targetHeight.substring( 0, targetHeight.length - 1 );
				targetHeight = Math.floor( fullHeight * percent / 100 );
			}

			if ( targetHeight < 150 )
				targetHeight = 150;
		}
		// Support for nesting on other pages
		if ( window.csapGraphSettings != undefined ) {
			targetHeight = window.csapGraphSettings.height;
			console.log( "getCsapGraphHeight: " + targetHeight );
		}
		

		
//		  
		// plotDiv.css( "height", targetHeight ); // height is applied to plot div
		plotPanel.css( "height", targetHeight );
		plotDiv.css( "height", targetHeight - 50 );

		if ( $( ".includeFullLegend", $graphContainer ).is( ":checked" ) ) {
			plotPanel.css( "height", "auto" );
			plotDiv.css( "height", targetHeight );
		}


		var fullWidth = $( window ).outerWidth( true ) - 100;
		var targetWidth = fullWidth;

		if ( graphLayout.getWidth( graphName, $graphContainer ) != null ) {
			targetWidth = graphLayout.getWidth( graphName, $graphContainer );
			if ( typeof targetWidth == "string" && targetWidth.contains( "%" ) ) {
				var percent = targetWidth.substring( 0, targetWidth.length - 1 );
				targetWidth = Math.floor( fullWidth * percent / 100 );
			}

			if ( targetWidth < 250 )
				targetWidth = 250;
		}


		// Support for nesting on other pages
		if ( window.csapGraphSettings != undefined ) {
			targetWidth = window.csapGraphSettings.width;
			console.log( "getCsapGraphWidth: " + targetWidth );
		}
		// console.log( "targetWidth: " + targetWidth + " height: " + targetHeight );

		plotPanel.css( "width", targetWidth ); // width is applied to entire panel

		var numHosts = $( ".instanceCheck:checked" ).length;
		// console.log(" num Hosts Checked" + numHosts) ;
		var isMultipleHosts = false;
		isMultipleHosts = numHosts > 1; // template is one, plus host is
		// another

		var useAutoSelect = $( ".useAutoSize", $graphContainer ).is(
				':checked' );


		if ( useAutoSelect && numGraphs == 1 && !isMultipleHosts ) {
			plotDiv.css( "height", "600px" );
		}


		if ( ! isOutsideLegend( $graphContainer ) ) {
			// console.log("Need to add title") ;
		} else {
			var plotLegendDiv = jQuery( '<div/>', {
				class: "graphLegend Legend" + graphName
			} );

			if ( targetWidth <= 300 ) {
				plotLegendDiv.addClass( "legendOnHover" );
				//plotLegendDiv.hide();
				$( ".graphNotes, .titleHelp", plotPanel ).hide();
			} else {

				plotLegendDiv.show();
				$( ".graphNotes, .titleHelp", plotPanel ).show();
			}

			plotPanel.append( plotLegendDiv );

			plotLegendDiv.qtip( {
				content: {
					text: function ( event, api ) {
						//console.log("plotLegendDiv.html()" + plotLegendDiv.html()) ;
						var content = "";
						if ( $( ".stackLabel", this ).text() != "" ) {
							content = '<span class="stackLabel">' + $( ".stackLabel", this ).text() + "</span><br/>";
						}
						$( ".legendColorBox >div, .legendLabel", plotLegendDiv ).each( function () {
							//console.log("qtip text: " + $( this ).text().length )
							if ( $( this ).text() == "" ) {
								content += '<div class="qtipExtract">'
								content += $( this ).html();
							}
							if ( $( this ).text() != "" ) {

								content += '<div class="qtipExtractText">' + $( this ).html();
								content += '</div></div>'
							}
						} )


						return content;
					},
					button: true
				},
//					 hide: { event : false } ,
				style: {
					classes: 'qtip-rounded qtipExtractContainer',
					width: plotPanel.outerWidth( false )
				},
				position: {
					my: 'top left', // Position my top left...
					at: 'bottom+ left', // at the bottom right of...
					adjust: { y: 10 },
					target: plotPanel // my target
				}
			} );
		}


		return plotDiv;
	}
	



	function isOutsideLegend( $graphContainer ) {

		if ( graphLayout.isAGraphMaximized( $graphContainer ) )
			return false;
		return $( '.outsideLabels', $graphContainer ).prop( "checked" );
	}


	function configurePanelEvents( $targetPanel, $panelContainer, graphsArray ) {

		configureToolTip( $targetPanel, $panelContainer );
		configurePanelZooming( $targetPanel, $panelContainer, graphsArray )

	}

	function configureToolTip( $targetPanel, $panelContainer ) {

		var hideWithTimeout = function () {
			tipTimer = setTimeout( function () {
				$( "#graphToolTip" ).hide();
			}, 500 )
		}

		$targetPanel.bind( "plothover", function ( event, pos, item ) {

			//  $( "#graphToolTip" ).hide();
			if ( item === null ) {
				clearTimeout( tipTimer );
				clearTimeout( bindTimer );
				hideWithTimeout();
				return;
			}
			// console.log( "item" + jsonToString( item ) );


			clearTimeout( tipTimer );
			clearTimeout( bindTimer );
			bindTimer = setTimeout( function () {
				var xValue = new Date( item.datapoint[0] ), yValue = item.datapoint[1]
						.toFixed( 2 );

				xValue.addMinutes( xValue.getTimezoneOffset() );

				var formatedDate = xValue.format( "HH:MM mmm d" );

				var label = item.series.label;

				if ( label == null )
					return;
				// support for mbean attributes versus entire location
				if ( label.indexOf( ':' ) != -1 )
					label = label.substring( 0, label.indexOf( ':' ) );
				if ( label.indexOf( '<' ) != -1 )
					label = label.substring( 0, label.indexOf( '<' ) );

				var tipContent = '<div class="tipInfo">' + label + " <br>" + formatedDate
						+ "</div><div class='tipValue'>"
						+ numberWithCommas( yValue ) + "</div>";

				var offset = 100;
				if ( $( ".ui-tabs" ).length > 0 )
					offset = 120;

				if ( window.csapGraphSettings != undefined ) {
					offset = 150;
				}

				$( "#graphToolTip" ).html( tipContent )
						.css( {
							top: item.pageY - offset,
							left: item.pageX + 15
						} ).fadeIn( 200 );
			}, 1000 );



		} );
	}

	function configurePanelZooming( $targetPanel, $panelContainer, graphsArray ) {

		$targetPanel.bind( "plotselected", function ( event, ranges ) {

			// console.log("Got " + $(this).attr("id") ) ;
			var bindHost = $( this ).parent().parent().data(
					"host" );
			// console.log("Got " + bindHost) ;

			var curHostPlots = graphsArray[bindHost];

			var origSettings = new Array();
			for ( i = 0; i < curHostPlots.length; i++ ) {
				if ( $( ".resetInterZoom", $panelContainer ).length == 0 ) {
					origSettings[i] = new Object();
					// preserver orig
					origSettings[i].xmin = curHostPlots[i]
							.getOptions().xaxes[0].min;
					origSettings[i].xmax = curHostPlots[i]
							.getOptions().xaxes[0].max;
					origSettings[i].ymin = curHostPlots[i]
							.getOptions().yaxes[0].min;
					origSettings[i].ymax = curHostPlots[i]
							.getOptions().yaxes[0].max;
				}

				if ( curHostPlots[i].getSelection() != null ) {
					// only Zoom Y on current graph
					curHostPlots[i].getOptions().yaxes[0].min = ranges.yaxis.from;
					curHostPlots[i].getOptions().yaxes[0].max = ranges.yaxis.to;
				}

				curHostPlots[i].getOptions().xaxes[0].min = ranges.xaxis.from;
				curHostPlots[i].getOptions().xaxes[0].max = ranges.xaxis.to;

				curHostPlots[i].setupGrid();
				curHostPlots[i].draw();

				curHostPlots[i].clearSelection();
			}

			if ( $( ".resetInterZoom", $panelContainer ).length == 0 ) {
				// console.log("Creating reset") ;
				var plotReset = jQuery( '<button/>', {
					class: "pushButton " + "resetInterZoom",
					text: "Reset Selection To Default"
				} ).css( "font-size", "0.8em" ).css( "line-height", "1em" );
				plotReset.click( function () {
					$( this ).remove();
					for ( i = 0; i < curHostPlots.length; i++ ) {
						curHostPlots[i]
								.getOptions().xaxes[0].min = origSettings[i].xmin;
						curHostPlots[i]
								.getOptions().xaxes[0].max = origSettings[i].xmax;
						curHostPlots[i]
								.getOptions().yaxes[0].min = origSettings[i].ymin;
						curHostPlots[i]
								.getOptions().yaxes[0].max = origSettings[i].ymax;
						curHostPlots[i]
								.setupGrid();
						curHostPlots[i].draw();

						curHostPlots[i]
								.clearSelection();
					}
				} );

				$targetPanel.parent().parent().prepend(
						plotReset );
			}

		} );
	}
} );
	 