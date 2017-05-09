lazy val root = (project in file(".")).
  settings(
    name := "Freestyle 0.1.0 Presentation",
    version := "1.0",
    scalaVersion := "2.12.1",
    libraryDependencies ++= Seq(
      "com.47deg" %% "freestyle" % "0.1.0",
      "com.47deg" %% "freestyle-tagless" % "0.1.0",
      "com.47deg" %% "freestyle-effects" % "0.1.0",
      "com.47deg" %% "freestyle-monix" % "0.1.0",
      "io.monix" %% "monix-cats" % "2.2.4",
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
    ),
    tutSettings,
    tutTargetDirectory := baseDirectory.value / "deck",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")
  )
