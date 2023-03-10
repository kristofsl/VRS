val scala3Version = "3.2.0"

name := "VRS"
version := "1.0"

scalaVersion := scala3Version

libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
libraryDependencies += "dev.zio" %% "zio" % "2.0.5"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.5"
libraryDependencies += "dev.zio" %% "zio-logging"       %   "2.1.6"
libraryDependencies += "commons-validator" % "commons-validator" % "1.7"
libraryDependencies += "dev.zio" %% "zio-test" % "2.0.5" % Test
libraryDependencies += "dev.zio" %% "zio-test-sbt" % "2.0.5" % Test
libraryDependencies += "dev.zio" %% "zio-test-magnolia" % "2.0.5" % Test
libraryDependencies += "com.google.ortools" % "ortools-java" % "9.4.1874"
libraryDependencies += "io.d11" %% "zhttp" % "2.0.0-RC11"
libraryDependencies += "dev.zio" %% "zio-json" % "0.4.2"

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assemblyJarName := s"scala.jar"
