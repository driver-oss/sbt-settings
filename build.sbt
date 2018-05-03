sbtPlugin := true

name := "sbt-settings"
scalaVersion := "2.12.5"

addSbtPlugin("com.lucidchart"  %% "sbt-scalafmt"          % "1.14")
addSbtPlugin("org.scalastyle"  %% "scalastyle-sbt-plugin" % "1.0.0")

// Launch and deploy/release plugins
addSbtPlugin("io.spray"          %% "sbt-revolver"        % "0.9.1")
addSbtPlugin("com.eed3si9n"      %% "sbt-buildinfo"       % "0.7.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-git"             % "0.9.3")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-native-packager" % "1.3.2")
addSbtPlugin("com.eed3si9n"      %% "sbt-assembly"        % "0.14.5")
addSbtPlugin("com.github.gseitz" %% "sbt-release"         % "1.0.7")

// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test
