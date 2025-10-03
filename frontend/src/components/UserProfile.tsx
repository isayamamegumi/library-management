import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import './UserProfile.css';

interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
}

interface UserUpdateData {
  email: string;
  password: string;
}

interface ValidationErrors {
  email?: string;
  password?: string;
  general?: string;
}

const UserProfile: React.FC = () => {
  const { user: authUser } = useAuth();
  const [user, setUser] = useState<User | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [updateData, setUpdateData] = useState<UserUpdateData>({
    email: '',
    password: ''
  });
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetchUserProfile();
  }, []);

  const fetchUserProfile = async () => {
    try {
      const token = localStorage.getItem('accessToken');
      if (!token) {
        navigate('/login');
        return;
      }

      const response = await fetch('/api/users/me', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        const userData = await response.json();
        setUser(userData);
        setUpdateData(prev => ({
          ...prev,
          email: userData.email
        }));
      } else if (response.status === 401) {
        localStorage.removeItem('accessToken');
        navigate('/login');
      }
    } catch (error) {
      console.error('Failed to fetch user profile:', error);
    }
  };

  const validateForm = (): boolean => {
    const newErrors: ValidationErrors = {};

    if (updateData.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(updateData.email)) {
      newErrors.email = '有効なメールアドレスを入力してください';
    }

    if (updateData.password && updateData.password.length > 0) {
      if (updateData.password.length < 8) {
        newErrors.password = 'パスワードは8文字以上である必要があります';
      } else if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{8,}$/.test(updateData.password)) {
        newErrors.password = 'パスワードは英数字を含む必要があります';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setUpdateData(prev => ({
      ...prev,
      [name]: value
    }));
    
    if (errors[name as keyof ValidationErrors]) {
      setErrors(prev => ({
        ...prev,
        [name]: undefined
      }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm() || !user) {
      return;
    }

    setIsLoading(true);
    setErrors({});

    // パスワードが空の場合は送信データから除外
    const submitData: any = {};
    if (updateData.email !== user.email) {
      submitData.email = updateData.email;
    }
    if (updateData.password.trim()) {
      submitData.password = updateData.password;
    }

    // 更新がない場合は編集モードを終了
    if (Object.keys(submitData).length === 0) {
      setIsEditing(false);
      setIsLoading(false);
      return;
    }

    try {
      const token = localStorage.getItem('accessToken');
      const response = await fetch(`/api/users/me`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(submitData),
      });

      const data = await response.json();

      if (response.ok) {
        setUser(prev => prev ? { ...prev, email: data.email } : null);
        setUpdateData(prev => ({ ...prev, password: '' }));
        setIsEditing(false);
        alert('プロフィールを更新しました');
      } else {
        setErrors({ general: data.error || '更新に失敗しました' });
      }
    } catch (error) {
      setErrors({ general: 'ネットワークエラーが発生しました' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      const token = localStorage.getItem('accessToken');
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
      });
      localStorage.removeItem('accessToken');
      navigate('/login');
    } catch (error) {
      console.error('Logout failed:', error);
      // エラーでもログアウト処理を継続
      localStorage.removeItem('accessToken');
      navigate('/login');
    }
  };

  if (!user) {
    return <div className="loading">読み込み中...</div>;
  }

  return (
    <div className="user-profile-container">
      <div className="profile-wrapper">
        <h2>ユーザープロフィール</h2>

        {!isEditing ? (
          <div className="profile-view">
            <div className="profile-item">
              <label>ユーザー名:</label>
              <span>{user.username}</span>
            </div>
            <div className="profile-item">
              <label>メールアドレス:</label>
              <span>{user.email}</span>
            </div>
            <div className="profile-item">
              <label>ロール:</label>
              <span className={`role-badge ${user.role.toLowerCase()}`}>
                {user.role === 'admin' ? '管理者' : 'ユーザー'}
              </span>
            </div>
            <div className="profile-item">
              <label>登録日:</label>
              <span>{new Date(user.createdAt).toLocaleDateString('ja-JP')}</span>
            </div>
            
            <div className="profile-actions">
              <button
                onClick={() => setIsEditing(true)}
                className="edit-button"
              >
                編集
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="profile-edit-form">
            <div className="form-group">
              <label htmlFor="email">メールアドレス</label>
              <input
                type="email"
                id="email"
                name="email"
                value={updateData.email}
                onChange={handleChange}
                className={errors.email ? 'error' : ''}
              />
              {errors.email && <span className="error-message">{errors.email}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="password">新しいパスワード（変更する場合のみ）</label>
              <input
                type="password"
                id="password"
                name="password"
                value={updateData.password}
                onChange={handleChange}
                className={errors.password ? 'error' : ''}
                placeholder="8文字以上の英数字"
              />
              {errors.password && <span className="error-message">{errors.password}</span>}
            </div>

            {errors.general && (
              <div className="error-message general-error">
                {errors.general}
              </div>
            )}

            <div className="form-actions">
              <button
                type="submit"
                className="save-button"
                disabled={isLoading}
              >
                {isLoading ? '保存中...' : '保存'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsEditing(false);
                  setUpdateData(prev => ({ ...prev, password: '' }));
                  setErrors({});
                }}
                className="cancel-button"
              >
                キャンセル
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default UserProfile;