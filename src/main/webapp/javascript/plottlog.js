// Copyright 2013 Kevin Hester
function plotInit(tlogid) {
	$(function() {
		var options = {
			xaxis : {
				mode : "time",
				timeformat : "%M:%S"
			},
			zoom : {
				interactive : true
			},
			pan : {
				interactive : true
			},
			/* For now let's leave it back in the main view
			legend : {
				container : "#legend",
				noColumns : 3,
				
				labelFormatter: function(label, series) {
				    // series is the series object for the label
				    return '<a href="#' + label + '">' + label + '</a>';
				}
			}
			*/
		};

		// var datasets = [];

		function onDataReceived(data) {

			// Extract the first coordinate pair; jQuery has parsed it, so
			// the data is now just an ordinary JavaScript object

			/*
			 * var firstcoordinate = "(" + series.data[0][0] + ", " +
			 * series.data[0][1] + ")"; button.siblings("span").text("Fetched " +
			 * series.label + ", first point: " + firstcoordinate); // Push the
			 * new data onto our existing data array
			 * 
			 * if (!alreadyFetched[series.label]) { alreadyFetched[series.label] =
			 * true; data.push(series); }
			 */

			var plot = $.plot("#plotcontent", data, options);

			/*
			 * fixme - doesn't work var placeholder = $("#plotcontent"); $("<div
			 * class='button' style='left:20px;top:20px'>zoom out</div>")
			 * .appendTo(placeholder) .click(function (event) {
			 * event.preventDefault(); plot.zoomOut(); });
			 */
		}

		$.ajax({
					url : tlogid,
					type : "GET",
					dataType : "json",
					success : onDataReceived
				});

		// $.plot($("#placeholder"), datasets, options);
	});
}