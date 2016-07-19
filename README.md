# WikiData Geo Mapper

Look up entities found by [TAGME] in Wikidata and extract geographical information.

## Usage

Compile using Maven:

    mvn package

Run as:

    java -jar target/wikidata-geo-mapping-0.0.1-SNAPSHOT-jar-with-dependencies.jar *.csv

where `*.csv` are the output files produced by TAGME. This will produce one new file per input file, e.g. for `0.html_annotations.csv` this will create `0.html_annotations-geo.csv`.


## Data format

Each file has lines corresponding to each entity mention. The lines have the following columns:

|Column       | Description
|:------------|:-----------
`token`       | text that occured in the document
`entity`      | normalized name of the entity
`offset`      | position of the token in the document (number of characters)
`entity_url`  | Wikipedia URL of the entity
`confidence`  | TAGME confidence that the token is really the entity
`wikidata_id` | WikiData ID of the entity (may be empty)
`coordinates` | geo-coordinates of the entity (may be empty)

## License

Copyright 2016 Gerhard Gossen. This program may be used under the Apache License 2.0.



[TAGME]: https://tagme.d4science.org/tagme/
