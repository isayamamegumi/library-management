import { useState, useEffect, useCallback } from 'react';
import batchService, {
  BatchExecution,
  RunningJob,
  BatchStatistics,
  JobParameter,
  BatchSchedule,
  NotificationConfig,
  JobExecutionResponse
} from '../services/batchService';

export interface AlertState {
  type: 'success' | 'error';
  message: string;
}

export function useBatchData() {
  const [executions, setExecutions] = useState<BatchExecution[]>([]);
  const [runningJobs, setRunningJobs] = useState<RunningJob[]>([]);
  const [statistics, setStatistics] = useState<BatchStatistics | null>(null);
  const [parameters, setParameters] = useState<JobParameter[]>([]);
  const [schedules, setSchedules] = useState<BatchSchedule[]>([]);
  const [notifications, setNotifications] = useState<NotificationConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState<AlertState | null>(null);

  // アラート自動消去
  useEffect(() => {
    if (alert) {
      const timer = setTimeout(() => {
        setAlert(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [alert]);

  // データ取得関数群
  const fetchExecutions = useCallback(async () => {
    try {
      const response = await batchService.getExecutions({ size: 50 });
      setExecutions(response.executions || []);
    } catch (error) {
      console.error('実行履歴取得エラー:', error);
      setAlert({ type: 'error', message: '実行履歴の取得に失敗しました' });
    }
  }, []);

  const fetchRunningJobs = useCallback(async () => {
    try {
      const data = await batchService.getRunningJobs();
      setRunningJobs(data);
    } catch (error) {
      console.error('実行中ジョブ取得エラー:', error);
    }
  }, []);

  const fetchStatistics = useCallback(async () => {
    try {
      const data = await batchService.getStatistics();
      setStatistics(data);
    } catch (error) {
      console.error('統計情報取得エラー:', error);
      setAlert({ type: 'error', message: '統計情報の取得に失敗しました' });
    }
  }, []);

  const fetchParameters = useCallback(async () => {
    try {
      const data = await batchService.getParameters();
      setParameters(data);
    } catch (error) {
      console.error('パラメータ取得エラー:', error);
      setAlert({ type: 'error', message: 'パラメータの取得に失敗しました' });
    }
  }, []);

  const fetchSchedules = useCallback(async () => {
    try {
      const data = await batchService.getSchedules();
      setSchedules(data);
    } catch (error) {
      console.error('スケジュール取得エラー:', error);
      setAlert({ type: 'error', message: 'スケジュールの取得に失敗しました' });
    }
  }, []);

  const fetchNotifications = useCallback(async () => {
    try {
      const data = await batchService.getNotifications();
      setNotifications(data);
    } catch (error) {
      console.error('通知設定取得エラー:', error);
      setAlert({ type: 'error', message: '通知設定の取得に失敗しました' });
    }
  }, []);

  const fetchAllData = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        fetchExecutions(),
        fetchRunningJobs(),
        fetchStatistics(),
        fetchParameters(),
        fetchSchedules(),
        fetchNotifications(),
      ]);
    } finally {
      setLoading(false);
    }
  }, [
    fetchExecutions,
    fetchRunningJobs,
    fetchStatistics,
    fetchParameters,
    fetchSchedules,
    fetchNotifications,
  ]);

  // ジョブ操作関数
  const executeJob = useCallback(async (jobName: string, params?: Record<string, string>): Promise<JobExecutionResponse> => {
    setLoading(true);
    try {
      const result = await batchService.executeJob(jobName, params);
      if (result.success) {
        setAlert({ type: 'success', message: `ジョブ「${jobName}」を実行しました` });
        await fetchAllData();
      } else {
        setAlert({ type: 'error', message: result.error || 'ジョブ実行に失敗しました' });
      }
      return result;
    } catch (error: any) {
      const errorMessage = error.response?.data?.error || 'ジョブ実行に失敗しました';
      setAlert({ type: 'error', message: errorMessage });
      return { success: false, message: errorMessage };
    } finally {
      setLoading(false);
    }
  }, [fetchAllData]);

  const stopJob = useCallback(async (executionId: number) => {
    try {
      const result = await batchService.stopJob(executionId);
      if (result.success) {
        setAlert({ type: 'success', message: 'ジョブ停止リクエストを送信しました' });
        await fetchRunningJobs();
      } else {
        setAlert({ type: 'error', message: 'ジョブ停止に失敗しました' });
      }
    } catch (error) {
      setAlert({ type: 'error', message: 'ジョブ停止に失敗しました' });
    }
  }, [fetchRunningJobs]);

  // パラメータ操作関数
  const createParameter = useCallback(async (parameter: JobParameter) => {
    try {
      await batchService.createParameter(parameter);
      setAlert({ type: 'success', message: 'パラメータを作成しました' });
      await fetchParameters();
    } catch (error) {
      setAlert({ type: 'error', message: 'パラメータの作成に失敗しました' });
    }
  }, [fetchParameters]);

  const updateParameter = useCallback(async (id: number, parameter: JobParameter) => {
    try {
      await batchService.updateParameter(id, parameter);
      setAlert({ type: 'success', message: 'パラメータを更新しました' });
      await fetchParameters();
    } catch (error) {
      setAlert({ type: 'error', message: 'パラメータの更新に失敗しました' });
    }
  }, [fetchParameters]);

  const deleteParameter = useCallback(async (id: number) => {
    try {
      await batchService.deleteParameter(id);
      setAlert({ type: 'success', message: 'パラメータを削除しました' });
      await fetchParameters();
    } catch (error) {
      setAlert({ type: 'error', message: 'パラメータの削除に失敗しました' });
    }
  }, [fetchParameters]);

  const toggleParameterStatus = useCallback(async (id: number) => {
    try {
      await batchService.toggleParameterStatus(id);
      setAlert({ type: 'success', message: 'パラメータのステータスを変更しました' });
      await fetchParameters();
    } catch (error) {
      setAlert({ type: 'error', message: 'パラメータのステータス変更に失敗しました' });
    }
  }, [fetchParameters]);

  // スケジュール操作関数
  const createSchedule = useCallback(async (schedule: BatchSchedule) => {
    try {
      const result = await batchService.createSchedule(schedule);
      if (result.success) {
        setAlert({ type: 'success', message: 'スケジュールを作成しました' });
        await fetchSchedules();
      } else {
        setAlert({ type: 'error', message: result.error || 'スケジュールの作成に失敗しました' });
      }
    } catch (error) {
      setAlert({ type: 'error', message: 'スケジュールの作成に失敗しました' });
    }
  }, [fetchSchedules]);

  const updateSchedule = useCallback(async (id: number, schedule: BatchSchedule) => {
    try {
      const result = await batchService.updateSchedule(id, schedule);
      if (result.success) {
        setAlert({ type: 'success', message: 'スケジュールを更新しました' });
        await fetchSchedules();
      } else {
        setAlert({ type: 'error', message: result.error || 'スケジュールの更新に失敗しました' });
      }
    } catch (error) {
      setAlert({ type: 'error', message: 'スケジュールの更新に失敗しました' });
    }
  }, [fetchSchedules]);

  const deleteSchedule = useCallback(async (id: number) => {
    try {
      await batchService.deleteSchedule(id);
      setAlert({ type: 'success', message: 'スケジュールを削除しました' });
      await fetchSchedules();
    } catch (error) {
      setAlert({ type: 'error', message: 'スケジュールの削除に失敗しました' });
    }
  }, [fetchSchedules]);

  const toggleScheduleStatus = useCallback(async (id: number) => {
    try {
      await batchService.toggleScheduleStatus(id);
      setAlert({ type: 'success', message: 'スケジュールのステータスを変更しました' });
      await fetchSchedules();
    } catch (error) {
      setAlert({ type: 'error', message: 'スケジュールのステータス変更に失敗しました' });
    }
  }, [fetchSchedules]);

  // 通知設定操作関数
  const createNotification = useCallback(async (notification: NotificationConfig) => {
    try {
      await batchService.createNotification(notification);
      setAlert({ type: 'success', message: '通知設定を作成しました' });
      await fetchNotifications();
    } catch (error) {
      setAlert({ type: 'error', message: '通知設定の作成に失敗しました' });
    }
  }, [fetchNotifications]);

  const updateNotification = useCallback(async (id: number, notification: NotificationConfig) => {
    try {
      await batchService.updateNotification(id, notification);
      setAlert({ type: 'success', message: '通知設定を更新しました' });
      await fetchNotifications();
    } catch (error) {
      setAlert({ type: 'error', message: '通知設定の更新に失敗しました' });
    }
  }, [fetchNotifications]);

  const deleteNotification = useCallback(async (id: number) => {
    try {
      await batchService.deleteNotification(id);
      setAlert({ type: 'success', message: '通知設定を削除しました' });
      await fetchNotifications();
    } catch (error) {
      setAlert({ type: 'error', message: '通知設定の削除に失敗しました' });
    }
  }, [fetchNotifications]);

  const toggleNotificationStatus = useCallback(async (id: number) => {
    try {
      await batchService.toggleNotificationStatus(id);
      setAlert({ type: 'success', message: '通知設定のステータスを変更しました' });
      await fetchNotifications();
    } catch (error) {
      setAlert({ type: 'error', message: '通知設定のステータス変更に失敗しました' });
    }
  }, [fetchNotifications]);

  return {
    // データ
    executions,
    runningJobs,
    statistics,
    parameters,
    schedules,
    notifications,
    loading,
    alert,

    // 状態管理
    setAlert,

    // データ取得
    fetchExecutions,
    fetchRunningJobs,
    fetchStatistics,
    fetchParameters,
    fetchSchedules,
    fetchNotifications,
    fetchAllData,

    // ジョブ操作
    executeJob,
    stopJob,

    // パラメータ操作
    createParameter,
    updateParameter,
    deleteParameter,
    toggleParameterStatus,

    // スケジュール操作
    createSchedule,
    updateSchedule,
    deleteSchedule,
    toggleScheduleStatus,

    // 通知設定操作
    createNotification,
    updateNotification,
    deleteNotification,
    toggleNotificationStatus,
  };
}

export function useRunningJobsPolling(interval: number = 10000) {
  const [runningJobs, setRunningJobs] = useState<RunningJob[]>([]);

  const fetchRunningJobs = useCallback(async () => {
    try {
      const data = await batchService.getRunningJobs();
      setRunningJobs(data);
    } catch (error) {
      console.error('実行中ジョブ取得エラー:', error);
    }
  }, []);

  useEffect(() => {
    fetchRunningJobs();
    const intervalId = setInterval(fetchRunningJobs, interval);
    return () => clearInterval(intervalId);
  }, [fetchRunningJobs, interval]);

  return { runningJobs, fetchRunningJobs };
}