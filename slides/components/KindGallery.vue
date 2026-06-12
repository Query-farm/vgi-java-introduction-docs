<script setup>
import KindIcon from './KindIcon.vue'
import { KINDS } from './kinds.js'

// Slide-deck strip of all five function kinds: glyph + name + cardinality
// formula, laid side by side so the shared shape language reads at a glance.
// Mirrors the docs KindGallery, restyled for Slidev (no VitePress CSS vars).
</script>

<template>
  <div class="kind-gallery">
    <div
      v-for="k in KINDS"
      :key="k.id"
      class="kind-gallery__card"
      :style="{ '--kind': k.color }"
    >
      <KindIcon :kind="k.id" :size="40" class="kind-gallery__glyph" />
      <span class="kind-gallery__name">{{ k.label }}</span>
      <code class="kind-gallery__formula">{{ k.formula }}</code>
    </div>
  </div>
</template>

<style scoped>
.kind-gallery {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin: 2rem auto 0;
  flex-wrap: wrap;
  max-width: 920px;
}
.kind-gallery__card {
  flex: 1 1 0;
  min-width: 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 12px 14px;
  text-align: center;
  border: 1px solid #e2e8f0;
  border-top: 3px solid var(--kind);
  border-radius: 12px;
  background: #ffffff;
}
.kind-gallery__glyph {
  color: var(--kind);
}
.kind-gallery__name {
  font-weight: 700;
  font-size: 14px;
  color: #1e293b;
}
.kind-gallery__formula {
  font-size: 11px;
  font-weight: 600;
  color: var(--kind);
  background: color-mix(in srgb, var(--kind) 12%, transparent);
  padding: 2px 8px;
  border-radius: 6px;
  white-space: normal;
  overflow-wrap: break-word;
}
</style>
