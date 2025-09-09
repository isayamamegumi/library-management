import React, { Component, ErrorInfo, ReactNode } from 'react';
import ErrorPage401 from './ErrorPage401';
import ErrorPage403 from './ErrorPage403';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorCode?: number;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    
    // セキュリティエラーの場合の特別な処理
    if (error.message.includes('401') || error.message.includes('Unauthorized')) {
      this.setState({ errorCode: 401 });
    } else if (error.message.includes('403') || error.message.includes('Forbidden')) {
      this.setState({ errorCode: 403 });
    }
  }

  public render() {
    if (this.state.hasError) {
      if (this.state.errorCode === 401) {
        return <ErrorPage401 />;
      } else if (this.state.errorCode === 403) {
        return <ErrorPage403 />;
      }
      
      return (
        <div className="error-page">
          <div className="error-container">
            <div className="error-icon">
              <span>⚠️</span>
            </div>
            <h1 className="error-code">ERROR</h1>
            <h2 className="error-title">予期しないエラーが発生しました</h2>
            <p className="error-message">
              申し訳ございませんが、アプリケーションでエラーが発生しました。<br />
              ページを再読み込みしてください。
            </p>
            <div className="error-actions">
              <button 
                onClick={() => window.location.reload()}
                className="btn btn-primary"
              >
                ページを再読み込み
              </button>
              <button 
                onClick={() => window.location.href = '/'}
                className="btn btn-secondary"
              >
                ホームに戻る
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;