# Installation instructions

## Install various dependencies

    apt-get install mysql-client mysql-server openjdk-8-jdk unzip build-essential uuid-dev nginx python-pip python-numpy

(you will be prompted to choose a root db psw)

## Install tools needed for LogAnalyzer

    sudo pip install MAVProxy

## Install lib ZeroMQ
Ubuntu 14.04 or later

    apt-get install libzmq-dev

Older version of ubuntu, you must build from source:

    ubuntu@ip-10-179-53-86:~/zeromq-2.2.0$ ./configure; make; sudo make install; sudo ldconfig


## Initialize the database

Create a user in mysql per [this document](https://www.digitalocean.com/community/articles/how-to-create-a-new-user-and-grant-permissions-in-mysql)

Also create the dapi master droneapi database.

    ubuntu@ip-10-179-53-86:~$ mysql --user=root --password=REDACTED
    Welcome to the MySQL monitor.  Commands end with ; or \g.
    Your MySQL connection id is 44
    Server version: 5.5.35-0ubuntu0.12.04.2 (Ubuntu)

    Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.

    Oracle is a registered trademark of Oracle Corporation and/or its
    affiliates. Other names may be trademarks of their respective
    owners.

    Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

    Enter the following commands:
    CREATE USER 'dapi'@'localhost' IDENTIFIED BY 'password';
    GRANT ALL PRIVILEGES ON * . * TO 'dapi'@'localhost';
    FLUSH PRIVILEGES;
    create database dapi;

## Setup the application config file

nestor.conf contains all the appkeys/setup needed before running the app.

* cp nestor.conf.template ~/nestor.conf
* edit nestor.conf to add API keys as needed (mainly the AWS key for S3 access, but you can also optionally use other providers - see comments in the file)
