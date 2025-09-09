import React from 'react';
import { useNavigate } from 'react-router-dom';
import './ErrorPage.css';

const ErrorPage403: React.FC = () => {
  const navigate = useNavigate();

  const handleHomeRedirect = () => {
    navigate('/');
  };

  const handleProfileRedirect = () => {
    navigate('/profile');
  };

  return (
    <div className="error-page">
      <div className="error-container">
        <div className="error-icon">
          <span>🚫</span>
        </div>
        <h1 className="error-code">403</h1>
        <h2 className="error-title">アクセス拒否</h2>
        <p className="error-message">
          申し訳ございませんが、このリソースにアクセスする権限がありません。<br />
          管理者権限または適切な権限が必要です。
        </p>
        
        <div className="error-actions">
          <button 
            onClick={handleHomeRedirect}
            className="btn btn-primary"
          >
            ホームに戻る
          </button>
          <button 
            onClick={handleProfileRedirect}
            className="btn btn-secondary"
          >
            プロフィールを確認
          </button>
        </div>
        
        <div className="error-details">
          <p>
            <strong>考えられる原因：</strong>
          </p>
          <ul>
            <li>操作に必要な権限がない</li>
            <li>他のユーザーのリソースにアクセスしようとした</li>
            <li>管理者権限が必要な機能へのアクセス</li>
          </ul>
        </div>
        
        <div className="help-section">
          <p>
            <strong>解決方法：</strong>
          </p>
          <ul>
            <li>適切な権限を持つアカウントでログインしてください</li>
            <li>管理者に権限の変更を依頼してください</li>
            <li>自分のリソースのみアクセスするようにしてください</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default ErrorPage403;