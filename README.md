# _sbt_ plugin for common _sbt_ settings [![Build Status](https://travis-ci.com/drivergroup/sbt-settings.svg?token=***REMOVED***&branch=master)](https://travis-ci.com/drivergroup/sbt-settings)
Provides common Driver Scala projects configuration for sbt, Scala compiler, testing, linting, formatting, release process, packaging, publication. Allows to use only necessary parts. Sets artifact `organization` to `xyz.driver`.

## TL;DR

### project/plugins.sbt

    resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases"
    credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "***REMOVED***")

    addSbtPlugin("xyz.driver" % "sbt-settings" % "0.7.34")

### build.sbt

There are two different ways to use `sbt-settings` configuration:

  * As a "Service" — deployed application distributed as a Docker container. Includes Driver Inc.'s artifactory settings, Docker packaging with truststore configuration, build info generation. Versioned using `version.sbt` file for major and minor versions. Usage example, as follows

	```
	lazy val root = (project in file("."))
	  .driverService ("Name-Of-Your-Service")

	  .integrationTestingConfiguration // Enable integration tests

	  .packagingConfiguration          // To package to zip file as java server app

	  .settings(lintingSettings)       // Scala code linting settings, includes `wartRemoverSettings` and `scalastyleSettings`

	  .settings(formatSettings)        // Standard Scala code formatting

	  .settings(releaseSettings(ServiceReleaseProcess)) // Release process configuration
	```

  * As a "Library" — commonly used code, distributed as jar using artifactory. Versioned  using git tag-based versioning. Includes Driver Inc.'s artifactory settings, publication to artifactory, `sbt release` settings,

  	```
  	lazy val root = (project in file("."))
          .driverLibrary("Name-Of-Your-Library")
          .settings(lintingSettings ++ formatSettings)
        ```

Do `sbt reload` after adding a plugin and changing project configuration.

### Acyclic import checking
To enable global project-level acyclic dependency checking in your project you need to do two things:

1. Add this to your `build.sbt` file in the global scope: `scalacOptions += "-P:acyclic:force"`
2. Add this to your `.settings(...)` lines in your project definition in build.sbt: `.settings(acyclicSettings)`

## Reference

Artifact organization is set to `xyz.driver`.

### Scala compiler settings
Scala version — 2.11.11, flags configured:

 - Common settings: `-unchecked -feature -encoding utf8`,
 - _Advanced Scala features_: `-language:higherKinds -language:implicitConversions -language:postfixOps -language:reflectiveCalls`,
 - _Compiler linting_: `-Xlint -deprecation -Ywarn-numeric-widen -Ywarn-dead-code -Ywarn-unused -Ywarn-unused-import -Xfatal-warnings -Xlint:-missing-interpolator`.

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

For examples please refer to [Driver Template](https://github.com/drivergroup/***REMOVED***), [Core library](https://github.com/drivergroup/***REMOVED***) or [***REMOVED***](https://github.com/drivergroup/***REMOVED***) projects.
