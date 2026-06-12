import { defineConfig } from 'vitepress'

// Canonical origin for absolute URLs (og:url, canonical links, sitemap).
const HOSTNAME = process.env.DOCS_HOSTNAME || 'https://vgi-java-introduction.query.farm'
const OG_IMAGE = `${HOSTNAME}/og-image.png`

// Sub-path base for project hosting (e.g. GitHub Pages). Empty => served at root
// (Cloudflare custom domain). Set DOCS_BASE='/repo/' for a project Pages deploy.
const BASE = process.env.DOCS_BASE || '/'

// Set on the temporary GitHub Pages build so search engines don't index it and
// compete with the canonical Cloudflare domain. Social/OG scrapers ignore this.
const NOINDEX = !!process.env.DOCS_NOINDEX

const SITE_DESCRIPTION =
  'Expose Java libraries as DuckDB / Haybarn SQL functions and tables over Apache Arrow — scalar, table, table-in-out, aggregate, and buffering functions served from your own JVM process.'

// Sidebar sections, used to build per-page BreadcrumbList structured data.
const SECTIONS = {
  intro: { name: 'Introduction', path: 'intro/what-is-vgi' },
  functions: { name: 'Function kinds', path: 'functions/scalar' },
  guides: { name: 'Guides', path: 'guides/catalog' },
  advanced: { name: 'Advanced', path: 'advanced/parallelism' },
  agents: { name: 'Coding agents', path: 'agents/' },
  reference: { name: 'Reference', path: 'reference/cli-and-env' },
}

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: 'VGI for Java',
  description:
    'Serve Haybarn / DuckDB catalogs and functions from Java over Apache Arrow IPC.',
  base: BASE,
  lang: 'en-US',
  cleanUrls: true,
  lastUpdated: true,

  // Emit sitemap.xml at build time (referenced from public/robots.txt). The
  // sitemap lib keeps only the origin of `hostname`, so when the site is served
  // under a sub-path (GitHub Pages), prefix each url with the base by hand.
  sitemap: {
    hostname: new URL(HOSTNAME).origin,
    transformItems: (items) =>
      items.map((i) => ({ ...i, url: BASE + i.url.replace(/^\//, '') })),
  },

  // The d2 sources live under docs/diagrams/ next to the rendered SVGs; its
  // README is tooling notes. _snippets holds reusable @include partials. Neither
  // is a standalone site page.
  srcExclude: ['diagrams/**', '_snippets/**'],

  // Site-wide tags. Per-page og:title/og:description/og:url/canonical are added
  // in transformPageData below.
  head: [
    ...(NOINDEX
      ? [['meta', { name: 'robots', content: 'noindex, follow' }] as [string, Record<string, string>]]
      : []),
    ['link', { rel: 'icon', type: 'image/png', href: `${BASE}vgi-logo.png` }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'VGI for Java' }],
    ['meta', { property: 'og:image', content: OG_IMAGE }],
    ['meta', { property: 'og:image:type', content: 'image/png' }],
    ['meta', { property: 'og:image:width', content: '1280' }],
    ['meta', { property: 'og:image:height', content: '640' }],
    ['meta', { property: 'og:image:alt', content: 'VGI — Vector Gateway Interface' }],
    ['meta', { property: 'og:locale', content: 'en_US' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:image', content: OG_IMAGE }],
    ['meta', { name: 'theme-color', content: '#2c5530' }],
  ],

  // Per-page Open Graph / Twitter / canonical tags, derived from each page's
  // title and `description` frontmatter.
  transformPageData(pageData) {
    const path = pageData.relativePath
      .replace(/index\.md$/, '')
      .replace(/\.md$/, '')
    const canonical = `${HOSTNAME}/${path}`
    const isHome = pageData.frontmatter.layout === 'home'
    const title = isHome ? 'VGI for Java' : `${pageData.title} | VGI for Java`
    const description =
      pageData.description ||
      pageData.frontmatter.description ||
      SITE_DESCRIPTION

    // Richer <title> on the home page (the og:title above stays the brand) — the
    // title tag is a primary SERP signal, so spell out what the project is.
    if (isHome) {
      pageData.title = 'VGI for Java — Java functions & tables for DuckDB / Haybarn'
      pageData.titleTemplate = false
    }

    // JSON-LD structured data: a WebSite + Organization + SoftwareSourceCode
    // graph on the home page, a BreadcrumbList on every inner page.
    const jsonLd = isHome
      ? {
          '@context': 'https://schema.org',
          '@graph': [
            {
              '@type': 'WebSite',
              '@id': `${HOSTNAME}/#website`,
              url: `${HOSTNAME}/`,
              name: 'VGI for Java',
              description: SITE_DESCRIPTION,
              inLanguage: 'en-US',
              publisher: { '@id': `${HOSTNAME}/#org` },
            },
            {
              '@type': 'Organization',
              '@id': `${HOSTNAME}/#org`,
              name: 'Query Farm',
              url: 'https://query.farm',
              logo: `${HOSTNAME}/vgi-logo.png`,
            },
            {
              '@type': 'SoftwareSourceCode',
              name: 'VGI for Java',
              description: SITE_DESCRIPTION,
              url: `${HOSTNAME}/`,
              programmingLanguage: 'Java',
              runtimePlatform: 'JVM (JDK 21+)',
              codeRepository: 'https://github.com/Query-farm/vgi-java',
              author: { '@id': `${HOSTNAME}/#org` },
              keywords:
                'VGI, DuckDB, Haybarn, Apache Arrow, Java, SQL functions, table functions, DuckDB extension',
            },
          ],
        }
      : (() => {
          const section = SECTIONS[path.split('/')[0] as keyof typeof SECTIONS]
          const crumbs: { name: string; url?: string }[] = [
            { name: 'Home', url: `${HOSTNAME}/` },
          ]
          if (section && section.path !== path) {
            crumbs.push({ name: section.name, url: `${HOSTNAME}/${section.path}` })
          }
          crumbs.push({ name: pageData.title })
          return {
            '@context': 'https://schema.org',
            '@type': 'BreadcrumbList',
            itemListElement: crumbs.map((c, i) => ({
              '@type': 'ListItem',
              position: i + 1,
              name: c.name,
              ...(c.url ? { item: c.url } : {}),
            })),
          }
        })()

    pageData.frontmatter.head ??= []
    pageData.frontmatter.head.push(
      ['meta', { property: 'og:title', content: title }],
      ['meta', { property: 'og:description', content: description }],
      ['meta', { property: 'og:url', content: canonical }],
      ['meta', { name: 'twitter:title', content: title }],
      ['meta', { name: 'twitter:description', content: description }],
      ['link', { rel: 'canonical', href: canonical }],
      ['script', { type: 'application/ld+json' }, JSON.stringify(jsonLd)],
    )
  },

  themeConfig: {
    logo: '/vgi-logo.png',

    nav: [
      { text: 'Guide', link: '/intro/what-is-vgi' },
      { text: 'Functions', link: '/functions/scalar' },
      { text: 'Advanced', link: '/advanced/parallelism' },
      { text: 'Reference', link: '/reference/cli-and-env' },
      { text: 'Agents', link: '/agents/' },
      {
        text: 'vgi-java',
        link: 'https://github.com/Query-farm/vgi-java',
      },
    ],

    sidebar: [
      {
        text: 'Introduction',
        items: [
          { text: 'What is VGI?', link: '/intro/what-is-vgi' },
          { text: 'Quickstart', link: '/intro/quickstart' },
          { text: 'Anatomy of a worker', link: '/intro/anatomy-of-a-worker' },
        ],
      },
      {
        text: 'Function kinds',
        items: [
          { text: 'Scalar', link: '/functions/scalar' },
          { text: 'Table', link: '/functions/table' },
          { text: 'Table-in-out', link: '/functions/table-in-out' },
          { text: 'Aggregate', link: '/functions/aggregate' },
          { text: 'Buffering', link: '/functions/buffering' },
        ],
      },
      {
        text: 'Guides',
        items: [
          { text: 'Building a catalog', link: '/guides/catalog' },
          { text: 'Testing your function', link: '/guides/testing' },
          { text: 'Errors & logging', link: '/guides/errors-and-logging' },
        ],
      },
      {
        text: 'Advanced',
        items: [
          { text: 'Parallelism', link: '/advanced/parallelism' },
          { text: 'Shared memory', link: '/advanced/shared-memory' },
          { text: 'Benchmarks', link: '/advanced/benchmarks' },
          {
            text: 'Filter & projection pushdown',
            link: '/advanced/filter-projection-pushdown',
          },
        ],
      },
      {
        text: 'Reference',
        items: [
          { text: 'CLI & environment', link: '/reference/cli-and-env' },
          { text: 'JVM flags', link: '/reference/jvm-flags' },
        ],
      },
      {
        text: 'Build with an agent',
        items: [
          { text: 'Coding-agent pack', link: '/agents/' },
          { text: 'Task recipes', link: '/agents/recipes' },
        ],
      },
    ],

    outline: { level: [2, 3] },

    search: { provider: 'local' },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/Query-farm/vgi-java' },
    ],

    // A site-wide footer is injected via the layout-bottom slot in
    // theme/index.ts so it shows on every page, not just the (sidebar-less)
    // home page that the built-in `footer` option is limited to.
  },
})
