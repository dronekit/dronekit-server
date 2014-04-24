echo "Prebuilding various dependencies needed for dronehub"

mkdir dependencies
cd dependencies
git clone git@github.com:geeksville/sbt-scalabuff.git
cd sbt-scalabuff/
sbt publishLocal
