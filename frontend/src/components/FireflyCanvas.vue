<script setup>import { onMounted, onUnmounted, watch, ref } from 'vue';
const props = defineProps({
 fireflyCount: {
 type: Number,
 default: 0
 },
 brightnessLevel: {
 type: Number,
 default: 0
 },
 centerLight: {
 type: Number,
 default: 0
 }
});
const canvasRef = ref(null);
let ctx = null;
let animationId = null;
let fireflies = [];
let mouseX = -1000;
let mouseY = -1000;
class Firefly {
 constructor(canvasWidth, canvasHeight) {
 this.reset(canvasWidth, canvasHeight);
 }
 reset(canvasWidth, canvasHeight) {
 this.x = Math.random() * canvasWidth;
 this.y = Math.random() * canvasHeight;
 this.baseSize = Math.random() * 1.2 + 0.8;
 this.size = this.baseSize;
 this.sizePhase = Math.random() * Math.PI * 2;
 this.sizePhaseSpeed = Math.random() * 0.03 + 0.01;
 this.sizeAmp = Math.random() * 0.3 + 0.2;
 this.alphaMin = Math.random() * 0.02;
 this.alphaMax = Math.random() * 0.25 + 0.75;
 this.alpha = this.alphaMin;
 this.phase = Math.random() * Math.PI * 2;
 this.phaseSpeed = (Math.random() * 0.008 + 0.004) * (Math.random() > 0.5 ? 1 : -1);
 this.targetX = Math.random() * canvasWidth;
 this.targetY = Math.random() * canvasHeight;
 this.sinOffset = Math.random() * Math.PI * 2;
 this.sinSpeed = Math.random() * 0.02 + 0.01;
 this.time = Math.random() * 1000;
 }
 update(canvasWidth, canvasHeight, mouseX, mouseY) {
 this.time += 0.05;
 const sinWave = Math.sin(this.time * this.sinSpeed + this.sinOffset);
 // 朝随机目标点缓动，叠加正弦抖动，形成自然曲线轨迹
 const tdx = this.targetX - this.x;
 const tdy = this.targetY - this.y;
 const tdist = Math.sqrt(tdx * tdx + tdy * tdy);
 if (tdist < 10) {
 this.targetX = Math.random() * canvasWidth;
 this.targetY = Math.random() * canvasHeight;
 }
 const moveSpeed = 0.5;
 let dx = (tdx / Math.max(tdist, 0.1)) * moveSpeed + sinWave * 0.25;
 let dy = (tdy / Math.max(tdist, 0.1)) * moveSpeed * 0.7;
 // 鼠标排斥
 const distToMouse = Math.sqrt(Math.pow(this.x - mouseX, 2) + Math.pow(this.y - mouseY, 2));
 const repelRadius = 80;
 if (distToMouse < repelRadius && distToMouse > 0) {
 const repelForce = (repelRadius - distToMouse) / repelRadius * 0.5;
 const angle = Math.atan2(this.y - mouseY, this.x - mouseX);
 dx += Math.cos(angle) * repelForce;
 dy += Math.sin(angle) * repelForce;
 }
 this.x += dx;
 this.y += dy;
 if (this.x < 0)
 this.x = canvasWidth;
 if (this.x > canvasWidth)
 this.x = 0;
 if (this.y < 0)
 this.y = canvasHeight;
 if (this.y > canvasHeight)
 this.y = 0;
 // 脉冲式 alpha：陡升 → 维持亮 → 陡降 → 维持暗
 this.phase += this.phaseSpeed;
 const period = Math.PI * 2;
 const t = ((this.phase % period) + period) % period;
 const norm = t / period;
 let pulse;
 if (norm < 0.12) {
 pulse = norm / 0.12;
 } else if (norm < 0.42) {
 pulse = 1;
 } else if (norm < 0.54) {
 pulse = 1 - (norm - 0.42) / 0.12;
 } else {
 pulse = 0;
 }
 this.alpha = this.alphaMin + (this.alphaMax - this.alphaMin) * pulse;
 // 大小渐变：随时间正弦呼吸，幅度和速度随机
 this.sizePhase += this.sizePhaseSpeed;
 this.size = this.baseSize * (1 + Math.sin(this.sizePhase) * this.sizeAmp);
 }
 draw(ctx) {
  const alpha = Math.min(1, this.alpha * 1.9);
  // 光晕圈更小：size * 2.3（原 3），且衰减更慢（中段占比更高）→ 小而亮
  const gradient = ctx.createRadialGradient(this.x, this.y, 0, this.x, this.y, this.size * 2.3);
  gradient.addColorStop(0, `rgba(255, 255, 240, ${alpha})`);
  gradient.addColorStop(0.35, `rgba(255, 230, 120, ${alpha * 0.95})`);
  gradient.addColorStop(0.70, `rgba(255, 210, 90, ${alpha * 0.60})`);
  gradient.addColorStop(1, `rgba(255, 180, 60, 0)`);
  ctx.beginPath();
  ctx.arc(this.x, this.y, this.size * 2.3, 0, Math.PI * 2);
  ctx.fillStyle = gradient;
  ctx.fill();
  // 中心亮点略放大 + 纯白色永远 1.0 alpha = 极亮的小灯泡芯
  ctx.beginPath();
  ctx.arc(this.x, this.y, this.size * 0.95, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(255, 255, 255, ${Math.min(1, this.alpha * 2.5)})`;
  ctx.fill();
 }
}
function initCanvas() {
 const canvas = canvasRef.value;
 if (!canvas)
 return;
 ctx = canvas.getContext('2d');
 resizeCanvas();
 window.addEventListener('resize', resizeCanvas);
 window.addEventListener('mousemove', handleMouseMove);
 document.addEventListener('mouseleave', handleMouseLeave);
 initFireflies();
 animate();
}
function resizeCanvas() {
  const canvas = canvasRef.value;
  if (!canvas)
    return;
  const parent = canvas.parentElement;
  canvas.width = parent.clientWidth;
  canvas.height = parent.clientHeight;
}
function handleMouseMove(e) {
 mouseX = e.clientX;
 mouseY = e.clientY;
}
function handleMouseLeave() {
 mouseX = -1000;
 mouseY = -1000;
}
function initFireflies() {
 fireflies = [];
 const canvas = canvasRef.value;
 if (!canvas)
 return;
 const count = Math.max(0, props.fireflyCount);
 for (let i = 0; i < count; i++) {
 fireflies.push(new Firefly(canvas.width, canvas.height));
 }
}
function animate() {
 if (!ctx || !canvasRef.value)
 return;
 const canvas = canvasRef.value;
 ctx.clearRect(0, 0, canvas.width, canvas.height);
 // 1. 黑暗遮罩：双段二次曲线 + 平滑倾斜椭圆
 //    设计目标：
 //      - 中心紧凑小亮区、外围极黑（与萤火虫挖洞强对比）
 //      - 整体倾斜 17°、X 轴拉长 12%、Y 轴压扁 15% → 光滑倾斜椭圆，边界完全平滑无啃噬
 const R = 0.36;         // 中心小亮区半径阈值（相对于 maxR 的比例）
 const DARK_ALPHA = 0.92; // 超过 R 半径的基础暗度
 const EDGE_ALPHA = 0.995;
 const TILT_DEG = 17;     // 整体倾斜角度
 const S_X = 1.12;        // X 轴拉伸
 const S_Y = 0.85;        // Y 轴压缩
 const cx = canvas.width / 2;
 const cy = canvas.height / 2;
 const maxR = Math.sqrt(cx * cx + cy * cy);
 const centerAlpha = Math.max(0, 1 - props.centerLight);

 // 用 transform 建立倾斜 + 缩放坐标系，径向渐变会自然变成"倾斜椭圆"
 ctx.globalCompositeOperation = 'source-over';
 ctx.save();
 ctx.translate(cx, cy);
 ctx.rotate(TILT_DEG * Math.PI / 180);
 ctx.scale(S_X, S_Y);
 const maskGrad = ctx.createRadialGradient(0, 0, 0, 0, 0, maxR);
 const innerStops = [0.00, 0.10, 0.22, R];
 for (const r of innerStops) {
   const t = r / R;
   const ts = t * t * (3 - 2 * t); // smoothstep：中心→暗区过渡更自然
   const a = centerAlpha + (DARK_ALPHA - centerAlpha) * ts;
   maskGrad.addColorStop(r, `rgba(0, 0, 0, ${a.toFixed(4)})`);
 }
 const outerStops = [R, 0.40, 0.52, 0.64, 0.76, 0.88, 1.00];
 for (const r of outerStops) {
   const t = (r - R) / (1 - R);
   const ts = t * t * (3 - 2 * t); // smoothstep：椭圆边缘亮度递减更平缓
   const a = DARK_ALPHA + (EDGE_ALPHA - DARK_ALPHA) * ts;
   maskGrad.addColorStop(r, `rgba(0, 0, 0, ${a.toFixed(4)})`);
 }
 maskGrad.addColorStop(1, `rgba(0, 0, 0, ${EDGE_ALPHA})`);
 ctx.fillStyle = maskGrad;
 // fillRect 在变形坐标系中，大尺寸覆盖旋转+缩放后的整个画布
 const PAD = maxR * 2.2;
 ctx.fillRect(-PAD, -PAD, PAD * 2, PAD * 2);
 ctx.restore();
 // 2. 萤火虫光照：在黑暗层上"挖洞"，露出下方背景图（萤火虫作为光源照亮周边）
 //    大光圈 + 多段渐变 → 边缘柔和；alpha 阈值化 → 暗阶段熄灭不挖洞
 ctx.globalCompositeOperation = 'destination-out';
 fireflies.forEach(firefly => {
 firefly.update(canvas.width, canvas.height, mouseX, mouseY);
 // 光圈更大（size * 11），渐变 stops 更多 → 边缘过渡自然
 const lightRadius = Math.max(10, firefly.size * 11);
 const gradient = ctx.createRadialGradient(
 firefly.x, firefly.y, 0,
 firefly.x, firefly.y, lightRadius
 );
 // 阈值化：alpha < 0.08 时不挖洞 → 萤火虫暗阶段真正熄灭，不再持续发光
 const coreStrength = Math.min(1, Math.max(0, firefly.alpha - 0.08) * 2.8);
 gradient.addColorStop(0, `rgba(0, 0, 0, ${coreStrength})`);
 gradient.addColorStop(0.20, `rgba(0, 0, 0, ${coreStrength * 0.85})`);
 gradient.addColorStop(0.45, `rgba(0, 0, 0, ${coreStrength * 0.55})`);
 gradient.addColorStop(0.75, `rgba(0, 0, 0, ${coreStrength * 0.20})`);
 gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
 ctx.beginPath();
 ctx.arc(firefly.x, firefly.y, lightRadius, 0, Math.PI * 2);
 ctx.fillStyle = gradient;
 ctx.fill();
 });
 // 3. 萤火虫粒子本身（亮点 + 光晕）
 ctx.globalCompositeOperation = 'source-over';
 fireflies.forEach(firefly => {
 firefly.draw(ctx);
 });
 animationId = requestAnimationFrame(animate);
}
function updateFireflies() {
 const canvas = canvasRef.value;
 if (!canvas)
 return;
 const targetCount = Math.max(0, props.fireflyCount);
 const currentCount = fireflies.length;
 if (targetCount > currentCount) {
 for (let i = currentCount; i < targetCount; i++) {
 fireflies.push(new Firefly(canvas.width, canvas.height));
 }
 }
 else if (targetCount < currentCount) {
 fireflies = fireflies.slice(0, targetCount);
 }
}
watch(() => props.fireflyCount, () => {
 updateFireflies();
});
onMounted(() => {
 initCanvas();
});
onUnmounted(() => {
 if (animationId) {
 cancelAnimationFrame(animationId);
 }
 window.removeEventListener('resize', resizeCanvas);
 window.removeEventListener('mousemove', handleMouseMove);
 document.removeEventListener('mouseleave', handleMouseLeave);
});
</script>

<template>
  <canvas ref="canvasRef" class="firefly-canvas"></canvas>
</template>

<style scoped>
.firefly-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 10;
}
</style>