addCommandAlias("website", "docs/mdoc; makeSite")

lazy val currentYear: String =
  java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString

enablePlugins(
  SiteScaladocPlugin,
  SitePreviewPlugin,
  ScalaUnidocPlugin,
  GhpagesPlugin
)

ScalaUnidoc / siteSubdirName := ""
addMappingsToSiteDir(
  ScalaUnidoc / packageDoc / mappings,
  ScalaUnidoc / siteSubdirName
)
git.remoteRepo := "git@github.com:cheleb/laminar-form-derivation.git"
ghpagesNoJekyll := true
Compile / doc / scalacOptions ++= Seq(
  "-siteroot",
  "zio-magnum-docs/target/mdoc",
  "-project",
  "ZIO Magnum",
  "-groups",
  "-project-version",
  sys.env.getOrElse("VERSION", version.value),
  "-revision",
  version.value,
  // "-default-templates",
  // "static-site-main",
  "-project-footer",
  s"Copyright (c) 2025-$currentYear, Olivier NOUGUIER",
  "-social-links:github::https://github.com/cheleb/zio-magnum,twitter::https://twitter.com/oNouguier,linkedIn::https://www.linkedin.com/in/olivier-nouguier::linkedin-day.png::linkedin-night.png,bluesky::https://bsky.app/profile/onouguier.bsky.social::bluesky-day.svg::bluesky-night.jpg",
  "-Ygenerate-inkuire",
  "-skip-by-regex:facades\\..*",
  "-skip-by-regex:samples\\..*",
  "-skip-by-regex:html\\..*",
  "-snippet-compiler:compile"
)
