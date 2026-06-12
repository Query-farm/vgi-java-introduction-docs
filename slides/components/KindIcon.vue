<script setup>
import { computed } from 'vue'
import { KIND_COLORS } from './kinds.js'

// Inline shape-glyph for a function kind — copied verbatim from
// docs/.vitepress/theme/components/KindIcon.vue. Filled "row" bars, stroked
// arrows/buffer box, a filled "value" dot, in the kind's brand colour. Each glyph
// reads left-to-right as a cardinality transform, so the five read as one family.
const props = defineProps({
  kind: { type: String, required: true },
  size: { type: [Number, String], default: 40 },
})

const S = 'stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"'
const bar = (x, y, w) => `<rect x="${x}" y="${y}" width="${w}" height="6" rx="3" fill="currentColor"/>`
const arrow = (x1, x2) =>
  `<line x1="${x1}" y1="20" x2="${x2 - 3}" y2="20" ${S}/>` +
  `<polyline points="${x2 - 8},15.5 ${x2 - 2.5},20 ${x2 - 8},24.5" fill="none" ${S}/>`
const fmark = (x, y) =>
  `<text x="${x}" y="${y}" text-anchor="middle" fill="currentColor" font-style="italic" font-weight="700" font-size="13" font-family="Georgia,serif">ƒ</text>`

const GLYPHS = {
  // 1 row → 1 value: same cardinality, a per-row transform (ƒ)
  scalar: {
    viewBox: '0 0 64 40',
    body: bar(6, 17, 16) + arrow(26, 40) + fmark(33, 10) + bar(43, 17, 16),
  },
  // args → N rows: one source block fans out into a stack
  table: {
    viewBox: '0 0 66 40',
    body:
      '<rect x="7" y="11" width="14" height="18" rx="3" fill="currentColor"/>' +
      arrow(26, 40) +
      bar(44, 8, 18) + bar(44, 17, 18) + bar(44, 26, 18),
  },
  // N rows → M rows: stack in, stack out (ƒ)
  'table-in-out': {
    viewBox: '0 0 66 40',
    body:
      bar(4, 8, 16) + bar(4, 17, 16) + bar(4, 26, 16) +
      arrow(26, 40) + fmark(33, 8) +
      bar(45, 8, 16) + bar(45, 17, 16) + bar(45, 26, 16),
  },
  // N rows → 1 value: the converging funnel
  aggregate: {
    viewBox: '0 0 66 40',
    body:
      bar(4, 8, 18) + bar(4, 17, 18) + bar(4, 26, 18) +
      '<path d="M24,11 L46,20 M24,20 L46,20 M24,29 L46,20" fill="none" stroke="currentColor" stroke-width="1.7" opacity="0.5"/>' +
      '<circle cx="52" cy="20" r="8" fill="currentColor"/>',
  },
  // stream → [held state] → stream: the buffer box is the tell
  buffering: {
    viewBox: '0 0 102 40',
    body:
      bar(2, 12, 13) + bar(2, 22, 13) +
      arrow(19, 32) +
      '<rect x="35" y="5" width="30" height="30" rx="5" fill="none" stroke="currentColor" stroke-width="2.6"/>' +
      bar(41, 14, 18) + bar(41, 23, 18) +
      arrow(70, 85) +
      bar(88, 12, 13) + bar(88, 22, 13),
  },
}

const glyph = computed(() => GLYPHS[props.kind])
const color = computed(() => KIND_COLORS[props.kind])
</script>

<template>
  <svg
    class="kind-icon"
    :viewBox="glyph.viewBox"
    :height="size"
    overflow="visible"
    :style="{ color }"
    role="img"
    :aria-label="`${kind} function shape`"
    v-html="glyph.body"
  />
</template>

<style scoped>
.kind-icon {
  display: inline-block;
  width: auto;
  vertical-align: middle;
}
</style>
