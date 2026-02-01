---
layout: default
parent: LDIO Outputs
title: Relational Database Out
---

# LDIO Relational Database Out

***Ldio:LdioRdbOut***

The LDIO RDB Out will transform the RDF model into a relational database by using a SPARQL query.

You have to create the database table beforehand. Then, you can use a SPARQL query to insert the data into the table. 
All variable names will be mapped to the column names of the database table.
You can set the `ignore-duplicate-key-exception` when you have duplicate key constraints on your table,
and when you try to insert multiple times the same data. When you set this to `true`, the pipeline will 
not halt, and will proceed with the next member(s).

## Config


| Property                         | Description                                                                             | Required | Default | Supported values | Example |
|----------------------------------|-----------------------------------------------------------------------------------------|----------|---------|------------------|---------|
| _table-name_                     | The name of the database table where the data will be stored                            | Yes      | N/A     | String           | sensor  |
| _sparql-select-query_            | A SPARQL SELECT query to format the members into a tabular format                       | Yes      | N/A     | String           |         |
| _ignore-duplicate-key-exception_ | Change the behaviour of the pipeline when you have a duplicate key constraint exception | No       | false   | Boolean value    | true    |


## Configuration Example

```yaml
orchestrator:
  pipelines:
    name: my-pipeline
    input:
      name: Ldio:LdesClient
      config:
        urls:
          - http://localhost:8080/my-ldes
        sourceFormat: text/turtle
    outputs:
    - name: Ldio:LdioRdbOut
      config:
        table-name: sensor
        sparql-select-query: -|
            SELECT ?sensor_id ?sensor ?latitude ?longitude ?generated_at_time ?feature_of_interest
            WHERE {
              ?sensor_id <http://purl.org/dc/terms/isVersionOf> ?sensor ;
              <http://www.w3.org/2003/01/geo/wgs84_pos#lat_Lambert72> ?latitude ;
              <http://www.w3.org/2003/01/geo/wgs84_pos#long_Lambert72> ?longitude ;
              <http://www.w3.org/ns/prov#generatedAtTime> ?generated_at_time ;
              <http://www.w3.org/ns/sosa/hasFeatureOfInterest> ?feature_of_interest .
            }
        ignore-duplicate-key-exception: true
```
