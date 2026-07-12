export type JobState = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
export interface StageProgress { jobStage: string; stageMode: 'SIMPLE' | 'COUNTED'; state: JobState; totalItems: number | null; successItems: number; failedItems: number; message: string; startTime: string | null; endTime: string | null }
export interface JobProgress { jobId: number; currentStage: string; state: JobState; startTime: string; updateTime: string; endTime: string | null; stages: StageProgress[] }
