package xyz.driver.sbt

import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.{GitBranchPrompt, GitVersioning}
import org.scalastyle.sbt.ScalastylePlugin._
import sbt.Keys._
import sbt.{Credentials, Project, State, _}
import sbtassembly.AssemblyKeys._
import sbtassembly._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, BuildInfoOption, _}
import sbtdocker.DockerPlugin
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.{Version, _}
import wartremover.WartRemover.autoImport._

// we hide the existing definition for setReleaseVersion to replace it with our own
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _}


/**
  * @see https://engineering.sharethrough.com/blog/2015/09/23/capturing-common-config-with-an-sbt-parent-plugin/
  */
object SbtSettings extends AutoPlugin {

  object autoImport {

    lazy val scalafmtTest = taskKey[Unit]("scalafmtTest")

    lazy val formatSettings = {

      Seq(
        resourceGenerators in Compile += Def.task {
          val scalafmtStream = getClass.getClassLoader.getResourceAsStream("scalafmt")
          val scalafmtFile = file("scalafmt")
          IO.write(scalafmtFile, IO.readBytes(scalafmtStream))
          Seq(scalafmtFile)
        }.taskValue,
        resourceGenerators in Compile += Def.task {
          val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
          val formatConfFile = file(".scalafmt.conf")
          IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
          Seq(formatConfFile)
        }.taskValue,
        scalafmtTest := {
          "scalafmt --test".!
        },
        testExecution in (Test, test) <<=
          (testExecution in (Test, test)) dependsOn (scalafmtTest in Compile, scalafmtTest in Test)
      )
    }

    lazy val testScalastyle = taskKey[Unit]("testScalastyle")

    lazy val scalastyleSettings = Seq(
      resourceGenerators in Compile += Def.task {
        val stream = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
        val styleFile = file("scalastyle-config.xml")
        IO.write(styleFile, IO.readBytes(stream))
        Seq(styleFile)
      }.taskValue,
      scalastyleConfig := file("scalastyle-config.xml"),
      testScalastyle := scalastyle.in(Compile).toTask("").value,
      testExecution in (Test, test) <<=
        testExecution in (Test, test) dependsOn (testScalastyle in Compile, testScalastyle in Test))

    lazy val wartRemoverSettings = Seq(
      wartremoverErrors in (Compile, compile) ++= Warts.allBut(
        Wart.AsInstanceOf, Wart.Nothing, Wart.Overloading, Wart.DefaultArguments, Wart.Any, Wart.NonUnitStatements,
        Wart.Option2Iterable, Wart.ExplicitImplicitTypes, Wart.Throw, Wart.ToString, Wart.PublicInference,
        Wart.ImplicitParameter, Wart.Equals))

    lazy val lintingSettings = scalastyleSettings ++ wartRemoverSettings

    lazy val repositoriesSettings: Seq[Setting[_]] = {
      Seq(
        resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases",
        resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots",
        credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***"))
    }

    lazy val publicationSettings: Seq[Setting[_]] = Seq(
      publishTo := {
        val jfrog = "https://drivergrp.jfrog.io/drivergrp/"

        if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots;build.timestamp=" + new java.util.Date().getTime)
        else                  Some("releases"  at jfrog + "releases")
      },
      credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***"))

    def ServiceReleaseProcess = {
      Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        setReleaseVersion,
        runTest,
        tagRelease,
        pushChanges // also checks that an upstream branch is properly configured
      )
    }

    def LibraryReleaseProcess = {
      Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        setReleaseVersion,
        runTest,
        tagRelease,
        publishArtifacts,
        pushChanges // also checks that an upstream branch is properly configured
      )
    }

