/**
 * 文章正文轻量 Markdown 渲染工具
 *
 * 设计目标（面向未来 uni-app 5+App 打包）：
 * - 正文在编辑端就是纯文本 + Markdown 图片标记 ![](url)，
 *   H5 的 textarea 与 uni-app 的 <textarea> 行为一致，无富文本编辑器的手机兼容问题。
 * - 渲染端只支持「图片 / 加粗 / 换行」三个白名单语法，
 *   先整体 HTML 转义再替换标记，从根本上杜绝 XSS（不能直接 v-html 原文）。
 */

// 图片标记：![说明](http(s)地址)，地址只允许 http/https
const IMAGE_PATTERN = /!\[([^\]]*)\]\((https?:\/\/[^\s)]+)\)/g

/**
 * HTML 转义：& < > " '，保证用户输入不会被当成标签执行
 */
function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 将正文 Markdown 渲染为安全的 HTML 片段（用于 v-html）
 * @param {string} markdown 原始正文
 * @returns {string} 安全 HTML
 */
export function renderArticleContent(markdown) {
  if (!markdown) return ''
  let html = escapeHtml(markdown)
  // 图片：URL 中的 & 已被转义成 &amp;，浏览器解析属性时会自动还原，不影响访问
  html = html.replace(IMAGE_PATTERN, (match, alt, url) => {
    return `<img class="inline-article-img" src="${url}" alt="${alt || '文章图片'}" />`
  })
  // 加粗：**文字**
  html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  // 换行
  html = html.replace(/\r?\n/g, '<br>')
  return html
}

/**
 * 从正文中按出现顺序提取所有内嵌图片 URL（去重，不限制数量）。
 * 与后端 FireflyArticleServiceImpl.extractInlineImageUrls 保持一致，
 * 提交时回填 images 字段供列表页缩略图预览。
 * @param {string} markdown 原始正文
 * @returns {string[]} 图片 URL 列表
 */
export function extractInlineImageUrls(markdown) {
  if (!markdown) return []
  const urls = []
  let match
  IMAGE_PATTERN.lastIndex = 0
  while ((match = IMAGE_PATTERN.exec(markdown)) !== null) {
    const url = match[2]
    if (!urls.includes(url)) urls.push(url)
  }
  return urls
}
