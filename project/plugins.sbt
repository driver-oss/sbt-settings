// Code style plugins
addSbtPlugin("org.scalastyle"  %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.lucidchart"  %% "sbt-scalafmt"          % "1.10")

// Launch and deploy/release plugins
addSbtPlugin("io.spray"          %% "sbt-revolver"  % "0.9.0")
addSbtPlugin("com.eed3si9n"      %% "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.typesafe.sbt"  %% "sbt-git"       % "0.9.3")
addSbtPlugin("com.github.gseitz" %% "sbt-release"   % "1.0.6")
