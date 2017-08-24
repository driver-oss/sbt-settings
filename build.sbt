sbtPlugin := true

organization := "xyz.driver"
name := "sbt-settings"
scalaVersion := "2.12.3"

publishMavenStyle := true

addSbtPlugin("com.lucidchart"  %% "sbt-scalafmt"          % "1.10")
addSbtPlugin("org.scalastyle"  %% "scalastyle-sbt-plugin" % "1.0.0")

// Launch and deploy/release plugins
addSbtPlugin("io.spray"          %% "sbt-revolver"        % "0.9.0")
addSbtPlugin("com.eed3si9n"      %% "sbt-buildinfo"       % "0.7.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-git"             % "0.9.3")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-native-packager" % "1.2.2")
addSbtPlugin("com.eed3si9n"      %% "sbt-assembly"        % "0.14.5")
addSbtPlugin("com.github.gseitz" %% "sbt-release"         % "1.0.6")

publishTo := {
  val jfrog = "https://drivergrp.jfrog.io/drivergrp/"
  if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots")
  else Some("releases" at jfrog + "releases")
}

credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***")
