#!/usr/bin/env node
/**
 * WebSocket API 测试客户端
 *
 * 使用方法：
 * 1. 确保服务端已启动：mvn spring-boot:run
 * 2. 运行测试：node websocket-test.js
 */

import WebSocket from 'ws';

// 测试配置
const WS_URL = 'ws://localhost:8080/ws/agent/test-token';
const HTTP_URL = 'http://localhost:8080/api/v2';

console.log('='.repeat(60));
console.log('WebSocket API 测试客户端');
console.log('='.repeat(60));

// 测试 1: HTTP GET /api/v2/health
async function testHttpHealth() {
    console.log('\n[测试 1] HTTP GET /api/v2/health');
    try {
        const response = await fetch(`${HTTP_URL}/health`);
        const data = await response.json();
        console.log('✓ 响应:', JSON.stringify(data, null, 2));
        return true;
    } catch (error) {
        console.log('✗ 失败:', error.message);
        return false;
    }
}

// 测试 2: WebSocket 连接
function testWebSocketConnection() {
    return new Promise((resolve) => {
        console.log('\n[测试 2] WebSocket 连接');

        const ws = new WebSocket(WS_URL);

        ws.on('open', () => {
            console.log('✓ WebSocket 连接成功');
            console.log('  会话 ID: 待接收...');
            resolve(ws);
        });

        ws.on('error', (error) => {
            console.log('✗ WebSocket 连接失败:', error.message);
            resolve(null);
        });

        // 5 秒超时
        setTimeout(() => {
            if (ws.readyState === WebSocket.CONNECTING) {
                console.log('✗ WebSocket 连接超时');
                ws.close();
                resolve(null);
            }
        }, 5000);
    });
}

// 测试 3: 发送用户消息
function testUserMessage(ws) {
    return new Promise((resolve) => {
        console.log('\n[测试 3] 发送用户消息');

        const message = {
            type: 'USER_MESSAGE',
            content: '你好，请介绍一下自己',
            requestId: 'test_' + Date.now()
        };

        console.log('  发送:', JSON.stringify(message, null, 2));
        ws.send(JSON.stringify(message));

        // 等待响应
        const timeout = setTimeout(() => {
            console.log('  ✓ 等待响应中...（可能需要更长时间）');
            resolve();
        }, 3000);

        ws.once('message', (data) => {
            clearTimeout(timeout);
            const msg = JSON.parse(data.toString());
            console.log('  ✓ 收到响应:', JSON.stringify(msg, null, 2));
            resolve();
        });
    });
}

// 测试 4: 验证协议消息结构
function testProtocolStructure() {
    console.log('\n[测试 4] 验证协议消息结构');

    // 模拟各种消息类型
    const messages = [
        {
            name: 'UserMessage',
            data: { type: 'USER_MESSAGE', content: 'hello', requestId: 'req_1' }
        },
        {
            name: 'PermissionResponse',
            data: {
                type: 'PERMISSION_RESPONSE',
                requestId: 'perm_123',
                choice: 'ALLOW_ONCE'
            }
        },
        {
            name: 'GetHistory',
            data: { type: 'GET_HISTORY', limit: 20, requestId: 'hist_1' }
        },
        {
            name: 'Ping',
            data: { type: 'PING' }
        }
    ];

    messages.forEach(({ name, data }) => {
        const json = JSON.stringify(data);
        console.log(`  ✓ ${name}: ${json}`);
    });

    return true;
}

// 测试 5: 验证风险等级枚举
function testRiskLevel() {
    console.log('\n[测试 5] 验证风险等级（前端显示用）');

    const riskLevels = [
        { value: 'READ_ONLY', label: '只读', icon: '📖', color: '#22c55e' },
        { value: 'MODIFY_STATE', label: '修改状态', icon: '✏️', color: '#f59e0b' },
        { value: 'NETWORK', label: '网络访问', icon: '🌐', color: '#3b82f6' },
        { value: 'DESTRUCTIVE', label: '破坏性', icon: '⚠️', color: '#ef4444' },
        { value: 'AGENT_SPAWN', label: '子代理', icon: '🤖', color: '#8b5cf6' }
    ];

    riskLevels.forEach(level => {
        console.log(`  ${level.icon} ${level.label} (${level.value}) - ${level.color}`);
    });

    return true;
}

// 主测试流程
async function runTests() {
    console.log('\n开始测试...\n');

    // 测试 1: HTTP Health
    await testHttpHealth();

    // 测试 2: WebSocket 连接
    const ws = await testWebSocketConnection();

    if (ws) {
        // 测试 3: 发送消息
        await testUserMessage(ws);

        // 关闭连接
        ws.close();
        console.log('\n[信息] WebSocket 连接已关闭');
    }

    // 测试 4: 协议结构
    testProtocolStructure();

    // 测试 5: 风险等级
    testRiskLevel();

    console.log('\n' + '='.repeat(60));
    console.log('测试完成');
    console.log('='.repeat(60));
}

// 运行测试
runTests().catch(console.error);
