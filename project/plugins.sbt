// Code style plugins

addSbtPlugin("org.wartremover" % "sbt-wartremover"        % "2.0.3")
addSbtPlugin("org.scalastyle"  %% "scalastyle-sbt-plugin" % "0.8.0")

// Launch and deploy/release plugins
addSbtPlugin("io.spray"          % "sbt-revolver"  % "0.8.0")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"       % "0.8.5")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"  % "0.14.3")
addSbtPlugin("com.github.gseitz" % "sbt-release"   % "1.0.3")

// This plugin represents functionality that is to be added to sbt in the future
addSbtPlugin("org.scala-sbt" % "sbt-core-next" % "0.1.1")
