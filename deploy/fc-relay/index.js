// ============================================================
// 阿里云函数计算（FC）- 短信API中转服务
// 背景：生产服务器在腾讯云香港，到 dypnsapi.aliyuncs.com（106.11.x，张家口）
//       网络路径不通；FC 选内地地域即可直连阿里云短信API。
// 用途：转发 SendSmsVerifyCode / CheckSmsVerifyCode 两个接口
// 安全：请求头 x-relay-secret 与环境变量 RELAY_SECRET 比对，防公网滥用
// 部署：函数计算控制台 -> 创建函数 -> Web函数 -> 运行环境 Node.js 18
//       -> 在线编辑粘贴本文件 -> 环境变量配置 AK_ID / AK_SECRET / RELAY_SECRET
//       -> 部署后复制公网访问地址（形如 https://xxx.{region}.fcapp.run）
// 本地自测：AK_ID=xx AK_SECRET=xx RELAY_SECRET=dev node index.js 后 curl
// ============================================================
const http = require('http');
const https = require('https');
const crypto = require('crypto');

const PORT = process.env.FC_SERVER_PORT || 9000;
const AK_ID = process.env.AK_ID;
const AK_SECRET = process.env.AK_SECRET;
const RELAY_SECRET = process.env.RELAY_SECRET;
// 号码认证服务接入点（与后端 SmsServiceImpl 的 DOMAIN 一致）
const ENDPOINT_HOST = 'dypnsapi.aliyuncs.com';
// 只允许中转这两个动作，白名单防滥用
const ALLOWED_ACTIONS = ['SendSmsVerifyCode', 'CheckSmsVerifyCode'];

// RFC3986 百分号编码（阿里云签名规范：+ -> %20，* -> %2A，~ 还原）
function percentEncode(s) {
  return encodeURIComponent(s)
    .replace(/\+/g, '%20')
    .replace(/\*/g, '%2A')
    .replace(/%7E/g, '~');
}

// 构造签名请求并调用阿里云 API，返回响应体 JSON 字符串
function callAliyun(action, params) {
  return new Promise((resolve, reject) => {
    // 公共请求参数（阿里云 RPC 风格）
    const all = {
      AccessKeyId: AK_ID,
      Action: action,
      Format: 'JSON',
      RegionId: 'cn-hangzhou',
      SignatureMethod: 'HMAC-SHA1',
      SignatureNonce: crypto.randomUUID(),
      SignatureVersion: '1.0',
      // 阿里云要求 UTC 时间，格式 yyyy-MM-ddTHH:mm:ssZ（无毫秒）
      Timestamp: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
      Version: '2017-05-25',
      ...params,
    };
    // 1. 按 key 升序拼接规范化查询串
    const canonical = Object.keys(all)
      .sort()
      .map((k) => percentEncode(k) + '=' + percentEncode(String(all[k])))
      .join('&');
    // 2. StringToSign = POST&%2F&percentEncode(规范化串)
    const stringToSign = 'POST&' + percentEncode('/') + '&' + percentEncode(canonical);
    // 3. HMAC-SHA1，密钥为 AccessKeySecret + '&'
    const signature = crypto
      .createHmac('sha1', AK_SECRET + '&')
      .update(stringToSign)
      .digest('base64');

    const body = canonical + '&Signature=' + percentEncode(signature);
    const req = httpsRequest(body, resolve, reject);
    req.write(body);
    req.end();
  });
}

function httpsRequest(body, resolve, reject) {
  const req = https.request(
    {
      host: ENDPOINT_HOST,
      path: '/',
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(body),
      },
      timeout: 8000,
    },
    (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => resolve(data));
    }
  );
  req.on('error', reject);
  req.on('timeout', () => req.destroy(new Error('aliyun api timeout')));
  return req;
}

// FC Web函数：标准 HTTP 服务器，监听 FC_SERVER_PORT（默认9000）
http
  .createServer((req, res) => {
    // 健康检查
    if (req.method === 'GET' && (req.url === '/healthz' || req.url === '/')) {
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end('ok');
      return;
    }
    if (req.method !== 'POST') {
      res.writeHead(405);
      res.end();
      return;
    }
    // 共享密钥校验
    if (!RELAY_SECRET || req.headers['x-relay-secret'] !== RELAY_SECRET) {
      res.writeHead(403);
      res.end('forbidden');
      return;
    }
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', async () => {
      try {
        const { action, params } = JSON.parse(body || '{}');
        if (!ALLOWED_ACTIONS.includes(action)) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ relayError: 'action not allowed: ' + action }));
          return;
        }
        const out = await callAliyun(action, params || {});
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(out);
      } catch (e) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ relayError: String((e && e.message) || e) }));
      }
    });
  })
  .listen(PORT, () => console.log('sms relay listening on :' + PORT));
