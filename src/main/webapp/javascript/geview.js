// Copyright 2013 Kevin Hester
function geInit(kmzUrl) {
    var ge;
    google.load("earth", "1");

    function isOSSupported() {
        return (navigator.appVersion.indexOf("Win")!=-1) || (navigator.appVersion.indexOf("Mac")!=-1);
    }
    
    function init() {
      google.earth.createInstance('map3d', initCB, failureCB);
    }

    function initCB(instance) {
      ge = instance;
      ge.getWindow().setVisibility(true);
      
      // Allow controls
      ge.getNavigationControl().setVisibility(ge.VISIBILITY_AUTO);
      
      // Zoom to our tracklog
      var link = ge.createLink('');
      link.setHref(kmzUrl);

      var networkLink = ge.createNetworkLink('');
      networkLink.set(link, true, true); // Sets the link, refreshVisibility, and flyToView

      ge.getFeatures().appendChild(networkLink);
    }

    function failureCB(errorCode) {
    	// alert("failure");
    }
    
    $(function() {
    	init();
    });
}