import React from 'react';
import { useNavigate } from 'react-router-dom';
import './ErrorPage.css';

const ErrorPage401: React.FC = () => {
  const navigate = useNavigate();

  const handleLoginRedirect = () => {
    navigate('/login');
  };

  const handleHomeRedirect = () => {
    navigate('/');
  };

  return (
    <div className="error-page">
      <div className="error-container">
        <div className="error-icon">
          <span>🔒</span>
        </div>
        <h1 className="error-code">401</h1>
        <h2 className="error-title">認証が必要です</h2>
        <p className="error-message">
          このページにアクセスするには、ログインが必要です。<br />
          認証情報が無効または期限切れの可能性があります。
        </p>
        
        <div className="error-actions">
          <button 
            onClick={handleLoginRedirect}
            className="btn btn-primary"
          >
            ログインページへ
          </button>
          <button 
            onClick={handleHomeRedirect}
            className="btn btn-secondary"
          >
            ホームに戻る
          </button>
        </div>
        
        <div className="error-details">
          <p>
            <strong>考えられる原因：</strong>
          </p>
          <ul>
            <li>ログインしていない</li>
            <li>セッションが期限切れ</li>
            <li>認証トークンが無効</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default ErrorPage401;