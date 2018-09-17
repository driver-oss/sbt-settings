enablePlugins(SbtPlugin)

name := "sbt-settings"
scalaVersion := "2.12.6"

// Plugins that will be included transitively in projects depending on sbt-settings
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")
addSbtPlugin("org.scalastyle"  %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("io.spray"          %% "sbt-revolver"        % "0.9.1")
addSbtPlugin("com.eed3si9n"      %% "sbt-buildinfo"       % "0.9.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-git"             % "1.0.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-native-packager" % "1.3.6")

// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false
