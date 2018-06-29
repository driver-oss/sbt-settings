package xyz.driver.sbt

import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{AutoPlugin, _}

object Versioning extends AutoPlugin {

  override def requires = JvmPlugin
  override def trigger  = allRequirements

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

  override def buildSettings = versionSettings ++ Seq(
    organization := "xyz.driver"
  )

}
