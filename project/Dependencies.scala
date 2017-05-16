import sbt._

object Dependencies {

  val funCqrsVersion ="1.0.0-M1-17-f5776013" 
  val macwire        = "2.3.0"

  lazy val appDeps = {
    Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      // Fun.CQRS
      "io.strongtyped" %% "fun-cqrs-akka" % funCqrsVersion,
      // LevelDB
      "org.iq80.leveldb"          % "leveldb"        % "0.7",
      "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
      // Macwire
      "com.softwaremill.macwire" %% "macros" % macwire
    )
  }

  lazy val testDeps =
    Seq(
      "io.strongtyped"         %% "fun-cqrs-test-kit"  % funCqrsVersion % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1"        % Test,
      "org.scalatest"          %% "scalatest"          % "3.0.0"        % Test
    )

}
