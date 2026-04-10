---
layout: default
parent: LDIO Outputs
title: Relational Database Out
---

# LDIO Relational Database Out

***Ldio:LdioRdbOut***

The LDIO RDB Out will transform the RDF model into a relational database using a SPARQL query.

Configure your database connection like you would normally do for a Spring boot application (
see [Spring Boot Data Access](https://docs.spring.io/spring-boot/how-to/data-access.html#howto.data-access.configure-custom-datasource)
and [Spring Boot Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.datasource.url)
for more details).

You have to create the database table beforehand. Then, you can use a SPARQL query to insert the
data into that table. All variable names in the SPARQL query will be mapped to the column names of
the database table.

You can set the `ignore-duplicate-key-exception` when you have duplicate key constraints on your
table, and when you try to insert multiple times the same data. When you set this to `true`, the
pipeline will not halt, and will proceed with the next member(s). This behavior is only working for
a Postgresql database right now, but we are planning to add support for other databases in the
future.

## Config

| Property                         | Description                                                                             | Required | Default | Supported values | Example |
|----------------------------------|-----------------------------------------------------------------------------------------|----------|---------|------------------|---------|
| _table-name_                     | The name of the database table where the data will be stored                            | Yes      | N/A     | String           | sensor  |
| _sparql-select-query_            | A SPARQL SELECT query to format the members into a tabular format                       | Yes      | N/A     | String           |         |
| _ignore-duplicate-key-exception_ | Change the behaviour of the pipeline when you have a duplicate key constraint exception | No       | false   | Boolean value    | true    |

## Configuration Example

application.yaml that is mounted in the LDIO Workbench container under `/ldio/application.yaml`:
```yaml
spring:
  datasource:
    url: "jdbc:postgresql://localhost:5432/mydatabase"
    username: "dbuser"
    password: "dbpass"
    configuration:
      maximum-pool-size: 30
```

pipeline:
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
