/**
 * 设备/运行环境检测工具
 *
 * 判定规则：
 * 1. 原生 App（APK WebView）：始终返回移动端布局
 *    - 优先检测 Capacitor / Cordova 注入的全局对象
 *    - 其次通过构建变量 VITE_NATIVE_APP 标记
 * 2. 浏览器端：根据屏幕宽度判定（< 768px 视为移动端）
 *
 * 这样可以满足：「打包成 APK 显示手机版，打开网页显示网页版」
 * 同时手机浏览器访问时也能获得可用的响应式体验。
 */

/** 移动端断点（与 CSS 媒体查询保持一致） */
export const MOBILE_BREAKPOINT = 768

/**
 * 判断是否运行在原生 App WebView 中（APK 打包场景）
 */
export function isNativeApp() {
  if (typeof window === 'undefined') return false
  // Capacitor（Ionic 官方推荐的 Vue 打包方案）
  if (window.Capacitor && window.Capacitor.isNativePlatform) {
    return true
  }
  // Cordova
  if (window.cordova) {
    return true
  }
  // 构建时注入的原生标记（.env 或打包命令中设置 VITE_NATIVE_APP=true）
  if (import.meta.env && import.meta.env.VITE_NATIVE_APP === 'true') {
    return true
  }
  return false
}

/**
 * 判断当前视口宽度是否为移动端尺寸
 */
export function isMobileWidth() {
  if (typeof window === 'undefined') return false
  return window.innerWidth < MOBILE_BREAKPOINT
}

/**
 * 是否使用移动端布局
 * 规则：原生 App 强制移动端；浏览器端按屏幕宽度自适应
 */
export function useIsMobile() {
  return isNativeApp() || isMobileWidth()
}
