require( ["dialog-cluster-edit"], function ( clusterEditDialog ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
		CsapCommon.configureCsapAlertify();
	} );


	function initialize() {


		$( '#showButton' ).click( function () {
			// alertify.notify("Getting clusters") ;
			
			// $( ".releasePackage" ).val(), $( ".lifeSelection" ).val(),$( "#dialogClusterSelect" ).val()
			var params = {
				releasePackage: $( ".releasePackage" ).val(),
				lifeEdit: $( ".lifeSelection" ).val(),
				clusterName: $( "#dialogClusterSelect" ).val()
			}


			$.get( clusterEditUrl,
					params,
					clusterEditDialog.show,
					'html' );
			return false;
		} );
		// for testing page without launching dialog
		clusterEditDialog.configureForTest();
	}

} );


