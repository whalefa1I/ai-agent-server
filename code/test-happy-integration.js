#!/usr/bin/env node
/**
 * Test script for Happy App integration with ai-agent-server
 *
 * This script tests the Happy-compatible API endpoints
 * in the ai-agent-server project.
 *
 * Usage:
 *   node test-happy-integration.js [server-url]
 *
 * Example:
 *   node test-happy-integration.js http://localhost:8080
 */

const http = require('http');

// Configuration
const SERVER_URL = process.argv[2] || 'http://localhost:8080';
const TEST_ACCOUNT_ID = 'test-account-' + Date.now();
const TEST_SESSION_ID = 'test-session-' + Date.now();

console.log('='.repeat(60));
console.log('Happy App Integration Test');
console.log('='.repeat(60));
console.log('Server URL:', SERVER_URL);
console.log('Test Account ID:', TEST_ACCOUNT_ID);
console.log('Test Session ID:', TEST_SESSION_ID);
console.log('');

// Helper function to make HTTP requests
function request(method, path, body = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, SERVER_URL);
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        };

        console.log(`\n>>> ${method} ${path}`);
        if (body) {
            console.log('Request body:', JSON.stringify(body, null, 2));
        }

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                console.log(`<<< ${res.statusCode} ${res.statusMessage}`);
                try {
                    const parsed = JSON.parse(data);
                    console.log('Response:', JSON.stringify(parsed, null, 2));
                    resolve({ status: res.statusCode, data: parsed });
                } catch (e) {
                    console.log('Response (raw):', data.substring(0, 500));
                    resolve({ status: res.statusCode, data: data });
                }
            });
        });

        req.on('error', reject);
        req.setTimeout(10000, () => {
            req.destroy();
            reject(new Error('Request timeout'));
        });

        if (body) {
            req.write(JSON.stringify(body));
        }
        req.end();
    });
}

// Helper function to sleep
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// Test functions
async function testServerHealth() {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 1: Server Health Check');
    console.log('='.repeat(60));

    try {
        const result = await request('GET', '/actuator/health');
        if (result.status === 200 && result.data.status === 'UP') {
            console.log('✓ Server is healthy');
            return true;
        } else {
            console.log('✗ Server health check failed');
            return false;
        }
    } catch (error) {
        console.log('✗ Cannot connect to server:', error.message);
        console.log('\nMake sure ai-agent-server is running:');
        console.log('  cd G:/project/ai-agent-server');
        console.log('  ./mvnw spring-boot:run');
        return false;
    }
}

