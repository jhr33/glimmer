// 系统默认头像：8 款渐变笑脸，按 userId 取模稳定分配（同一用户默认头像固定）
const modules = import.meta.glob('../assets/avatars/default-*.svg', { eager: true, import: 'default' })

const DEFAULT_AVATARS = Array.from(
  { length: 8 },
  (_, i) => modules[`../assets/avatars/default-${i + 1}.svg`]
)

/**
 * 解析头像地址：有自定义头像用自定义；否则按 userId 稳定分配系统默认头像。
 * 匿名场景后端返回 avatarUrl=null，同样落到默认头像（跟随匿名模式的体现）。
 * @param {string|null} url 后端返回的头像URL
 * @param {number|string} userId 用户ID（用于默认头像分配）
 * @returns {string} 头像地址
 */
export function resolveAvatar(url, userId) {
  if (url) return url
  const id = Number(userId) || 0
  return DEFAULT_AVATARS[Math.abs(id) % DEFAULT_AVATARS.length]
}
