val akkaVersion = "2.3.7"
val sprayVersion = "1.3.2"

name := "akka-messaging"

version := "1.0"

scalaVersion := "2.11.4"

mainClass in Compile := Some("cc.evgeniy.akka.messaging.ClusterMessagingApp")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

libraryDependencies ++= Seq(
  "com.typesafe.akka"  %%  "akka-actor"                    % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka"  %%  "akka-testkit"                  % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka"  %%  "akka-cluster"                  % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka"  %%  "akka-contrib"                  % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka"  %%  "akka-slf4j"                     % akkaVersion withSources() withJavadoc(),
  "ch.qos.logback"     %   "logback-classic"               % "1.1.2",
  "org.fusesource"     %   "sigar"                         % "1.6.4" classifier("native") classifier(""),
  "org.scalatest"      %%  "scalatest"                     % "2.2.0" withSources() withJavadoc()
)


scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding",
  "UTF-8",
  "-feature",
  "-" +
    "Xlog-reflective-calls")

fork := true // for sigar java.library.path only

connectInput in run := true

javaOptions  ++= Seq(
  "-Djava.library.path=./sigar",
  "-Xms128m", "-Xmx1024m")

javacOptions ++= Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation")

// copy sigars libs to jar
//resourceDirectory in Compile <<=
//  baseDirectory{ _ / "./sigar" }

// ======== assembly settings ========
mainClass in assembly := Some("cc.evgeniy.akka.messaging.ClusterMessagingApp")

assemblyJarName in assembly := "akka-messaging.jar"
