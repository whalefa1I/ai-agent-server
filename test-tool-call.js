/**
 * 测试 Happy Chat 工具调用链路
 * 1. 创建 user-message artifact 请求文件操作
 * 2. 等待 AI 调用工具并回复
 * 3. 验证工具调用和回复
 */

const SERVER_URL = 'http://localhost:8080';
const ACCOUNT_ID = `test-tool-${Date.now()}`;
const SESSION_ID = `session-tool-${Date.now()}`;
const TEST_FILE = 'G:/project/ai-agent-server/test-data/hello.txt';

const colors = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[36m'
};

function log(message, color = 'reset') {
  console.log(`${colors[color]}${message}${colors.reset}`);
}

function encodeBase64(str) {
  return Buffer.from(str).toString('base64');
}

function decodeBase64(base64) {
  return Buffer.from(base64, 'base64').toString('utf-8');
}

async function request(method, url, data = null) {
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' }
  };
  if (data) options.body = JSON.stringify(data);

  const res = await fetch(url, options);
  const json = await res.json();
  return { status: res.status, data: json };
}

// 测试 1: 请求 AI 读取文件
async function testFileRead() {
  log('\n' + '='.repeat(60), 'blue');
  log('📋 测试 1: AI 读取文件', 'green');
  log('='.repeat(60), 'blue');

  // 创建测试文件
  const fs = await import('fs');
  const path = await import('path');

  const testDir = path.dirname(TEST_FILE);
  if (!fs.existsSync(testDir)) {
    fs.mkdirSync(testDir, { recursive: true });
  }
  fs.writeFileSync(TEST_FILE, 'Hello from Happy Chat! 这是一个测试文件。');
  log(`✅ 创建测试文件：${TEST_FILE}`, 'green');

  // 发送消息请求 AI 读取文件
  const userArtifact = {
    id: `user-${Date.now()}`,
    header: encodeBase64(JSON.stringify({
      type: 'message',
      subtype: 'user-message',
      title: 'User Message',
      timestamp: Date.now()
    })),
    body: encodeBase64(JSON.stringify({
      type: 'user-message',
      content: `请读取文件 ${TEST_FILE} 的内容并告诉我`,
      timestamp: Date.now()
    })),
    dataEncryptionKey: encodeBase64('test-key'),
    accountId: ACCOUNT_ID,
    sessionId: SESSION_ID,
    headerVersion: 1,
    bodyVersion: 1,
    seq: 0,
    createdAt: Date.now(),
    updatedAt: Date.now()
  };

  await request('POST', `${SERVER_URL}/api/v1/artifacts`, userArtifact);
  log('📤 已发送用户消息', 'blue');

  // 等待 AI 回复
  return await waitForResponse(SESSION_ID, ACCOUNT_ID, 20);
}

// 测试 2: 请求 AI 写入文件
async function testFileWrite() {
  log('\n' + '='.repeat(60), 'blue');
  log('📋 测试 2: AI 写入文件', 'green');
  log('='.repeat(60), 'blue');

  const writeTarget = 'G:/project/ai-agent-server/test-data/ai-written.txt';

  // 发送消息请求 AI 写入文件
  const userArtifact = {
    id: `user-${Date.now()}`,
    header: encodeBase64(JSON.stringify({
      type: 'message',
      subtype: 'user-message',
      title: 'User Message',
      timestamp: Date.now()
    })),
    body: encodeBase64(JSON.stringify({
      type: 'user-message',
      content: `请写入内容 "Hello from AI!" 到文件 ${writeTarget}`,
      timestamp: Date.now()
    })),
    dataEncryptionKey: encodeBase64('test-key'),
    accountId: ACCOUNT_ID,
    sessionId: SESSION_ID,
    headerVersion: 1,
    bodyVersion: 1,
    seq: 0,
    createdAt: Date.now(),
    updatedAt: Date.now()
  };

  await request('POST', `${SERVER_URL}/api/v1/artifacts`, userArtifact);
  log('📤 已发送用户消息', 'blue');

  // 等待 AI 回复
  const response = await waitForResponse(SESSION_ID, ACCOUNT_ID, 20);

  // 验证写入的文件
  if (response) {
    await new Promise(resolve => setTimeout(resolve, 500));
    const fs = await import('fs');
    try {
      const content = fs.readFileSync(writeTarget, 'utf-8');
      if (content.includes('Hello from AI!')) {
        log(`✅ 文件写入验证成功 - 内容：${content}`, 'green');
      } else {
        log(`❌ 文件写入验证失败 - 内容：${content}`, 'red');
      }
    } catch (e) {
      log(`❌ 读取写入文件失败：${e.message}`, 'red');
    }
  }

  return response;
}

