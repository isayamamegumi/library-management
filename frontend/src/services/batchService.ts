import api from './api';

export interface BatchExecution {
  job_name: string;
  job_execution_id: number;
  start_time: string;
  end_time?: string;
  status: string;
  exit_code?: string;
  exit_message?: string;
  read_count: number;
  write_count: number;
  execution_time_ms?: number;
  error_message?: string;
  created_at: string;
}

export interface RunningJob {
  jobName: string;
  executionId: number;
  status: string;
  startTime: string;
  runningTimeMs: number;
  runningTimeMinutes: number;
  stepExecutions: StepExecution[];
}

export interface StepExecution {
  stepName: string;
  status: string;
  readCount: number;
  writeCount: number;
  commitCount: number;
}

export interface BatchStatistics {
  jobStatistics: {
    total_executions: number;
    completed_executions: number;
    failed_executions: number;
    running_executions: number;
    avg_execution_time_ms: number;
    total_read_count: number;
    total_write_count: number;
  };
  jobBreakdown: Array<{
    job_name: string;
    execution_count: number;
    success_count: number;
    failure_count: number;
    avg_time_ms: number;
    last_execution: string;
  }>;
  availableJobs: Record<string, string>;
  generatedAt: string;
}

export interface JobParameter {
  id?: number;
  jobName: string;
  parameterName: string;
  parameterValue: string;
  parameterType: string;
  description?: string;
  isActive: boolean;
}

export interface BatchSchedule {
  id?: number;
  jobName: string;
  cronExpression: string;
  description?: string;
  isEnabled: boolean;
  timezone: string;
  lastExecution?: string;
  nextExecution?: string;
  executionCount: number;
}

export interface NotificationConfig {
  id?: number;
  jobName?: string;
  notificationType: string;
  triggerEvent: string;
  recipientAddress: string;
  isEnabled: boolean;
}

export interface JobExecutionResponse {
  success: boolean;
  jobName?: string;
  jobExecutionId?: number;
  status?: string;
  startTime?: string;
  message: string;
  error?: string;
}

class BatchService {
  // ジョブ実行関連
  async executeJob(jobName: string, params?: Record<string, string>): Promise<JobExecutionResponse> {
    const response = await api.post(`/batch/jobs/${jobName}/execute`, params);
    return response.data;
  }

  async stopJob(executionId: number): Promise<{ success: boolean; message: string }> {
    const response = await api.post(`/batch/executions/${executionId}/stop`);
    return response.data;
  }

  // データ取得関連
  async getExecutions(params?: {
    page?: number;
    size?: number;
    jobName?: string;
    status?: string;
  }): Promise<{
    executions: BatchExecution[];
    totalCount: number;
    currentPage: number;
    pageSize: number;
    totalPages: number;
  }> {
    const response = await api.get('/batch/executions', { params });
    return response.data;
  }

  async getRunningJobs(): Promise<RunningJob[]> {
    const response = await api.get('/batch/running');
    return response.data;
  }

  async getStatistics(): Promise<BatchStatistics> {
    const response = await api.get('/batch/statistics');
    return response.data;
  }

  async getJobNames(): Promise<string[]> {
    const response = await api.get('/batch/jobs');
    return response.data;
  }

  // パラメータ管理
  async getParameters(): Promise<JobParameter[]> {
    const response = await api.get('/batch/parameters');
    return response.data;
  }

  async getJobParameters(jobName: string): Promise<JobParameter[]> {
    const response = await api.get(`/batch/parameters/${jobName}`);
    return response.data;
  }

  async createParameter(parameter: JobParameter): Promise<JobParameter> {
    const response = await api.post('/batch/parameters', parameter);
    return response.data;
  }

  async updateParameter(id: number, parameter: JobParameter): Promise<JobParameter> {
    const response = await api.put(`/batch/parameters/${id}`, parameter);
    return response.data;
  }

  async deleteParameter(id: number): Promise<void> {
    await api.delete(`/batch/parameters/${id}`);
  }

  async toggleParameterStatus(id: number): Promise<void> {
    await api.post(`/batch/parameters/${id}/toggle`);
  }

  // スケジュール管理
  async getSchedules(): Promise<BatchSchedule[]> {
    const response = await api.get('/batch/schedules');
    return response.data;
  }

