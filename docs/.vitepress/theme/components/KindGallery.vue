<script setup>
import KindIcon from './KindIcon.vue'
import { KINDS } from './kinds.js'

// Home-page strip of all five function kinds: each a linked card showing the
// shape glyph, the name, and its cardinality formula. Lays the whole family out
// side by side so the shared shape language is visible at a glance.
</script>

<template>
  <div class="kind-gallery">
    <a
      v-for="k in KINDS"
      :key="k.id"
      class="kind-gallery__card"
      :href="`/function-kinds/${k.id}`"
      :style="{ '--kind': k.color }"
    >
      <KindIcon :kind="k.id" :size="46" class="kind-gallery__glyph" />
      <span class="kind-gallery__name">{{ k.label }}</span>
      <code class="kind-gallery__formula">{{ k.formula }}</code>
    </a>
  </div>
</template>

<style scoped>
.kind-gallery {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin: 1.5rem 0 0.5rem;
}
.kind-gallery__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 14px 16px;
  text-align: center;
  text-decoration: none;
  border: 1px solid var(--vp-c-divider);
  border-radius: 14px;
  background: var(--vp-c-bg-soft);
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}
.kind-gallery__card:hover {
  border-color: var(--kind);
  transform: translateY(-3px);
  box-shadow: 0 10px 28px -18px var(--kind);
}
.kind-gallery__glyph {
  color: var(--kind);
}
.kind-gallery__name {
  font-weight: 700;
  font-size: 14px;
  color: var(--vp-c-text-1);
}
.kind-gallery__formula {
  max-width: 100%;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--kind);
  background: color-mix(in srgb, var(--kind) 12%, transparent);
  padding: 2px 8px;
  border-radius: 6px;
  /* Let long formulas (e.g. buffering's "stream → [state] → stream") wrap
     instead of overflowing the card at narrow widths. */
  white-space: normal;
  overflow-wrap: break-word;
  text-align: center;
}

/* Wrap to a flexible grid before the cards get too narrow. */
@media (max-width: 720px) {
  .kind-gallery {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }
}
</style>
