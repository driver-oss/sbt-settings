import java.nio.file.{Files, Paths}

object Main extends App {
  val version = xyz.driver.BuildInfo.version
  Files.write(Paths.get("out.txt"), s"$version\n".getBytes("utf-8"))
  println(s"hello world ($version)")
}
