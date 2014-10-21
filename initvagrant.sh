echo "PROVISIONING DroneAPI-Private vagrant instance."
sudo apt-get -y update
sudo apt-get -y install git python-pip openjdk-6-jre-headless openjdk-7-jdk python-dev build-essential python-numpy
pushd /tmp
wget http://dl.bintray.com/sbt/debian/sbt-0.13.5.deb
sudo dpkg -i sbt-0.13.5.deb
popd
echo "INITING JAVA/SBT build dependancies - this will take a while..."
# Note - this script is run as root, so change to the vagrant user for remaining steps
sudo --login --set-home -u vagrant bash << EOF
cp -a /home/vagrant/host-ssh/id* /home/vagrant/.ssh

echo "Initing build dependencies"
cd /vagrant
./init-build.sh

echo "Doing an initial build"
sbt compile
EOF
echo "Done PROVISIONING DroneAPI-Private You'll need to manually edit /home/vagrant/nestor.conf"