async function testHappyArtifactsList() {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 2: GET /api/v1/artifacts (Empty List)');
    console.log('='.repeat(60));

    try {
        const result = await request('GET', `/api/v1/artifacts?accountId=${TEST_ACCOUNT_ID}`);
        if (result.status === 200 && Array.isArray(result.data)) {
            console.log('✓ Artifacts list retrieved successfully');
            return true;
        } else {
            console.log('✗ Failed to get artifacts list');
            return false;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return false;
    }
}

async function testCreateArtifact() {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 3: POST /api/v1/artifacts (Create Artifact)');
    console.log('='.repeat(60));

    // Create a simple encrypted artifact (simulating happy protocol)
    // In real happy app, these would be actually encrypted
    const testArtifact = {
        id: 'artifact-' + Date.now(),
        header: Buffer.from(JSON.stringify({
            title: 'Test Artifact',
            type: 'tool',
            name: 'BashTool'
        })).toString('base64'),
        body: Buffer.from(JSON.stringify({
            input: { command: 'echo "Hello World"' },
            output: 'Hello World',
            status: 'completed'
        })).toString('base64'),
        dataEncryptionKey: Buffer.from('test-encryption-key').toString('base64'),
        accountId: TEST_ACCOUNT_ID,
        sessionId: TEST_SESSION_ID
    };

    try {
        const result = await request('POST', '/api/v1/artifacts', testArtifact);
        if (result.status === 200 && result.data.id) {
            console.log('✓ Artifact created successfully');
            return result.data.id;
        } else {
            console.log('✗ Failed to create artifact');
            return null;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return null;
    }
}

async function testGetArtifact(artifactId) {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 4: GET /api/v1/artifacts/:id (Get Single Artifact)');
    console.log('='.repeat(60));

    try {
        const result = await request('GET', `/api/v1/artifacts/${artifactId}?accountId=${TEST_ACCOUNT_ID}`);
        if (result.status === 200 && result.data.id === artifactId) {
            console.log('✓ Artifact retrieved successfully');

            // Decode and display header/body
            if (result.data.header) {
                const header = JSON.parse(Buffer.from(result.data.header, 'base64').toString());
                console.log('Decrypted header:', JSON.stringify(header, null, 2));
            }
            if (result.data.body) {
                const body = JSON.parse(Buffer.from(result.data.body, 'base64').toString());
                console.log('Decrypted body:', JSON.stringify(body, null, 2));
            }
            return true;
        } else {
            console.log('✗ Failed to get artifact');
            return false;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return false;
    }
}

async function testUpdateArtifact(artifactId) {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 5: POST /api/v1/artifacts/:id (Update Artifact)');
    console.log('='.repeat(60));

    const updateBody = {
        body: Buffer.from(JSON.stringify({
            input: { command: 'echo "Updated"' },
            output: 'Updated',
            status: 'completed',
            version: 2
        })).toString('base64'),
        expectedBodyVersion: 1
    };

    try {
        const result = await request('POST', `/api/v1/artifacts/${artifactId}`, updateBody);
        if (result.status === 200 && result.data.success) {
            console.log('✓ Artifact updated successfully');
            console.log('New bodyVersion:', result.data.bodyVersion);
            return true;
        } else {
            console.log('✗ Failed to update artifact');
            return false;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return false;
    }
}

async function testGetArtifactsList() {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 6: GET /api/v1/artifacts (List with Data)');
    console.log('='.repeat(60));

    try {
        const result = await request('GET', `/api/v1/artifacts?accountId=${TEST_ACCOUNT_ID}`);
        if (result.status === 200 && Array.isArray(result.data) && result.data.length > 0) {
            console.log('✓ Artifacts list retrieved with', result.data.length, 'item(s)');
            return true;
        } else {
            console.log('✗ Failed to get artifacts list');
            return false;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return false;
    }
}

async function testDeleteArtifact(artifactId) {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 7: DELETE /api/v1/artifacts/:id (Delete Artifact)');
    console.log('='.repeat(60));

    try {
        const result = await request('DELETE', `/api/v1/artifacts/${artifactId}?accountId=${TEST_ACCOUNT_ID}`);
        if (result.status === 200 && result.data.success) {
            console.log('✓ Artifact deleted successfully');
            return true;
        } else {
            console.log('✗ Failed to delete artifact');
            return false;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return false;
    }
}

async function testToolStateApi() {
    console.log('\n' + '='.repeat(60));
    console.log('TEST 8: GET /api/v2/tool-state/session/:sessionId');
    console.log('='.repeat(60));

    try {
        const result = await request('GET', `/api/v2/tool-state/session/${TEST_SESSION_ID}`);
        if (result.status === 200) {
            console.log('✓ Tool state session retrieved successfully');
            return true;
        } else {
            console.log('✗ Failed to get tool state session');
            return false;
        }
    } catch (error) {
        console.log('✗ Error:', error.message);
        return false;
    }
}

// Main test runner
async function runTests() {
    const results = [];

    // Test 1: Health check (required)
    if (!await testServerHealth()) {
        console.log('\n' + '='.repeat(60));
        console.log('TESTS ABORTED: Server not reachable');
        console.log('='.repeat(60));
        process.exit(1);
    }
    results.push(true);

    // Test 2: Empty artifacts list
    results.push(await testHappyArtifactsList());

    // Test 3: Create artifact
    const artifactId = await testCreateArtifact();
    results.push(!!artifactId);

    if (artifactId) {
        // Test 4: Get single artifact
        results.push(await testGetArtifact(artifactId));

        // Test 5: Update artifact
        results.push(await testUpdateArtifact(artifactId));

        // Test 6: Get artifacts list
        results.push(await testGetArtifactsList());

        // Test 7: Delete artifact
        results.push(await testDeleteArtifact(artifactId));
    }

    // Test 8: Tool state API
    results.push(await testToolStateApi());

    // Summary
    console.log('\n' + '='.repeat(60));
    console.log('TEST SUMMARY');
    console.log('='.repeat(60));
    const passed = results.filter(r => r).length;
    const total = results.length;
    console.log(`Passed: ${passed}/${total}`);
    console.log(`Success Rate: ${Math.round(passed/total*100)}%`);

    if (passed === total) {
        console.log('\n✓ All tests passed! Happy App integration is working.');
    } else {
        console.log('\n✗ Some tests failed. Check the output above for details.');
    }

    process.exit(passed === total ? 0 : 1);
}

// Run tests
runTests().catch(err => {
    console.error('Test runner error:', err);
    process.exit(1);
});
