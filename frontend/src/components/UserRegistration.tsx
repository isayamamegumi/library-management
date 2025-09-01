import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './UserRegistration.css';

interface UserRegistrationData {
  username: string;
  email: string;
  password: string;
}

interface ValidationErrors {
  username?: string;
  email?: string;
  password?: string;
  general?: string;
}

const UserRegistration: React.FC = () => {
  const [formData, setFormData] = useState<UserRegistrationData>({
    username: '',
    email: '',
    password: ''
  });
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  const validateForm = (): boolean => {
    const newErrors: ValidationErrors = {};

    if (!formData.username.trim()) {
      newErrors.username = 'ユーザー名は必須です';
    } else if (formData.username.length < 3 || formData.username.length > 50) {
      newErrors.username = 'ユーザー名は3-50文字である必要があります';
    }

    if (!formData.email.trim()) {
      newErrors.email = 'メールアドレスは必須です';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = '有効なメールアドレスを入力してください';
    }

    if (!formData.password.trim()) {
      newErrors.password = 'パスワードは必須です';
    } else if (formData.password.length < 8) {
      newErrors.password = 'パスワードは8文字以上である必要があります';
    } else if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{8,}$/.test(formData.password)) {
      newErrors.password = 'パスワードは英数字を含む必要があります';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
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
    
    if (!validateForm()) {
      return;
    }

    setIsLoading(true);
    setErrors({});

    try {
      const response = await fetch('http://localhost:8080/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      });

      const data = await response.json();

      if (response.ok) {
        alert('ユーザー登録が完了しました！ログイン画面に移動します。');
        navigate('/login');
      } else {
        setErrors({ general: data.error || '登録に失敗しました' });
      }
    } catch (error) {
      setErrors({ general: 'ネットワークエラーが発生しました' });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="user-registration-container">
      <div className="registration-form-wrapper">
        <h2>ユーザー登録</h2>
        
        <form onSubmit={handleSubmit} className="registration-form">
          <div className="form-group">
            <label htmlFor="username">ユーザー名</label>
            <input
              type="text"
              id="username"
              name="username"
              value={formData.username}
              onChange={handleChange}
              className={errors.username ? 'error' : ''}
              placeholder="3-50文字のユーザー名"
            />
            {errors.username && <span className="error-message">{errors.username}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="email">メールアドレス</label>
            <input
              type="email"
              id="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              className={errors.email ? 'error' : ''}
              placeholder="example@email.com"
            />
            {errors.email && <span className="error-message">{errors.email}</span>}
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
              placeholder="8文字以上の英数字"
            />
            {errors.password && <span className="error-message">{errors.password}</span>}
          </div>

          {errors.general && (
            <div className="error-message general-error">
              {errors.general}
            </div>
          )}

          <button
            type="submit"
            className="register-button"
            disabled={isLoading}
          >
            {isLoading ? '登録中...' : '登録'}
          </button>

          <div className="login-link">
            <p>
              既にアカウントをお持ちですか？{' '}
              <span onClick={() => navigate('/login')} className="link">
                ログイン
              </span>
            </p>
          </div>
        </form>
      </div>
    </div>
  );
};

export default UserRegistration;