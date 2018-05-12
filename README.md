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

* place_abbrevs table; the use of latin1 character set is intentional:

```
CREATE TABLE `place_abbrevs` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `abbrev` varchar(191) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
  `name` varchar(255) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
  `primary_name` varchar(255) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
  `title` varchar(255) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
  `latitude` decimal(9,6) NOT NULL DEFAULT '0.000000',
  `longitude` decimal(9,6) NOT NULL DEFAULT '0.000000',
  `priority` int(10) unsigned NOT NULL,
  PRIMARY KEY (`id`),
  KEY `abbrev` (`abbrev`,`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_bin
```

* Generate place_abbrevs from places and place_links
* upload place_abbrevs

```
wikidata/shell/run.sh org.werelate.scripts.GeneratePlaceAbbrevs places.tsv place_links.tsv place_abbrevs.tsv
mysqlimport -h <host> -u<user> -p<password> --default-character-set=utf8mb4 --fields-terminated-by='\t' --local trees place_abbrevs.tsv
# RENAME TABLE place_abbrevs TO place_abbrevs1, place_abbrevs2 TO place_abbrevs;
```

## Sources

### Extract sources from pages.xml
* sources.tsv is the main sources file
* source_counts.tsv counts the number of times each source is cited in person/family pages

```
wikidata/shell/run.sh org.werelate.scripts.ExtractSources pages.xml sources.tsv source_counts.tsv
```
