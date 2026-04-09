/**
 * 测试 Happy Chat 完整对话流程
 * 1. 创建 user-message artifact
 * 2. 等待 AI 自动回复 (创建 assistant-message artifact)
 * 3. 验证回复内容
 */

const SERVER_URL = 'http://localhost:8080';
const ACCOUNT_ID = `test-${Date.now()}`;
const SESSION_ID = `session-${Date.now()}`;

function log(message, color = 'reset') {
  const colors = {
    reset: '\x1b[0m',
    green: '\x1b[32m',
    red: '\x1b[31m',
    yellow: '\x1b[33m',
    blue: '\x1b[36m'
  };
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

async function testConversation() {
  log('\n' + '='.repeat(60), 'blue');
  log('🚀 Happy Chat 完整对话测试', 'green');
  log('='.repeat(60), 'blue');
  log(`📍 服务器地址：${SERVER_URL}`);
  log(`📍 测试账户 ID: ${ACCOUNT_ID}`);
  log(`📍 测试会话 ID: ${SESSION_ID}`);

  // 步骤 1: 创建 user-message artifact
  log('\n📋 步骤 1: 创建用户消息 artifact', 'blue');

  const header = {
    type: 'message',
    subtype: 'user-message',
    title: 'User Message',
    timestamp: Date.now()
  };

  const body = {
    type: 'user-message',
    content: '你好，请介绍一下你自己',
    timestamp: Date.now()
  };

  const userArtifact = {
    id: `user-${Date.now()}`,
    header: encodeBase64(JSON.stringify(header)),
    body: encodeBase64(JSON.stringify(body)),
    dataEncryptionKey: encodeBase64('test-key'),
    accountId: ACCOUNT_ID,
    sessionId: SESSION_ID,
    headerVersion: 1,
    bodyVersion: 1,
    seq: 0,
    createdAt: Date.now(),
    updatedAt: Date.now()
  };

  const createRes = await request(
    'POST',
    `${SERVER_URL}/api/v1/artifacts`,
    userArtifact
  );

  if (createRes.status !== 200) {
    log('❌ 创建用户消息失败', 'red');
    return false;
  }

  log('✅ 创建用户消息成功', 'green');
  log(`   消息内容：${body.content}`, 'yellow');

  // 步骤 2: 等待 AI 回复 (轮询)
  log('\n📋 步骤 2: 等待 AI 自动回复...', 'blue');

  let assistantMessage = null;
  const maxAttempts = 15; // 最多等待 15 秒
  const pollInterval = 1000; // 每秒轮询一次

  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(resolve => setTimeout(resolve, pollInterval));

    // 获取 artifacts 列表（不带 body）
    let { data: artifacts } = await request(
      'GET',
      `${SERVER_URL}/api/v1/artifacts?accountId=${ACCOUNT_ID}`
    );

    // 如果有超过 1 个 artifact，获取最后一个的详细信息（带 body）
    if (artifacts.length > 1) {
      const latestId = artifacts[0].id;
      const detailRes = await request(
        'GET',
        `${SERVER_URL}/api/v1/artifacts/${latestId}?accountId=${ACCOUNT_ID}`
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
          log(`   解析失败：${e.message}`, 'yellow');
        }
      }
    }

    log(`   等待中... (${i + 1}/${maxAttempts}), artifacts: ${artifacts.length}`, 'yellow');
  }

  if (!assistantMessage) {
    log('❌ 等待 AI 回复超时', 'red');
    return false;
  }

  // 步骤 3: 验证回复内容
  log('\n📋 步骤 3: 验证回复内容', 'blue');

  log(`   AI 回复内容：`, 'yellow');
  log(`   ${assistantMessage.body.content.substring(0, 100)}...`, 'green');

  if (assistantMessage.body.content.length > 0) {
    log('✅ AI 回复内容有效', 'green');
  } else {
    log('❌ AI 回复内容为空', 'red');
    return false;
  }

  // 步骤 4: 统计
  log('\n' + '='.repeat(60), 'blue');
  log('📊 测试结果', 'blue');
  log('='.repeat(60), 'blue');

  const { data: allArtifacts } = await request(
    'GET',
    `${SERVER_URL}/api/v1/artifacts?accountId=${ACCOUNT_ID}`
  );

  log(`   总 artifacts 数：${allArtifacts.length}`, 'yellow');
  log(`   用户消息：1`, 'yellow');
  log(`   助手消息：1`, 'yellow');
  log(`   响应时间：${(allArtifacts[1]?.updatedAt - allArtifacts[0]?.createdAt) / 1000}s`, 'yellow');

  log('\n✅ 完整对话测试通过！', 'green');
  log('='.repeat(60), 'blue');

  return true;
}

// 执行测试
testConversation().then(success => {
  process.exit(success ? 0 : 1);
}).catch(err => {
  console.error('测试失败:', err);
  process.exit(1);
});
