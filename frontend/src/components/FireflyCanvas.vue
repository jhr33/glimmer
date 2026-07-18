<script setup>import { onMounted, onUnmounted, watch, ref } from 'vue';
const props = defineProps({
 fireflyCount: {
 type: Number,
 default: 0
 },
 brightnessLevel: {
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
 this.speedX = (Math.random() - 0.5) * 0.8;
 this.speedY = (Math.random() - 0.5) * 0.6;
 this.baseSpeedX = this.speedX;
 this.size = Math.random() * 3 + 2;
 this.baseAlpha = Math.random() * 0.5 + 0.3;
 this.alpha = this.baseAlpha;
 this.alphaSpeed = (Math.random() * 0.015 + 0.005) * (Math.random() > 0.5 ? 1 : -1);
 this.alphaMin = Math.random() * 0.2 + 0.1;
 this.alphaMax = Math.random() * 0.4 + 0.6;
 this.hueOffset = Math.random() * 30 - 15;
 this.sinOffset = Math.random() * Math.PI * 2;
 this.sinSpeed = Math.random() * 0.02 + 0.01;
 this.time = Math.random() * 1000;
 }
 update(canvasWidth, canvasHeight, mouseX, mouseY) {
 this.time += 0.05;
 const sinWave = Math.sin(this.time * this.sinSpeed + this.sinOffset) * 0.5;
 let dx = this.baseSpeedX + sinWave * 0.3;
 let dy = this.speedY;
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
 this.alpha += this.alphaSpeed;
 if (this.alpha >= this.alphaMax || this.alpha <= this.alphaMin) {
 this.alphaSpeed *= -1;
 }
 }
 draw(ctx) {
 const alpha = this.alpha;
 const gradient = ctx.createRadialGradient(this.x, this.y, 0, this.x, this.y, this.size * 3);
 gradient.addColorStop(0, `rgba(255, 255, 240, ${alpha})`);
 gradient.addColorStop(0.3, `rgba(255, 220, 100, ${alpha * 0.8})`);
 gradient.addColorStop(0.6, `rgba(255, 200, 80, ${alpha * 0.4})`);
 gradient.addColorStop(1, `rgba(255, 180, 60, 0)`);
 ctx.beginPath();
 ctx.arc(this.x, this.y, this.size * 3, 0, Math.PI * 2);
 ctx.fillStyle = gradient;
 ctx.fill();
 ctx.beginPath();
 ctx.arc(this.x, this.y, this.size * 0.8, 0, Math.PI * 2);
 ctx.fillStyle = `rgba(255, 255, 255, ${alpha})`;
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
 ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height);
 fireflies.forEach(firefly => {
 firefly.update(canvasRef.value.width, canvasRef.value.height, mouseX, mouseY);
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