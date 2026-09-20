<template>
  <div class="pane">
    <div class="head">
      <h2>延误桌</h2>
      <span class="sub">
        交期、纸、版在这一张桌上一起算：过了交期还没印完的单自动上桌，已完成的不进。
        逾期 &gt; 3 天加急；指定纸缺货或指定版已磨损，只过一天也加急，并写明谁拖的。
      </span>
      <button class="ghost" @click="load">刷新</button>
    </div>

    <div class="summary">
      <div class="stat">
        <b>{{ items.length }}</b>
        <span>张在桌</span>
      </div>
      <div class="stat hot">
        <b>{{ urgentCount }}</b>
        <span>张加急</span>
      </div>
      <div class="asof">今天 {{ today }} · 天数与加急每次刷新都按眼下交期、纸张、印版状态现算，不落死</div>
    </div>

    <div class="table">
      <div class="row head-row">
        <span>工单号</span><span>客户</span><span>工序</span><span>交期</span>
        <span class="c">逾期</span><span>用纸</span><span>印版</span>
        <span>延误原因</span><span>操作</span>
      </div>
      <div v-for="r in items" :key="r.id" class="row" :class="{ hotrow: r.urgent }">
        <span class="mono">{{ r.jobNo }}</span>
        <span>{{ r.clientName }}</span>
        <span class="state" :class="r.jobState === '印刷中' ? 'doing' : ''">{{ r.jobState }}</span>
        <span class="dim">{{ r.dueDate }}</span>
        <span class="c"><b :class="r.urgent ? 'hottext' : ''">{{ r.overdueDays }}</b> 天</span>
        <span>
          <router-link class="reslink" to="/papers">
            {{ r.paperName || '未指定' }}
            <i v-if="r.paperBlocked" class="badge bad">缺货</i>
            <i v-else-if="r.paperState" class="badge" :class="r.paperState === '紧张' ? 'warn' : 'ok'">
              {{ r.paperState }}
            </i>
          </router-link>
        </span>
        <span>
          <router-link class="reslink" to="/plates">
            {{ r.plateCode || '未指定' }}
            <i v-if="r.plateBlocked" class="badge bad">已磨损</i>
            <i v-else-if="r.plateState" class="badge" :class="r.plateState === '已作废' ? 'stale' : 'ok'">
              {{ r.plateState }}
            </i>
          </router-link>
        </span>
        <span class="reason">
          <i class="urgent-tag" v-if="r.urgent">加急</i>
          <i class="chip-reason paper" v-if="r.paperBlocked">纸拖</i>
          <i class="chip-reason plate" v-if="r.plateBlocked">版拖</i>
          <span class="reason-text">{{ reasonText(r) }}</span>
        </span>
        <span><button class="ghost small" @click="openDue(r)">改交期</button></span>
      </div>
      <div v-if="!items.length" class="empty">桌上是空的：没有过了交期还没印完的单</div>
    </div>

    <el-dialog v-model="dialog" title="改交期" width="380px">
      <p class="dlg-line">工单 <b>{{ form.jobNo }}</b> · {{ form.clientName }}</p>
      <div class="fr">
        <label>新交期</label>
        <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD"
                        placeholder="选个日子" style="flex:1" />
      </div>
      <p class="note">交期改成未来的日子，这条会立刻从桌上拿掉；天数和加急跟着新交期重算。</p>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { delayDeskApi, jobApi } from '../api'
import { emit, on } from '../utils/bus'

const items = ref([])
const dialog = ref(false)
const form = ref({ id: null, jobNo: '', clientName: '', dueDate: '' })

// 展示用「今天」：按本地时区拼，避免 toISOString 在夜里差出一天（后台以服务器日期为准）。
const today = (() => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
})()
const urgentCount = computed(() => items.value.filter((r) => r.urgent).length)

async function load() {
  items.value = await delayDeskApi.list()
}

// 加急标由后台按天数/纸/版给；桌上的正文只留人和物的说法，不把「超过 3 天」重复一遍。
function reasonText(r) {
  const parts = []
  if (r.paperBlocked) parts.push(`用纸「${r.paperName}」(${r.paperCode})缺货`)
  if (r.plateBlocked) parts.push(`印版 ${r.plateCode}「${r.plateName}」已磨损`)
  if (!parts.length) return '无纸/版异常，按交期逾期跟进'
  return parts.join('；') + '，先解这头再谈上机'
}

