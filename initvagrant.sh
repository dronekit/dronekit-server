echo "PROVISIONING DroneAPI-Private vagrant instance.  You'll need to manually edit ~/nestor.conf"
sudo apt-get update
sudo apt-get install git python-pip openjdk-6-jre-headless openjdk-7-jdk python-dev build-essential python-numpy
pushd /tmp
wget http://dl.bintray.com/sbt/debian/sbt-0.13.5.deb
sudo dpkg -i sbt-0.13.5.deb
popd
cp -a ~/host-ssh/id* ~/.ssh

echo "INITING JAVA/SBT build dependancies - this will take a while..."
cd /vagrant
./init-build
echo "Doing an initial build"
sbt compile

