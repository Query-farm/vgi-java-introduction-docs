import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import './custom.css'
import KindIcon from './components/KindIcon.vue'
import KindBanner from './components/KindBanner.vue'
import KindGallery from './components/KindGallery.vue'

// VitePress's built-in `themeConfig.footer` only renders on pages WITHOUT a
// sidebar (i.e. just the home page). To show a footer on every page we inject
// one through the `layout-bottom` slot, which renders site-wide.
export default {
  extends: DefaultTheme,
  // Function-kind shape glyphs, available in every markdown page.
  enhanceApp({ app }) {
    app.component('KindIcon', KindIcon)
    app.component('KindBanner', KindBanner)
    app.component('KindGallery', KindGallery)
  },
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'layout-bottom': () =>
        h('div', { class: 'global-footer' }, [
          '© 2026 🚜 Query Farm LLC — ',
          h(
            'a',
            { href: 'https://query.farm', target: '_blank', rel: 'noopener' },
            'https://query.farm',
          ),
        ]),
    })
  },
}
