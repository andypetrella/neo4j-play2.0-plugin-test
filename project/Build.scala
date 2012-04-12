import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "neo4j-play2.0-plugin-test"
  val appVersion = "1.0"

  val appDependencies = Seq(
    "be.nextlab" %% "neo4j-rest-play-plugin" % "1.0-SNAPSHOT",


    "org.specs2" %% "specs2" % "1.8.2" % "test" withSources,
    "be.nextlab" %% "gatling-play2-plugin" % "1.0-SNAPSHOT" % "test"
  )
  // Only compile the bootstrap bootstrap.less file and any other *.less file in the stylesheets directory
  def customLessEntryPoints(base: File): PathFinder = (
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "bootstrap.less") +++
      (base / "app" / "assets" / "stylesheets" * "*.less")
    )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    lessEntryPoints <<= baseDirectory(customLessEntryPoints)
  )

}
