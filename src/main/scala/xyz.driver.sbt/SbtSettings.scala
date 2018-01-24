package xyz.driver.sbt

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.{GitBranchPrompt, GitVersioning}
import org.scalastyle.sbt.ScalastylePlugin.autoImport._
import sbt.Keys._
import sbt.{Project, State, _}
import sbtassembly.AssemblyKeys._
import sbtassembly._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, BuildInfoOption, _}
import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.{Version, _}
import IntegrationTestPackaging.autoImport.IntegrationTest

// we hide the existing definition for setReleaseVersion to replace it with our own
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => recordReleaseVersion, inquireVersions => _}

/**
  * @see https://engineering.sharethrough.com/blog/2015/09/23/capturing-common-config-with-an-sbt-parent-plugin/
  */
object SbtSettings extends AutoPlugin {

  val JMX_PORT = 8686

  object autoImport {
    lazy val formatSettings = {
      val generateScalafmtConfTask = Def.task {
        val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
        val formatConfFile     = file(".scalafmt.conf")
        IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
        formatConfFile
      }

      Seq(
        scalafmtConfig := generateScalafmtConfTask.value,
        scalafmt in Compile := {
          (scalafmt in Compile).dependsOn(scalafmt in Test).value
        },
        // scalafmt::test -> tests scalafmt format in src/main + src/test (added behavior)
        test in scalafmt in Compile := {
          (test in scalafmt in Compile).dependsOn(test in scalafmt in Test).value
        },
        test in Test := {
          (test in scalafmt in Compile).value
          (test in Test).value
        }
      )
    }

    lazy val testScalastyle = taskKey[Unit]("testScalastyle")

    lazy val scalastyleSettings = {
      val generateScalastyleConfTask = Def.task {
        val stream    = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
        val styleFile = file("scalastyle-config.xml")
        IO.write(styleFile, IO.readBytes(stream))
        Seq(styleFile)
      }
      Seq(
        resourceGenerators in Compile += generateScalastyleConfTask.taskValue,
        scalastyleConfig := file("scalastyle-config.xml"),
        testScalastyle := scalastyle.in(Compile).toTask("").value,
        testScalastyle in (Test, test) := {
          generateScalastyleConfTask.value
          (testScalastyle in (Test, test)).value
        },
        testExecution in (Test, test) := {
          generateScalastyleConfTask.value
          (testScalastyle in Compile).value
          (testScalastyle in Test).value
          (testExecution in (Test, test)).value
        }
      )
    }

    val scalacLintingSettings = Seq(
      scalacOptions ++= {
        scalaBinaryVersion.value match {
          case "2.12" =>
            Seq(
              "-Xfatal-warnings",
              "-Xlint:_,-unused,-missing-interpolator",
              "-Ywarn-numeric-widen",
              "-Ywarn-dead-code",
              "-Ywarn-unused:_,-explicits,-implicits"
            )
          case _ =>
            Seq(
              "-Xfatal-warnings",
              "-Xlint:-missing-interpolator",
              "-Ywarn-numeric-widen",
              "-Ywarn-dead-code",
              "-Ywarn-unused",
              "-Ywarn-unused-import"
            )
        }
      }
    )

    lazy val lintingSettings = scalacLintingSettings ++ scalastyleSettings

    lazy val repositoriesSettings: Seq[Setting[_]] = {
      Seq(
        resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases",
        resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots"
      )
    }

    lazy val publicationSettings: Seq[Setting[_]] = Seq(
      publishTo := {
        val jfrog = "https://drivergrp.jfrog.io/drivergrp/"

        if (isSnapshot.value) Some("snapshots" at jfrog + "snapshots;build.timestamp=" + new java.util.Date().getTime)
        else Some("releases" at jfrog + "releases")
      }
    )

    private def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
      val vs = st
        .get(ReleaseKeys.versions)
        .getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
      val selected = selectVersion(vs)

      st.log.info("Setting version to '%s'." format selected)
      val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)

