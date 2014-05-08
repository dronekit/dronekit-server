# Installation instructions

To install on a new server

* Install various dependancies
Ubuntu 14.04 or later
apt-get install libzmq-dev

Older version of ubuntu, you must build
ubuntu@ip-10-179-53-86:~/zeromq-2.2.0$ make
from src.

* Install MySql
apt-get install mysql-client mysql-server
(you will be prompted to choose a root db psw)

* Create a user in mysql per https://www.digitalocean.com/community/articles/how-to-create-a-new-user-and-grant-permissions-in-mysql

ubuntu@ip-10-179-53-86:~$ mysql --user=root --password=REDACTED
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 44
Server version: 5.5.35-0ubuntu0.12.04.2 (Ubuntu)

Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> CREATE USER 'dapi'@'localhost' IDENTIFIED BY 'password';
Query OK, 0 rows affected (0.00 sec)

mysql> GRANT ALL PRIVILEGES ON * . * TO 'dapi'@'localhost';
Query OK, 0 rows affected (0.01 sec)

mysql> FLUSH PRIVILEGES;
Query OK, 0 rows affected (0.00 sec)

mysql> create database dapi;
Query OK, 1 row affected (0.00 sec)


