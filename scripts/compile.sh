#!/bin/bash

git pull

echo "Downloading data..."
wget -O ../data.zip https://chalmersuniversity.box.com/shared/static/2zob4ruojm860sps7xi7sxhzxyijmx0c.zip
echo "Extracting files..."
unzip -q ../data.zip -d ../
rm ../data.zip
echo "Data downloaded"


cd ../
BRANCH="$(git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/(\1)/')"
COMMIT="$(git rev-parse --short HEAD)"
mvn clean package
mvn assembly:assembly

mv "target/pi-lisco-0.0.1-SNAPSHOT-jar-with-dependencies.jar" "target/pi-lisco-0.0.1-${COMMIT}.jar"
echo "Using branch $BRANCH and commit $COMMIT"