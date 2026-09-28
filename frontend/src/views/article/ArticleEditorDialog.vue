<script setup>
import { reactive, ref, watch, nextTick, computed } from 'vue'
import { ElMessage, ElImageViewer } from 'element-plus'
import { Picture, Delete, View, Edit } from '@element-plus/icons-vue'
import { createArticle, updateArticle } from '@/api/article'
import { useUserStore } from '@/stores/user'
import { getOssSignature, uploadToOss } from '@/api/upload'
import { extractInlineImageUrls, renderArticleContent } from '@/utils/articleMarkdown'

const props = defineProps({
  visible: { type: Boolean, default: false },
  // 编辑时传入文章详情对象；发布新文章时传 null
  article: { type: Object, default: null }
})
const emit = defineEmits(['update:visible', 'success'])

const userStore = useUserStore()
const submitting = ref(false)
const formRef = ref(null)
// 正文 textarea 引用，用于光标定位
const contentInputRef = ref(null)
// 隐藏的文件选择框引用
const fileInputRef = ref(null)
// 正在上传中的图片数量（多张并发上传时展示进度）
const uploadingCount = ref(0)

// 频道常量：与后端白名单保持一致 chat 杂谈 / tech 技术笔记
const CHANNELS = [
  { value: 'chat', label: '杂谈' },
  { value: 'tech', label: '技术笔记' }
]

// 正文长度上限：与后端 @Size(max=50000) 对齐（图片标记也占字符）
const CONTENT_MAX_LENGTH = 50000
// 单张图片大小上限 5MB
const MAX_IMAGE_SIZE_MB = 5

// 编辑器当前模式：edit 编辑态 / preview 预览态。
// 不引入富文本（contenteditable）是为了保留 textarea 的纯文本特性，
// 保证后续迁移 uni-app 时 <textarea> 组件能无缝接管。
const editorMode = ref('edit')

const defaultForm = () => ({
  penName: userStore.userInfo?.nickname || '',
  channel: 'chat',
  title: '',
  content: '',
  tags: [],
  isPublic: true
})

const form = reactive(defaultForm())

const rules = {
  penName: [{ required: true, message: '请填写笔名', trigger: 'blur' }],
  channel: [{ required: true, message: '请选择频道', trigger: 'change' }],
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [{ required: true, message: '正文不能为空', trigger: 'blur' }]
}

// 记录 textarea 最后一次光标位置。
// H5 用原生 selectionStart/selectionEnd；
// 迁移 uni-app 时改为监听 <textarea> 的 @cursor 事件保存 cursor，插入后用 :selection-start/end 恢复。
let lastCursorPos = 0

function syncCursor() {
  const el = contentInputRef.value?.textarea
  if (el) lastCursorPos = el.selectionStart
}

// 弹窗打开时初始化表单：编辑模式回填，发布模式给笔名预填用户昵称
watch(
  () => props.visible,
  (val) => {
    if (!val) return
    if (props.article) {
      form.penName = props.article.penName || ''
      form.channel = props.article.channel || 'chat'
      form.title = props.article.title || ''
      form.content = props.article.content || ''
      form.tags = Array.isArray(props.article.tags) ? [...props.article.tags] : []
      form.isPublic = props.article.isPublic !== false
    } else {
      Object.assign(form, defaultForm())
    }
    lastCursorPos = form.content.length
    // 每次打开弹窗默认回到编辑态
    editorMode.value = 'edit'
    formRef.value?.clearValidate()
  }
)

function handleClose() {
  emit('update:visible', false)
}

// ============================ 编辑/预览切换 ============================

/**
 * 切换编辑/预览模式。
 * 上传中禁止切到预览，避免渲染时机错乱。
 * 切回编辑时恢复光标位置。
 * uni-app 迁移点：<textarea> 不支持 v-if 切换，改为 v-show 或两个组件并存。
 */
function switchEditorMode(mode) {
  if (mode === 'preview' && uploadingCount.value > 0) {
    ElMessage.warning('有图片正在上传，请等待上传完成再预览')
    return
  }
  editorMode.value = mode
  if (mode === 'edit') {
    nextTick(() => {
      const el = contentInputRef.value?.textarea
      if (el) {
        el.focus()
        const pos = Math.min(lastCursorPos, form.content.length)
        el.setSelectionRange(pos, pos)
      }
    })
  }
}

// 预览态渲染后的安全 HTML（已在工具函数内做 HTML 转义，仅放行 img/strong/br）
const renderedPreview = computed(() => renderArticleContent(form.content))

// 预览区点击图片：放大查看
function handlePreviewClick(event) {
  if (event.target.tagName === 'IMG') {
    const src = event.target.getAttribute('src')
    const idx = inlineImages.value.indexOf(src)
    openImageViewer(idx >= 0 ? idx : 0)
  }
}

// ============================ 图片缩略图栏 ============================

// 从正文 Markdown 标记提取的图片 URL 列表（按出现顺序，去重）
const inlineImages = computed(() => extractInlineImageUrls(form.content))

