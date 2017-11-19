package xyz.driver.sbt

import java.nio.file._

import scala.collection.JavaConverters._

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import com.typesafe.sbt.packager._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.universal._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt._
import sbt.plugins._
import sbt.Keys._

object IntegrationTestPackaging extends AutoPlugin {

  override def requires = UniversalPlugin && DockerPlugin
  override def trigger  = AllRequirements

  object autoImport {
    lazy val IntegrationTest = config("it") extend (Test) // make test classes available
  }
  import autoImport._

  private def list(base: Path): Seq[(Path, String)] = base match {
    case _ if Files.isRegularFile(base) => Seq(base -> base.getFileName.toString)
    case _ if Files.isDirectory(base) =>
      Files.walk(base).iterator().asScala.toSeq.map { file =>
        file -> base.relativize(file).toString
      }
  }

  private def configurationSettings =
    inConfig(IntegrationTest)(Defaults.configSettings) ++
      inConfig(IntegrationTest)(Defaults.testSettings) ++ Seq(
      ivyConfigurations := overrideConfigs(IntegrationTest)(ivyConfigurations.value)
    )

  private def formatSettings =
    inConfig(IntegrationTest)(scalafmtSettings) ++
      Seq(
        scalafmt in Test := {
          (scalafmt in Test).dependsOn(scalafmt in IntegrationTest).value
        },
        // test:scalafmt::test -> tests scalafmt format in src/test + src/it
        test in scalafmt in Test := {
          (test in scalafmt in Test).dependsOn(test in scalafmt in IntegrationTest).value
        }
      )

  override def projectSettings =
    configurationSettings ++
      formatSettings ++
      Seq(
        mappings in Universal ++= {
          val cp: Seq[(File, String)] = (dependencyClasspath in IntegrationTest).value
            .map(_.data.toPath)
            .flatMap(list)
            .map {
              case (file, location) =>
                file.toFile -> ("lib-it/" + location)
            }

          val tests: Seq[(File, String)] = list((packageBin in IntegrationTest).value.toPath)
            .map {
              case (file, location) =>
                file.toFile -> ("lib-it/" + location)
            }

          val script = {
            def libs(mappings: Seq[(File, String)]): String =
              mappings
                .map {
                  case (_, location) =>
                    s"$$bin_dir/../$location"
                }
                .mkString(":")
            s"""|#!/bin/bash
                |bin_dir="$$(dirname "$$(readlink -f "$$0")")"
                |exec java \\
                |        -cp "${libs(tests ++ cp)}" \\
                |        org.scalatest.tools.Runner \\
                |        -o \\
                |        -R "${libs(tests)}"
                |""".stripMargin
          }

          val scriptFile = (resourceManaged in IntegrationTest).value / "runit"
          IO.write(scriptFile, script)
          scriptFile.setExecutable(true)

          cp ++ tests ++ Seq(scriptFile -> s"bin/${normalizedName.value}-it")
        },
        dockerCommands in Docker := {
          (dockerCommands in Docker).value ++ Seq(
            ExecCmd("RUN", "mkdir", "-p", "/test"),
            ExecCmd("RUN",
                    "ln",
                    "-s",
                    s"${(defaultLinuxInstallLocation in Docker).value}/bin/${normalizedName.value}-it",
                    "/test/run_integration_test.sh")
          )
        }
      )

}
