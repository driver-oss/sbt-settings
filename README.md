# _sbt_ plugin for common _sbt_ settings
Provides common sbt configuration for sbt itself, Scala compiler, testing, linting, formatting, release process, packaging, publication to Driver Scala projects. Allowing to use only necessary parts. Artifact organization is set to `com.drivergrp`.

## TL;DR

### project/plugins.sbt

    resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases"
    credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***")

    addSbtPlugin("com.drivergrp" % "sbt-settings" % "0.5.0")

### build.sbt

Do `sbt reload` after adding a plugin and configure a project:

    lazy val root = (project in file("."))
      .settings (name := "Name-Of-Your-Project")

      .integrationTestingConfiguration // Enable integration tests, test-all command

      .buildInfoConfiguration          // Build info accessible in app code

      .gitPluginConfiguration          // Git tag to application version number

      .packagingConfiguration          // Currently packages to zip file as java server app

      .settings(lintingSettings)       // Scala code linting settings, includes `wartRemoverSettings` and `scalastyleSettings`

      .settings(formatSettings)        // Standard Scala code formatting

      .settings(repositoriesSettings)  // To use dependencies from Driver jar repository

      .settings(publicationSettings)   // Publishing to Driver jar repository

      .settings(releaseSettings)       // Release process configuration

      .dockerConfiguration             // Docker containerization settings

## Reference

Artifact organization is set to `com.drivergrp`.

### Scala compiler settings
Scala version — 2.11.8, flags configured:

 - Common settings: `-unchecked -feature -encoding utf8`,
 - _Advanced Scala features_: `-language:higherKinds -language:implicitConversions -language:postfixOps`,
 - _Compiler linting_: `-Xlint -deprecation -Ywarn-numeric-widen -Ywarn-dead-code -Ywarn-unused -Ywarn-unused-import`.

### Used sbt plugins

 - [sbt-scalafmt](https://olafurpg.github.io/scalafmt/) - code formatter for Scala,
 - [sbt-wartremover](https://github.com/puffnfresh/wartremover) - flexible Scala code linting tool,
 - [scalastyle-sbt-plugin](https://github.com/scalastyle) - examines your Scala code and indicates potential problems with it,
 - [sbt-revolver](https://github.com/spray/sbt-revolver) - for dangerously fast development turnaround in Scala,
 - [sbt-buildinfo](https://github.com/sbt/sbt-buildinfo) - generates Scala source from your build definitions,
 - [sbt-git](https://github.com/sbt/sbt-git) - to get access to git data (commit hash, branch) in sbt,
 - [sbt-native-packager](https://github.com/sbt/sbt-native-packager) - build application packages in native formats,
 - [sbt-assembly](https://github.com/sbt/sbt-assembly) - deploy fat JARs. Restart processes (sbt-native-packager is used instead,)
 - [sbt-release](https://github.com/sbt/sbt-release) - customizable release process,
 - [sbt-docker](https://github.com/marcuslonnberg/sbt-docker) - create Docker images directly from sbt.

## Examples

### Simple project configuration example

    import sbt._
    import Keys._

    lazy val core = (project in file(".")).
      settings(name := "Name-Of-Your-Project").
      settings(
        libraryDependencies ++= Seq(
          "com.drivergrp"       %% "core"         % "0.2.0",
          "com.drivergrp"       %% "domain-model" % "0.1.0",
          "com.typesafe.slick"  %% "slick"        % "3.1.1",
          // ... etc
        ))
      .gitPluginConfiguration
      .settings (lintingSettings ++ formatSettings)
      .settings(releaseSettings)


### Complex project configuration example

    import sbt._
    import sbt.Keys._

    // we hide the existing definition for setReleaseVersion to replace it with our own
    import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _}

    // Sub-project specific dependencies
    lazy val dependencies = Seq(
      "com.drivergrp"       %% "core"         % "0.2.0",
      "com.drivergrp"       %% "domain-model" % "0.1.0",
      "com.typesafe.slick"  %% "slick"        % "3.1.1",
      "com.typesafe"         % "config" % "1.2.1",
      // ... etc
    )

    lazy val dependenciesSettings = Seq(
      resolvers += "justwrote" at "http://repo.justwrote.it/releases/",
      libraryDependencies ++= dependencies
    )

    lazy val testingDependencies = Seq(
      libraryDependencies ++= Seq(
        "com.typesafe.akka"   %% "akka-testkit"  % "2.4.8" % "test",
        "org.scalatest"        % "scalatest_2.11" % "2.2.1" % "test",
        "org.mockito"          % "mockito-core" % "1.9.5" % "test"
      )
    )

    lazy val integrationTestingDependencies = Seq(
      libraryDependencies ++= Seq(
        "com.typesafe.akka"   %% "akka-testkit"  % akkaV   % "test,it",
        "org.scalatest"        % "scalatest_2.11" % "2.2.1" % "test,it",
        "org.mockito"          % "mockito-core" % "1.9.5" % "test,it"
      )
    )

    lazy val usersModule = (project in file("users"))
      .settings (lintingSettings ++ formatSettings ++ repositoriesSettings)
      .settings (dependenciesSettings ++ testingDependencies)
      .settings (publishTo := Some(Resolver.defaultLocal))

    lazy val assaysModule = (project in file("assays"))
      .settings (lintingSettings ++ formatSettings ++ repositoriesSettings)
      .settings (dependenciesSettings ++ testingDependencies)
      .settings (publishTo := Some(Resolver.defaultLocal))

    lazy val reportsModule = (project in file("reports"))
      .settings (lintingSettings ++ formatSettings ++ repositoriesSettings)
      .settings (dependenciesSettings ++ testingDependencies)
      .settings (publishTo := Some(Resolver.defaultLocal))

    lazy val root = (project in file("."))
      .settings (name := "direct")
      .integrationTestingConfiguration
      .settings (lintingSettings ++ formatSettings ++ repositoriesSettings)
      .settings (dependenciesSettings ++ integrationTestingDependencies)
      .settings (publicationSettings ++ releaseSettings)
      .buildInfoConfiguration
      .gitPluginConfiguration
      .packagingConfiguration
      .dockerConfiguration
      .dependsOn (usersModule, assaysModule, reportsModule)
      .aggregate (usersModule, assaysModule, reportsModule)

For more examples please refer to [Driver Template](https://github.com/drivergroup/***REMOVED***), [Core library](https://github.com/drivergroup/***REMOVED***) or [***REMOVED***](https://github.com/drivergroup/***REMOVED***) projects.