echo "Prebuilding various dependencies needed for dronehub"

# The akka build will fail on some unimportant parts
# set -e
git submodule update --recursive --init

echo "Installing LogAnalyzer dependencies"
pip install numpy

cp nestor.conf.template ~/nestor.conf

mkdir dependencies
cd dependencies

#SCALA=scala-2.10.4
#wget http://www.scala-lang.org/files/archive/$SCALA.tgz
#tar xvzf $SCALA.tgz
#pushd ~/bin
#ln -s ../dependencies/$SCALA/bin/* .
#popd

git clone git@github.com:geeksville/sbt-scalabuff.git
cd sbt-scalabuff/
sbt publishLocal
cd ..

git clone -b fixes_for_dronehub git@github.com:geeksville/json4s.git
cd json4s
sbt publishLocal
cd ..

# akka needs sphinx to make docs
pip install sphinx
git clone https://github.com/geeksville/akka.git
cd akka
sbt -Dakka.scaladoc.diagrams=false publishLocal
cd ..

git clone -b 2.3.x_2.10 git@github.com:geeksville/scalatra.git
cd scalatra
sbt publishLocal
cd ..

git clone git@github.com:geeksville/scala-activerecord.git
cd scala-activerecord
sbt "project core" publishLocal "project generator" publishLocal "project scalatra" publishLocal "project scalatraSbt" publishLocal
cd ..

echo Fixing up bad ivy files on codeship
find ~/.ivy2/cache -name \*.original | xargs rm

