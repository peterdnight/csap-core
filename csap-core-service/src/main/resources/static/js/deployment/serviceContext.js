/**
 * 
 * Helper file for service context operations
 * 
 * http://medialize.github.com/jQuery-contextMenu/
 * 
 * @param jquerySelector
 */

// Alert hooks


function addServiceContext( jquerySelector, getSelectedFunction ) {


	// 
	// alert("loaded") ;
	$.contextMenu( {
		selector: jquerySelector,
		callback: function ( key, options ) {
			//var m = "clicked: " + key;
			// window.console && console.log(m) || alert(m);
			var rowObj = getSelectedFunction();

			if ( rowObj == null ) {
				alertify.alert( "Warning: Did not find last roll over row" );
				return;
			}
			var host = rowObj.data( 'host' );
			var instance = rowObj.data( 'instance' );

			// alert("key: " + key + " Host: " + host + " instance: " + serviceName ) ;


			/**
			 * 
			 * Note icons are in jquery.contextMenu.css
			 * 
			 */
			switch ( key ) {

				case "service" :
					launchDefaultService( rowObj );
					break;

				case "logs" :
					launchLogs( rowObj );
					break;

				case "files" :
					launchFiles( rowObj );
					break;

				case "history" :
					launchHistory( rowObj, host );
					break;

				case "httpdConn" :
					launchHttpdStatus( rowObj );
					break;

				case "hostover" :
					launchHostOver( rowObj );
					break;

				case "hostdashboard" :
					launchHostDash( rowObj );
					break;

				case "jmxdashboard" :
					launchJmxDash( rowObj );
					break;

				case "jvisualvm" :
					launchProfiler( rowObj, true );
					break;

				case "jmc" :
					launchProfiler( rowObj, false );
					break;

				default:
					alert( "unknown key: " + key + " Host: " + host + " instance: " + instance );
			}

		},
		items: {
			"service": { name: "Service url", icon: "service" },
			"logs": { name: "Logs", icon: "logs" },
			"files": { name: "Files", icon: "files" },
			"history": { name: "History", icon: "history" },
			"httpdConn": { name: "Httpd Status", icon: "httpd" },
			"hostover": { name: "About host", icon: "about" },
			"hostdashboard": { name: "Host Dashboard", icon: "dash" },
			"jmxdashboard": { name: "Java Dashboard", icon: "dash" },
			"jvisualvm": { name: "JVisual VM", icon: "jvisualvm" },
			"jmc": { name: "Mission Control", icon: "jmc" },
			"sep1": "---------"
		}
	} );

}


function launchHistory( jqueryRows, host ) {

	var serviceInstance = jqueryRows.first().data( "instance" );

	var jvmName = serviceInstance;

	if ( historyUrl.indexOf( "data" ) != -1 && serviceInstance.indexOf( "_" ) != -1 ) {
		jvmName = serviceInstance.substring( 0, serviceInstance.indexOf( "_" ) );
		console.log( "jvmName is: " + jvmName );
	}

	// String off the *
	var urlAction = historyUrl.substring( 0, historyUrl.length - 1 ) + "svc/" + jvmName + "&";



	if ( host != null ) {
		urlAction += "hostName=" + host;
	}
	openWindowSafely( urlAction, "_blank" );

}




function launchFiles( jqueryRows ) {

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	// alert (" Num: " +jqueryRows.length) ;

	jqueryRows.each( function ( index ) {

		var serviceInstance = $( this ).data( "instance" );
		var host = $( this ).data( "host" );
		var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );
		urlAction += "/file/FileManager?serviceName=" + serviceInstance + "&hostName=" + host + "&ts=1&fromFolder=.&";
		openWindowSafely( urlAction, host + serviceInstance + "Files" );

	} );

}


function confirmMaxItem( jqueryRows ) {

	if ( jqueryRows.length > 4 ) {
		return confirm( "You currently have " + jqueryRows.length + " rows selected. Each will open in a new window if you proceed" );
	}

	return true;
}
/**
 * 
 * @param jqueryRows
 */
function launchLogs( jqueryRows ) {

	// alert (" Num: " +jqueryRows.length) ;

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	jqueryRows.each( function ( index ) {

		var serviceInstance = $( this ).data( "instance" );
		var host = $( this ).data( "host" );

		var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );
		urlAction += "/file/FileMonitor?isLogFile=true&serviceName=" + serviceInstance
				+ "&hostName="
				+ host;
		openWindowSafely( urlAction, host + serviceInstance + "logs" );



	} );
}


