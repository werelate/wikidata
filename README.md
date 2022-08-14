wikidata
========

Various scripts to parse and update data on WeRelate.org

## Building

* run `ant build`

## Places

### Extract places from pages.xml
* place_words.tsv maps words to places
* places.tsv is the main places file
* place_links identifies places that at least one person or family in WeRelate links to

```
wikidata/shell/run.sh org.werelate.scripts.ExtractPlaces pages.xml place_words.tsv places.tsv place_links.tsv
```

### Generate and load place abbreviations
* Generate place_abbrevs from places and place_links
* upload place_abbrevs

```
wikidata/shell/run.sh org.werelate.scripts.GeneratePlaceAbbrevs places.tsv place_links.tsv place_abbrevs2.tsv
# CREATE TABLE place_abbrevs2 like place_abbrevs;
mysqlimport -h <host> -u<user> -p<password> --default-character-set=utf8mb4 --fields-terminated-by='\t' --local wikidb place_abbrevs2.tsv
# DROP TABLE place_abbrevs1;
# RENAME TABLE place_abbrevs TO place_abbrevs1, place_abbrevs2 TO place_abbrevs;
```

## Sources

### Extract sources from pages.xml
* sources.tsv is the main sources file
* source_counts.tsv counts the number of times each source is cited in person/family pages

```
wikidata/shell/run.sh org.werelate.scripts.ExtractSources pages.xml sources.tsv source_counts.tsv
```

## Names

* mysql ... -e 'select * from givenname_similar_names' > givenname_similar_names.werelate.tsv
* mysql ... -e 'select * from surname_similar_names' > surname_similar_names.werelate.tsv