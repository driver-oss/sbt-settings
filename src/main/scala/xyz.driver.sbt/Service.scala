package xyz.driver.sbt

import com.typesafe.sbt.GitPlugin.autoImport._
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.{Cmd, DockerPlugin}
import java.time.Instant
import sbt.Keys._
import sbt.{Def, _}
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._

/** Common settings to a service. */
object Service extends AutoPlugin {

  override def requires = BuildInfoPlugin && DockerPlugin && JavaAppPackaging

  object autoImport {
    val customCommands = taskKey[List[String]]("Additional commands that are run as part of docker packaging.")
  }
  import autoImport._

  lazy val buildInfoSettings = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit),
    buildInfoPackage := organization.value,
    buildInfoOptions += BuildInfoOption.BuildTime
  )

  lazy val dockerSettings = Seq(
    name in Docker := name.value,
    // Settings reference http://www.scala-sbt.org/sbt-native-packager/formats/docker.html
    maintainer in Docker := "Driver Inc. <info@driver.xyz>",
    aggregate in Docker := true, // include subprojects,
    dockerRepository := Some("gcr.io/driverinc-sandbox"),
    dockerUpdateLatest := true, // automatically update the latest tag
    dockerBaseImage := "openjdk:11",
    dockerLabels := Map(
      "build.timestamp"                           -> Instant.now().toString
    ) ++ git.gitHeadCommit.value.map("git.commit" -> _),
    customCommands := Nil,
    dockerCommands := dockerCommands.value.flatMap { // @see http://blog.codacy.com/2015/07/16/dockerizing-scala/
      case cmd @ Cmd("FROM", _) => cmd :: customCommands.value.map(customCommand => Cmd("RUN", customCommand))
      case other                => List(other)
    },
    bashScriptExtraDefines += {
      s"""|if [[ -f /etc/${name.value}/ssl/issuing_ca ]]; then
          |  keytool -import \
          |    -alias driverincInternal \
          |    -cacerts \
          |    -file /etc/${name.value}/ssl/issuing_ca \
          |    -storepass changeit -noprompt \
          |  || exit 1
          |else
          |  echo "No truststore customization." >&2
          |fi
          |""".stripMargin
    },
    javaOptions in Universal ++= Seq(
      // -J params will be added as jvm parameters

      // Leave some space for overhead, such as running a debug shell in a
      // container under heavy load. This may need to be tweaked if heavy use of
      // off-heap memory is made.
      "-J-XX:MaxRAMPercentage=90"
    )
  )

  override def projectSettings: Seq[Def.Setting[_]] =
    Library.repositorySettings ++ buildInfoSettings ++ dockerSettings ++ Seq(
      organization := "xyz.driver",
      crossScalaVersions := List("2.12.6"),
      scalaVersion := crossScalaVersions.value.last,
      publish := {
        streams.value.log
          .warn(s"Project ${name.value} is a service and will therefore not be published to an artifactory.")
      }
    )

  override def buildSettings: Seq[Def.Setting[_]] =
    addCommandAlias("start", "reStart") ++
      addCommandAlias("stop", "reStop")

}
