sbtPlugin := true

name := "sbt-settings"
scalaVersion := "2.12.6"

// Plugins that will be included transitively in projects depending on sbt-settings
addSbtPlugin("com.lucidchart"  %% "sbt-scalafmt"          % "1.15")
addSbtPlugin("org.scalastyle"  %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("io.spray"          %% "sbt-revolver"        % "0.9.1")
addSbtPlugin("com.eed3si9n"      %% "sbt-buildinfo"       % "0.9.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-git"             % "1.0.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-native-packager" % "1.3.4")
addSbtPlugin("com.github.gseitz" %% "sbt-release"         % "1.0.8")

// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false