// 图片大图预览器
const imageViewerVisible = ref(false)
const imageViewerIndex = ref(0)
function openImageViewer(index) {
  if (!inlineImages.value.length) return
  imageViewerIndex.value = index
  imageViewerVisible.value = true
}

/**
 * 从正文中移除指定 URL 的图片标记。
 * marker 形式：![任意说明](url)，删除时连同紧邻的多余换行符一起清理，
 * 防止删除后留下连续空行影响排版。
 */
function removeInlineImage(url) {
  if (!url) return
  const escaped = escapeRegExp(url)
  // 严格匹配：连带前后多余换行（避免每次删除都把段落间距压掉）
  const strictPattern = new RegExp(`\\n*!\\[[^\\]]*\\]\\(${escaped}\\)\\n*`, 'g')
  let matched = false
  let newContent = form.content.replace(strictPattern, (match) => {
    if (matched) return match
    matched = true
    // 保留段落分隔：用两个换行替代原 marker 占位
    return '\n\n'
  })
  if (!matched) return
  // 折叠 3 个以上连续换行为 2 个
  newContent = newContent.replace(/\n{3,}/g, '\n\n').replace(/^\n+/, '').replace(/\n+$/, '')
  form.content = newContent
  ElMessage.success('已从正文移除图片')
}

