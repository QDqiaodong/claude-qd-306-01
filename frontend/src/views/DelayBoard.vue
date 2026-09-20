<template>
  <div class="pane">
    <div class="head">
      <h2>延误桌</h2>
      <span class="sub">过了交期还没印完的单子都摊在这：交期、纸、版一张桌读全。
        改交期、补纸、修版之后，桌上的天数和加急跟着台账走</span>
      <span v-if="loadedAt" class="at">看的是 {{ loadedAt }} 的台账</span>
      <button class="ghost" @click="load">再看一眼</button>
    </div>

    <div class="chips" v-if="items.length">
      <span class="chip">桌上 {{ items.length }} 张</span>
      <span class="chip hot" v-if="count.urgent">加急 {{ count.urgent }} 张</span>
      <span class="chip paper" v-if="count.paper">纸拖的 {{ count.paper }} 张</span>
      <span class="chip plate" v-if="count.plate">版拖的 {{ count.plate }} 张</span>
    </div>

    <div class="table">
      <div class="row head-row">
        <span>工单号</span><span>客户</span><span>状态</span><span>交期</span>
        <span class="r">超期</span><span>急缓</span><span>谁拖的</span><span>用纸</span><span>印版</span>
      </div>
      <div v-for="r in items" :key="r.jobId" class="row" :class="{ hot: r.urgent }">
        <span class="mono">{{ r.jobNo }}</span>
        <span>{{ r.clientName }}</span>
        <span class="state" :class="r.jobState === '印刷中' ? 'doing' : ''">{{ r.jobState }}</span>
        <span class="dim">{{ r.dueDate }}</span>
        <span class="r days" :class="{ hot: r.urgent }">{{ r.overdueDays }} 天</span>
        <span><i class="tag" :class="r.urgent ? 'urgent' : 'plain'">{{ r.urgent ? '加急' : '普通' }}</i></span>
        <span><i v-if="r.dragBy" class="tag" :class="dragTone(r)">{{ dragText(r) }}</i><span v-else class="dim">—</span></span>
        <span>
          {{ r.paperName || '未指定' }}
          <i v-if="r.paperState" class="mini" :class="{ bad: r.paperOut }">{{ r.paperState }}</i>
        </span>
        <span>
          {{ r.plateCode || '未指定' }}
          <i v-if="r.plateState" class="mini" :class="{ worn: r.plateWorn }">{{ r.plateState }}</i>
        </span>
      </div>
      <div v-if="!items.length" class="empty">桌上是空的——没有过了交期还没印完的单子</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { delayBoardApi } from '../api'
import { on } from '../utils/bus'

const items = ref([])
const loadedAt = ref('')

const count = computed(() => ({
  urgent: items.value.filter((r) => r.urgent).length,
  paper: items.value.filter((r) => r.paperOut).length,
  plate: items.value.filter((r) => r.plateWorn).length
}))

async function load() {
  items.value = await delayBoardApi.board()
  loadedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}
function dragText(r) {
  return r.dragBy === '纸+版' ? '纸+版都拖' : r.dragBy + '拖的'
}
function dragTone(r) {
  return r.dragBy === '纸' ? 'paper' : r.dragBy === '版' ? 'plate' : 'both'
}

// 仓管改纸、机长改版、调度改交期，任何一处动了台账，这张桌跟着重算
const off = on('data-changed', load)
onUnmounted(off)
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.head h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #8d92a8; font-size: 12px; }
.at { font-size: 12px; color: #b6bad0; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 7px; padding: 6px 14px; font-size: 12px; cursor: pointer; }
.chips { display: flex; gap: 8px; margin-bottom: 14px; }
.chip { background: #f2f3f9; color: #71758c; border-radius: 14px; padding: 3px 12px; font-size: 12px; }
.chip.hot { background: #fbe9e7; color: #c0392b; font-weight: 600; }
.chip.paper { background: #fdf0e3; color: #b4761f; }
.chip.plate { background: #efe7f7; color: #6d4a9e; }
.table { background: #fff; border: 1px solid #e9ebf5; border-radius: 12px; overflow: hidden; }
.row { display: grid; grid-template-columns: 96px 1.1fr 64px 100px 64px 64px 96px 1.2fr 1.2fr;
  gap: 8px; align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f4fa; font-size: 13px; }
.row.hot { background: #fdf6f5; box-shadow: inset 3px 0 0 #d9534f; }
.head-row { background: #f7f8fc; color: #8d92a8; font-size: 12px; box-shadow: none; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #8d92a8; }
.r { text-align: right; }
.dim { color: #8d92a8; font-size: 12px; }
.state { font-size: 12px; }
.state.doing { color: var(--el-color-primary-dark-2); font-weight: 600; }
.days { font-weight: 600; color: #71758c; }
.days.hot { color: #c0392b; }
.tag { font-style: normal; font-size: 12px; border-radius: 10px; padding: 2px 10px; white-space: nowrap; }
.tag.urgent { background: #fbe9e7; color: #c0392b; font-weight: 600; }
.tag.plain { background: #f3f4fa; color: #8d92a8; }
.tag.paper { background: #fdf0e3; color: #b4761f; }
.tag.plate { background: #efe7f7; color: #6d4a9e; }
.tag.both { background: #fbe9e7; color: #c0392b; }
.mini { font-style: normal; font-size: 11px; border-radius: 8px; padding: 1px 7px; margin-left: 4px;
  background: #f3f4fa; color: #8d92a8; white-space: nowrap; }
.mini.bad { background: #fbe9e7; color: #c0392b; }
.mini.worn { background: #fdf0e3; color: #b4761f; }
.empty { padding: 26px; text-align: center; color: #b6bad0; font-size: 13px; }
</style>
