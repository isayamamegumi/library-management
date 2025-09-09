import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import './Navigation.css';

const Navigation: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logout();
      navigate('/login');
    } catch (error) {
      console.error('Logout failed:', error);
      // ログアウト処理が失敗してもログイン画面にリダイレクト
      navigate('/login');
    }
  };

  // ログイン・登録画面では表示しない
  if (location.pathname === '/login' || location.pathname === '/register') {
    return null;
  }

  return (
    <nav className="navigation">
      <div className="nav-container">
        <div className="nav-brand">
          <Link to="/" className="brand-link">
            📚 図書管理システム
          </Link>
        </div>

        <div className="nav-menu">
          {isAuthenticated && user ? (
            <>
              <Link to="/" className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}>
                書籍一覧
              </Link>
              
              <div className="user-menu">
                <span className="user-greeting">
                  {user.username} さん
                  {user.role === 'admin' && (
                    <span className="admin-badge">管理者</span>
                  )}
                </span>
                <div className="user-dropdown">
                  <Link to="/profile" className="dropdown-item">
                    プロフィール
                  </Link>
                  <button onClick={handleLogout} className="dropdown-item logout">
                    ログアウト
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div className="auth-links">
              <Link to="/login" className="nav-link">
                ログイン
              </Link>
              <Link to="/register" className="nav-link register">
                新規登録
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navigation;