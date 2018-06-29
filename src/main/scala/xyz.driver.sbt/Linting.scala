package xyz.driver.sbt

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport.{scalafmtConfig, _}
import com.lucidchart.sbt.scalafmt.ScalafmtPlugin
import org.scalastyle.sbt.ScalastylePlugin
import org.scalastyle.sbt.ScalastylePlugin.autoImport.{scalastyle, scalastyleConfig}
import sbt.Keys._
import sbt._

import scala.collection.JavaConverters._

/** Enforces common formatting and catches compiler warnings. */
object Linting extends AutoPlugin {

  override def requires = ScalafmtPlugin && ScalastylePlugin
  override def trigger  = allRequirements

  lazy val formatSettings: Seq[Def.Setting[_]] = Seq(
    scalafmtConfig := {
      val packaged = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
      val out      = file(".scalafmt.conf")
      IO.write(out, IO.readBytes(packaged))
      out
    },
    scalafmtTestOnCompile in Test := true
  )

  lazy val scalastyleSettings: Seq[Def.Setting[_]] = Seq(
    scalastyleConfig := {
      val stream = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
      val out    = file(".scalastyle-config.xml")
      IO.write(out, IO.readBytes(stream))
      out
    },
    test in Test := (test in Test).dependsOn((scalastyle in Test).toTask("")).value
  )

  lazy val scalacSettings: Seq[Def.Setting[_]] = Seq(
    scalacOptions in Compile ++= Seq(
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls", // TODO this should be discouraged
      "-unchecked",
      "-deprecation",
      "-feature",
      "-encoding",
      "utf8",
      "-Xlint:_,-unused,-missing-interpolator",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused:_,-explicits,-implicits"
    ),
    // Currently, scalac does not provide a way to fine-tune the treating of
    // warnings as errors. Either all are considered errors
    // (with -Xfatal-warnings), or none are. This hack analyzes the compiler's
    // output and treats all warnings as errors, except for deprecations.
    compile in Compile := {
      val log      = streams.value.log
      val compiled = (compile in Compile).value
      val problems = compiled.readSourceInfos().getAllSourceInfos.asScala.flatMap {
        case (_, info) => info.getReportedProblems
      }
      var deprecationsOnly = true
      problems.foreach { problem =>
        if (!problem.message().contains("is deprecated")) {
          deprecationsOnly = false
          log.error(s"[fatal warning] ${problem.message()}")
        }
      }
      if (!deprecationsOnly)
        throw new FatalWarningsException("Fatal warnings: some warnings other than deprecations were found.")
      compiled
    }
  )

  lazy val lintSettings = formatSettings ++ scalastyleSettings ++ scalacSettings

  override def projectSettings: Seq[Def.Setting[_]] = inConfig(Compile)(lintSettings) ++ inConfig(Test)(lintSettings)

}
case class FatalWarningsException(message: String) extends Exception(message)
