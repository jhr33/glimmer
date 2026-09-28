import { ref, onMounted, onUnmounted } from 'vue'
import { isNativeApp, isMobileWidth, MOBILE_BREAKPOINT } from '@/utils/device'

/**
 * 响应式设备检测 composable
 * 在组件中使用：
 *   const { isMobile, isNative } = useDevice()
 * 监听窗口尺寸变化，自动更新 isMobile
 */
export function useDevice() {
  // 首次同步判定，避免首屏从桌面布局闪到移动端布局
  const isNative = ref(isNativeApp())
  const isMobile = ref(isNative.value || isMobileWidth())

  function update() {
    isNative.value = isNativeApp()
    // 原生 App 强制移动端；浏览器端按屏幕宽度判定
    isMobile.value = isNative.value || isMobileWidth()
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update)
    // 部分 Android WebView 需要等 orientationchange
    window.addEventListener('orientationchange', update)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', update)
    window.removeEventListener('orientationchange', update)
  })

  return { isMobile, isNative, MOBILE_BREAKPOINT }
}
