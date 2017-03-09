package xyz.driver.sbt

import sbt.Keys._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbtdocker.DockerPlugin
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.{GitBranchPrompt, GitVersioning}
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin._
import sbt.{Credentials, Project, State, _}
import sbtassembly.AssemblyKeys._
import sbtassembly._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport.{BuildInfoKey, BuildInfoOption, _}
import sbtrelease.{Version, _}
import wartremover.WartRemover.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
// we hide the existing definition for setReleaseVersion to replace it with our own
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _}


/**
  * @see https://engineering.sharethrough.com/blog/2015/09/23/capturing-common-config-with-an-sbt-parent-plugin/
  */
object SbtSettings extends AutoPlugin {

  object autoImport {

    lazy val formatSettings = Seq(
      resourceGenerators in Test += Def.task {
        val contents =
          """|# scalafmt sbt plugin config
             |# refer to https://olafurpg.github.io/scalafmt/#Configuration for properties
             |
             |style = defaultWithAlign
             |maxColumn = 120
             |
             |docstrings = ScalaDoc
             |
             |continuationIndent.callSite = 2
             |continuationIndent.defnSite = 8
             |
             |rewriteTokens: {
             |  "⇒" = "=>"
             |  "←" = "<-"
             |}
             |danglingParentheses = false
             |align.arrowEnumeratorGenerator = true
             |align.openParenCallSite = true
             |spaces.afterTripleEquals = true
             |spaces.inImportCurlyBraces = false
             |newlines.alwaysBeforeCurlyBraceLambdaParams = false
             |newlines.sometimesBeforeColonInMethodReturnType = false
             |binPack.parentConstructors = true
             |assumeStandardLibraryStripMargin = true
             |
             |# align.openParenCallSite = <value>
             |# align.openParenDefnSite = <value>
             |""".stripMargin
        val formatFile = file(".scalafmt.conf")
        IO.write(formatFile, contents)
        Seq(formatFile)
      }.taskValue,
      scalafmtConfig in ThisBuild := Some(file(".scalafmt.conf")),
      testExecution in (Test, test) <<=
        (testExecution in (Test, test)) dependsOn (scalafmtTest in Compile, scalafmtTest in Test))

    lazy val testScalastyle = taskKey[Unit]("testScalastyle")

    lazy val scalastyleSettings = Seq(
      resourceGenerators in Test += Def.task {
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
            |            <parameter name="maxTypes"><![CDATA[100]]></parameter>
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
            |            <parameter name="ignoreRegex">`.*`</parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.ClassTypeParameterChecker" enabled="false">
            |        <parameters>
            |            <parameter name="regex"><![CDATA[^[A-Za-z]*$]]></parameter>
            |        </parameters>
            |    </check>
            |    <check level="error" class="org.scalastyle.scalariform.NumberOfMethodsInTypeChecker" enabled="true">
            |        <parameters>
            |            <parameter name="maxMethods"><![CDATA[50]]></parameter>
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
      testScalastyle := scalastyle.in(Compile).toTask("").value,
      (test in Test) <<= (test in Test) dependsOn testScalastyle)

    lazy val wartRemoverSettings = Seq(
      wartremoverErrors in (Compile, compile) ++= Warts.allBut(
        Wart.AsInstanceOf, Wart.Nothing, Wart.Overloading, Wart.DefaultArguments, Wart.Any, Wart.NonUnitStatements,
        Wart.Option2Iterable, Wart.ExplicitImplicitTypes, Wart.Throw, Wart.ToString, Wart.NoNeedForMonad))

    lazy val lintingSettings = scalastyleSettings ++ wartRemoverSettings

    lazy val repositoriesSettings = {
      Seq(
        resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases",
        resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots",
        credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***"))
    }

    lazy val publicationSettings = Seq(
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
        releaseProcess := Seq[ReleaseStep](
          checkSnapshotDependencies,
          inquireVersions,
          setReleaseVersion,
          runTest,
          // commitReleaseVersion, // performs the initial git checks
          tagRelease,
          publishArtifacts,
          // setNextVersion,
          // commitNextVersion,
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
            maintainer := "Direct Inc. <info@driver.xyz>",
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
                                  exposedPorts: Seq[Int],
                                  clusterName: String = "dev-uw1a-1",
                                  clusterZone: String = "us-west1-a",
                                  gCloudProject: String = "driverinc-dev",
                                  baseImage: String = "openjdk:8-jre-alpine",
                                  dockerCustomCommands: List[String] = List.empty[String],
                                  aggregateSubprojects: Boolean = false) = {

        val repositoryName = "gcr.io/" + gCloudProject

        val trustStoreConfiguration =
          "[ -n \"$TRUSTSTORE\" ] && keytool -import -noprompt -trustcacerts -alias driver-internal -file /etc/$imageName/ssl/issuing_ca -storepass 123456"
  
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
