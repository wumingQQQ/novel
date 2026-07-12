export interface NovelSummary { id: number; name: string; originalFilename: string; fileSize: number | null; createTime: string | null; mine: boolean }
/** 小说详情独立于列表摘要，便于后续增加作者、摘要与标签。 */
export interface NovelDetail { id: number; name: string; originalFilename: string; fileSize: number | null; createTime: string | null }
export interface PageResponse<T> { items: T[]; total: number; page: number; size: number }
