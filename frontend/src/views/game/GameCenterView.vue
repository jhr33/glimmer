<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

/**
 * 小游戏板块首页
 * 已上线：贪吃蛇；其余卡片为敬请期待占位，后续新游戏直接在此扩展
 */
const games = [
  {
    key: 'snake',
    name: '贪吃蛇',
    icon: '🐍',
    desc: '吃萤果不断变长，速度逐级递增，冲击排行榜',
    path: '/game/snake',
    available: true,
    tag: '已上线'
  },
  {
    key: '2048',
    name: '2048',
    icon: '🔢',
    desc: '滑动数字方块，合成 2048',
    path: '',
    available: false,
    tag: '敬请期待'
  },
  {
    key: 'match',
    name: '萤光消消乐',
    icon: '🫧',
    desc: '交换相邻萤光，三连消除',
    path: '',
    available: false,
    tag: '敬请期待'
  }
]

function openGame(game) {
  if (!game.available) return
  router.push(game.path)
}
</script>

<template>
  <div class="game-center">
    <div class="center-head">
      <h2 class="center-title">小游戏</h2>
      <p class="center-desc">累了就玩一会儿吧，成绩还能和大家比一比</p>
    </div>

    <div class="game-grid">
      <div
        v-for="game in games"
        :key="game.key"
        class="game-card"
        :class="{ disabled: !game.available }"
        @click="openGame(game)"
      >
        <div class="game-icon">{{ game.icon }}</div>
        <div class="game-name">{{ game.name }}</div>
        <div class="game-desc">{{ game.desc }}</div>
        <div class="game-tag" :class="{ online: game.available }">{{ game.tag }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.center-head {
  margin-bottom: 18px;
}
.center-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: #303133;
}
.center-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.game-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.game-card {
  position: relative;
  background: #fff;
  border: 1px solid #f0e6d2;
  border-radius: 12px;
  padding: 24px 20px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.game-card:hover:not(.disabled) {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(245, 166, 35, 0.14);
}
.game-card.disabled {
  cursor: not-allowed;
  opacity: 0.65;
}
.game-icon {
  font-size: 44px;
  line-height: 1;
  margin-bottom: 10px;
}
.game-name {
  font-size: 17px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 6px;
}
.game-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  min-height: 38px;
}
.game-tag {
  display: inline-block;
  margin-top: 10px;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  background: #f0f2f5;
  color: #909399;
}
.game-tag.online {
  background: #fff8e6;
  color: #b8821f;
  border: 1px solid #ffe6a8;
}

@media (max-width: 768px) {
  .game-grid {
    grid-template-columns: 1fr;
  }
}
</style>
