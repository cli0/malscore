name := """score_calculation"""

version := "1.0-SNAPSHOT"

//lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

val jacksonVersion = "2.7.4"

libraryDependencies ++= Seq(
  cache,
  ws)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"


libraryDependencies ++= Seq(
  // The other implementation (slf4j-log4j12) would be transitively
  // included by Spark. Prevent that with exclude().
  "org.apache.spark" %% "spark-core" % "1.6.1" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark" %% "spark-sql" % "1.6.1" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark" %% "spark-mllib" % "1.6.1" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark" %% "spark-hive" % "1.6.1",
  "com.datastax.spark" %% "spark-cassandra-connector" % "1.6.0-M2" exclude("org.slf4j", "slf4j-log4j12")
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" % "jackson-module-paranamer" % jacksonVersion,
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % jacksonVersion,
  //"com.fasterxml.jackson.module" % "jackson-datatype-jdk8" % jacksonVersion,
  //"com.fasterxml.jackson.module" % "jackson-datatype-jsr310" % jacksonVersion,
  // test dependencies
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % jacksonVersion,
  "com.fasterxml.jackson.module" % "jackson-module-jsonSchema" % jacksonVersion)

libraryDependencies += "joda-time" % "joda-time" % "2.9.4"