function launchDefaultService( jqueryRows ) {

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	jqueryRows.each( function ( index ) {

		var urlAction = $( this ).data( "launchurl" );
		var instance = $( this ).data( "instance" );
		var host = $( this ).data( "host" );

		// hook for multiple urls used for a few services
		var urlArray = urlAction.split( ',' );
		for ( var i = 0; i < urlArray.length; i++ ) {
			//alert("url: " + url) ;
			openWindowSafely( urlArray[i], instance + host + "Launch" + i );
		}
		// openWindowSafely(urlAction,  host + serviceInstance + "Service");

	} );

}



function launchHttpdStatus( jqueryRows, host ) {

	// Csagent names modjks based on service name not context
	// var context = jqueryRows.first().data("context")
	var context = jqueryRows.first().data( "instance" ).split( "_" )[0];
	for ( var i = 0; i < httpdArray.length; i++ ) {
		//alert("url: " + url) ;
		var hostPort = httpdArray[i];
		var hostPortArray = hostPort.split( ":" );
		var curHost = document.domain;
		var myDomain = curHost.substring( curHost.indexOf( "." ) );
		var urlAction = "http://" + hostPortArray[0] + myDomain + ":" + hostPortArray[1] + "/admin/service/status?cmd=show&w=" + context + "_LB&";
		// re=10& will trigger autorefresh
		openWindowSafely( urlAction, context + " Httpd" + i );
	}


}

function launchHostOver( jqueryRows ) {

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	// alert (" Num: " +jqueryRows.length) ;

	$( 'body' ).css( 'cursor', 'wait' );
	jqueryRows.each( function ( index ) {

		var host = $( this ).data( "host" );
		var paramObject = {
			hostName: host
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
				"os/hostOverview", paramObject )
				.done(
						function ( loadJson ) {
							var $about = jQuery( '<div/>', { class: "aWrap" } );
							$about.append( buildAboutLine( "Host", host ) );
							$about.append( buildAboutLine( "Version", loadJson.redhat ) );
							$about.append( buildAboutLine( "Uptime", loadJson.uptime ) );
							$about.append( buildAboutLine( "uname", loadJson.uname ) );
							$about.append( buildAboutLine( "Disk", "df Output\n" + loadJson.df ) );

							alertify.alert( "About " + host, $about.html() );

							$( ".alertify" ).css( "width", "800px" );
							$( ".alertify" ).css( "margin-left", "-400px" );
							$( ".awrap" ).css( "text-align", "justify" );
							$( ".awrap" ).css( "white-space", "pre-wrap" );
							$( 'body' ).css( 'cursor', 'default' );

						} )

				.error( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving service instances", errorThrown );
				} );

	} );

}


function launchHostDash( jqueryRows, serviceLaunchFilter ) {

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	// alert (" Num: " +jqueryRows.length) ;

	jqueryRows.each( function ( index ) {

		var host = $( this ).data( "host" );

		var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );
		urlAction += "/os/HostDashboard";

		if ( serviceLaunchFilter != undefined )
			urlAction += serviceLaunchFilter;

		openWindowSafely( urlAction, host + "Dashboard" );
	} );

}

function launchJmxDash( jqueryRows, serviceLaunchFilter ) {

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	// alert (" Num: " +jqueryRows.length) ;

	jqueryRows.each( function ( index ) {

		var host = $( this ).data( "host" );
		var instance = $( this ).data( "instance" );

		var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );
		urlAction += "/os/java?hosts=" + host + "&service=" + instance;

		if ( serviceLaunchFilter != undefined )
			urlAction += serviceLaunchFilter;

		openWindowSafely( urlAction, host + instance + "Jmx" );
	} );

}

function launchCustomJmxDash( jqueryRows, serviceLaunchFilter ) {

	if ( !confirmMaxItem( jqueryRows ) )
		return;

	// alert (" Num: " +jqueryRows.length) ;

	jqueryRows.each( function ( index ) {

		var host = $( this ).data( "host" );
		var instance = $( this ).data( "instance" );

		var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, host );
		urlAction += "/os/application?hosts=" + host + "&service=" + instance;

		if ( serviceLaunchFilter != undefined )
			urlAction += serviceLaunchFilter;

		openWindowSafely( urlAction, host + instance + "CustomJmx" );
	} );

}
/**
 * 
 * Ajax call to resetJmxAuth, then save launch file
 * 
 */
