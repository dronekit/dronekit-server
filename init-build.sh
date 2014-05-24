echo "Prebuilding various dependencies needed for dronehub"

git submodule update --recursive --init

mkdir dependencies
cd dependencies
git clone git@github.com:geeksville/sbt-scalabuff.git
cd sbt-scalabuff/
sbt publishLocal
cd ..

git clone -b fixes_for_dronehub git@github.com:geeksville/json4s.git
cd json4s
sbt publishLocal
cd ..

git clone https://github.com/geeksville/akka.git
cd akka
sbt publishLocal
cd ..

git clone -b 2.3.x_2.10 git@github.com:geeksville/scalatra.git
cd scalatra
sbt publishLocal
cd ..

