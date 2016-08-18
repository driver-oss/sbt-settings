package com.drivergrp.sbt

import sbt.Keys._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.SettingsHelper._
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import com.typesafe.sbt.{GitBranchPrompt, GitVersioning}
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin._
import sbt.{Credentials, Project, State, _}
import sbtassembly.AssemblyKeys._
import sbtassembly._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, BuildInfoOption, _}
import sbtdocker.DockerPlugin
import sbtrelease.{Version, _}
import wartremover.WartRemover.autoImport._
// we hide the existing definition for setReleaseVersion to replace it with our own
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _}


/**
  * @see https://engineering.sharethrough.com/blog/2015/09/23/capturing-common-config-with-an-sbt-parent-plugin/
  */
object SbtSettings extends AutoPlugin {

  object autoImport {

    lazy val scalaFormatSettings = Seq(
      scalafmtConfig in ThisBuild := Some(file(".scalafmt")),
      testExecution in (Test, test) <<=
        (testExecution in (Test, test)) dependsOn (scalafmtTest in Compile, scalafmtTest in Test))

    private lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

    lazy val scalastyleSettings = Seq(
      compileScalastyle := (scalastyle in Compile).toTask("").value,
      (compile in Compile) <<= ((compile in Compile) dependsOn compileScalastyle))

    lazy val wartRemoverSettings = Seq(
      wartremoverErrors in (Compile, compile) ++= Warts.allBut(
        Wart.AsInstanceOf, Wart.Nothing, Wart.Overloading, Wart.DefaultArguments, Wart.Any,
        Wart.Option2Iterable, Wart.ExplicitImplicitTypes, Wart.Throw, Wart.ToString, Wart.NoNeedForMonad))

    lazy val lintingSettings = scalastyleSettings ++ wartRemoverSettings

    lazy val testAll = TaskKey[Unit]("test-all", "Launches all unit and integration tests")

    lazy val repositoriesSettings = {
      Seq(
        resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases",
        resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots",
        credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***"))
    }

    lazy val publicationSettings = Seq(
      // publishTo := Some(Resolver.file("file", new File("releases")))
      publishTo := {
        val jfrog = "https://drivergrp.jfrog.io/drivergrp/"

        if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots;build.timestamp=" + new java.util.Date().getTime)
        else                  Some("releases"  at jfrog + "releases")
      },
      credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***"))

    lazy val releaseSettings = {
      def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
        val vs = st.get(ReleaseKeys.versions).getOrElse(
          sys.error("No versions are set! Was this release part executed before inquireVersions?"))
        val selected = selectVersion(vs)

        st.log.info("Setting version to '%s'." format selected)
        val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)

        reapply(Seq(
          if (useGlobal) version in ThisBuild := selected else version := selected
        ), st)
      }

      lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

      Seq(
        releaseIgnoreUntrackedFiles := true,
        // Check http://blog.byjean.eu/2015/07/10/painless-release-with-sbt.html for details
        releaseVersionBump := sbtrelease.Version.Bump.Minor,
        releaseVersion <<= releaseVersionBump(bumper => {
          ver => Version(ver)
            .map(_.withoutQualifier)
            .map(_.bump(bumper).string).getOrElse(versionFormatError)
        }),
        releaseProcess := Seq[ReleaseStep](
          checkSnapshotDependencies,
          inquireVersions,
          runTest, // probably, runTest after setReleaseVersion, if tests depend on version
          setReleaseVersion,
          commitReleaseVersion, // performs the initial git checks
          tagRelease,
          publishArtifacts,
          setNextVersion,
          commitNextVersion,
          pushChanges // also checks that an upstream branch is properly configured
        )
      )
    }

    lazy val acyclicSettings = Seq(
      autoCompilerPlugins := true,
      addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.4"))


    implicit class driverConfigurations(project: Project) {

      def gitPluginConfiguration: Project = {
        val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

        project
          .enablePlugins(GitVersioning, GitBranchPrompt)
          .settings(
            git.useGitDescribe := true,
            git.baseVersion := "0.0.0",
            git.gitTagToVersionNumber := {
              case VersionRegex(v, "SNAPSHOT") => Some(s"$v-SNAPSHOT")
              case VersionRegex(v, "") => Some(v)
              case VersionRegex(v, s) => Some(s"$v-$s-SNAPSHOT")
              case _ => None
            })
      }

      def buildInfoConfiguration: Project = {
        project
          .enablePlugins(BuildInfoPlugin)
          .settings(
            buildInfoKeys := Seq[BuildInfoKey](
              name, version, scalaVersion, sbtVersion, buildInfoBuildNumber, git.gitHeadCommit),
            buildInfoPackage := "com.drivergrp",
            buildInfoOptions += BuildInfoOption.BuildTime)
      }

      def integrationTestingConfiguration: Project = {
        project
          .configs(IntegrationTest)
          .settings(Defaults.itSettings ++ Seq(
            testAll <<= (test in IntegrationTest).dependsOn(test in Test)
          ))
      }

      def packagingConfiguration: Project = {
        project
          .enablePlugins(JavaServerAppPackaging)
          .settings(// for sbt-native-packager
            makeDeploymentSettings(Universal, packageBin in Universal, "zip")
          )
          .settings(// for assembly plugin
            test in assembly := {},
            assemblyMergeStrategy in assembly := {
              case PathList("org", "slf4j", "impl", xs@_*) => MergeStrategy.rename
              case "logback.xml" => MergeStrategy.first
              case strategy: String =>
                val oldStrategy = (assemblyMergeStrategy in assembly).value
                oldStrategy(strategy)
            })
      }

      def dockerConfiguration: Project = {
        project
          .enablePlugins(DockerPlugin)
        // .settings(
        //   aggregate in Docker := false, // when building Docker image, don't build images for sub-projects
        //   maintainer in Linux := "XXX",
        //   dockerExposedPorts in Docker := Seq(9000, 9443),
        //   dockerRepository := Some("localhost:5000"),
        //   dockerCommands ++= Seq(
        //     ExecCmd("VOLUME", "/var/www/uploads")
        //   )
        // )

        // And then you can run "sbt docker:publishLocal"
      }
    }
  }

  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq (
    organization := "com.drivergrp",
    scalaVersion := "2.11.8",

    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-Xlint",
      "-encoding",
      "utf8",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused",
      "-Ywarn-unused-import"
    ),

    libraryDependencies ++= Seq(
      "org.scalaz"     %% "scalaz-core"    % "7.2.4",
      "com.lihaoyi"    %% "acyclic"        % "0.1.4" % "provided"
    ),

    fork in run := true
  )
}