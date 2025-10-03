import React, { useState, useEffect } from 'react';
import api from '../services/api';
import './BatchManagement.css';

interface BatchExecution {
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

interface RunningJob {
  jobName: string;
  executionId: number;
  status: string;
  startTime: string;
  runningTimeMs: number;
  runningTimeMinutes: number;
  stepExecutions: Array<{
    stepName: string;
    status: string;
    readCount: number;
    writeCount: number;
    commitCount: number;
  }>;
}

interface BatchStatistics {
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
}

interface JobParameter {
  id?: number;
  jobName: string;
  parameterName: string;
  parameterValue: string;
  parameterType: string;
  description?: string;
  isActive: boolean;
}

interface BatchSchedule {
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

interface NotificationConfig {
  id?: number;
  jobName?: string;
  notificationType: string;
  triggerEvent: string;
  recipientAddress: string;
  isEnabled: boolean;
}

const BatchManagement: React.FC = () => {
  const [activeTab, setActiveTab] = useState('overview');
  const [executions, setExecutions] = useState<BatchExecution[]>([]);
  const [runningJobs, setRunningJobs] = useState<RunningJob[]>([]);
  const [statistics, setStatistics] = useState<BatchStatistics | null>(null);
  const [parameters, setParameters] = useState<JobParameter[]>([]);
  const [schedules, setSchedules] = useState<BatchSchedule[]>([]);
  const [notifications, setNotifications] = useState<NotificationConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [confirmDialog, setConfirmDialog] = useState<{
    show: boolean;
    jobName: string;
    jobDescription: string;
  }>({ show: false, jobName: '', jobDescription: '' });

  useEffect(() => {
    fetchAllData();
    const interval = setInterval(fetchRunningJobs, 10000); // 10秒間隔で実行中ジョブを更新
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (alert) {
      const timer = setTimeout(() => {
        setAlert(null);
      }, 5000); // 5秒後にアラートを自動で消す
      return () => clearTimeout(timer);
    }
  }, [alert]);

  const fetchAllData = async () => {
    await Promise.all([
      fetchExecutions(),
      fetchRunningJobs(),
      fetchStatistics(),
      fetchParameters(),
      fetchSchedules(),
      fetchNotifications(),
    ]);
  };

  const fetchExecutions = async () => {
    try {
      const response = await api.get('/batch/executions?size=50');
      setExecutions(response.data.executions || []);
    } catch (error) {
      console.error('実行履歴取得エラー:', error);
    }
  };

  const fetchRunningJobs = async () => {
    try {
      const response = await api.get('/batch/running');
      setRunningJobs(response.data);
    } catch (error) {
      console.error('実行中ジョブ取得エラー:', error);
    }
  };

  const fetchStatistics = async () => {
    try {
      const response = await api.get('/batch/statistics');
      setStatistics(response.data);
    } catch (error) {
      console.error('統計情報取得エラー:', error);
    }
  };

  const fetchParameters = async () => {
    try {
      const response = await api.get('/batch/parameters');
      setParameters(response.data);
    } catch (error) {
      console.error('パラメータ取得エラー:', error);
    }
  };

  const fetchSchedules = async () => {
    try {
      const response = await api.get('/batch/schedules');
      setSchedules(response.data);
    } catch (error) {
      console.error('スケジュール取得エラー:', error);
    }
  };

  const fetchNotifications = async () => {
    try {
      const response = await api.get('/batch/notifications');
      setNotifications(response.data);
    } catch (error) {
      console.error('通知設定取得エラー:', error);
    }
  };

  const showConfirmDialog = (jobName: string, jobDescription: string) => {
    setConfirmDialog({
      show: true,
      jobName,
      jobDescription
    });
  };

  const hideConfirmDialog = () => {
    setConfirmDialog({ show: false, jobName: '', jobDescription: '' });
  };

  const executeJob = async (jobName: string) => {
    try {
      setLoading(true);
      hideConfirmDialog();
      await api.post(`/batch/jobs/${jobName}/execute`);
      setAlert({ type: 'success', message: `ジョブ「${jobName}」を実行しました` });
      fetchAllData();
    } catch (error) {
      setAlert({ type: 'error', message: 'ジョブ実行に失敗しました' });
    } finally {
      setLoading(false);
    }
  };

  const stopJob = async (executionId: number) => {
    try {
      await api.post(`/batch/executions/${executionId}/stop`);
      setAlert({ type: 'success', message: 'ジョブ停止リクエストを送信しました' });
      fetchRunningJobs();
    } catch (error) {
      setAlert({ type: 'error', message: 'ジョブ停止に失敗しました' });
    }
  };

  const getStatusBadge = (status: string) => {
    const variants = {
      'COMPLETED': 'badge-success',
      'FAILED': 'badge-error',
      'STARTED': 'badge-info',
      'STOPPING': 'badge-secondary',
    } as const;

    const className = variants[status as keyof typeof variants] || 'badge-secondary';

    return (
      <span className={`badge ${className}`}>
        {status}
      </span>
    );
  };

  const formatDateTime = (dateString: string) => {
    return new Date(dateString).toLocaleString('ja-JP');
  };

  const formatDuration = (ms: number) => {
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (hours > 0) {
      return `${hours}時間${minutes % 60}分`;
    } else if (minutes > 0) {
      return `${minutes}分${seconds % 60}秒`;
    } else {
      return `${seconds}秒`;
    }
  };

  return (
    <div className="batch-management-container">
      <div className="batch-header">
        <h1>バッチ管理</h1>
        <p>バッチジョブの実行、監視、管理を行います</p>
      </div>

      {alert && (
        <div className={`alert ${alert.type === 'error' ? 'alert-error' : 'alert-success'}`}>
          {alert.message}
        </div>
      )}

      <div className="batch-tabs">
        <div className="tab-list">
          <button
            className={`tab-trigger ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
          >
            概要
          </button>
          <button
            className={`tab-trigger ${activeTab === 'executions' ? 'active' : ''}`}
            onClick={() => setActiveTab('executions')}
          >
            実行履歴
          </button>
          <button
            className={`tab-trigger ${activeTab === 'running' ? 'active' : ''}`}
            onClick={() => setActiveTab('running')}
          >
            実行中
          </button>
          <button
            className={`tab-trigger ${activeTab === 'parameters' ? 'active' : ''}`}
            onClick={() => setActiveTab('parameters')}
          >
            パラメータ
          </button>
          <button
            className={`tab-trigger ${activeTab === 'schedules' ? 'active' : ''}`}
            onClick={() => setActiveTab('schedules')}
          >
            スケジュール
          </button>
          <button
            className={`tab-trigger ${activeTab === 'notifications' ? 'active' : ''}`}
            onClick={() => setActiveTab('notifications')}
          >
            通知設定
          </button>
        </div>

        {activeTab === 'overview' && (
          <div className="tab-content">
            <div className="card-grid">
              {/* 手動実行セクション */}
              <div className="card">
                <div className="card-header">
                  <h3 className="card-title">ジョブ手動実行</h3>
                </div>
                <div className="card-content">
                  <div className="job-list">
                    {statistics?.availableJobs && Object.entries(statistics.availableJobs).map(([jobName, description]) => (
                      <button
                        key={jobName}
                        onClick={() => showConfirmDialog(jobName, description)}
                        disabled={loading}
                        className={`button button-outline button-full ${loading ? 'loading' : ''}`}
                      >
                        {description}
                      </button>
                    ))}
                    {(!statistics?.availableJobs || Object.keys(statistics.availableJobs).length === 0) && <p>利用可能なジョブが見つかりません</p>}
                  </div>
                </div>
              </div>

              {/* 統計情報 */}
              {statistics && (
                <div className="card">
                  <div className="card-header">
                    <h3 className="card-title">実行統計（過去30日）</h3>
                  </div>
                  <div className="card-content">
                    <div className="stats-grid">
                      <div className="stat-row">
                        <span className="stat-label">総実行数:</span>
                        <span className="stat-value">{statistics.jobStatistics.total_executions}</span>
                      </div>
                      <div className="stat-row">
                        <span className="stat-label">成功:</span>
                        <span className="stat-value success">{statistics.jobStatistics.completed_executions}</span>
                      </div>
                      <div className="stat-row">
                        <span className="stat-label">失敗:</span>
                        <span className="stat-value error">{statistics.jobStatistics.failed_executions}</span>
                      </div>
                      <div className="stat-row">
                        <span className="stat-label">実行中:</span>
                        <span className="stat-value info">{statistics.jobStatistics.running_executions}</span>
                      </div>
                      <div className="stat-row">
                        <span className="stat-label">平均実行時間:</span>
                        <span className="stat-value">
                          {statistics.jobStatistics.avg_execution_time_ms
                            ? formatDuration(statistics.jobStatistics.avg_execution_time_ms)
                            : 'N/A'}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* ジョブ別実行統計 */}
              {statistics?.jobBreakdown && statistics.jobBreakdown.length > 0 && (
                <div className="card" style={{ gridColumn: 'span 2' }}>
                  <div className="card-header">
                    <h3 className="card-title">ジョブ別実行統計（過去30日）</h3>
                  </div>
                  <div className="card-content">
                    <div className="table-container">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>ジョブ名</th>
                            <th>実行回数</th>
                            <th>成功</th>
                            <th>失敗</th>
                            <th>平均実行時間</th>
                            <th>最終実行</th>
                          </tr>
                        </thead>
                        <tbody>
                          {statistics.jobBreakdown.map((job, index) => (
                            <tr key={index}>
                              <td>{job.job_name}</td>
                              <td>{job.execution_count}</td>
                              <td className="success">{job.success_count}</td>
                              <td className="error">{job.failure_count}</td>
                              <td>
                                {job.avg_time_ms
                                  ? formatDuration(job.avg_time_ms)
                                  : 'N/A'}
                              </td>
                              <td>{job.last_execution ? formatDateTime(job.last_execution) : 'N/A'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              )}

              {/* 実行中ジョブ */}
              <div className="card">
                <div className="card-header">
                  <h3 className="card-title">実行中ジョブ</h3>
                </div>
                <div className="card-content">
                  {runningJobs.length === 0 ? (
                    <p className="no-data">実行中のジョブはありません</p>
                  ) : (
                    <div className="job-list">
                      {runningJobs.map((job) => (
                        <div key={job.executionId} className="running-job">
                          <div className="running-job-header">
                            <div className="running-job-info">
                              <h4>{job.jobName}</h4>
                              <p>ID: {job.executionId}</p>
                            </div>
                            <button
                              className="button button-danger button-small"
                              onClick={() => stopJob(job.executionId)}
                            >
                              停止
                            </button>
                          </div>
                          <div className="running-job-details">
                            <p>実行時間: {formatDuration(job.runningTimeMs)}</p>
                            <p>開始時刻: {formatDateTime(job.startTime)}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'executions' && (
          <div className="tab-content">
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">実行履歴</h3>
              </div>
              <div className="card-content">
                <div className="table-container">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>ジョブ名</th>
                        <th>ステータス</th>
                        <th>開始時刻</th>
                        <th>実行時間</th>
                        <th>読み込み</th>
                        <th>書き込み</th>
                      </tr>
                    </thead>
                    <tbody>
                      {executions.map((execution, index) => (
                        <tr key={index}>
                          <td>{execution.job_name}</td>
                          <td>{getStatusBadge(execution.status)}</td>
                          <td>{formatDateTime(execution.start_time)}</td>
                          <td>
                            {execution.execution_time_ms
                              ? formatDuration(execution.execution_time_ms)
                              : 'N/A'}
                          </td>
                          <td>{execution.read_count.toLocaleString()}</td>
                          <td>{execution.write_count.toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'running' && (
          <div className="tab-content">
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">実行中ジョブ詳細</h3>
              </div>
              <div className="card-content">
                {runningJobs.length === 0 ? (
                  <p className="no-data">実行中のジョブはありません</p>
                ) : (
                  <div className="job-list">
                    {runningJobs.map((job) => (
                      <div key={job.executionId} className="running-job">
                        <div className="running-job-header">
                          <div className="running-job-info">
                            <h4>{job.jobName}</h4>
                            <p>実行ID: {job.executionId}</p>
                            <p>実行時間: {formatDuration(job.runningTimeMs)} ({job.runningTimeMinutes}分)</p>
                          </div>
                          <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                            {getStatusBadge(job.status)}
                            <button
                              className="button button-danger button-small"
                              onClick={() => stopJob(job.executionId)}
                            >
                              停止
                            </button>
                          </div>
                        </div>

                        {job.stepExecutions.length > 0 && (
                          <div className="step-table">
                            <h4>ステップ実行状況</h4>
                            <div className="table-container">
                              <table className="table">
                                <thead>
                                  <tr>
                                    <th>ステップ名</th>
                                    <th>ステータス</th>
                                    <th>読み込み</th>
                                    <th>書き込み</th>
                                    <th>コミット</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {job.stepExecutions.map((step, stepIndex) => (
                                    <tr key={stepIndex}>
                                      <td>{step.stepName}</td>
                                      <td>{getStatusBadge(step.status)}</td>
                                      <td>{step.readCount.toLocaleString()}</td>
                                      <td>{step.writeCount.toLocaleString()}</td>
                                      <td>{step.commitCount.toLocaleString()}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'parameters' && (
          <div className="tab-content">
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">バッチパラメータ管理</h3>
              </div>
              <div className="card-content">
                <p style={{ color: '#666', marginBottom: '16px' }}>バッチジョブの実行パラメータを管理します</p>
                <div className="table-container">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>ジョブ名</th>
                        <th>パラメータ名</th>
                        <th>値</th>
                        <th>タイプ</th>
                        <th>ステータス</th>
                      </tr>
                    </thead>
                    <tbody>
                      {parameters.map((param) => (
                        <tr key={param.id}>
                          <td>{param.jobName}</td>
                          <td>{param.parameterName}</td>
                          <td>{param.parameterValue}</td>
                          <td>{param.parameterType}</td>
                          <td>
                            <span className={`badge ${param.isActive ? 'badge-success' : 'badge-secondary'}`}>
                              {param.isActive ? '有効' : '無効'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'schedules' && (
          <div className="tab-content">
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">スケジュール管理</h3>
              </div>
              <div className="card-content">
                <p style={{ color: '#666', marginBottom: '16px' }}>バッチジョブの自動実行スケジュールを管理します</p>
                <div className="table-container">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>ジョブ名</th>
                        <th>クーロン式</th>
                        <th>次回実行</th>
                        <th>実行回数</th>
                        <th>ステータス</th>
                      </tr>
                    </thead>
                    <tbody>
                      {schedules.map((schedule) => (
                        <tr key={schedule.id}>
                          <td>{schedule.jobName}</td>
                          <td style={{ fontFamily: 'monospace', fontSize: '14px' }}>{schedule.cronExpression}</td>
                          <td>
                            {schedule.nextExecution ? formatDateTime(schedule.nextExecution) : 'N/A'}
                          </td>
                          <td>{schedule.executionCount}</td>
                          <td>
                            <span className={`badge ${schedule.isEnabled ? 'badge-success' : 'badge-secondary'}`}>
                              {schedule.isEnabled ? '有効' : '無効'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'notifications' && (
          <div className="tab-content">
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">通知設定</h3>
              </div>
              <div className="card-content">
                <p style={{ color: '#666', marginBottom: '16px' }}>バッチジョブの実行結果に関する通知設定を管理します</p>
                <div className="table-container">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>ジョブ名</th>
                        <th>通知タイプ</th>
                        <th>トリガー</th>
                        <th>送信先</th>
                        <th>ステータス</th>
                      </tr>
                    </thead>
                    <tbody>
                      {notifications.map((notification) => (
                        <tr key={notification.id}>
                          <td>{notification.jobName || '全ジョブ'}</td>
                          <td>{notification.notificationType}</td>
                          <td>{notification.triggerEvent}</td>
                          <td>{notification.recipientAddress}</td>
                          <td>
                            <span className={`badge ${notification.isEnabled ? 'badge-success' : 'badge-secondary'}`}>
                              {notification.isEnabled ? '有効' : '無効'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 確認ダイアログ */}
      {confirmDialog.show && (
        <div className="modal-overlay" onClick={hideConfirmDialog}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">バッチジョブ実行確認</h3>
            </div>
            <div className="modal-body">
              <p>
                以下のバッチジョブを実行しますか？
              </p>
              <p style={{ marginTop: '12px' }}>
                <span className="job-name-highlight">{confirmDialog.jobDescription}</span>
              </p>
              <p style={{ marginTop: '8px', fontSize: '14px', color: '#888' }}>
                ジョブ名: {confirmDialog.jobName}
              </p>
            </div>
            <div className="modal-actions">
              <button
                className="button button-outline"
                onClick={hideConfirmDialog}
                disabled={loading}
              >
                キャンセル
              </button>
              <button
                className="button button-primary"
                onClick={() => executeJob(confirmDialog.jobName)}
                disabled={loading}
              >
                {loading ? '実行中...' : '実行'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BatchManagement;