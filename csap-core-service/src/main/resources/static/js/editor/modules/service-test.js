require( ["service-edit"], function ( serviceEdit ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
	} );


	function initialize() {
		CsapCommon.configureCsapAlertify();
		
		$( '#showServiceDialog' ).click( function () {
			// alertify.notify("Getting clusters") ;
			var params = {
				serviceName: serviceName,
				hostName: hostName
			}
			$.get( serviceEditUrl,
				params,
				serviceEdit.showServiceDialog,
				'html' );
			return false;
		} );
		// for testing page without launching dialog

		serviceEdit.configureForTest() ;

	}

} );


