// Copyright 2013 Kevin Hester
function uploadInit(awsKey, awsPolicy, awsSignature) {
	$(function() {

		var dropbox = $('#dropbox'), message = $('.message', dropbox);

		var fileKey = 'uploads/' + Math.random() + '.tlog'
		dropbox
				.filedrop({
					// The name of the $_FILES entry:
					paramname : 'file',

					maxfiles : 25,
					maxfilesize : 50, // max file size in MBs
					url : 'https://s3-droneshare.s3.amazonaws.com/',
					allowedfiletypes : [], // ['image/jpeg','image/png','image/gif']
					data : {
						key : fileKey, // send POST variables
						AWSAccessKeyId : awsKey,
						acl : 'private',
						policy : awsPolicy,
						signature : awsSignature,
						"Content-Type" : "application/octet-stream"
					},

					uploadFinished : function(i, file, response, time, xhr) {

						var dat = JSON.stringify({
							key : fileKey,
							userId: null,
							userPass: null
							});

						if(xhr.status < 300 && xhr.status >= 200)
							$.ajax({
										type: 'POST',
										url: '/api/upload/froms3.json',
										data: dat,
										dataType: 'json',
										contentType: "application/json; charset=utf-8",
										success: function(response) {
											var template = '<p>'
													+ file.name
													+ ' uploaded: <a class="btn btn-primary" href="/view/'
													+ response
													+ '">View</a></p>';
											var message = $(template)

											$.data(file).addClass('done');
											// response is the JSON object that
											// the server
											// returns

											// Remove the progress bar
											$.data(file).find('.progress')
													.hide();
											$.data(file).find('.bar').hide();

											// Add a URL
											message.appendTo(dropbox);
										}});
						else
							showMessage('Upload failed: ' + xhr.message)
					},

					error : function(err, file) {
						switch (err) {
						case 'BrowserNotSupported':
							showMessage('Your browser does not support HTML5 file uploads!');
							break;
						case 'TooManyFiles':
							showMessage('Too many files! Please select 5 at most! (configurable)');
							break;
						case 'FileTooLarge':
							showMessage(file.name
									+ ' is too large! Please upload files up to 2mb (configurable).');
							break;
						default:
							break;
						}
					},

					uploadStarted : function(i, file, len) {
						// From bootstrap
						var template = '<div class="progress">'
								+ '<div class="bar"></div>' + '</div>';
						var preview = $(template)

						message.hide();
						preview.appendTo(dropbox);

						// Associating a preview container
						// with the file, using jQuery's $.data():

						$.data(file, preview);
					},

					progressUpdated : function(i, file, progress) {
						// We always stop at 90% so we can have time to wait for
						// the
						// slow json response
						$.data(file).find('.bar').width(progress + "%");
					}

				});

		function showMessage(msg) {
			message.html(msg);
		}
	});
}