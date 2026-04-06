/**
 * Artifact HTTP API Client
 *
 * 与后端 /api/v1/artifacts 端点交互
 */

import type { BackendArtifact, DecryptedHeader, DecryptedBody } from '@/types/happy-protocol';

const API_BASE = '/api/v1';

/**
 * 获取会话的所有 artifacts
 */
export async function fetchArtifacts(accountId: string): Promise<BackendArtifact[]> {
    const url = `${API_BASE}/artifacts?accountId=${encodeURIComponent(accountId)}`;
    const response = await fetch(url);

    if (!response.ok) {
        throw new Error(`Failed to fetch artifacts: ${response.status}`);
    }

    return response.json();
}

/**
 * 获取单个 artifact（包含 header 和 body）
 */
export async function fetchArtifact(accountId: string, artifactId: string): Promise<BackendArtifact> {
    const url = `${API_BASE}/artifacts/${artifactId}?accountId=${encodeURIComponent(accountId)}`;
    const response = await fetch(url);

    if (!response.ok) {
        if (response.status === 404) {
            throw new Error('Artifact not found');
        }
        throw new Error(`Failed to fetch artifact: ${response.status}`);
    }

    return response.json();
}

/**
 * 获取会话的所有 tool-state artifacts（兼容端点）
 */
export async function fetchSessionArtifacts(sessionId: string): Promise<BackendArtifact[]> {
    const url = `${API_BASE}/tool-state/session/${sessionId}`;
    const response = await fetch(url);

    if (!response.ok) {
        if (response.status === 404) {
            return [];
        }
        throw new Error(`Failed to fetch session artifacts: ${response.status}`);
    }

    return response.json();
}

/**
 * 创建新的 artifact
 */
export async function createArtifact(params: {
    id: string;
    header: string;
    body?: string | null;
    accountId: string;
    sessionId?: string;
}): Promise<BackendArtifact> {
    const response = await fetch(`${API_BASE}/artifacts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params)
    });

    if (!response.ok) {
        if (response.status === 409) {
            throw new Error('Artifact ID already exists');
        }
        throw new Error(`Failed to create artifact: ${response.status}`);
    }

    return response.json();
}

/**
 * 更新 artifact
 */
export async function updateArtifact(
    artifactId: string,
    params: {
        header?: string;
        expectedHeaderVersion?: number;
        body?: string;
        expectedBodyVersion?: number;
    }
): Promise<{ success: boolean; headerVersion?: number; bodyVersion?: number }> {
    const response = await fetch(`${API_BASE}/artifacts/${artifactId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params)
    });

    if (!response.ok) {
        throw new Error(`Failed to update artifact: ${response.status}`);
    }

    return response.json();
}

/**
 * 删除 artifact
 */
export async function deleteArtifact(accountId: string, artifactId: string): Promise<void> {
    const url = `${API_BASE}/artifacts/${artifactId}?accountId=${encodeURIComponent(accountId)}`;
    const response = await fetch(url, { method: 'DELETE' });

    if (!response.ok) {
        if (response.status === 404) {
            throw new Error('Artifact not found');
        }
        throw new Error(`Failed to delete artifact: ${response.status}`);
    }
}

/**
 * 解码 Base64 编码的 header
 */
export function decodeHeader(encryptedHeader: string): DecryptedHeader | null {
    try {
        const decoded = atob(encryptedHeader);
        return JSON.parse(decoded) as DecryptedHeader;
    } catch (error) {
        console.error('Failed to decode header:', error);
        return null;
    }
}

/**
 * 解码 Base64 编码的 body
 */
export function decodeBody(encryptedBody: string | null): DecryptedBody | null {
    if (!encryptedBody) return null;

    try {
        const decoded = atob(encryptedBody);
        return JSON.parse(decoded) as DecryptedBody;
    } catch (error) {
        console.error('Failed to decode body:', error);
        return null;
    }
}
