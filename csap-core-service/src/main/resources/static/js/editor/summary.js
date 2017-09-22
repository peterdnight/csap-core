$(document).ready(function () {

    appInit();

});

function appInit() {
	
	$('.releasePackage').change(function() {
		
		var url="showSummary?releasePackage=" + $(this).val() ;
		// console.log("Going to " + url) ;
		window.location.href=url;
		return true; // prevents link
	});
	
    $('.editLimitsButton').click(function () {
		var pkg = $('.releasePackage').val() ;
		
		var targetUrl="editor?path=" + $(this).data("editorpath") +"&releasePackage="+ pkg  ;
		CsapCommon.openWindowSafely(targetUrl ,   "_blank");
		return false ;
    });

	
    $('.viewDataButton').click(function () {
		var pkg = $('.releasePackage').val() ;
		
		var targetUrl= $(this).data("url") +"&project="+ pkg  ;
		CsapCommon.openWindowSafely(targetUrl ,   "_blank");
		return false ;
    });


}