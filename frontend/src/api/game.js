import request from '@/utils/request'

/**
 * 小游戏板块接口封装
 */

// 提交一局游戏成绩
export function submitScore(data) {
  return request({ url: '/game/scores', method: 'post', data })
}

// 游戏排行榜（游客可访问）
export function getLeaderboard(gameType = 'snake', limit = 20) {
  return request({ url: '/game/leaderboard', method: 'get', params: { gameType, limit } })
}

// 我的最佳成绩与名次（需登录）
export function getMyBest(gameType = 'snake') {
  return request({ url: '/game/scores/my-best', method: 'get', params: { gameType } })
}

// 查询我在某游戏下保存的匿名代号（需登录）
export function getMyAlias(gameType = 'snake') {
  return request({ url: '/game/alias', method: 'get', params: { gameType } })
}

// 设置/修改我在某游戏下的匿名代号（需登录）
export function saveMyAlias(gameType, alias) {
  return request({ url: '/game/alias', method: 'put', data: { gameType, alias } })
}
