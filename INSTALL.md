# Installation instructions

To install on a new server

* Install MySql
* Create a user in mysql per https://www.digitalocean.com/community/articles/how-to-create-a-new-user-and-grant-permissions-in-mysql
CREATE USER 'dapi'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON * . * TO 'newuser'@'localhost';
FLUSH PRIVILEGES;
