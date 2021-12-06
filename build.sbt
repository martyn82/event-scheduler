ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

lazy val AkkaVersion = "2.6.17"
lazy val AkkaProjectionVersion = "1.2.2"

lazy val root = (project in file("."))
  .settings(
    name := "event-scheduler",
    organization := "com.github.martyn82",
    organizationName := "martyn82",
    organizationHomepage := Some(url("https://github.com/martyn82")),

    startYear := Some(2021),

    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen"
    ),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
      "com.lightbend.akka" %% "akka-projection-cassandra" % AkkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,

      "org.scalatest" %% "scalatest" % "3.2.10" % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
    )
  )
