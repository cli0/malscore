name := """malscore"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
                                      .dependsOn(manual,query,mlearning,score_calculation)
                                      .aggregate(manual,query,mlearning,score_calculation)

lazy val manual = project.in(file("modules/manual")).enablePlugins(PlayScala)
lazy val query = project.in(file("modules/query")).enablePlugins(PlayScala)
lazy val mlearning = project.in(file("modules/mlearning")).enablePlugins(PlayScala)
lazy val score_calculation = project.in(file("modules/score_calculation")).enablePlugins(PlayScala)

val jacksonVersion = "2.7.4"

libraryDependencies ++= Seq(
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)


resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "Cloudera Repositories" at "https://repository.cloudera.com/artifactory/cloudera-repos/"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "1.6.1" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark" %% "spark-sql" % "1.6.1" exclude("org.slf4j", "slf4j-log4j12"),
  "com.datastax.spark" %% "spark-cassandra-connector" % "1.6.0-M2" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark" %% "spark-mllib" % "1.6.1" exclude("org.slf4j", "slf4j-log4j12"),
  "org.apache.spark" %% "spark-hive" % "1.6.1"
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

libraryDependencies += "com.cloudera.livy" % "livy-main" % "0.2.0"
libraryDependencies += "com.cloudera.livy" % "livy-client-http" % "0.2.0"
libraryDependencies += "com.cloudera.livy" % "livy-api" % "0.2.0"
//libraryDependencies += "com.cloudera.livy" % "livy-rsc" % "0.2.0"
//libraryDependencies += "com.cloudera.livy" % "livy-core_2.11.8" % "0.2.0"
//libraryDependencies += "com.cloudera.livy" % "livy-scala-api" % "0.2.0"
libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.3.0"


