
lazy val AkkaVersion = "2.6.17"
lazy val AkkaHttpVersion = "10.2.7"
lazy val AkkaGrpcVersion = "2.1.1"
lazy val AkkaProjectionVersion = "1.2.2"
lazy val SlickVersion = "3.3.3"
lazy val Slf4jVersion = "1.7.32"
lazy val ScalikeJdbcVersion = "3.5.0"

ThisBuild / organization := "com.github.martyn82"
ThisBuild / organizationName := "martyn82"
ThisBuild / organizationHomepage := Some(url("https://github.com/martyn82"))

ThisBuild / scalaVersion := "2.13.7"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / startYear := Some(2021)

ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen"
)

ThisBuild / libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "ch.qos.logback" % "logback-classic" % "1.2.7",

  "org.mockito" % "mockito-core" % "4.1.0" % Test,
  "org.scalatest" %% "scalatest" % "3.2.10" % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
)

lazy val `event-scheduler-service` = project
  .dependsOn(
    `event-scheduler-api`,
    `akka-event-scheduler`
  )
  .settings(
    name := "event-scheduler",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.1.1",
    ),

    dependencyOverrides ++= Seq(
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    )
  )

lazy val `akka-event-scheduler` = project
  .settings(
    name := "akka-event-scheduler",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,

      "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.4",
      "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,

      "com.lightbend.akka" %% "akka-projection-slick" % AkkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,

      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,

      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,

      "org.postgresql" % "postgresql" % "42.3.1",
      "org.scalikejdbc" %% "scalikejdbc" % ScalikeJdbcVersion,
      "org.scalikejdbc" %% "scalikejdbc-config" % ScalikeJdbcVersion,

      "org.scalikejdbc" %% "scalikejdbc-test" % ScalikeJdbcVersion % Test,
    )
  )

lazy val `event-scheduler-api` = project
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    name := "event-scheduler-api",

    akkaGrpcCodeGeneratorSettings += "flat_package",

    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,

      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    )
  )

lazy val `event-scheduler-client` = project
  .dependsOn(
    `event-scheduler-api`
  )
  .settings(
    name := "event-scheduler-client",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    )
  )
