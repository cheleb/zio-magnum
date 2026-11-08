import sbt.*
import sbt.Keys.*

import com.github.sbt.git.GitPlugin.autoImport.*
import com.github.sbt.sbtghpages.GhpagesPlugin
import com.github.sbt.sbtghpages.GhpagesPlugin.autoImport.*
import com.typesafe.sbt.site.SitePlugin.autoImport.*
import com.typesafe.sbt.site.{SitePreviewPlugin, SiteScaladocPlugin}
import mdoc.MdocPlugin.autoImport.mdocOut
import sbtunidoc.ScalaUnidocPlugin
import sbtunidoc.ScalaUnidocPlugin.autoImport.*

/** Website (scaladoc + mdoc + gh-pages) configuration.
  *
  * sbt 2.x applies bare settings from `.sbt` files to every subproject, so
  * these settings are applied explicitly to the root project only.
  */
object Website {

  val plugins: Seq[Plugins] = Seq(
    SiteScaladocPlugin,
    SitePreviewPlugin,
    ScalaUnidocPlugin,
    GhpagesPlugin
  )

  private lazy val currentYear: String =
    java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString

  val settings: Seq[Setting[?]] = addCommandAlias(
    "website",
    "docs/mdoc; makeSite"
  ) ++ Seq(
    ScalaUnidoc / siteSubdirName := "",
    addMappingsToSiteDir(
      ScalaUnidoc / packageDoc / mappings,
      ScalaUnidoc / siteSubdirName
    ),
    git.remoteRepo := "git@github.com:cheleb/zio-magnum.git",
    ghpagesNoJekyll := true,
    Compile / doc / scalacOptions ++= Seq(
      "-siteroot",
      (LocalProject("docs") / mdocOut).value.getPath,
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
  )
}
