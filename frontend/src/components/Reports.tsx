import React, { useState, useEffect } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { Card } from './ui/Card';
import { Button } from './ui/Button';
import { Alert } from './ui/Alert';
import LoadingSpinner from './ui/LoadingSpinner';
import './Reports.css';

interface ReportFilter {
  readStatus?: string[];
  publisher?: string;
  startDate?: string;
  endDate?: string;
  author?: string;
  genre?: string;
  sortBy?: string;
  sortOrder?: string;
  includeImages?: boolean;
}

interface ReportRequest {
  reportType: string;
  format: string;
  filters?: ReportFilter;
  options?: {
    sortBy?: string;
    sortOrder?: string;
    includeImages?: boolean;
    customOptions?: { [key: string]: any };
  };
}

interface ReportHistory {
  id: number;
  reportType: string;
  format: string;
  status: string;
  createdAt: string;
  downloadUrl?: string;
  fileSize?: number;
}

interface Statistics {
  totalCount: number;
  statusCounts: { [key: string]: number };
  publisherCounts: { [key: string]: number };
}

const Reports: React.FC = () => {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [reportHistory, setReportHistory] = useState<ReportHistory[]>([]);
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [filters, setFilters] = useState<ReportFilter>({
    sortBy: 'created_at',
    sortOrder: 'DESC',
    includeImages: false
  });
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  const [progressReports, setProgressReports] = useState<{[key: number]: {progress: number, status: string, message: string}}>({});
  const [showPreview, setShowPreview] = useState(false);
  const [previewData, setPreviewData] = useState<any>(null);
  const [buttonLoading, setButtonLoading] = useState<{[key: string]: boolean}>({});
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pendingReportParams, setPendingReportParams] = useState<{reportType: string, format: string} | null>(null);
  const [showDownloadSuccess, setShowDownloadSuccess] = useState(false);

