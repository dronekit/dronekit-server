# Nestor #

Copyright 2013 Kevin Hester

Released under a GPL 3.0 license (see LICENSE.txt)

This project is the source code of [droneshare.com](http://www.droneshare.com).  I'm making this release in hopes of collaborating with other developers.  Pull requests are eagerly accepted, if you have questions building or running this application please send me an email.

If you are interested in making Droneish webservices (either as a hobby or as a commerical endeavor) please contact me.  I might be able to help.

## General structure

This application is a small (and still slightly hacky) [REST](http://en.wikipedia.org/wiki/Representational_state_transfer) + Javascript web app based on [scalatra](http://www.scalatra.org/).  Broadly speaking there are three 
general areas:
* The REST backend is written in scala and lives in src/main/scala.  I'd recommend looking at MainServlet and DeviceServlet first.
* The frontend is in HTML (acutally a more programmer friendly [dry](http://en.wikipedia.org/wiki/Don't_repeat_yourself) flavor called [scaml](http://scalate.fusesource.org/documentation/scaml-reference.html)).  It lives in src/main/webapp/WEB-INF/templates.  You can either look at the scaml files or view the generated html in Chrome.
* The javascript to provide all the Ajax goodness for the frontend lives in src/main/webapp/javascript.

In general - you can add new features by futzing with the html/javascript OR the scala code.  For most work you don't need to change both areas.

FIXME - document the REST API (for now this is best seen by just looking at the calls the frontend makes - use the Chrome debugger)

## Build & Run ##

```sh
$ apt-get install sbt
$ cd droneshare
$ git submodule init
$ git submodule update
$ sbt
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

# Deployment
This app can be easily deployed to either Heroku or EC2.

## Heroku

```
kevinh@kevin-server:~/development/drone/nestor$ heroku login
Enter your Heroku credentials.
Email: ...
Password (typing will be hidden): 
Authentication successful.
kevinh@kevin-server:~/development/drone/nestor$ git gui
kevinh@kevin-server:~/development/drone/nestor$ git push heroku master
heroku config:set JAVA_OPTS="-Xmx215m -Xss300k -XX:+UseCompressedOops"
```

## EC2

```sh
sbt assembly
./deploy-to-ec2
```

## Possible future ideas

* Use bootstrap via templates like this: https://github.com/tototoshi/scalatra-bootstrap.g8/tree/master/src/main/g8/src/main
* Fix coffeescript to happen automatically on container:start?
// Generate Resource task is invoked if package command is invoked
    pack in Compile <<= (pack in Compile) dependsOn (generateResources in Compile)

* Use salat for serialization https://github.com/novus/salat/wiki/Annotations

### Features to add

Before release:
* add wpt file extraction
* show loading animation for plot & param view
* show mode changes by changing flightpath color
* Report exceptions via github
* Show a world summary map
* use bootstrap lock icon
* fix logback thresholds
* put waypoints on a seperate maps layer
* When uploading do the slow s3 write in a future
* fix auto rebuild
* add authentication - don't use oauth, rather just let the user pick a username and psw (no email) - http://www.jaredarmstrong.name/2011/08/scalatra-form-authentication-with-remember-me/
* Use a full screen google maps view as the default, with overlay menus for switch to google earth or plot view? (see testmaps.html)
* Show waypoints using custom icons https://developers.google.com/earth/documentation/placemarks (with baloon)
* show mode changes and status msgs on the google earth file - with links back to the log html

* Have :id magically expand into either one files worth of chunks or n files

* Emit annotations where mode changes occur etc... http://www.flotcharts.org/flot/examples/annotating/index.html

* make series selectable view-source:http://www.flotcharts.org/flot/examples/series-toggle/index.html
* make plot resizable view-source:http://www.flotcharts.org/flot/examples/resize/index.html

* Show loading animation

* Have andropilot offer to share this flight notification...

* Make a browse view that lists recent uploads

### Much later
(for now just return all the channels worth of data and let JS reduce it)
* Have :id/channel/channelname.json return data for that channel
* Have :id/channelnames.json return all possible channel names
* Huge FIXME: TLogChunk.allocateId should check for collisions

