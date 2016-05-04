# DroneKit-Server
# License
Copyright 2014 3D Robotics, Inc.

DroneKit-Server is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

DroneKit-Server is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with DroneKit-Server.  If not, see [http://www.gnu.org/licenses/](http://www.gnu.org/licenses/).

# Contact
The primary author of this software is Kevin Hester, you can reach him at kevinh@geeksville.com.

# How to setup your build tree
For an Ubuntu 14.10 machine, the steps to install a 'virgin' copy of this build tree are as follows:
- sudo apt-get install sbt openjdk-7-jsk
- cp nestor.conf.template ~/nestor.conf
- edit nestor.conf to add API keys as needed (mainly the AWS key for S3 access)
- ./init-build.sh

# How to build/run locally
To start build environment: "sbt" Once build env is running you have various options:
- 'test' - runs the unit & integration tests (using a throw away SQL server instance)
- 'container:restart' - start a web server running the app on port 8080
- '~ container:restart' - restart the webserver any time a source file changes
- 'eclipse' - build eclipse project files

# How to install on a production server

Please see [INSTALL.md](INSTALL.md).