// PDFプレビュー用コンポーネント
const PreviewFrame: React.FC<{ reportId: number }> = ({ reportId }) => {
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);

  useEffect(() => {
    const loadPdf = async () => {
      try {
        const token = localStorage.getItem('accessToken');
        const response = await fetch(`/api/reports/preview-content/${reportId}`, {
          headers: {
            'Authorization': `Bearer ${token}`,
          },
        });

        if (response.ok) {
          const blob = await response.blob();
          const url = window.URL.createObjectURL(blob);
          setPdfUrl(url);
        }
      } catch (error) {
        console.error('PDFプレビュー読み込みエラー:', error);
      }
    };

    loadPdf();

    // クリーンアップ
    return () => {
      if (pdfUrl) {
        window.URL.revokeObjectURL(pdfUrl);
      }
    };
  }, [reportId]);

  return pdfUrl ? (
    <iframe
      src={`${pdfUrl}#toolbar=0&navpanes=0&scrollbar=0`}
      width="100%"
      height="600px"
      style={{ border: 'none' }}
      title="PDF プレビュー"
    />
  ) : (
    <div className="pdf-loading">
      <LoadingSpinner size="small" />
      <p>PDFを読み込み中...</p>
    </div>
  );
};

  useEffect(() => {
    loadReportHistory();
    loadStatistics();
  }, []);

  const loadReportHistory = async () => {
    try {
      const token = localStorage.getItem('accessToken');
      const response = await fetch('/api/reports/history', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        const data = await response.json();
        setReportHistory(data.reports || []);
      }
    } catch (error) {
      console.error('履歴の読み込みに失敗しました:', error);
    }
  };

  const loadStatistics = async () => {
    try {
      const token = localStorage.getItem('accessToken');
      const response = await fetch('/api/reports/statistics', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(filters),
      });

      if (response.ok) {
        const data = await response.json();
        console.log('統計データレスポンス:', data);
        console.log('統計データ詳細:', data.statistics);
        console.log('状況別カウント:', data.statistics?.statusCounts);
        setStatistics(data.statistics);
      } else {
        console.error('統計API応答エラー:', response.status, response.statusText);
        const errorData = await response.text();
        console.error('エラー詳細:', errorData);
      }
    } catch (error) {
      console.error('統計の読み込みに失敗しました:', error);
    }
  };

  const generateReportWithPreview = async (reportType: string, format: string) => {
    const buttonKey = `${reportType}-${format}`;
    setButtonLoading(prev => ({ ...prev, [buttonKey]: true }));
    setAlert(null);
    setPendingReportParams({ reportType, format });

    try {
      const token = localStorage.getItem('accessToken');
      // バックエンドで対応しているレポートタイプに変換
      const backendReportType = reportType === 'personal' ? 'BOOK_LIST' :
                                reportType === 'system' ? 'SYSTEM' : reportType;

      const request: ReportRequest = {
        reportType: backendReportType,
        format,
        filters,
        options: {
          sortBy: filters.sortBy || 'created_at',
          sortOrder: filters.sortOrder || 'DESC',
          includeImages: filters.includeImages || false,
          // 完全版生成のためcustomOptionsは空
          customOptions: {}
        }
      };

      const response = await fetch('/api/reports/generate-preview', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });

      const data = await response.json();

      if (data.success) {
        setPreviewUrl(data.previewUrl);
        setPreviewData({
          reportId: data.reportId,
          reportType,
          format,
          fileName: data.fileInfo?.fileName || `${reportType}_report.${format.toLowerCase()}`,
          fileSize: data.fileInfo?.fileSize || 0,
          downloadUrl: data.downloadUrl
        });
        setShowPreview(true);
        // キャッシュヒット判定によるメッセージ変更
        const isCacheHit = data.cacheHit === true;
        const generationTime = data.generationTimeMs;

        let successMessage = '帳票を生成しました。内容を確認してください。';
        if (isCacheHit) {
          successMessage = '帳票を生成しました（キャッシュから高速表示）。内容を確認してください。';
        } else if (generationTime && generationTime > 0) {
          successMessage = `帳票を生成しました（${(generationTime / 1000).toFixed(1)}秒）。内容を確認してください。`;
        }

        setAlert({ type: 'success', message: successMessage });
      } else {
        setAlert({ type: 'error', message: data.message || '帳票生成に失敗しました。' });
      }
    } catch (error) {
      console.error('プレビュー生成エラー:', error);
      setAlert({ type: 'error', message: 'プレビュー生成中にエラーが発生しました。' });
    } finally {
      setButtonLoading(prev => ({ ...prev, [buttonKey]: false }));
    }
  };

  const confirmAndSaveReport = () => {
    // プレビューを確認して履歴に保存
    setShowPreview(false);
    setPendingReportParams(null);
    setAlert({ type: 'success', message: '帳票が生成履歴に保存されました。履歴からダウンロードできます。' });

    // 履歴を更新
    setTimeout(() => {
      loadReportHistory();
    }, 500);
  };

  const startProgressMonitoring = (reportId: number) => {
    // 初期状態設定
    setProgressReports(prev => ({
      ...prev,
      [reportId]: { progress: 0, status: 'GENERATING', message: '帳票生成を開始しています...' }
    }));

    // 進捗監視
    const interval = setInterval(async () => {
      try {
        const token = localStorage.getItem('accessToken');
        const response = await fetch(`/api/reports/status/${reportId}`, {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
        });

        if (response.ok) {
          const data = await response.json();

          setProgressReports(prev => ({
            ...prev,
            [reportId]: {
              progress: data.progress || 0,
              status: data.status,
              message: data.message || '処理中...'
            }
          }));

          // 完了または失敗時は監視停止
          if (data.status === 'COMPLETED' || data.status === 'FAILED') {
            clearInterval(interval);

            // 履歴更新
            loadReportHistory();

            // 完了通知
            if (data.status === 'COMPLETED') {
              setAlert({ type: 'success', message: '帳票が生成完了しました。ダウンロードできます。' });
            } else {
              setAlert({ type: 'error', message: data.message || '帳票生成に失敗しました。' });
            }

            // 進捗状態をクリア
            setTimeout(() => {
              setProgressReports(prev => {
                const updated = {...prev};
                delete updated[reportId];
                return updated;
              });
            }, 5000);
          }
        }
      } catch (error) {
        console.error('進捗取得エラー:', error);
        clearInterval(interval);
      }
    }, 2000); // 2秒毎に確認

    // 5分でタイムアウト
    setTimeout(() => {
      clearInterval(interval);
    }, 300000);
  };

  const downloadReport = async (reportId: number) => {
    const token = localStorage.getItem('accessToken');
    const url = `/api/reports/download/${reportId}`;
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', '');
    link.style.display = 'none';

    try {
      // 認証ヘッダーは直接リンクには設定できないため、fetchを使用
      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const blob = await response.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      link.href = downloadUrl;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(downloadUrl);

      // ダウンロード成功後の処理
      await loadReportHistory(); // 履歴を再読み込み
      setShowPreview(false); // プレビューを閉じる
      setShowDownloadSuccess(true); // 成功ダイアログを表示

      // 3秒後に成功ダイアログを自動で閉じる
      setTimeout(() => {
        setShowDownloadSuccess(false);
      }, 3000);

    } catch (error) {
      console.error('ダウンロードエラー:', error);
      setAlert({ type: 'error', message: 'ダウンロードに失敗しました。' });
    }
  };

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  };

  const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleString('ja-JP');
  };

  const handleFilterChange = (key: keyof ReportFilter, value: any) => {
    setFilters(prev => ({
      ...prev,
      [key]: value
    }));
  };

  const clearFilters = () => {
    setFilters({
      sortBy: 'created_at',
      sortOrder: 'DESC',
      includeImages: false
    });
  };

  const applyFilters = () => {
    loadStatistics();
  };


  return (
    <div className="reports-container">
      <div className="reports-header">
        <h1>帳票出力</h1>
        <p>書籍データの統計情報と帳票を生成・ダウンロードできます。</p>
      </div>

      {alert && (
        <Alert type={alert.type} className="reports-alert">
          {alert.message}
        </Alert>
      )}

      {/* 統計情報 */}
      {statistics && (
        <Card className="statistics-card">
          <div className="card-header">
            <h2>統計情報</h2>
          </div>
          <div className="statistics-grid">
            <div className="stat-item">
              <span className="stat-label">総書籍数</span>
              <span className="stat-value">{statistics.totalCount}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">読了</span>
              <span className="stat-value">{statistics.statusCounts['読了'] || 0}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">未読</span>
              <span className="stat-value">{statistics.statusCounts['未読'] || 0}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">読書中</span>
              <span className="stat-value">{statistics.statusCounts['読書中'] || 0}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">中断中</span>
              <span className="stat-value">{statistics.statusCounts['中断中'] || 0}</span>
            </div>
          </div>
        </Card>
      )}

      {/* フィルター設定 */}
      <Card className="filter-card">
        <div className="card-header">
          <h2>フィルター設定</h2>
          <Button
            onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
            variant="outline"
            size="small"
          >
            {showAdvancedFilters ? '▲ 詳細設定を閉じる' : '▼ 詳細設定を開く'}
          </Button>
        </div>

        <div className="filter-section">
          {/* 基本フィルター */}
          <div className="filter-row">
            <div className="filter-item">
              <label>読書状況</label>
              <select
                value={filters.readStatus?.[0] || ''}
                onChange={(e) => handleFilterChange('readStatus', e.target.value ? [e.target.value] : [])}
              >
                <option value="">すべて</option>
                <option value="未読">未読</option>
                <option value="読書中">読書中</option>
                <option value="読了">読了</option>
                <option value="中断中">中断中</option>
              </select>
            </div>

            <div className="filter-item">
              <label>期間（開始）</label>
              <input
                type="date"
                value={filters.startDate || ''}
                onChange={(e) => handleFilterChange('startDate', e.target.value)}
              />
            </div>

            <div className="filter-item">
              <label>期間（終了）</label>
              <input
                type="date"
                value={filters.endDate || ''}
                onChange={(e) => handleFilterChange('endDate', e.target.value)}
              />
            </div>
          </div>

          {/* 詳細フィルター */}
          {showAdvancedFilters && (
            <div className="advanced-filters">
              <div className="filter-row">
                <div className="filter-item">
                  <label>出版社</label>
                  <input
                    type="text"
                    placeholder="出版社名で絞り込み"
                    value={filters.publisher || ''}
                    onChange={(e) => handleFilterChange('publisher', e.target.value)}
                  />
                </div>

                <div className="filter-item">
                  <label>著者</label>
                  <input
                    type="text"
                    placeholder="著者名で絞り込み"
                    value={filters.author || ''}
                    onChange={(e) => handleFilterChange('author', e.target.value)}
                  />
                </div>

                <div className="filter-item">
                  <label>ジャンル</label>
                  <input
                    type="text"
                    placeholder="ジャンルで絞り込み"
                    value={filters.genre || ''}
                    onChange={(e) => handleFilterChange('genre', e.target.value)}
                  />
                </div>
              </div>

              <div className="filter-row">
                <div className="filter-item">
                  <label>並び順</label>
                  <select
                    value={filters.sortBy || 'created_at'}
                    onChange={(e) => handleFilterChange('sortBy', e.target.value)}
                  >
                    <option value="created_at">登録日</option>
                    <option value="title">タイトル</option>
                    <option value="author">著者</option>
                    <option value="publisher">出版社</option>
                    <option value="read_status">読書状況</option>
                  </select>
                </div>

                <div className="filter-item">
                  <label>順序</label>
                  <select
                    value={filters.sortOrder || 'DESC'}
                    onChange={(e) => handleFilterChange('sortOrder', e.target.value)}
                  >
                    <option value="DESC">降順</option>
                    <option value="ASC">昇順</option>
                  </select>
                </div>

                <div className="filter-item checkbox-item">
                  <label>
                    <input
                      type="checkbox"
                      checked={filters.includeImages || false}
                      onChange={(e) => handleFilterChange('includeImages', e.target.checked)}
                    />
                    画像を含める
                  </label>
                </div>
              </div>
            </div>
          )}

          <div className="filter-actions">
            <Button onClick={applyFilters} variant="default">
              フィルターを適用
            </Button>
            <Button onClick={clearFilters} variant="outline">
              リセット
            </Button>
          </div>
        </div>
      </Card>

      {/* 帳票生成 */}
      <Card className="generate-card">
        <div className="card-header">
          <h2>帳票生成</h2>
        </div>
        <div className="generate-section">
          <h3>個人統計</h3>
          <p>あなたの書籍データの統計情報を出力します。</p>
          <div className="report-buttons">
            <Button
              onClick={() => generateReportWithPreview('personal', 'PDF')}
              disabled={buttonLoading['personal-PDF'] || loading}
              className="report-btn-pdf"
            >
              {buttonLoading['personal-PDF'] ? <LoadingSpinner size="small" /> : 'PDF出力'}
            </Button>
            <Button
              onClick={() => generateReportWithPreview('personal', 'EXCEL')}
              disabled={buttonLoading['personal-EXCEL'] || loading}
              className="report-btn-excel"
            >
              {buttonLoading['personal-EXCEL'] ? <LoadingSpinner size="small" /> : 'Excel出力'}
            </Button>
          </div>
        </div>

        {user?.role === 'admin' && (
          <div className="generate-section">
            <h3>全体統計</h3>
            <p>システム全体の書籍データの統計情報を出力します。</p>
            <div className="report-buttons">
              <Button
                onClick={() => generateReportWithPreview('system', 'PDF')}
                disabled={buttonLoading['system-PDF'] || loading}
                className="report-btn-pdf"
              >
                {buttonLoading['system-PDF'] ? <LoadingSpinner size="small" /> : 'PDF出力'}
              </Button>
              <Button
                onClick={() => generateReportWithPreview('system', 'EXCEL')}
                disabled={buttonLoading['system-EXCEL'] || loading}
                className="report-btn-excel"
              >
                {buttonLoading['system-EXCEL'] ? <LoadingSpinner size="small" /> : 'Excel出力'}
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* 進捗表示 */}
      {Object.keys(progressReports).length > 0 && (
        <Card className="progress-card">
          <div className="card-header">
            <h2>生成進捗</h2>
          </div>
          <div className="progress-section">
            {Object.entries(progressReports).map(([reportId, progress]) => (
              <div key={reportId} className="progress-item">
                <div className="progress-header">
                  <span className="progress-label">レポート #{reportId}</span>
                  <span className="progress-percentage">{progress.progress}%</span>
                </div>
                <div className="progress-bar-container">
                  <div
                    className="progress-bar"
                    style={{ width: `${progress.progress}%` }}
                  ></div>
                </div>
                <div className="progress-message">
                  {progress.message}
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* 履歴 */}
      <Card className="history-card">
        <div className="card-header">
          <h2>生成履歴</h2>
          <Button
            onClick={loadReportHistory}
            variant="outline"
            size="small"
          >
            更新
          </Button>
        </div>
        <div className="history-list">
          {reportHistory.length === 0 ? (
            <p className="no-history">帳票生成履歴がありません。</p>
          ) : (
            <table className="history-table">
              <thead>
                <tr>
                  <th>種類</th>
                  <th>形式</th>
                  <th>状態</th>
                  <th>作成日時</th>
                  <th>ファイルサイズ</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {reportHistory.map((report) => (
                  <tr key={report.id}>
                    <td>
                      {report.reportType === 'personal' ? '個人統計' : '全体統計'}
                    </td>
                    <td>{report.format}</td>
                    <td>
                      <span className={`status ${report.status.toLowerCase()}`}>
                        {report.status === 'COMPLETED' ? '完了' :
                         report.status === 'PROCESSING' ? '処理中' :
                         report.status === 'FAILED' ? '失敗' : report.status}
                      </span>
                    </td>
                    <td>{formatDate(report.createdAt)}</td>
                    <td>{formatFileSize(report.fileSize)}</td>
                    <td>
                      {report.status === 'COMPLETED' && report.downloadUrl && (
                        <Button
                          onClick={() => downloadReport(report.id)}
                          size="small"
                          variant="outline"
                        >
                          ダウンロード
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </Card>

      {/* プレビューモーダル */}
      {showPreview && previewData && (
        <div className="preview-overlay" onClick={() => setShowPreview(false)}>
          <div className="preview-modal" onClick={(e) => e.stopPropagation()}>
            <div className="preview-header">
              <h2>生成された帳票</h2>
              <div className="preview-info">
                <span className="preview-filename">{previewData.fileName}</span>
                <span className="preview-filesize">({Math.round(previewData.fileSize / 1024)} KB)</span>
              </div>
              <Button
                onClick={() => setShowPreview(false)}
                variant="outline"
                size="small"
              >
                ✕ 閉じる
              </Button>
            </div>

            <div className="preview-content">
              {previewUrl && (
                <div className="preview-frame">
                  {previewData.format === 'PDF' ? (
                    <PreviewFrame reportId={previewData.reportId} />
                  ) : (
                    <div className="excel-preview-notice">
                      <div className="notice-icon">📊</div>
                      <h3>Excelファイルが生成されました</h3>
                      <p>Excelファイルは直接プレビューできません。</p>
                      <p>「ダウンロード」ボタンからファイルをダウンロードしてご確認ください。</p>
                      <div className="file-details">
                        <div><strong>ファイル名:</strong> {previewData.fileName}</div>
                        <div><strong>サイズ:</strong> {Math.round(previewData.fileSize / 1024)} KB</div>
                        <div><strong>形式:</strong> {previewData.format}</div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="preview-footer">
              <Button
                onClick={() => {
                  setShowPreview(false);
                  setPendingReportParams(null);
                }}
                variant="outline"
              >
                キャンセル
              </Button>
              <Button
                onClick={() => downloadReport(previewData.reportId)}
                variant="outline"
              >
                ダウンロード
              </Button>
              <Button
                onClick={confirmAndSaveReport}
                variant="default"
              >
                履歴に保存
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* ダウンロード成功ダイアログ */}
      {showDownloadSuccess && (
        <div className="download-success-overlay">
          <div className="download-success-modal">
            <div className="success-icon">✓</div>
            <h3>ダウンロードしました</h3>
            <p>帳票のダウンロードが完了し、履歴に追加されました。</p>
            <Button
              onClick={() => setShowDownloadSuccess(false)}
              variant="default"
            >
              OK
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Reports;