    def releaseSettings(releaseProcessSteps: Seq[ReleaseStep]): Seq[Setting[_]] = {

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

      val showNextVersion = settingKey[String]("the future version once releaseNextVersion has been applied to it")
      val showReleaseVersion = settingKey[String]("the future version once releaseNextVersion has been applied to it")
      Seq(
        releaseIgnoreUntrackedFiles := true,
        // Check http://blog.byjean.eu/2015/07/10/painless-release-with-sbt.html for details
        releaseVersionBump := sbtrelease.Version.Bump.Bugfix,
        releaseNextVersion <<= releaseVersionBump(bumper => { ver =>
          Version(ver)
            .map(_.withoutQualifier)
            .map(_.bump(bumper).string + "-SNAPSHOT").getOrElse(versionFormatError)
        }),
        showReleaseVersion <<= (version, releaseVersion)((v,f) => f(v)),
        showNextVersion <<= (version, releaseNextVersion)((v,f) => f(v)),
        releaseProcess := releaseProcessSteps
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
              case VersionRegex(v, "SNAPSHOT") => // There are not committed changes at tagged commit
                val ver = Version(v)
                  .map(_.withoutQualifier)
                  .map(_.bump(sbtrelease.Version.Bump.Bugfix).string).getOrElse(versionFormatError)

                Some(s"$ver-SNAPSHOT")

              case VersionRegex(v, "") =>

                Some(v)

              case VersionRegex(v, s) => // Commit is ahead of the last tagged commit
                val ver = Version(v)
                  .map(_.withoutQualifier)
                  .map(_.bump(sbtrelease.Version.Bump.Bugfix).string).getOrElse(versionFormatError)

                Some(s"$ver-$s-SNAPSHOT")

              case _ => None
            }
          )
      }

      def buildInfoConfiguration(packageName: String = "xyz.driver"): Project = {
        project
          .enablePlugins(BuildInfoPlugin)
          .settings(
            buildInfoKeys := Seq[BuildInfoKey](
              name, version, scalaVersion, sbtVersion, git.gitHeadCommit),
            buildInfoPackage := packageName,
            buildInfoOptions += BuildInfoOption.BuildTime)
      }

      def integrationTestingConfiguration: Project = {
        project.configs(IntegrationTest).settings(Defaults.itSettings ++ Seq(
          parallelExecution in IntegrationTest := false
        ))
      }

      def packagingConfiguration: Project = {
        project
          .settings(// for assembly plugin
            test in assembly := {},
            assemblyMergeStrategy in assembly := {
              case PathList("org", "slf4j", "impl", xs@_*) => MergeStrategy.first
              case "logback.xml" => MergeStrategy.first
              case strategy: String =>
                val oldStrategy = (assemblyMergeStrategy in assembly).value
                oldStrategy(strategy)
            })
      }

      def dockerConfiguration(imageName: String,
                              repository: String,
                              exposedPorts: Seq[Int],
                              baseImage: String = "openjdk:8-jre-alpine",
                              customCommands: List[String] = List.empty[String],
                              aggregateSubprojects: Boolean = false): Project = {

        project
          .enablePlugins(DockerPlugin, JavaAppPackaging)
          .settings(
            // Settings reference http://www.scala-sbt.org/sbt-native-packager/formats/docker.html
            packageName in Docker := imageName,
            version in Docker := version.value.stripSuffix("-SNAPSHOT"),
            dockerRepository := Some(repository),
            maintainer := "Driver Inc. <info@driver.xyz>",
            dockerUpdateLatest := true, // to automatic update the latest tag
            dockerExposedPorts := exposedPorts,
            dockerBaseImage := baseImage,
            dockerCommands := dockerCommands.value.flatMap { // @see http://blog.codacy.com/2015/07/16/dockerizing-scala/
              case cmd@Cmd("FROM", _) => cmd :: customCommands.map(customCommand => Cmd("RUN", customCommand))
              case other => List(other)
            },
            aggregate in Docker := aggregateSubprojects // to include subprojects
          )

        // And then you can run "sbt docker:publish"
      }

