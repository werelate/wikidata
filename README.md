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

* place_abbrevs table

```
CREATE TABLE `place_abbrevs` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `abbrev` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `primary_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `latitude` decimal(9,6) NOT NULL DEFAULT '0.000000',
  `longitude` decimal(9,6) NOT NULL DEFAULT '0.000000',
  `priority` int(10) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `abbrev` (`abbrev`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

* Generate place_abbrevs from places and place_links
* upload place_abbrevs

```
wikidata/shell/run.sh org.werelate.scripts.GeneratePlaceAbbrevs places.tsv place_links.tsv place_abbrevs2.tsv
# CREATE TABLE place_abbrevs2 like place_abbrevs;
mysqlimport -h <host> -u<user> -p<password> --default-character-set=utf8mb4 --fields-terminated-by='\t' --local trees place_abbrevs2.tsv
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