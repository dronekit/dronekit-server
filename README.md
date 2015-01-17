# APIHub

Build Status: [![Dependency Status](https://www.codeship.io/projects/6b33c470-ae1f-0131-dd5f-06fd12e6a611/status)](https://www.codeship.io/projects/18692)

Copyright 2014 3D Robotics

Under a TBD license

# How to setup your build tree

For an Ubuntu 14.10 machine, the steps to install a 'virgin' copy of this build tree are as follows:

* sudo apt-get install sbt openjdk-7-jsk
* cp nestor.conf.template ~/nestor.conf
* edit nestor.conf to add API keys as needed (mainly the AWS key for S3 access)
* ./init-build.sh

# How to build/run locally

To start build environment: "sbt"
Once build env is running you have various options:

* 'test' - runs the unit & integration tests (using a throw away SQL server instance)
* 'container:restart' - start a web server running the app on port 8080
* '~ container:restart' - restart the webserver any time a source file changes
* 'eclipse' - build eclipse project files