      reapply(Seq(
                if (useGlobal) version in ThisBuild := selected else version := selected
              ),
              st)
    }

    lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

    // Remove the prompt for next version
    lazy val inquireVersions: ReleaseStep = { st: State =>
      val extracted = Project.extract(st)

      val useDefs  = st.get(useDefaults).getOrElse(false)
      val currentV = extracted.get(version)

      val (_, releaseFunc)  = extracted.runTask(releaseVersion, st)
      val suggestedReleaseV = releaseFunc(currentV)

      // flatten the Option[Option[String]] as the get returns an Option, and the value inside is an Option
      val releaseV =
        readVersion(suggestedReleaseV, "Release version [%s] : ", useDefs, st.get(commandLineReleaseVersion).flatten)
      val nextV = releaseV

      st.put(versions, (releaseV, nextV))

    }

    def ServiceReleaseProcess = {
      Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runTest,
        recordReleaseVersion, // set release version and persistent in version.sbt
        commitReleaseVersion, // performs the initial git checks
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

      val showReleaseVersion = taskKey[String]("the future version once releaseNextVersion has been applied to it")
      Seq(
        releaseCrossBuild := true,
        releaseIgnoreUntrackedFiles := true,
        // Check http://blog.byjean.eu/2015/07/10/painless-release-with-sbt.html for details
        releaseVersionBump := sbtrelease.Version.Bump.Bugfix,
        releaseVersion := {
          case ver @ snapshotVersion if snapshotVersion.endsWith("-SNAPSHOT") =>
            Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError)
          case ver =>
            Version(ver).map(_.bumpBugfix.withoutQualifier.string).getOrElse(versionFormatError)
        },
        showReleaseVersion := {
          releaseVersion.value(version.value)
        },
        releaseProcess := releaseProcessSteps
      )
    }

    lazy val acyclicSettings =
      Seq(autoCompilerPlugins := true, addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.7"))
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
                  .map(_.bump(sbtrelease.Version.Bump.Bugfix).string)
                  .getOrElse(versionFormatError)

                Some(s"$ver-SNAPSHOT")

              case VersionRegex(v, "") =>
                Some(v)

              case VersionRegex(v, s) => // Commit is ahead of the last tagged commit
                val ver = Version(v)
                  .map(_.withoutQualifier)
                  .map(_.bump(sbtrelease.Version.Bump.Bugfix).string)
                  .getOrElse(versionFormatError)

                Some(s"$ver-$s-SNAPSHOT")

              case _ => None
            }
          )
      }

      def buildInfoConfiguration(packageName: String = "xyz.driver"): Project = {
        project
          .enablePlugins(BuildInfoPlugin)
          .settings(
            buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit),
            buildInfoPackage := packageName,
            buildInfoOptions += BuildInfoOption.BuildTime
          )
      }

      def packagingConfiguration: Project = {
        project
          .settings( // for assembly plugin
            test in assembly := {},
            assemblyMergeStrategy in assembly := {
              case PathList("org", "slf4j", "impl", xs @ _*) => MergeStrategy.first
              case "logback.xml"                             => MergeStrategy.first
              case strategy: String =>
                val oldStrategy = (assemblyMergeStrategy in assembly).value
                oldStrategy(strategy)
            }
          )
      }

      def dockerConfiguration(imageName: String,
                              repository: String,
                              exposedPorts: Seq[Int],
                              baseImage: String = "java:8",
                              customCommands: List[String] = List.empty[String],
                              aggregateSubprojects: Boolean = false): Project = {
        import com.typesafe.sbt.packager.Keys._

        project
          .enablePlugins(JavaAppPackaging)
          .settings(
            // Settings reference http://www.scala-sbt.org/sbt-native-packager/formats/docker.html
            packageName in Docker := imageName,
            version in Docker := version.value.stripSuffix("-SNAPSHOT"),
            dockerRepository := Some(repository),
            maintainer := "Driver Inc. <info@driver.xyz>",
            dockerUpdateLatest := true, // to automatic update the latest tag
            dockerExposedPorts := exposedPorts,
            dockerBaseImage := baseImage,
            daemonUser in Docker := "root",
            dockerCommands := dockerCommands.value
              .flatMap { // @see http://blog.codacy.com/2015/07/16/dockerizing-scala/
                case cmd @ Cmd("FROM", _) => cmd :: customCommands.map(customCommand => Cmd("RUN", customCommand))
                case other                => List(other)
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
                                  baseImage: String = "java:8",
                                  dockerCustomCommands: List[String] = List.empty[String],
                                  aggregateSubprojects: Boolean = false) = {

        val repositoryName = "gcr.io/" + gCloudProject

        val keytoolCommand =
          "keytool -import -alias driverincInternal -keystore $JAVA_HOME/jre/lib/security/cacerts " +
            s"-file /etc/$imageName/ssl/issuing_ca -storepass changeit -noprompt"

        // If issuing_ca exists, import it into the internal default ca store
        val importTrustStoreCommand =
          s"if [ -f /etc/$imageName/ssl/issuing_ca ] ; then " + keytoolCommand + "; else echo \"No truststore customization.\"; fi"

        val dockerCommands = dockerCustomCommands // :+ importTrustStoreCommand

        val allExposedPorts = exposedPorts ++ Seq(JMX_PORT)

        dockerConfiguration(imageName, repositoryName, allExposedPorts, baseImage, dockerCommands, aggregateSubprojects)
          .settings(NativePackagerKeys.bashScriptExtraDefines += importTrustStoreCommand)
          .settings(NativePackagerKeys.bashScriptExtraDefines += s"""addJava "-Dcom.sun.management.jmxremote"""")
          .settings(
            NativePackagerKeys.bashScriptExtraDefines += s"""addJava "-Dcom.sun.management.jmxremote.port=$JMX_PORT"""")
          .settings(
            NativePackagerKeys.bashScriptExtraDefines += s"""addJava "-Dcom.sun.management.jmxremote.local.only=false"""")
          .settings(
            NativePackagerKeys.bashScriptExtraDefines += s"""addJava "-Dcom.sun.management.jmxremote.authenticate=false"""")
          .settings(
            NativePackagerKeys.bashScriptExtraDefines += s"""addJava "-Dcom.sun.management.jmxremote.ssl=false"""")
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
                        baseImage: String = "java:8",
                        dockerCustomCommands: List[String] = List.empty[String],
                        aggregateSubprojects: Boolean = false): Project = {
        project
          .settings(name := appName)
          .settings(repositoriesSettings ++ releaseSettings(ServiceReleaseProcess))
          .buildInfoConfiguration()
          .deploymentConfiguration(appName,
                                   exposedPorts,
                                   clusterName,
                                   clusterZone,
                                   gCloudProject,
                                   baseImage,
                                   dockerCustomCommands,
                                   aggregateSubprojects)
      }
    }
  }

  val scalacDefaultOptions = Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-encoding", "utf8")

  val scalacLanguageFeatures = Seq(
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:reflectiveCalls"
  )

  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Setting[_]] = Seq(
    organization := "xyz.driver",
    crossScalaVersions := List("2.12.4"),
    scalaVersion := crossScalaVersions.value.last,
    scalacOptions := (scalacDefaultOptions ++ scalacLanguageFeatures),
    scalacOptions in (Compile, console) := (scalacDefaultOptions ++ scalacLanguageFeatures),
    scalacOptions in (Compile, consoleQuick) := (scalacDefaultOptions ++ scalacLanguageFeatures),
    scalacOptions in (Compile, consoleProject) := (scalacDefaultOptions ++ scalacLanguageFeatures),
    libraryDependencies ++= Seq(
      "org.scalaz"  %% "scalaz-core" % "7.2.8",
      "com.lihaoyi" %% "acyclic"     % "0.1.7" % "provided"
    ),
    version := {
      // Sbt release versioning based on git given double -SNAPSHOT suffix
      // if current commit is not tagged AND there are uncommitted changes (e.g., some file is modified),
      // this setting fixes that, by just removing double -SNAPSHOT if it happened somehow
      Option(version.value).map(vv => vv.replaceAll("-SNAPSHOT-SNAPSHOT", "-SNAPSHOT")).getOrElse("0.0.0")
    },
    fork := true
  )
}
