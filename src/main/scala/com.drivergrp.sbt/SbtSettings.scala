package com.drivergrp.sbt

import sbt.Keys._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.SettingsHelper._
import com.typesafe.sbt.packager.archetypes._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
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

    lazy val formatSettings = Seq(
      resourceGenerators in Compile += Def.task {
        val contents =
          """# scalafmt sbt plugin config
            |# refer to https://olafurpg.github.io/scalafmt/#Configuration for properties
            |
            |--style defaultWithAlign # For pretty alignment.
            |--maxColumn 120          # For my wide 30" display.
            |
            |--reformatDocstrings true
            |--scalaDocs
            |
            |--continuationIndentCallSite 4
            |--continuationIndentDefnSite 4
            |
            |--rewriteTokens ⇒;=>,←;<-
            |--danglingParentheses false
            |--spaceAfterTripleEquals true
            |--alignByArrowEnumeratorGenerator true
            |--binPackParentConstructors true
            |--allowNewlineBeforeColonInMassiveReturnTypes true
            |--spacesInImportCurlyBraces false
            |
            |# --alignByOpenParenCallSite <value>
            |# --alignByOpenParenDefnSite <value>
            |
          """.stripMargin
        val formatFile = file(".scalafmt")
        IO.write(formatFile, contents)
        Seq(formatFile)
      }.taskValue,
      scalafmtConfig in ThisBuild := Some(file(".scalafmt")),
      testExecution in (Test, test) <<=
        (testExecution in (Test, test)) dependsOn (scalafmtTest in Compile, scalafmtTest in Test))

    private lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

    lazy val scalastyleSettings = Seq(
      resourceGenerators in Compile += Def.task {
        val styleFile = file("scalastyle-config.xml")
        val contents =
          """<scalastyle>
            |    <name>Scalastyle standard configuration</name>
            |    <check level="error" class="org.scalastyle.file.FileTabChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.file.FileLengthChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxFileLength"><![CDATA[800]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.file.HeaderMatchesChecker" enabled="false">
            |        <parameters>
            |            <parameter name="header">package</parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.SpacesBeforePlusChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.SpacesAfterPlusChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.file.WhitespaceEndOfLineChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.file.FileLineLengthChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxLineLength"><![CDATA[160]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.ClassNamesChecker" enabled="true">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[[A-Z][A-Za-z]*]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.ObjectNamesChecker" enabled="true">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[[A-Za-z]*]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.PackageObjectNamesChecker" enabled="true">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[^[a-z][A-Za-z]*$]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.EqualsHashCodeChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.IllegalImportsChecker" enabled="true">
            |        <parameters>
            |            <parameter name="illegalImports"><![CDATA[sun._,java.awt._]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.ParameterNumberChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxParameters"><![CDATA[8]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.MagicNumberChecker" enabled="false">
            |        <parameters>
            |            <parameter name="ignore"><![CDATA[-1,0,1,2,3]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.NoWhitespaceBeforeLeftBracketChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.NoWhitespaceAfterLeftBracketChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.ReturnChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.NullChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.NoCloneChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.NoFinalizeChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.CovariantEqualsChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.file.RegexChecker" enabled="true">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[println]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.NumberOfTypesChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxTypes"><![CDATA[30]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.CyclomaticComplexityChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maximum"><![CDATA[50]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.UppercaseLChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.SimplifyBooleanExpressionChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.IfBraceChecker" enabled="true">
            |        <parameters>
            |            <parameter name="singleLineAllowed"><![CDATA[true]]></parameter>
            |            <parameter name="doubleLineAllowed"><![CDATA[true]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.MethodLengthChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxLength"><![CDATA[100]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.MethodNamesChecker" enabled="true">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[^[A-Za-z\\*][A-Za-z0-9]*$]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.ClassTypeParameterChecker" enabled="false">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[^[A-Za-z]*$]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.NumberOfMethodsInTypeChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxMethods"><![CDATA[30]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.PublicMethodsHaveTypeChecker" enabled="false"/>
            |    <check level="error" class="org.scalastyle.file.NewLineAtEofChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.file.NoNewLineAtEofChecker" enabled="false"/>
            |    <check level="error" class="org.scalastyle.scalariform.DeprecatedJavaChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.EmptyClassChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.RedundantIfChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.MultipleStringLiteralsChecker" enabled="false"/>
            |    <check level="error" class="org.scalastyle.scalariform.SpaceAfterCommentStartChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.ProcedureDeclarationChecker" enabled="true"/>
            |    <check level="error" class="org.scalastyle.scalariform.NotImplementedErrorUsage" enabled="true"/>
            |</scalastyle>
          """.stripMargin
        IO.write(styleFile, contents)
        Seq(styleFile)
      }.taskValue,
      scalastyleConfig := file("scalastyle-config.xml"),
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
          .enablePlugins(JavaAppPackaging)
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
          .settings(
            maintainer := "Direct Inc. <info@driver.xyz>",
            dockerBaseImage := "java:openjdk-8-jre-alpine",
            dockerCommands := dockerCommands.value.flatMap { // @see http://blog.codacy.com/2015/07/16/dockerizing-scala/
              case cmd@Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
              case other => List(other)
            }
          )

        // And then you can run "sbt docker:publishLocal"
      }
    }
  }

  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq (
    organization := "xyz.driver",
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