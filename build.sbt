lazy val root = (project in file(".")).
  settings(
    name := "Freestyle 0.4.0 Presentation",
    version := "1.0",
    scalaVersion := "2.12.4",
    libraryDependencies ++= Seq(
      "io.frees" %% "frees-core" % "0.4.0",
      "io.frees" %% "frees-tagless" % "0.4.0",
      "io.frees" %% "frees-effects" % "0.4.0",
      "io.frees" %% "frees-monix" % "0.4.0",
      "org.typelevel" %% "cats-effect" % "0.4",
      "org.scalameta" %% "scalameta" % "1.8.0",
    ),
    tutTargetDirectory := baseDirectory.value / "deck",
    addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.full),
    scalacOptions += "-Xplugin-require:macroparadise",
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
  )


enablePlugins(TutPlugin)