  async getEnabledSchedules(): Promise<BatchSchedule[]> {
    const response = await api.get('/batch/schedules/enabled');
    return response.data;
  }

  async createSchedule(schedule: BatchSchedule): Promise<{
    success: boolean;
    schedule?: BatchSchedule;
    nextExecution?: string;
    error?: string;
  }> {
    const response = await api.post('/batch/schedules', schedule);
    return response.data;
  }

  async updateSchedule(id: number, schedule: BatchSchedule): Promise<{
    success: boolean;
    schedule?: BatchSchedule;
    nextExecution?: string;
    error?: string;
  }> {
    const response = await api.put(`/batch/schedules/${id}`, schedule);
    return response.data;
  }

  async deleteSchedule(id: number): Promise<void> {
    await api.delete(`/batch/schedules/${id}`);
  }

  async toggleScheduleStatus(id: number): Promise<void> {
    await api.post(`/batch/schedules/${id}/toggle`);
  }

  async validateCronExpression(cronExpression: string, timezone?: string): Promise<{
    valid: boolean;
    nextExecution?: string;
    error?: string;
  }> {
    const response = await api.post('/batch/schedules/validate-cron', {
      cronExpression,
      timezone
    });
    return response.data;
  }

  // 通知設定
  async getNotifications(): Promise<NotificationConfig[]> {
    const response = await api.get('/batch/notifications');
    return response.data;
  }

  async createNotification(notification: NotificationConfig): Promise<NotificationConfig> {
    const response = await api.post('/batch/notifications', notification);
    return response.data;
  }

  async updateNotification(id: number, notification: NotificationConfig): Promise<NotificationConfig> {
    const response = await api.put(`/batch/notifications/${id}`, notification);
    return response.data;
  }

  async deleteNotification(id: number): Promise<void> {
    await api.delete(`/batch/notifications/${id}`);
  }

  async toggleNotificationStatus(id: number): Promise<void> {
    await api.post(`/batch/notifications/${id}/toggle`);
  }

  // エラー回復
  async restartFailedJob(executionId: number): Promise<{
    success: boolean;
    message: string;
    newExecutionId?: number;
    error?: string;
  }> {
    const response = await api.post(`/batch/recovery/restart/${executionId}`);
    return response.data;
  }

  async retryFailedStep(executionId: number, stepName: string): Promise<{
    success: boolean;
    message: string;
  }> {
    const response = await api.post(`/batch/recovery/retry-step/${executionId}/${stepName}`);
    return response.data;
  }

  async analyzeSkippedItems(executionId: number): Promise<{
    success: boolean;
    message: string;
  }> {
    const response = await api.post(`/batch/recovery/analyze-skips/${executionId}`);
    return response.data;
  }

  async stopLongRunningJob(executionId: number): Promise<{
    success: boolean;
    message: string;
  }> {
    const response = await api.post(`/batch/recovery/stop-long-running/${executionId}`);
    return response.data;
  }

  async analyzeJobFailure(executionId: number): Promise<Record<string, any>> {
    const response = await api.get(`/batch/recovery/analyze/${executionId}`);
    return response.data;
  }

  async getRecoveryRecommendations(executionId: number): Promise<Array<Record<string, any>>> {
    const response = await api.get(`/batch/recovery/recommendations/${executionId}`);
    return response.data;
  }

  // ログ取得
  async getLogs(params?: {
    page?: number;
    size?: number;
    jobName?: string;
    level?: string;
  }): Promise<{
    logs: Array<{
      id: number;
      log_level: string;
      message: string;
      user_id?: number;
      ip_address?: string;
      created_at: string;
    }>;
    totalCount: number;
    currentPage: number;
    pageSize: number;
    totalPages: number;
  }> {
    const response = await api.get('/batch/logs', { params });
    return response.data;
  }

  // レポート取得
  async getReport(reportType: string, targetDate?: string): Promise<{
    reportType: string;
    targetDate: string;
    data: any;
    createdAt: string;
    updatedAt: string;
  }> {
    const params = targetDate ? { targetDate } : undefined;
    const response = await api.get(`/batch/reports/${reportType}`, { params });
    return response.data;
  }
}

export const batchService = new BatchService();
export default batchService;