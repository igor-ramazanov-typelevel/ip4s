import com.typesafe.tools.mima.core._
import org.typelevel.sbt.gha.WorkflowStep.Run
import org.typelevel.sbt.gha.WorkflowStep.Sbt

ThisBuild / tlBaseVersion := "3.8"

ThisBuild / organization := "com.comcast"
ThisBuild / organizationName := "Comcast Cable Communications Management, LLC"
ThisBuild / tlSonatypeUseLegacyHost := true

ThisBuild / startYear := Some(2018)

ThisBuild / developers ++= List(
  tlGitHubDev("mpilquist", "Michael Pilquist"),
  tlGitHubDev("matthughes", "Matt Hughes"),
  tlGitHubDev("nequissimus", "Tim Steinbach")
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("8"))

ThisBuild / crossScalaVersions := List("2.13.16", "3.3.5")

ThisBuild / tlVersionIntroduced := Map("3" -> "3.0.3")

ThisBuild / doctestTestFramework := DoctestTestFramework.ScalaCheck

ThisBuild / initialCommands := "import com.comcast.ip4s._"

ThisBuild / mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.comcast.ip4s.IpAddress.toDefaultString"), // #553
  ProblemFilters.exclude[DirectMissingMethodProblem]("com.comcast.ip4s.Ipv6Address.toInetAddress"),
  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.comcast.ip4s.Dns.*"), // sealed trait
  // Scala 3 (erroneously?) considered Multicast/SourceSpecificMulticast as sum types
  ProblemFilters.exclude[DirectMissingMethodProblem]("com.comcast.ip4s.Multicast.ordinal"),
  ProblemFilters.exclude[MissingTypesProblem]("com.comcast.ip4s.Multicast$"),
  ProblemFilters.exclude[DirectMissingMethodProblem]("com.comcast.ip4s.SourceSpecificMulticast.ordinal"),
  ProblemFilters.exclude[MissingTypesProblem]("com.comcast.ip4s.SourceSpecificMulticast$"),
  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.comcast.ip4s.IpAddress.isPrivate"), // #562
  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.comcast.ip4s.IpAddress.isLoopback"),
  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.comcast.ip4s.IpAddress.isLinkLocal")
)

ThisBuild / githubOwner := "igor-ramazanov-typelevel"
ThisBuild / githubRepository := "ip4s"
ThisBuild / githubWorkflowPublishPreamble := List.empty
ThisBuild / githubWorkflowUseSbtThinClient := true
ThisBuild / githubWorkflowPublish := List(
  Run(
    commands = List("echo \"$PGP_SECRET\" | gpg --import"),
    id = None,
    name = Some("Import PGP key"),
    env = Map("PGP_SECRET" -> "${{ secrets.PGP_SECRET }}"),
    params = Map(),
    timeoutMinutes = None,
    workingDirectory = None
  ),
  Sbt(
    commands = List("+ publish"),
    id = None,
    name = Some("Publish"),
    cond = None,
    env = Map("GITHUB_TOKEN" -> "${{ secrets.GB_TOKEN }}"),
    params = Map.empty,
    timeoutMinutes = None,
    preamble = true
  )
)
ThisBuild / gpgWarnOnFailure := false

lazy val root = tlCrossRootProject.aggregate(core, testKit)

lazy val testKit = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("./test-kit"))
  .settings(commonSettings)
  .settings(
    name := "ip4s-test-kit"
  )
  .settings(
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .settings(mimaPreviousArtifacts := Set.empty)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % "1.18.1",
      "org.scalameta" %%% "munit-scalacheck" % "1.1.0" % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.2.0-M1" % Test
    )
  )
  .jvmSettings(
    libraryDependencies += "com.google.guava" % "guava" % "33.4.8-jre" % "test"
  )
  .dependsOn(core % "compile->compile")

lazy val testKitJVM = testKit.jvm
lazy val testKitJS = testKit.js
  .disablePlugins(DoctestPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
lazy val testKitNative = testKit.js
  .disablePlugins(DoctestPlugin)
  .settings(commonNativeSettings)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("."))
  .settings(commonSettings)
  .settings(
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .settings(
    name := "ip4s-core",
    libraryDependencies ++= {
      if (tlIsScala3.value) Nil
      else List("org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided")
    },
    scalacOptions := scalacOptions.value.filterNot(_ == "-source:3.0-migration"),
    Compile / doc / scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-nowarn")
                                       else Nil)
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "literally" % "1.2.0",
      "org.typelevel" %%% "cats-core" % "2.13.0",
      "org.typelevel" %%% "cats-effect" % "3.7-4972921",
      "org.scalacheck" %%% "scalacheck" % "1.18.1" % Test
    )
  )
  .nativeSettings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "idna4s-core" % "0.2.0-M1"
    )
  )

lazy val coreJVM = core.jvm.settings(
  doctestIgnoreRegex := Some(".*Literals.scala")
)

lazy val coreJS = core.js
  .disablePlugins(DoctestPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  .settings(
    Compile / npmDependencies += "punycode" -> "2.1.1",
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )

lazy val coreNative = core.native
  .disablePlugins(DoctestPlugin)
  .settings(commonNativeSettings)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(MdocPlugin)
  .dependsOn(coreJVM)
  .settings(
    mdocIn := baseDirectory.value / "src",
    mdocOut := baseDirectory.value / "../docs",
    githubWorkflowArtifactUpload := false,
    libraryDependencies += "org.typelevel" %%% "cats-effect" % "3.6.1"
  )

lazy val commonSettings = Seq(
  Compile / unmanagedResources ++= {
    val base = baseDirectory.value / ".."
    (base / "NOTICE") +: (base / "LICENSE") +: (base / "CONTRIBUTING") +: ((base / "licenses") * "LICENSE_*").get
  }
)

lazy val commonNativeSettings = Seq(
  tlVersionIntroduced := List("2.13", "3").map(_ -> "3.1.4").toMap
)
