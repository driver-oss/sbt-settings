package xyz.driver.sbt

import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.git
import java.time.Instant
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

/** Common settings for a library, Driver style. */
object LibraryPlugin extends AutoPlugin {

  override def requires = JvmPlugin

  lazy val repositorySettings: Seq[Setting[_]] = Seq(
    resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases",
    resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots"
  )

  lazy val publicationSettings: Seq[Setting[_]] = Seq(
    organization := "xyz.driver",
    publishTo := {
      val jfrog = "https://drivergrp.jfrog.io/drivergrp/"
      if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots;build.timestamp=" + Instant.now().toEpochMilli)
      else Some("releases" at jfrog + "releases")
    },
    skip in publish := false
  )

  // Get version from git unless a VERSION environment variable is set
  lazy val versionSettings: Seq[Setting[_]] = sys.env.get("VERSION") match {
    case None =>
      GitPlugin.autoImport.versionWithGit ++ Seq(
        git.useGitDescribe := true, // get version from git
        git.baseVersion := "0.0.0" // this version is used for new projects without any commits
      )
    case Some(v) =>
      Seq(
        version := v
      )
  }

  override def buildSettings: Seq[sbt.Setting[_]] = versionSettings ++ Seq(
    skip in publish := true
  )

  override def projectSettings: Seq[Def.Setting[_]] = repositorySettings ++ publicationSettings ++ Seq(
    javacOptions ++= Seq("-target", "1.8"),
    crossScalaVersions := List("2.12.6"),
    scalaVersion := crossScalaVersions.value.last
  )

}
