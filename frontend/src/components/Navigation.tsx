import React, { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import './Navigation.css';

interface User {
  username: string;
  email: string;
  role: string;
}

const Navigation: React.FC = () => {
  const [user, setUser] = useState<User | null>(null);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const userData = sessionStorage.getItem('user');
    if (userData) {
      setUser(JSON.parse(userData));
    }
  }, [location]);

  const handleLogout = async () => {
    try {
      await fetch('http://localhost:8080/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
    } catch (error) {
      console.error('Logout failed:', error);
    }
    
    sessionStorage.removeItem('user');
    setUser(null);
    navigate('/login');
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
          {user ? (
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