// 等待 AI 回复
async function waitForResponse(sessionId, accountId, maxAttempts) {
  const pollInterval = 1000;
  let assistantMessage = null;

  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(resolve => setTimeout(resolve, pollInterval));

    let { data: artifacts } = await request(
      'GET',
      `${SERVER_URL}/api/v1/artifacts?accountId=${accountId}`
    );

    if (artifacts.length > 1) {
      const latestId = artifacts[0].id;
      const detailRes = await request(
        'GET',
        `${SERVER_URL}/api/v1/artifacts/${latestId}?accountId=${accountId}`
      );

      if (detailRes.status === 200 && detailRes.data.body) {
        try {
          const headerData = JSON.parse(decodeBase64(detailRes.data.header));
          if (headerData.type === 'message' && headerData.subtype === 'assistant-message') {
            assistantMessage = {
              ...detailRes.data,
              header: headerData,
              body: JSON.parse(decodeBase64(detailRes.data.body))
            };
            log(`✅ 收到 AI 回复 (尝试 ${i + 1}/${maxAttempts} 次)`, 'green');
            break;
          }
        } catch (e) {
          // 继续等待
        }
      }
    }

    log(`   等待中... (${i + 1}/${maxAttempts})`, 'yellow');
  }

  return assistantMessage;
}

// 验证文件内容
async function verifyFileContent(expected, description) {
  const fs = await import('fs');

  try {
    const content = fs.readFileSync(TEST_FILE, 'utf-8');
    if (content.includes(expected)) {
      log(`✅ ${description} - 文件内容正确`, 'green');
      return true;
    } else {
      log(`❌ ${description} - 文件内容不匹配`, 'red');
      log(`   期望包含：${expected}`, 'yellow');
      log(`   实际内容：${content}`, 'yellow');
      return false;
    }
  } catch (e) {
    log(`❌ ${description} - 读取文件失败：${e.message}`, 'red');
    return false;
  }
}

// 主函数
async function runTests() {
  log('\n' + '='.repeat(60), 'blue');
  log('🔧 Happy Chat 工具调用测试', 'green');
  log('='.repeat(60), 'blue');
  log(`📍 服务器地址：${SERVER_URL}`);
  log(`📍 测试账户 ID: ${ACCOUNT_ID}`);

  // 测试 1: 文件读取
  const readResponse = await testFileRead();
  const readSuccess = readResponse && readResponse.body.content.includes('Hello from Happy Chat');

  if (readSuccess) {
    log('✅ 文件读取测试通过', 'green');
    log(`   AI 回复：${readResponse.body.content.substring(0, 100)}...`, 'yellow');
  } else {
    log('❌ 文件读取测试失败', 'red');
  }

  // 等待一下
  await new Promise(resolve => setTimeout(resolve, 2000));

  // 测试 2: 文件写入
  const writeResponse = await testFileWrite();
  const writeSuccess = writeResponse &&
    (writeResponse.body.content.includes('写入成功') ||
     writeResponse.body.content.includes('已写入') ||
     writeResponse.body.content.includes('成功'));

  // 总结
  log('\n' + '='.repeat(60), 'blue');
  log('📊 测试结果', 'blue');
  log('='.repeat(60), 'blue');
  log(`   文件读取：${readSuccess ? '✅ 通过' : '❌ 失败'}`, readSuccess ? 'green' : 'red');
  log(`   文件写入：${writeSuccess ? '✅ 通过' : '❌ 失败'}`, writeSuccess ? 'green' : 'red');

  if (readSuccess && writeSuccess) {
    log('\n✅ 工具调用测试全部通过！', 'green');
    return true;
  } else {
    log('\n❌ 部分测试失败', 'red');
    return false;
  }
}

// 执行测试
runTests().then(success => {
  process.exit(success ? 0 : 1);
}).catch(err => {
  console.error('测试失败:', err);
  process.exit(1);
});
