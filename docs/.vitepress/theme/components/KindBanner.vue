<script setup>
import { computed } from 'vue'
import KindIcon from './KindIcon.vue'
import { KIND_META } from './kinds.js'

// The header card shown at the top of each function-kind page: the shape glyph
// alongside its cardinality formula and a one-line gloss, tinted in the kind's
// brand colour. Pairs the picture with the words so the shape sticks.
const props = defineProps({ kind: { type: String, required: true } })

const copy = computed(() => KIND_META[props.kind])
const color = computed(() => copy.value.color)
</script>

<template>
  <div class="kind-banner" :style="{ '--kind': color }">
    <KindIcon :kind="kind" :size="48" class="kind-banner__glyph" />
    <div class="kind-banner__text">
      <code class="kind-banner__formula">{{ copy.formula }}</code>
      <p class="kind-banner__desc">{{ copy.desc }}</p>
    </div>
  </div>
</template>

<style scoped>
.kind-banner {
  display: flex;
  align-items: center;
  gap: 20px;
  margin: 1.25rem 0 2rem;
  padding: 16px 20px;
  border: 1px solid var(--vp-c-divider);
  border-left: 4px solid var(--kind);
  border-radius: 12px;
  background: color-mix(in srgb, var(--kind) 6%, var(--vp-c-bg));
}
.kind-banner__glyph {
  flex: none;
  color: var(--kind);
}
.kind-banner__text {
  min-width: 0;
}
.kind-banner__formula {
  display: inline-block;
  font-weight: 700;
  font-size: 13px;
  color: var(--kind);
  background: color-mix(in srgb, var(--kind) 13%, transparent);
  padding: 2px 9px;
  border-radius: 6px;
}
.kind-banner__desc {
  margin: 0.5rem 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--vp-c-text-2);
}
</style>
