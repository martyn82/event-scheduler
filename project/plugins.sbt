resolvers ++= Seq(
  Resolver.sbtPluginRepo("releases")
)

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5")
