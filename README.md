# storm-postgresql

[![Snacktrace Score](http://snacktrace.com/artifacts/com.snacktrace/storm-postgresql/latest/score/image "Snacktrace Score")](http://snacktrace.com/artifacts/com.snacktrace/storm-postgresql/latest)

A Trident state implementation for PostgreSQL that supports non-transactional, transactional, and opaque-transactional modes

## Maven Coordinates

```
<dependency>
  <groupId>com.snacktrace</groupId>
  <artifactId>storm-postgresql</artifactId>
  <version>1.0.0</version>
</dependency>
```

## SBT

```
libraryDependencies += "com.snacktrace" % "storm-postgresql" % "1.0.0"
```

## Usage

```
PostgresqlStatConfig postgresConfig = new PostgresqlStateConfig();
postgresConfig.setUrl("jdbc:postgresql://HOST:PORT/DATABASE?user=USERNAME&password=PASSWORD");
postgresConfig.setTable("TABLE");
postgresConfig.setType(StateType.OPAQUE);
// Key columns are the columns that form a compound, unique key for the state
postgresConfig.setKeyColumns(new String[]{"word"});
// Value columns are the columns that will hold the value of the state
postgresConfig.setValueColumns(new String[]{"count"});
// The sql type of the key columns
postgresConfig.setKeyTypes(new String[]{"text"});
// The sql type of the value columns
postgresConfig.setValueTypes(new String[]{"integer"});
// Construct a state factory using the configuration
Factory factory = PostgresqlState.newFactory(postgresConfig);

// Then use the factory to persist state in your topology. E.g.
topology.newStream("spout1", spout)
  .each(new Fields("str"), new Split(), new Fields("word"))
  .groupBy(new Fields("word"))
  .persistentAggregate(factory, new Count(), new Fields("count"))
```
