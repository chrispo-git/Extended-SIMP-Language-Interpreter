scalaVersion := "3.6.4"

name := "simp-interpreter"
organization := "ch.epfl.scala"
version := "1.0.0"

enablePlugins(JavaAppPackaging, GraalVMNativeImagePlugin)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

libraryDependencies += "org.jline" % "jline" % "3.25.1"