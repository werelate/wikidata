java -Xmx768m -Dfile.encoding=UTF-8 \
  -classpath classes:lib/log4j-1.3alpha-7.jar:lib/xom-1.1b5.jar:lib/icu4j_3_4.jar:conf:lib/commons-codec-1.3.jar:lib/commons-httpclient-3.1.jar:lib/commons-logging.jar:lib/commons-cli-1.0.jar \
  org.werelate.duplicates.ProduceFamilyDuplicates $1 $2 $3 $4 $5 $6 $7 $8 $9
