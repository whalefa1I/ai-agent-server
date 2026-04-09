/**
 * ai-agent-web 联调测试脚本
 *
 * 测试 Happy REST API 与后端的完整集成
 */

const SERVER_URL = 'http://localhost:8080';
const ACCOUNT_ID = `test-${Date.now()}`;
const SESSION_ID = `session-${Date.now()}`;

// 颜色输出
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

// Base64 编码/解码
function encodeBase64(str) {
  return Buffer.from(str).toString('base64');
}

function decodeBase64(base64) {
  return Buffer.from(base64, 'base64').toString('utf-8');
}

// HTTP 请求封装
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

// 测试用例
const tests = {
  // 测试 1: 健康检查
  async healthCheck() {
    log('\n📋 测试 1: 健康检查', 'blue');
    const { status, data } = await request('GET', `${SERVER_URL}/actuator/health`);
    if (status === 200 && data.status === 'UP') {
      log('✅ 健康检查通过', 'green');
      return true;
    }
    log('❌ 健康检查失败', 'red');
    return false;
  },

  // 测试 2: 获取空 artifact 列表
  async getEmptyArtifacts() {
    log('\n📋 测试 2: 获取空 artifact 列表', 'blue');
    const { status, data } = await request(
      'GET',
      `${SERVER_URL}/api/v1/artifacts?accountId=${ACCOUNT_ID}`
    );
    if (status === 200 && Array.isArray(data) && data.length === 0) {
      log('✅ 空列表测试通过', 'green');
      return true;
    }
    log('❌ 空列表测试失败', 'red');
    return false;
  },

  // 测试 3: 创建用户消息 artifact
  async createUserMessage() {
    log('\n📋 测试 3: 创建用户消息 artifact', 'blue');

    const header = {
      type: 'message',
      subtype: 'user-message',
      title: 'User Message',
      timestamp: Date.now()
    };

    const body = {
      type: 'user-message',
      content: '你好，这是一个测试消息！',
      timestamp: Date.now()
    };

    const artifact = {
      id: `artifact-${Date.now()}`,
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

    const { status, data } = await request(
      'POST',
      `${SERVER_URL}/api/v1/artifacts`,
      artifact
    );

    if (status === 200 && data.id === artifact.id) {
      log('✅ 创建用户消息成功', 'green');
      log(`   ID: ${data.id}`, 'yellow');
      return { passed: true, artifactId: data.id };
    }
    log('❌ 创建用户消息失败', 'red');
    return { passed: false, artifactId: null };
  },

  // 测试 4: 获取 artifact 列表（应有 1 条）
  async getArtifactsAfterCreate() {
    log('\n📋 测试 4: 获取 artifact 列表', 'blue');
    const { status, data } = await request(
      'GET',
      `${SERVER_URL}/api/v1/artifacts?accountId=${ACCOUNT_ID}`
    );
    if (status === 200 && Array.isArray(data) && data.length === 1) {
      log('✅ 列表查询通过 (1 条记录)', 'green');
      return true;
    }
    log('❌ 列表查询失败', 'red');
    return false;
  },

  // 测试 5: 创建助手消息 artifact
  async createAssistantMessage() {
    log('\n📋 测试 5: 创建助手消息 artifact', 'blue');

    const header = {
      type: 'message',
      subtype: 'assistant-message',
      title: 'Assistant Message',
      timestamp: Date.now()
    };

    const body = {
      type: 'assistant-message',
      content: '收到您的消息，这是一个测试回复。',
      inputTokens: 10,
      outputTokens: 20,
      timestamp: Date.now()
    };

    const artifact = {
      id: `artifact-${Date.now()}`,
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

    const { status, data } = await request(
      'POST',
      `${SERVER_URL}/api/v1/artifacts`,
      artifact
    );

    if (status === 200 && data.id === artifact.id) {
      log('✅ 创建助手消息成功', 'green');
      return true;
    }
    log('❌ 创建助手消息失败', 'red');
    return false;
  },

  // 测试 6: 创建工具调用 artifact
  async createToolCall() {
    log('\n📋 测试 6: 创建工具调用 artifact', 'blue');

    const header = {
      type: 'tool-call',
      subtype: 'file-read',
      toolName: 'read_file',
      toolDisplayName: '读取文件',
      icon: 'file',
      status: 'started',
      inputSummary: 'path: /test/file.txt'
    };

    const body = {
      status: 'started',
      input: { path: '/test/file.txt' },
      timestamp: Date.now()
    };

    const artifact = {
      id: `tool-${Date.now()}`,
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

    const { status, data } = await request(
      'POST',
      `${SERVER_URL}/api/v1/artifacts`,
      artifact
    );

    if (status === 200 && data.id === artifact.id) {
      log('✅ 创建工具调用成功', 'green');
      return { passed: true, artifactId: data.id };
    }
    log('❌ 创建工具调用失败', 'red');
    return { passed: false, artifactId: null };
  },

  // 测试 7: 更新工具调用状态
  async updateToolCall(artifactId) {
    if (!artifactId) {
      log('⏭️  跳过测试 7: 无有效 artifact ID', 'yellow');
      return true;
    }

    log('\n📋 测试 7: 更新工具调用状态', 'blue');

    const body = {
      body: encodeBase64(JSON.stringify({
        status: 'completed',
        input: { path: '/test/file.txt' },
        output: '文件内容测试',
        outputType: 'text',
        timestamp: Date.now()
      })),
      expectedBodyVersion: 1
    };

    const { status, data } = await request(
      'POST',
      `${SERVER_URL}/api/v1/artifacts/${artifactId}`,
      body
    );

    if (status === 200 && data.success) {
      log('✅ 更新工具状态成功', 'green');
      return true;
    }
    log('❌ 更新工具状态失败', 'red');
    return false;
  },

  // 测试 8: 创建权限请求 artifact
  async createPermissionRequest() {
    log('\n📋 测试 8: 创建权限请求 artifact', 'blue');

    const header = {
      type: 'permission',
      toolName: 'execute_command',
      toolDisplayName: '执行命令',
      toolDescription: '在 shell 中执行系统命令',
      icon: 'terminal',
      level: 'NETWORK',
      levelLabel: '网络请求',
      levelIcon: '🌐',
      levelColor: '#f59e0b'
    };

    const body = {
      inputSummary: 'command: rm -rf /tmp/test',
      riskExplanation: '此操作可能删除文件，请谨慎确认',
      permissionOptions: [
        { value: 'ALLOW_ONCE', label: '允许一次', style: 'primary' },
        { value: 'DENY', label: '拒绝', style: 'danger' }
      ],
      timestamp: Date.now()
    };

    const artifact = {
      id: `permission-${Date.now()}`,
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

    const { status, data } = await request(
      'POST',
      `${SERVER_URL}/api/v1/artifacts`,
      artifact
    );

    if (status === 200 && data.id === artifact.id) {
      log('✅ 创建权限请求成功', 'green');
      return { passed: true, artifactId: data.id };
    }
    log('❌ 创建权限请求失败', 'red');
    return { passed: false, artifactId: null };
  },

  // 测试 9: 响应权限请求
  async respondPermission(artifactId) {
    if (!artifactId) {
      log('⏭️  跳过测试 9: 无有效 artifact ID', 'yellow');
      return true;
    }

    log('\n📋 测试 9: 响应权限请求', 'blue');

    const body = {
      body: encodeBase64(JSON.stringify({
        inputSummary: 'command: rm -rf /tmp/test',
        riskExplanation: '此操作可能删除文件，请谨慎确认',
        permissionOptions: [
          { value: 'ALLOW_ONCE', label: '允许一次', style: 'primary' },
          { value: 'DENY', label: '拒绝', style: 'danger' }
        ],
        response: {
          choice: 'ALLOW_ONCE',
          timestamp: Date.now()
        }
      })),
      expectedBodyVersion: 1
    };

    const { status, data } = await request(
      'POST',
      `${SERVER_URL}/api/v1/artifacts/${artifactId}`,
      body
    );

    if (status === 200 && data.success) {
      log('✅ 响应权限请求成功', 'green');
      return true;
    }
    log('❌ 响应权限请求失败', 'red');
    return false;
  },

  // 测试 10: 验证所有 artifacts
  async verifyAllArtifacts() {
    log('\n📋 测试 10: 验证所有 artifacts', 'blue');

    const { status, data } = await request(
      'GET',
      `${SERVER_URL}/api/v1/artifacts?accountId=${ACCOUNT_ID}`
    );

    if (status === 200 && Array.isArray(data) && data.length >= 4) {
      log(`✅ 共创建 ${data.length} 个 artifacts`, 'green');

      // 解析并显示
      data.forEach((artifact, i) => {
        try {
          const header = JSON.parse(decodeBase64(artifact.header));
          log(`   [${i + 1}] ${header.type}/${header.subtype || 'N/A'} - ${header.title || header.toolDisplayName || 'N/A'}`, 'yellow');
        } catch (e) {
          log(`   [${i + 1}] ${artifact.id} (解析失败)`, 'yellow');
        }
      });

      return true;
    }
    log('❌ 验证 artifacts 失败', 'red');
    return false;
  },

  // 测试 11: 删除 artifact
  async deleteArtifact() {
    log('\n📋 测试 11: 删除 artifact', 'blue');

    // 先获取一个 artifact 来删除
    const { data: artifacts } = await request(
      'GET',
      `${SERVER_URL}/api/v1/artifacts?accountId=${ACCOUNT_ID}`
    );

    if (artifacts.length === 0) {
      log('⏭️  跳过测试：无 artifacts 可删除', 'yellow');
      return true;
    }

    const artifactId = artifacts[0].id;
    const { status } = await request(
      'DELETE',
      `${SERVER_URL}/api/v1/artifacts/${artifactId}?accountId=${ACCOUNT_ID}`
    );

    if (status === 204 || status === 200) {
      log(`✅ 删除 artifact 成功: ${artifactId}`, 'green');
      return true;
    }
    log('❌ 删除 artifact 失败', 'red');
    return false;
  },

  // 测试 12: 前端构建检查
  async checkFrontendBuild() {
    log('\n📋 测试 12: 前端构建检查', 'blue');

    const fs = await import('fs');
    const path = await import('path');

    const distPath = path.join(process.cwd(), '..', 'ai-agent-web', 'dist');

    // 检查 dist 目录是否存在
    if (!fs.existsSync(distPath)) {
      log('❌ dist 目录不存在', 'red');
      return false;
    }

    // 检查关键文件（允许 hash 文件名）
    const indexHtml = path.join(distPath, 'index.html');
    const jsFiles = fs.readdirSync(path.join(distPath, 'assets')).filter(f => f.endsWith('.js'));
    const cssFiles = fs.readdirSync(path.join(distPath, 'assets')).filter(f => f.endsWith('.css'));

    if (fs.existsSync(indexHtml) && jsFiles.length > 0 && cssFiles.length > 0) {
      log(`✅ 前端构建文件完整 (${jsFiles.length} JS, ${cssFiles.length} CSS)`, 'green');
      return true;
    }

    log('❌ 前端构建文件不完整', 'red');
    return false;
  }
};

// 主函数
async function runTests() {
  log('\n' + '='.repeat(60), 'blue');
  log('🚀 ai-agent-web 联调测试开始', 'green');
  log('='.repeat(60), 'blue');
  log(`📍 服务器地址：${SERVER_URL}`);
  log(`📍 测试账户 ID: ${ACCOUNT_ID}`);
  log(`📍 测试会话 ID: ${SESSION_ID}`);

  const results = [];

  // 运行所有测试
  results.push(await tests.healthCheck());
  results.push(await tests.getEmptyArtifacts());

  const userMsg = await tests.createUserMessage();
  results.push(userMsg.passed);

  results.push(await tests.getArtifactsAfterCreate());
  results.push(await tests.createAssistantMessage());

  const toolCall = await tests.createToolCall();
  results.push(toolCall.passed);

  results.push(await tests.updateToolCall(toolCall.artifactId));

  const permission = await tests.createPermissionRequest();
  results.push(permission.passed);

  results.push(await tests.respondPermission(permission.artifactId));
  results.push(await tests.verifyAllArtifacts());
  results.push(await tests.deleteArtifact());
  results.push(await tests.checkFrontendBuild());

  // 统计结果
  const passed = results.filter(r => r).length;
  const total = results.length;

  log('\n' + '='.repeat(60), 'blue');
  log(`📊 测试结果：${passed}/${total} 通过`, passed === total ? 'green' : 'yellow');
  log('='.repeat(60), 'blue');

  if (passed === total) {
    log('\n✅ 所有测试通过！可以推送到 git', 'green');
    return true;
  } else {
    log(`\n❌ 有 ${total - passed} 个测试失败，请检查`, 'red');
    return false;
  }
}

// 执行测试
runTests().then(success => {
  process.exit(success ? 0 : 1);
}).catch(err => {
  console.error('测试执行出错:', err);
  process.exit(1);
});