      def deploymentConfiguration(imageName: String,
                                  exposedPorts: Seq[Int] = Seq(8080),
                                  clusterName: String = "sand-uw1a-1",
                                  clusterZone: String = "us-west1-a",
                                  gCloudProject: String = "driverinc-sandbox",
                                  baseImage: String = "java:openjdk-8-jre-alpine",
                                  dockerCustomCommands: List[String] = List.empty[String],
                                  aggregateSubprojects: Boolean = false) = {

        val repositoryName = "gcr.io/" + gCloudProject

        val keytoolCommand =
          s"keytool -import -noprompt -trustcacerts -alias driver-internal -file /etc/$imageName/ssl/issuing_ca -storepass 123456"

        val trustStoreConfiguration =
          "if [ -n \"$TRUSTSTORE\" ] ; then " + keytoolCommand + "; else echo \"No truststore customization.\"; fi"

        val dockerCommands =
          dockerCustomCommands :+ trustStoreConfiguration

        dockerConfiguration(imageName, repositoryName, exposedPorts, baseImage, dockerCommands, aggregateSubprojects)
          .settings(
          Seq(resourceGenerators in Test += Def.task {
            val variablesFile = file("deploy/variables.sh")
            val contents =
              s"""|#!/bin/sh
                  |
                  |export SCRIPT_DIR="$$( cd "$$( dirname "$$0" )" && pwd )"
                  |export GCLOUD_PROJECT=$gCloudProject
                  |export REGISTRY_PREFIX=$repositoryName
                  |export KUBE_CLUSTER_NAME=$clusterName
                  |export KUBE_CLUSTER_ZONE=$clusterZone
                  |
                  |export APP_NAME='$imageName'
                  |export VERSION='${version.value.stripSuffix("-SNAPSHOT")}'
                  |export IMAGE_ID="$${REGISTRY_PREFIX}/$${APP_NAME}:$${VERSION}"
                  |""".stripMargin
            IO.write(variablesFile, contents)
            Seq(variablesFile)
          }.taskValue)
        )
      }

      def driverLibrary(libraryName: String): Project = {
        project
          .settings(name := libraryName)
          .gitPluginConfiguration
          .settings(repositoriesSettings ++ publicationSettings ++ releaseSettings(LibraryReleaseProcess))
      }

      def driverService(appName: String,
                        exposedPorts: Seq[Int] = Seq(8080),
                        clusterName: String = "sand-uw1a-1",
                        clusterZone: String = "us-west1-a",
                        gCloudProject: String = "driverinc-sandbox",
                        baseImage: String = "java:openjdk-8-jre-alpine",
                        dockerCustomCommands: List[String] = List.empty[String],
                        aggregateSubprojects: Boolean = false): Project = {
        project
          .settings(name := appName)
          .settings(repositoriesSettings)
          .buildInfoConfiguration()
          .deploymentConfiguration(
            appName, exposedPorts,
            clusterName, clusterZone, gCloudProject,
            baseImage, dockerCustomCommands, aggregateSubprojects)
      }
    }
  }

  val scalacDefaultOptions = Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-encoding", "utf8")

  val scalacLintingOptions = Seq(
    "-Xfatal-warnings",
    "-Xlint:-missing-interpolator",
    "-Ywarn-numeric-widen",
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ywarn-unused-import"
  )

  val scalacLanguageFeatures = Seq(
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:reflectiveCalls"
  )

  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq (
    organization := "xyz.driver",
    scalaVersion := "2.11.8",
    scalacOptions := (scalacDefaultOptions ++ scalacLanguageFeatures ++ scalacLintingOptions),
    scalacOptions in (Compile, console) := (scalacDefaultOptions ++ scalacLanguageFeatures),
    scalacOptions in (Compile, consoleQuick) := (scalacDefaultOptions ++ scalacLanguageFeatures),
    scalacOptions in (Compile, consoleProject) := (scalacDefaultOptions ++ scalacLanguageFeatures),
    libraryDependencies ++= Seq(
      "org.scalaz"     %% "scalaz-core"    % "7.2.8",
      "com.lihaoyi"    %% "acyclic"        % "0.1.4" % "provided"
    ),
    version <<= version(v => {
      // Sbt release versioning based on git given double -SNAPSHOT suffix
      // if current commit is not tagged AND there are uncommitted changes (e.g., some file is modified),
      // this setting fixes that, by just removing double -SNAPSHOT if it happened somehow
      Option(v).map(vv => vv.replaceAll("-SNAPSHOT-SNAPSHOT", "-SNAPSHOT")).getOrElse("0.0.0")
    }),
    fork := true
  )
}