function openDue(r) {
  form.value = { id: r.id, jobNo: r.jobNo, clientName: r.clientName, dueDate: r.dueDate }
  dialog.value = true
}

async function submit() {
  if (!form.value.dueDate) {
    ElMessage.warning('得选一个新交期')
    return
  }
  try {
    await jobApi.save(form.value.id, { dueDate: form.value.dueDate })
    dialog.value = false
    await load()
    emit('data-changed', 'job')
    ElMessage.success('交期改好了，桌上已按新日子重算')
  } catch (e) { ElMessage.error(e.message) }
}

// 仓管在纸张页补了货（paper）、机长在印版页修了版（plate）、调度在工单页动了单（job），
// 这张桌都跟着刷——三个来源汇到一张桌，而不是各刷各的。
const off = on('data-changed', () => { load() })
onUnmounted(off)
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.head h2 { margin: 0; font-size: 20px; }
.sub { flex: 1; color: #8d92a8; font-size: 12px; line-height: 1.6; }
.summary { display: flex; align-items: center; gap: 14px; background: #fff;
  border: 1px solid #e9ebf5; border-radius: 12px; padding: 12px 18px; margin-bottom: 14px; }
.stat { display: flex; align-items: baseline; gap: 6px; }
.stat b { font-size: 22px; color: var(--el-color-primary-dark-2); }
.stat span { font-size: 12px; color: #8d92a8; }
.stat.hot b { color: #c0392b; }
.asof { margin-left: auto; font-size: 12px; color: #b6bad0; }
.table { background: #fff; border: 1px solid #e9ebf5; border-radius: 12px; overflow: hidden; }
.row { display: grid; grid-template-columns: 86px 1.1fr 64px 96px 64px 1.15fr 1.15fr 1.7fr 78px;
  gap: 8px; align-items: center; padding: 11px 14px; border-bottom: 1px solid #f3f4fa; font-size: 13px; }
.head-row { background: #f7f8fc; color: #8d92a8; font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; color: #8d92a8; }
.c { text-align: center; font-size: 12px; color: #71758c; }
.dim { color: #8d92a8; font-size: 12px; }
.state { font-size: 12px; }
.state.doing { color: var(--el-color-primary-dark-2); font-weight: 600; }
.hottext { color: #c0392b; font-size: 14px; }
/* 加急是整行的事：左侧压一条红杠 + 浅红底，不是只把交期格子涂红。 */
.hotrow { background: #fff6f5; box-shadow: inset 3px 0 0 #d9534f; }
.reslink { display: inline-flex; align-items: center; gap: 6px; color: inherit;
  text-decoration: none; }
.reslink:hover { color: var(--el-color-primary-dark-2); }
.badge { font-style: normal; font-size: 11px; border-radius: 9px; padding: 1px 8px; }
.badge.ok { background: #e6f4ec; color: #2e7d4f; }
.badge.warn { background: #fdf0e3; color: #b4761f; }
.badge.bad { background: #fde8e6; color: #c0392b; font-weight: 600; }
.badge.stale { background: #f3f4fa; color: #8d92a8; }
.reason { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.urgent-tag { font-style: normal; font-size: 11px; font-weight: 700; color: #fff;
  background: #d9534f; border-radius: 4px; padding: 2px 8px; }
.chip-reason { font-style: normal; font-size: 11px; border-radius: 9px; padding: 1px 8px; }
.chip-reason.paper { background: #fde8e6; color: #c0392b; }
.chip-reason.plate { background: #f7e8f6; color: #a03a92; }
.reason-text { font-size: 12px; color: #8d92a8; }
.ghost { background: #fff; border: 1px solid var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2);
  border-radius: 7px; padding: 6px 14px; font-size: 12px; cursor: pointer; }
.ghost.small { padding: 4px 10px; }
.empty { padding: 30px; text-align: center; color: #b6bad0; font-size: 13px; }
.dlg-line { font-size: 13px; margin: 0 0 14px; }
.fr { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.fr label { width: 56px; text-align: right; font-size: 13px; color: #71758c; }
.note { font-size: 12px; color: #b6bad0; margin: 8px 0 0 66px; line-height: 1.6; }
</style>
