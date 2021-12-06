ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

lazy val AkkaVersion = "2.6.17"

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
    )
  )