function escapeRegExp(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// ============================ 图片上传 ============================

/**
 * 点击「插入图片」：打开系统选图（允许多选，不限制张数）。
 * uni-app 迁移点：替换为 uni.chooseImage({ count: 99 })，
 * 拿到 tempFilePaths 后逐个走 uni.uploadFile（需换成小程序/APP 直传签名，或由后端代理转发）。
 */
function triggerChooseImage() {
  if (editorMode.value === 'preview') {
    // 预览态点击插入图片，先切回编辑态再选图，保证光标可定位
    switchEditorMode('edit')
  }
  fileInputRef.value?.click()
}

async function handleFileChange(event) {
  const files = Array.from(event.target.files || [])
  // 清空 input 的 value，否则选同一张图不会再次触发 change
  event.target.value = ''
  if (!files.length) return

  // 上传前校验：仅图片、单张不超过 5MB
  for (const file of files) {
    if (!file.type.startsWith('image/')) {
      ElMessage.error(`「${file.name}」不是图片文件，已跳过`)
      continue
    }
    if (file.size / 1024 / 1024 >= MAX_IMAGE_SIZE_MB) {
      ElMessage.error(`「${file.name}」超过 ${MAX_IMAGE_SIZE_MB}MB，已跳过`)
      continue
    }
    // 不 await：多张图片并发上传，逐张在光标处插入，互不阻塞
    uploadAndInsert(file)
  }
}

/**
 * 上传单张图片并把 Markdown 标记插入正文光标位置
 */
async function uploadAndInsert(file) {
  uploadingCount.value += 1
  try {
    const signRes = await getOssSignature('article')
    const url = await uploadToOss(file, signRes.data)
    // 图片独占一行，上下各留空行，保证移动端渲染和文本可读性
    const marker = `\n\n![](${url})\n\n`
    insertTextAtCursor(marker)
    ElMessage.success(`「${file.name}」已插入正文`)
  } catch (e) {
    ElMessage.error(e.message || `「${file.name}」上传失败`)
  } finally {
    uploadingCount.value -= 1
  }
}

/**
 * 在 textarea 光标处插入文本；上传期间焦点可能已丢失，使用最近记录的光标位置。
 * uni-app 迁移点：改用 this.createSelectorQuery() 或 @cursor 记录位置后重设 selection。
 */
function insertTextAtCursor(text) {
  const el = contentInputRef.value?.textarea
  const pos = document.activeElement === el ? el.selectionStart : lastCursorPos
  const before = form.content.slice(0, pos)
  const after = form.content.slice(el && document.activeElement === el ? el.selectionEnd : pos)
  form.content = before + text + after
  // 超长保护：超出上限截断尾部（正常不会触发，因为后端同样限制 50000）
  if (form.content.length > CONTENT_MAX_LENGTH) {
    form.content = form.content.slice(0, CONTENT_MAX_LENGTH)
    ElMessage.warning('正文已达长度上限')
  }
  const newPos = Math.min((before + text).length, form.content.length)
  lastCursorPos = newPos
  nextTick(() => {
    if (el) {
      el.focus()
      el.setSelectionRange(newPos, newPos)
    }
  })
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      // 从正文提取内嵌图片 URL 回填 images（不限制张数），供列表页缩略图预览
      const images = extractInlineImageUrls(form.content)
      const payload = {
        penName: form.penName.trim(),
        channel: form.channel,
        title: form.title.trim(),
        content: form.content.trim(),
        tags: form.tags,
        images,
        isPublic: form.isPublic
      }
      if (props.article) {
        await updateArticle(props.article.id, payload)
        ElMessage.success('文章已更新')
      } else {
        await createArticle(payload)
        ElMessage.success('发布成功')
      }
      emit('success')
      emit('update:visible', false)
    } catch (e) {
      // 错误提示已由 request.js 拦截器统一处理
    } finally {
      submitting.value = false
    }
  })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="article ? '编辑文章' : '写点什么'"
    width="720px"
    top="6vh"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="笔名（署名仅跟随本篇文章）" prop="penName">
            <el-input v-model="form.penName" maxlength="50" show-word-limit placeholder="给自己起个笔名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="发布到频道" prop="channel">
            <el-radio-group v-model="form.channel">
              <el-radio-button v-for="c in CHANNELS" :key="c.value" :value="c.value">
                {{ c.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="给文章起个标题" />
      </el-form-item>

      <el-form-item prop="content">
        <template #label>
          <div class="content-label">
            <span>正文</span>
            <div class="content-toolbar">
              <el-radio-group
                :model-value="editorMode"
                size="small"
                @change="switchEditorMode"
              >
                <el-radio-button value="edit">
                  <el-icon class="mode-icon"><Edit /></el-icon>
                  <span class="mode-text">编辑</span>
                </el-radio-button>
                <el-radio-button value="preview">
                  <el-icon class="mode-icon"><View /></el-icon>
                  <span class="mode-text">预览</span>
                </el-radio-button>
              </el-radio-group>
              <el-button
                size="small"
                :icon="Picture"
                :loading="uploadingCount > 0"
                @click="triggerChooseImage"
              >
                {{ uploadingCount > 0 ? `图片上传中 ${uploadingCount}` : '插入图片' }}
              </el-button>
            </div>
          </div>
        </template>

        <!-- 编辑态：纯文本 textarea，保留以兼容 uni-app 迁移 -->
        <el-input
          v-if="editorMode === 'edit'"
          ref="contentInputRef"
          v-model="form.content"
          type="textarea"
          :rows="14"
          :maxlength="CONTENT_MAX_LENGTH"
          show-word-limit
          placeholder="写下你想分享的内容……点击上方「插入图片」可把图片插到光标位置，图片数量不限"
          resize="none"
          @keyup="syncCursor"
          @click="syncCursor"
          @select="syncCursor"
          @blur="syncCursor"
        />
        <!-- 预览态：渲染后的正文（图文混排真实效果，已防 XSS） -->
        <div
          v-else
          class="content-preview"
          v-html="renderedPreview"
          @click="handlePreviewClick"
        />

        <!-- 图片缩略图栏：编辑/预览态都显示，直观管理已插入图片 -->
        <div v-if="inlineImages.length" class="inline-images-bar">
          <div class="bar-title">
            已插入图片（{{ inlineImages.length }}张） · 点击缩略图预览大图 · 点击右上角 × 从正文移除
          </div>
          <div class="thumb-list">
            <div
              v-for="(url, idx) in inlineImages"
              :key="url"
              class="thumb-item"
            >
              <img :src="url" :alt="`图片${idx + 1}`" @click="openImageViewer(idx)" />
              <span class="remove-btn" title="从正文移除该图片" @click.stop="removeInlineImage(url)">×</span>
            </div>
          </div>
        </div>

        <!-- 隐藏的文件选择框：multiple 支持一次选多张 -->
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*"
          multiple
          style="display: none"
          @change="handleFileChange"
        >
        <div class="editor-tip">
          图片以 Markdown 标记保存在正文中，发布后按插入位置显示；可切换「预览」实时查看渲染效果
        </div>
      </el-form-item>

      <el-row :gutter="16" align="middle">
        <el-col :span="14">
          <el-form-item label="标签（最多5个，回车添加）" style="margin-bottom: 0">
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="如：Java、面试、随笔"
              style="width: 100%"
            >
              <el-option v-for="t in form.tags" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="是否公开" style="margin-bottom: 0">
            <el-switch
              v-model="form.isPublic"
              active-text="公开（广场可见）"
              inactive-text="私密（仅自己可见）"
              inline-prompt
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 图片大图预览器（共用一个，编辑/预览态都可触发） -->
    <el-image-viewer
      v-if="imageViewerVisible"
      :url-list="inlineImages"
      :initial-index="imageViewerIndex"
      @close="imageViewerVisible = false"
    />

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ article ? '保存修改' : '发布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.content-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.content-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.mode-icon {
  margin-right: 2px;
  vertical-align: -2px;
}
.mode-text {
  vertical-align: middle;
}
/* 预览区：高度与编辑态 textarea 大致一致，便于视觉切换 */
.content-preview {
  min-height: 332px;
  max-height: 460px;
  overflow-y: auto;
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  line-height: 1.7;
  color: #303133;
  background: #fff;
  word-break: break-word;
}
.content-preview :deep(.inline-article-img) {
  max-width: 100%;
  border-radius: 6px;
  margin: 8px 0;
  display: block;
}
.content-preview :deep(strong) {
  font-weight: 700;
}
/* 图片缩略图栏 */
.inline-images-bar {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
}
.bar-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  line-height: 1.5;
}
.thumb-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.thumb-item {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  background: #fff;
}
.thumb-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  display: block;
}
.thumb-item img:hover {
  opacity: 0.85;
}
.remove-btn {
  position: absolute;
  top: 0;
  right: 0;
  width: 18px;
  height: 18px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  user-select: none;
}
.remove-btn:hover {
  background: rgba(245, 108, 108, 0.9);
}
.editor-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
