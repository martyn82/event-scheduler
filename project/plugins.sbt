resolvers ++= Seq(
  Resolver.sbtPluginRepo("releases")
)

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5")

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin"           % "0.11.7",
  "com.thesamet.scalapb" %% "scalapb-validate-codegen" % "0.3.2"
)
