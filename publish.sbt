version in ThisBuild := {
  import sys.process._
    ("git describe --always --dirty=-SNAPSHOT --match v[0-9].*" !!).tail.trim
}
organization in ThisBuild := "xyz.driver"
licenses in ThisBuild := Seq(
  ("Apache 2.0", url("https://www.apache.org/licenses/LICENSE-2.0")))
homepage in ThisBuild := Some(
  url("https://github.com/drivergroup/sbt-settings"))
publishMavenStyle in ThisBuild := true
publishTo in ThisBuild := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/drivergroup/sbt-settings"),
    "scm:git@github.com:drivergroup/sbt-settings.git"
  )
)

developers in ThisBuild := List(
  Developer(
    id = "jodersky",
    name = "Jakob Odersky",
    email = "jakob@driver.xyz",
    url = url("https://driver.xyz")
  )
)
