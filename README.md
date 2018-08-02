[![Build Status](https://travis-ci.org/drivergroup/sbt-settings.svg?branch=master)](https://travis-ci.org/drivergroup/sbt-settings)
[![Scaladex](https://index.scala-lang.org/drivergroup/sbt-settings/latest.svg)](https://index.scala-lang.org/drivergroup/sbt-settings)

# Common sbt settings

Provides common settings for Scala projects, as they are typically
configured at Driver.

sbt-settings is a suite of [sbt
autoplugins](https://www.scala-sbt.org/1.0/docs/Plugins.html) that
provide common settings for the scala compiler, testing, linting,
formatting, release process, packaging and publication.

## Getting started

Adding the following snippet to `project/plugins.sbt` will make the
plugins available:

```scala
addSbtPlugin("xyz.driver" % "sbt-settings" % "<latest_tag>")
```

The next section exlains what plugins are available and which ones are
activated out-of-the-box.

## Plugins

### Summary

| Name                     | Enabled                                |
|--------------------------|----------------------------------------|
| LintPlugin               | automatic                              |
| LibraryPlugin            | manual                                 |
| ServicePlugin            | manual                                 |
| IntegrationTestPackaging | automatic, if ServicePlugin is enabled |

### Lint Plugin

*[source](src/main/scala/xyz.driver.sbt/LintPlugin.scala)*

- Includes configuration for scalafmt and scalastyle, modifies the
  `test` task to check for formatting and styling.
- Sets strict compiler flags and treats warnings as errors (with the
  exception of deprecations).

This plugin can get in the way of developer productivity. If that is
the case, it can simply be disabled.

### Integration Test Packaging

*[source](src/main/scala/xyz.driver.sbt/IntegrationTestPackaging.scala)*

Augments the packaging configuration of ServicePlugin, to include
integration tests in deployed applications images.

### Library Plugin

*[source](src/main/scala/xyz.driver.sbt/LibraryPlugin.scala)*

Common settings for libraries. Sets the project organization and reads
version information from git. It also enables overriding the version
by setting a `VERSION` environment variable (which may be useful to do
from CI).

### Service Plugin

*[source](src/main/scala/xyz.driver.sbt/ServicePlugin.scala)*

Packages an application as a docker image and provides a way to
include internal TLS certificates.

It also includes some utility commands such as `start` and `stop`
(based on [sbt-revolver](https://github.com/spray/sbt-revolver), to
enable rapid local development cycles of applications.

## Canonical Use Case
sbt-settings provides many plugins, which may be used in various
ways. Typically however, a project is either a Library or a Service,
and as such, it will enable one of those plugins explicitly. The other
plugins will provide general settings for common conventions and are
always enabled.

The following provides a typical example for configuring a project as
a service called "myservice":

```scala
lazy val myservice = project
  .in(file("."))
  .enablePlugin(ServicePlugin)
  .disablePlugins(IntegrationTestPackaging) // we don't need integration tests
  .disablePlugins(LintPlugin) // I don't need style check during development!
  .settings( /* custom settings */)
```

## Developing this Plugin
This project is set up to auto-deploy on push. If an annotated tag in
the form `v<digit>.*` is pushed, a new version of this project will be
built and published to Maven Central.
