<script setup lang="ts">
import LeftSidebar from './LeftSidebar.vue'
import RightThoughtPanel from '../thought/RightThoughtPanel.vue'
</script>

<template>
  <div class="app-shell" data-test="app-shell">
    <LeftSidebar />

    <main class="workbench-main" aria-label="AI learning workbench">
      <slot />
    </main>

    <RightThoughtPanel />
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--shell-sidebar-width) minmax(0, 1fr) var(--shell-right-width);
  width: 100%;
  height: 100vh;
  max-width: 100vw;
  overflow: hidden;
  color: var(--color-text);
  background: var(--color-bg);
}

.workbench-main {
  min-width: 0;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(248, 250, 252, 0.92)),
    var(--color-bg);
}

:deep(.left-sidebar) {
  width: var(--shell-sidebar-width);
  min-width: var(--shell-sidebar-width);
  height: 100vh;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: 1px solid var(--color-border);
}

:deep(.right-thought-panel) {
  width: var(--shell-right-width);
  min-width: var(--shell-right-width);
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  border-left: 1px solid var(--color-border);
}

:deep(.right-thought-panel.collapsed) {
  width: 88px;
  min-width: 88px;
}

@media (max-width: 1100px) {
  .app-shell {
    grid-template-columns: var(--shell-sidebar-width) minmax(0, 1fr);
  }

  :deep(.right-thought-panel) {
    display: none;
  }
}

@media (max-width: 768px) {
  .app-shell {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
  }

  .workbench-main {
    height: auto;
    min-height: 0;
  }

  :deep(.left-sidebar) {
    width: 100%;
    min-width: 0;
    height: auto;
    max-height: 46vh;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
}
</style>
