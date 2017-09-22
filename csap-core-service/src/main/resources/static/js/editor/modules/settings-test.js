require( ["settings-edit"], function ( settingsEdit ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
	} );


	function initialize() {
		CsapCommon.configureCsapAlertify();
		$( '#showButton' ).click( function () {
			// alertify.notify("Getting clusters") ;
			var params = {
				lifeToEdit: "dev"
			}
			$.get( settingsEditUrl,
				params,
				settingsEdit.showSettingsDialog,
				'html' );
			return false;
		} );
		// for testing page without launching dialog

		settingsEdit.configureForTest() ;

	}

} );


