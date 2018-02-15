package xyz.driver.sbt

import sbt.{Def, _}
import sbt.Keys._
import xsbti.compile.CompileAnalysis

import scala.collection.JavaConverters._

object FatalWarnings extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    compile in Compile := {
      val compiled: CompileAnalysis = (compile in Compile).value
      val problems = compiled.readSourceInfos().getAllSourceInfos.asScala.flatMap {
        case (_, info) =>
          info.getReportedProblems
      }

      val deprecationsOnly = problems.forall { problem =>
        problem.message().contains("is deprecated")
      }

      if (!deprecationsOnly) sys.error("Fatal warnings: some warnings other than deprecations were found.")
      compiled
    }
  )

}
