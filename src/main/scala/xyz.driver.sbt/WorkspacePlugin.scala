package xyz.driver.sbt

import java.nio.file.{Path, Paths}

import sbt._

/** Enables using both source and binary dependencies for the same module,
  * for faster development cycles in multi-project workflows.
  * Adapted from https://github.com/sbt/sbt-sriracha. */
object WorkspacePlugin extends AutoPlugin {

  private var _workspace = sys.props.get("sbt.workspace").orElse(sys.env.get("SBT_WORKSPACE")).map { base =>
    Paths.get(base)
  }
  def workspace: Option[Path] = synchronized(_workspace)

  override val requires = plugins.JvmPlugin
  override val trigger  = allRequirements

  object autoImport {
    implicit class WorkspaceProject(project: Project) {
      def dependsOn(binary: ModuleID, projectName: String, directory: Option[String] = None): Project =
        WorkspacePlugin.workspace match {
          case Some(base) =>
            project.dependsOn(
              ProjectRef(base.resolve(directory.getOrElse(projectName)).toUri, projectName)
            )
          case None => project.settings(Keys.libraryDependencies += binary)
        }
    }
  }
}
