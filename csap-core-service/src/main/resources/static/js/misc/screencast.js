
$(document).ready(function() {

	CsapCommon.configureCsapAlertify();
	var screencast = new Screencast();
	screencast.appInit();

});

function Screencast() {

	// note the public method
	this.appInit = function() {
		console.log("Init");

		$("#vidSize").change(function() {

			var selectVal = $("#vidSize").val();
			var message = "Selected: " + selectVal;
			// alertify.alert( message ) ;
			alertify.notify(message, "dummy", 2000);

			switch (selectVal) {
			case "800x600":
				$("#theVideo").attr("width", "800");
				$("#theVideo").attr("height", "600");
				break;

			case "1024x768":
				$("#theVideo").attr("width", "1024");
				$("#theVideo").attr("height", "768");
				break;

			case "1280x960":
				$("#theVideo").attr("width", "1280");
				$("#theVideo").attr("height", "960");
				break;

			}
			return false; // prevents link
		});

	};

}
