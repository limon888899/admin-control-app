package com.adminctl.app

/** Add or edit your sites here. baseUrl = the live Vercel/custom domain, no trailing slash. */
data class Site(val name: String, val baseUrl: String)

val SITES = listOf(
    Site("Global Migration Hub", "https://www.globalmigrationhub.com"), // TODO: put your real live domain
    Site("Thai Pattana Bank", "https://YOUR-THAI-PATTANA-DOMAIN"),      // TODO: put your real live domain
)
