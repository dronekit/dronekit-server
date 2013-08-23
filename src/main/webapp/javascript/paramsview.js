// Copyright 2013 Kevin Hester
function paramsInit(tlogid) {
	$.getJSON(tlogid, {/*somedata*/}, function(json_data){

	    //no need for parsejson
	    //use the json_data object

	    var table_obj = $('#paramsTable');
	    var badRange = false;
	    $.each(json_data, function(index, item){
	         var style = item.rangeOk ? "" : "background-color:red";
	         
	         if(!item.rangeOk)
	        	 badRange = true;
	        	  
	         table_obj.append($('<tr><td>'+item.id+
	        		 '</td><td style="' + style + '">'+item.value+'</td><td>'+item.doc+'</td></tr>'));
	    })

	    if(badRange) {
	    	$('#param-warning').show();
	    	$('a[href=#paramsTab]').tab('show');
	    }
	})
}