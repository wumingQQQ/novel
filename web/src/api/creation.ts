import { authenticatedRequest, request } from '@/api/http'
import type { JobProgress } from '@/types/creation'
import type { NovelDetail, NovelSummary, PageResponse } from '@/types/novel'

const base = '/novel'
export function listNovels(scope: 'ALL' | 'MINE', page = 1, size = 12, keyword = '') { const params = new URLSearchParams({ page: String(page), size: String(size), scope }); if (keyword.trim()) params.set('keyword', keyword.trim()); const path = `/novels?${params}`; return scope === 'MINE' ? authenticatedRequest<PageResponse<NovelSummary>>(path) : request<PageResponse<NovelSummary>>(path) }
/** 读取一部公开小说的详情；详情字段可独立于列表继续扩展。 */
export function getNovelDetail(novelId: number | string) { return request<NovelDetail>(`/novels/${novelId}`) }
export function uploadNovel(file: File) { const body = new FormData(); body.append('file', file); return authenticatedRequest<number>(base, { method: 'POST', body }) }
export function createJob(novelId: number, protagonistName: string, targetName: string) { return authenticatedRequest<number>(`${base}/createJob`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ novelId, protagonistName, targetName }) }) }
export function processJob(jobId: number) { return authenticatedRequest<string>(`${base}/process/${jobId}`, { method: 'POST' }) }
export function redoJob(jobId: number) { return authenticatedRequest<string>(`${base}/redo/${jobId}`, { method: 'POST' }) }
export function getJobProgress(jobId: string | number) { return authenticatedRequest<JobProgress>(`${base}/progress/${jobId}`) }
export async function streamJobProgress(jobId: string | number, signal: AbortSignal, onProgress: (progress: JobProgress) => void) { const token = localStorage.getItem('access_token')?.trim(); if (!token) throw new Error('请先登录'); const response = await fetch(`/api${base}/progress/${jobId}/stream`, { headers: { Authorization: `Bearer ${token}` }, signal }); if (!response.ok || !response.body) throw new Error('进度订阅暂时不可用'); const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''; while (true) { const chunk = await reader.read(); if (chunk.done) return; buffer += decoder.decode(chunk.value, { stream: true }); const blocks = buffer.split(/\r?\n\r?\n/); buffer = blocks.pop() ?? ''; blocks.forEach(block => { if (!/^event:\s*progress/m.test(block)) return; const data = block.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n'); if (data) onProgress(JSON.parse(data) as JobProgress) }) } }
