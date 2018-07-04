package xyz.driver.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

/** Common settings for a library, Driver style. */
object Library extends AutoPlugin {

  override def requires = JvmPlugin

  lazy val repositorySettings: Seq[Setting[_]] = Seq(
    resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases",
    resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots"
  )

  lazy val publicationSettings: Seq[Setting[_]] = Seq(
    publishTo := {
      val jfrog = "https://drivergrp.jfrog.io/drivergrp/"
      if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots;build.timestamp=" + new java.util.Date().getTime)
      else Some("releases" at jfrog + "releases")
    },
    skip in publish := false
  )

  override def buildSettings = Seq(
    skip in publish := true
  )

  override def projectSettings: Seq[Def.Setting[_]] = repositorySettings ++ publicationSettings ++ Seq(
    javacOptions ++= Seq("-target", "1.8"),
    crossScalaVersions := List("2.12.6"),
    scalaVersion := crossScalaVersions.value.last
  )

}
