// Copyright 2013 Kevin Hester
function paramsInit(tlogid) {
	$.getJSON(tlogid, {/*somedata*/}, function(json_data){

	    //no need for parsejson
	    //use the json_data object

	    var table_obj = $('#paramsTable');
	    var badRange = false;
	    $.each(json_data, function(index, item){
	         var style = item.rangeOk ? "" : "background-color:red";
	        
	         var valueEntry = item.value
	         
	         if(null != item.range) {
	        	var title = 'Recommended between ' + item.range[0].toFixed(1) + ' and ' + item.range[1].toFixed(1)
	        	var prefix = '<a href="#" rel="tooltip" data-original-title="' + title + '">'
	        	valueEntry = prefix + valueEntry + '</a>'
	         }
	         
	         valueEntry = '<td style="' + style + '">'+valueEntry+'</td>'
	         
	         if(!item.rangeOk)
	        	 badRange = true;
	        
	         table_obj.append($('<tr><td>'+item.id+
	        		 '</td>' + valueEntry + '<td>' +item.doc+'</td></tr>'));
	    })

	    $('[rel=tooltip]').tooltip()
	    
	    if(badRange) {
	    	$('#param-warning').show();
	    	$('a[href=#paramsTab]').tab('show');
	    }
	})
}