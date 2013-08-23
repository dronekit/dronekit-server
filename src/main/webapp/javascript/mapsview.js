// Copyright 2013 Kevin Hester
function mapsInit(kmzUrl) {
 
    function initialize() {
        var mapOptions = {
          // Keep these defaults in case the layer load fails
          //center: new google.maps.LatLng(-33.820591,151.064736),
          //zoom: 8,
          mapTypeId: google.maps.MapTypeId.SATELLITE
        };
        var map = new google.maps.Map(document.getElementById("gmaps"), mapOptions);
        
        var ctaLayer = new google.maps.KmlLayer(kmzUrl);
        ctaLayer.setMap(map);
      }
    
    $(function() {
    	initialize();
    });
}