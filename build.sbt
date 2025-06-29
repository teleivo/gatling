import sbt._

import _root_.io.gatling.build.license.ApacheV2License

import BuildSettings._
import Dependencies._
import VersionFile._

Global / githubPath := "gatling/gatling"
Global / gatlingDevelopers := Seq(
  GatlingDeveloper("slandelle@gatling.io", "Stephane Landelle", isGatlingCorp = true),
  GatlingDeveloper("gcorre@gatling.io", "Guillaume Corré", isGatlingCorp = true),
  GatlingDeveloper("tpetillot@gatling.io", "Thomas Petillot", isGatlingCorp = true)
)
// [e]
//
// [e]

// Root project

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
Global / scalaVersion := "2.13.16"

lazy val root = Project("gatling-parent", file("."))
  .enablePlugins(GatlingOssPlugin)
  .disablePlugins(SbtSpotless)
  .aggregate(
    nettyUtil,
    commons,
    jsonpath,
    quicklens,
    core,
    coreJava,
    jdbc,
    jdbcJava,
    redis,
    redisJava,
    httpClient,
    http,
    httpJava,
    jms,
    jmsJava,
    charts,
    app,
    recorder,
    testFramework,
    logParserCli
  )
  .settings(basicSettings)
  .settings(skipPublishing)

// Modules

def gatlingModule(id: String) =
  Project(id, file(id))
    .enablePlugins(GatlingOssPlugin)
    .settings(gatlingModuleSettings ++ CodeAnalysis.settings)

lazy val nettyUtil = gatlingModule("gatling-netty-util")
  .settings(libraryDependencies ++= nettyUtilDependencies)

lazy val commons = gatlingModule("gatling-commons")
  .disablePlugins(SbtSpotless)
  .settings(libraryDependencies ++= commonsDependencies)
  .settings(generateVersionFileSettings)

lazy val jsonpath = gatlingModule("gatling-jsonpath")
  .disablePlugins(SbtSpotless)
  .settings(libraryDependencies ++= jsonpathDependencies)

lazy val quicklens = gatlingModule("gatling-quicklens")
  .settings(libraryDependencies ++= quicklensDependencies(scalaVersion.value))

lazy val core = gatlingModule("gatling-core")
  .dependsOn(nettyUtil, quicklens)
  .dependsOn(commons % "compile->compile;test->test")
  .dependsOn(jsonpath % "compile->compile;test->test")
  .settings(libraryDependencies ++= coreDependencies)

lazy val coreJava = gatlingModule("gatling-core-java")
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= coreJavaDependencies)

lazy val jdbc = gatlingModule("gatling-jdbc")
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= jdbcDependencies)

lazy val jdbcJava = gatlingModule("gatling-jdbc-java")
  .dependsOn(coreJava, jdbc % "compile->compile;test->test")
  .settings(libraryDependencies ++= defaultJavaDependencies)

lazy val redis = gatlingModule("gatling-redis")
  .disablePlugins(SbtSpotless)
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= redisDependencies)

lazy val redisJava = gatlingModule("gatling-redis-java")
  .dependsOn(coreJava, redis % "compile->compile;test->test")
  .settings(libraryDependencies ++= defaultJavaDependencies)

lazy val httpClient = gatlingModule("gatling-http-client")
  .dependsOn(nettyUtil % "compile->compile;test->test")
  .settings(libraryDependencies ++= httpClientDependencies)

lazy val http = gatlingModule("gatling-http")
  .dependsOn(core % "compile->compile;test->test", httpClient % "compile->compile;test->test")
  .settings(libraryDependencies ++= httpDependencies)

lazy val httpJava = gatlingModule("gatling-http-java")
  .dependsOn(coreJava, http % "compile->compile;test->test")
  .settings(libraryDependencies ++= defaultJavaDependencies)

lazy val jms = gatlingModule("gatling-jms")
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= jmsDependencies)
  .settings(Test / parallelExecution := false)

lazy val jmsJava = gatlingModule("gatling-jms-java")
  .dependsOn(coreJava, jms % "compile->compile;test->test")
  .settings(libraryDependencies ++= defaultJavaDependencies)

lazy val charts = gatlingModule("gatling-charts")
  .disablePlugins(SbtSpotless)
  .dependsOn(core % "compile->compile;test->test")
  .settings(libraryDependencies ++= chartsDependencies)
  .settings(chartTestsSettings)

lazy val benchmarks = gatlingModule("gatling-benchmarks")
  .disablePlugins(SbtSpotless)
  .dependsOn(core, http)
  .enablePlugins(JmhPlugin)
  .settings(libraryDependencies ++= benchmarkDependencies)

lazy val app = gatlingModule("gatling-app")
  .disablePlugins(SbtSpotless)
  .dependsOn(core, coreJava, http, httpJava, jms, jmsJava, jdbc, jdbcJava, redis, redisJava, charts)

lazy val recorder = gatlingModule("gatling-recorder")
  .dependsOn(core % "compile->compile;test->test", http)
  .settings(libraryDependencies ++= recorderDependencies)

lazy val testFramework = gatlingModule("gatling-test-framework")
  .disablePlugins(SbtSpotless)
  .dependsOn(app)
  .settings(libraryDependencies ++= testFrameworkDependencies)

lazy val logParserCli = gatlingModule("gatling-log-parser-cli")
  .enablePlugins(GraalVMNativeImagePlugin, AssemblyPlugin)
  .dependsOn(charts % "compile->compile;test->test", app % "compile->compile")
  .settings(
    Compile / mainClass := Some("io.gatling.logparser.GatlingLogParserCli"),
    assembly / mainClass := Some("io.gatling.logparser.GatlingLogParserCli"),
    assembly / assemblyJarName := s"gatling-log-parser-cli-${version.value}-assembly.jar",
    assembly / assemblyMergeStrategy := {
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
      case "module-info.class" => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) if xs.last.endsWith(".SF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) if xs.last.endsWith(".DSA") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) if xs.last.endsWith(".RSA") => MergeStrategy.discard
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", "native-image", xs @ _*) => MergeStrategy.first
      case x if x.endsWith(".conf") => MergeStrategy.concat
      case x if x.endsWith(".properties") => MergeStrategy.filterDistinctLines
      case x if x.endsWith(".xml") => MergeStrategy.first
      case x => MergeStrategy.first
    },
    graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "--initialize-at-build-time",
      "--enable-https",
      "--enable-all-security-services",
      "-H:+ReportExceptionStackTraces",
      "-H:IncludeResources=.*\\.properties",
      "-H:IncludeResources=.*\\.xml",
      "-H:IncludeResources=logback\\.xml",
      "-H:+AddAllCharsets",
      "--install-exit-handlers"
    ),
    graalVMNativeImageGraalVersion := Some("21.0.2"),
    graalVMNativeImageCommand := (if (System.getProperty("os.name").toLowerCase.contains("linux")) "/usr/bin/native-image" else "native-image")
  )
