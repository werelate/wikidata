#!/bin/bash
cd "$( dirname "${BASH_SOURCE[0]}" )"
java -Xmx1280m \
-Dfile.encoding=UTF-8 \
-DentityExpansionLimit=2147480000 \
-DtotalEntitySizeLimit=2147480000 \
-Djdk.xml.totalEntitySizeLimit=2147480000 \
-classpath \
../classes:\
../conf:\
../lib/log4j-api-2.12.4.jar:\
../lib/log4j-core-2.12.4.jar:\
../lib/xom-1.1b5.jar:\
../lib/icu4j_3_4.jar:\
../lib/commons-codec-1.3.jar:\
../lib/commons-httpclient-3.1.jar:\
../lib/commons-logging-1.1.1.jar:\
../lib/commons-cli-1.0.jar:\
../lib/mysql-connector-java-5.0.4-bin.jar:\
../lib/sparta.jar:\
../lib/SuperCSV-1.52.jar:\
../lib/shared.jar \
"$@"
