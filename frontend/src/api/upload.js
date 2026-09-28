import request from '@/utils/request'

/**
 * 获取 OSS 直传签名
 * @param {string} scene 上传场景：article 文章图片 / campfire 篝火图片
 * @returns {Promise<{host, policy, signature, xOssCredential, xOssDate, xOssSignatureVersion, dir, expire}>}
 */
export function getOssSignature(scene) {
  return request({ url: '/upload/signature', method: 'get', params: { scene } })
}

/**
 * 前端直传文件到 OSS（PostObject V4 签名）
 * @param {File} file 文件对象
 * @param {Object} signature 后端返回的签名信息
 * @returns {Promise<string>} 上传成功后的文件 URL
 */
export async function uploadToOss(file, signature) {
  try {
    // 注意：后端字段 xOssSignatureVersion 经 Jackson 序列化后实际返回 xossSignatureVersion（全小写 xoss）
    const { host, policy, signature: sign, xossCredential, xossDate, xossSignatureVersion, dir } = signature

    // 生成唯一文件名：dir + 时间戳 + 随机数 + 原始扩展名
    const ext = file.name.substring(file.name.lastIndexOf('.'))
    const timestamp = Date.now()
    const random = Math.random().toString(36).substring(2, 8)
    const key = `${dir}${timestamp}_${random}${ext}`

    const formData = new FormData()
    formData.append('key', key)
    formData.append('policy', policy)
    formData.append('x-oss-signature-version', xossSignatureVersion)
    formData.append('x-oss-credential', xossCredential)
    formData.append('x-oss-date', xossDate)
    formData.append('x-oss-signature', sign)
    formData.append('Content-Type', file.type)
    formData.append('file', file)

    const response = await fetch(host, {
      method: 'POST',
      body: formData
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`OSS上传失败: ${response.status} ${errorText}`)
    }

    // OSS PostObject 成功返回 204，拼接文件访问 URL
    return `${host}/${key}`
  } catch (e) {
    // fetch 网络错误（如 CORS 未配置、域名不可达）统一给出可排查的提示
    if (e instanceof TypeError || /Failed to fetch|NetworkError/i.test(e.message)) {
      throw new Error('上传失败：请检查 OSS Bucket 的 CORS 跨域配置或网络连接')
    }
    throw e
  }
}
