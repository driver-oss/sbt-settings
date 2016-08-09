
sbtPlugin := true

organization := "com.drivergrp"
name := "sbt-settings"
scalaVersion := "2.10.6"

publishMavenStyle := false

// Code style plugins
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.2.10")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "1.0.1")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

// Launch and deploy/release plugins
addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.0")

// This plugin represents functionality that is to be added to sbt in the future
addSbtPlugin("org.scala-sbt" % "sbt-core-next" % "0.1.1")

publishTo := {
  val jfrog = "https://drivergrp.jfrog.io/drivergrp/"
  if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots")
  else                  Some("releases"  at jfrog + "releases")
}

credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***")
