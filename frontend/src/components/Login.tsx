import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { sanitizeInput, containsMaliciousPattern } from '../utils/sanitization';
import './Login.css';

interface LoginData {
  username: string;
  password: string;
}

interface ValidationErrors {
  username?: string;
  password?: string;
  general?: string;
}

const Login: React.FC = () => {
  const [formData, setFormData] = useState<LoginData>({
    username: '',
    password: ''
  });
  const [errors, setErrors] = useState<ValidationErrors>({});
  const { login, isAuthenticated, loading } = useAuth();
  const navigate = useNavigate();

  // 既にログイン済みの場合はリダイレクト
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/');
    }
  }, [isAuthenticated, navigate]);

  const validateForm = (): boolean => {
    const newErrors: ValidationErrors = {};

    if (!formData.username.trim()) {
      newErrors.username = 'ユーザー名は必須です';
    }

    if (!formData.password.trim()) {
      newErrors.password = 'パスワードは必須です';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const sanitizeLoginInput = (input: string): string => {
    const sanitized = sanitizeInput(input, 100);
    
    // 悪意のあるパターンをチェック
    if (containsMaliciousPattern(input)) {
      console.warn('Malicious pattern detected in input');
      return '';
    }
    
    return sanitized;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    const sanitizedValue = sanitizeLoginInput(value);
    
    setFormData(prev => ({
      ...prev,
      [name]: sanitizedValue
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
    
    if (!validateForm()) {
      return;
    }

    setErrors({});

    try {
      await login(formData.username, formData.password);
      navigate('/');
    } catch (error: any) {
      console.error('Login error:', error);
      let errorMessage = 'ログインに失敗しました';
      
      if (error.response?.status === 401) {
        errorMessage = 'ユーザー名またはパスワードが間違っています';
      } else if (error.response?.status === 429) {
        errorMessage = 'ログイン試行回数が上限に達しました。しばらく待ってからお試しください';
      } else if (error.response?.status === 500) {
        errorMessage = 'サーバーエラーが発生しました。しばらく待ってからお試しください';
      } else if (error.message === 'Network Error') {
        errorMessage = 'ネットワークエラーが発生しました。インターネット接続を確認してください';
      }
      
      setErrors({ general: errorMessage });
    }
  };

  return (
    <div className="login-container">
      <div className="login-form-wrapper">
        <h2>ログイン</h2>
        
        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="username">ユーザー名</label>
            <input
              type="text"
              id="username"
              name="username"
              value={formData.username}
              onChange={handleChange}
              className={errors.username ? 'error' : ''}
              placeholder="ユーザー名を入力"
            />
            {errors.username && <span className="error-message">{errors.username}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="password">パスワード</label>
            <input
              type="password"
              id="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              className={errors.password ? 'error' : ''}
              placeholder="パスワードを入力"
            />
            {errors.password && <span className="error-message">{errors.password}</span>}
          </div>

          {errors.general && (
            <div className="error-message general-error" role="alert">
              <span>⚠️</span> {errors.general}
            </div>
          )}

          <button
            type="submit"
            className="login-button"
            disabled={loading}
          >
            {loading ? 'ログイン中...' : 'ログイン'}
          </button>

          <div className="register-link">
            <p>
              アカウントをお持ちでない場合は{' '}
              <span onClick={() => navigate('/register')} className="link">
                新規登録
              </span>
            </p>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Login;