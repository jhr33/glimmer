import request from '@/utils/request'

// 广场文章列表（游客可访问，仅返回公开文章）
export function getArticles(params) {
  return request({ url: '/articles', method: 'get', params })
}

// 文章详情（公开文章游客可访问，私密文章仅作者）
export function getArticle(id) {
  return request({ url: `/articles/${id}`, method: 'get' })
}

// 发布文章（笔名随文章署名）
export function createArticle(data) {
  return request({ url: '/articles', method: 'post', data })
}

// 编辑文章（仅作者）
export function updateArticle(id, data) {
  return request({ url: `/articles/${id}`, method: 'put', data })
}

// 删除文章（仅作者）
export function deleteArticle(id) {
  return request({ url: `/articles/${id}`, method: 'delete' })
}

// 切换文章公开/私密状态（仅作者）
export function changeArticleVisibility(id, isPublic) {
  return request({ url: `/articles/${id}/visibility`, method: 'put', params: { isPublic } })
}

// 我的随记：自己的文章列表（含私密，必须登录）
export function getMyArticles(params) {
  return request({ url: '/articles/mine', method: 'get', params })
}

// ============================ 评论 ============================

// 评论列表（游客可访问）
export function getArticleComments(articleId) {
  return request({ url: `/articles/${articleId}/comments`, method: 'get' })
}

// 发表评论（displayMode: nickname 昵称 / anonymous 匿名）
export function createArticleComment(articleId, data) {
  return request({ url: `/articles/${articleId}/comments`, method: 'post', data })
}

// 删除自己的评论
export function deleteArticleComment(commentId) {
  return request({ url: `/articles/comments/${commentId}`, method: 'delete' })
}

// ============================ 点赞 ============================

// 点赞 / 取消点赞切换
export function toggleArticleLike(articleId) {
  return request({ url: `/articles/${articleId}/like`, method: 'post' })
}

// 评论点赞 / 取消点赞切换（一人一评只能赞一次）
export function toggleCommentLike(commentId) {
  return request({ url: `/articles/comments/${commentId}/like`, method: 'post' })
}

// ============================ 关注作者 ============================

// 关注作者（账户+笔名组合，可携带自定义备注）
export function followAuthor(data) {
  return request({ url: '/articles/follow', method: 'post', data })
}

// 取消关注（按关注记录ID）
export function unfollowAuthor(followId) {
  return request({ url: `/articles/follow/${followId}`, method: 'delete' })
}

// 修改关注备注
export function updateFollowRemark(followId, remark) {
  return request({ url: `/articles/follow/${followId}/remark`, method: 'put', data: { remark } })
}

// 我的关注列表（含该笔名下可见文章数）
export function getMyFollows(params) {
  return request({ url: '/articles/following', method: 'get', params })
}

// 我的关注文章流
export function getFollowingFeed(params) {
  return request({ url: '/articles/following/feed', method: 'get', params })
}

// ============================ 管理员审核 ============================

// 文章审核列表（可按审核状态筛选、笔名/标题检索）
export function getAdminArticles(params) {
  return request({ url: '/admin/articles', method: 'get', params })
}

// 审核文章（action: approve 通过 / return 屏蔽打回）
export function reviewAdminArticle(id, data) {
  return request({ url: `/admin/articles/${id}/review`, method: 'post', data })
}

// 删除违规文章
export function deleteAdminArticle(id) {
  return request({ url: `/admin/articles/${id}`, method: 'delete' })
}

// ============================ 收藏文件夹 ============================

// 我的收藏文件夹列表
export function getFavoriteFolders() {
  return request({ url: '/articles/folders', method: 'get' })
}

// 新建收藏文件夹
export function createFavoriteFolder(name) {
  return request({ url: '/articles/folders', method: 'post', data: { name } })
}

// 删除空收藏文件夹
export function deleteFavoriteFolder(folderId) {
  return request({ url: `/articles/folders/${folderId}`, method: 'delete' })
}

// ============================ 收藏 ============================

// 收藏文章到指定文件夹（folderId 为空时使用默认收藏）
export function collectArticle(articleId, folderId) {
  return request({ url: `/articles/${articleId}/collect`, method: 'post', data: { folderId: folderId || null } })
}

// 取消收藏
export function uncollectArticle(articleId) {
  return request({ url: `/articles/${articleId}/collect`, method: 'delete' })
}

// 我的收藏（可按文件夹筛选，分页）
export function getMyCollections(params) {
  return request({ url: '/articles/collections', method: 'get', params })
}