function launchProfiler( jqueryRows, isJvisualVm ) {

	var firstHost = jqueryRows.first().data( "host" );
	var firstService = jqueryRows.first().data( "instance" );
	var jmxPort = jqueryRows.first().data( "jmx" );

	if ( jmxPort == "-1" ) {
		var message = "Current service does not support JMX (port set to -1 in definition).<br><br>";
		alertify.alert( message );
		return;
	}

	if ( isNaN( jmxPort ) ) {
		var remoteCollect = jmxPort.split( ":" );
		// support for remote collections
		firstHost = remoteCollect[0];
		jmxPort = parseInt( remoteCollect[1] );
	}


	var isRmi = true;
	// alert("jmxPort: " + jmxPort) ;
	console.log( "launchProfiler: jmxrmi", jqueryRows.first().data( "jmxrmi" ) );
	if ( jqueryRows.first().data( "jmxrmi" ) != undefined && jqueryRows.first().data( "jmxrmi" ) == false ) {
		// hook to NOT use rmiWrapping
		isRmi = false;
	}

//	$("#resultsSection").show(500);
//	$("#resultPre").html("Request is being processed");
	$( 'body' ).css( 'cursor', 'wait' );

	var paramObject = {
		serviceName: firstService
	};


	var hosts = new Array();
	hosts.push( firstHost );
	jmxPorts = firstHost + ":" + jmxPort;
//	var jmxPorts ="";
//	jqueryRows.each( function(index) {
//		hosts.push( $(this).data("host") );
//		jmxPorts+=$(this).data("host") + ":" + jmxPort + " ";
//	}) ;

	$.extend( paramObject, {
		hostName: hosts
	} );

	$.post(
			"service/resetJmxAuth",
			paramObject,
			function ( results ) {

//			$("#resultPre").html(results);
				$( 'body' ).css( 'cursor', 'default' );

//				var jmxConn = "service:jmx:rmi://" + firstHost + ":"
//						+ (jmxPort + 2);
//				jmxConn += "/jndi/rmi://" + firstHost + ":"
//						+ (jmxPort + 1) + "/jmxrmi";
//
//				if ( !isRmi ) {
//					jmxConn = jmxPorts;
//				}  

				var jmxConn = jmxPorts;

				var alertMessage = 'CSAP optionally configures secure connections - if prompted,  use userid "admin" and password:"'
						+ userid
						+ '"; password will expire in protected environments in 2 minutes (relaunch if needed)<br/><br/>'
						+ 'If unable to connect, use CSAP host dashboard to verify JVM parameters include'
						+ '<a class="simple" style="display: inline; padding: 5px" href="https://docs.oracle.com/javase/tutorial/jmx/remote/jconsole.html">jmx parametes</a>';

				if ( !isJvisualVm ) {
					alertMessage += "Windows Users: Click OK to Save/Launch the bat file. Recommended: Install the latest JDK, ensure that JAVA_HOME/bin must be in path.<br><br>"
							+ "Non-Windows: open JAVA_HOME/bin: jmc.  -  use the following to connect:<br><br>";
				} else {
					alertMessage += "Windows Users: Click OK to Save/Launch the bat file.Recommended: Install the latest JDK, ensure that JAVA_HOME/bin must be in path<br><br>"
							+ "Non-Windows: open JAVA_HOME/bin/jvisualvm. and use the following to connect (click cancel after copying): <br>";
				}

				alertMessage += '<br><div class="info">Jmx Connection: ' + jmxConn + '</div>'
				var url = "service/getProfilerLaunchFile?serviceName=" + firstService + "&jmxPorts="
						+ jmxPorts;

				var applyDialog = alertify.confirm( alertMessage );
				applyDialog.setting( {

					title: "Java JDK Tools",

					'labels': {
						ok: 'Save JDK Tools Launch File',
						cancel: 'Cancel request'
					},
					'onok': function () {
						var url = "service/getProfilerLaunchFile?serviceName="
								+ firstService + "&jmxPorts=" + jmxPorts;

						if ( isJvisualVm ) {
							url += "&jvisualvm=jvisualvm";
						}


						openWindowSafely( url, "_blank" );
					},

					'oncancel': function () {
						alertify.warning( "Operation Cancelled" );
					}

				} );




			} );

}


