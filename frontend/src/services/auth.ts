import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  id: number;
  username: string;
  email: string;
  role: string;
}

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
}

class AuthService {
  private static instance: AuthService;

  private constructor() {}

  public static getInstance(): AuthService {
    if (!AuthService.instance) {
      AuthService.instance = new AuthService();
    }
    return AuthService.instance;
  }

  /**
   * ログイン処理
   */
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await axios.post(`${API_BASE_URL}/auth/login`, credentials, {
      headers: { 'Content-Type': 'application/json' },
      withCredentials: true
    });

    if (response.status === 200) {
      const data = response.data;
      
      // JWTトークンの保存
      if (data.accessToken) {
        localStorage.setItem('accessToken', data.accessToken);
      }
      if (data.refreshToken) {
        localStorage.setItem('refreshToken', data.refreshToken);
      }

      // ユーザー情報の保存
      const user: User = {
        id: data.id,
        username: data.username,
        email: data.email,
        role: data.role
      };
      
      localStorage.setItem('user', JSON.stringify(user));
      sessionStorage.setItem('user', JSON.stringify(user));

      return data;
    }

    throw new Error('ログインに失敗しました');
  }

  /**
   * ログアウト処理
   */
  async logout(): Promise<void> {
    try {
      // サーバーサイドでのセッション無効化（オプション）
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken) {
        await axios.post(`${API_BASE_URL}/auth/logout`, 
          { refreshToken },
          { headers: { 'Content-Type': 'application/json' } }
        );
      }
    } catch (error) {
      console.warn('Server logout failed:', error);
    } finally {
      this.clearAuthData();
    }
  }

  /**
   * 認証データの削除
   */
  clearAuthData(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    sessionStorage.removeItem('user');
  }

  /**
   * アクセストークンの取得
   */
  getAccessToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  /**
   * リフレッシュトークンの取得
   */
  getRefreshToken(): string | null {
    return localStorage.getItem('refreshToken');
  }

  /**
   * 現在のユーザー情報を取得
   */
  getCurrentUser(): User | null {
    const userStr = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (userStr) {
      try {
        return JSON.parse(userStr);
      } catch (error) {
        console.error('Failed to parse user data:', error);
        this.clearAuthData();
      }
    }
    return null;
  }

  /**
   * 認証状態の確認
   */
  isAuthenticated(): boolean {
    const token = this.getAccessToken();
    const user = this.getCurrentUser();
    return !!(token && user);
  }

  /**
   * トークンの期限切れ検証
   */
  isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      return payload.exp < currentTime;
    } catch (error) {
      return true; // パースに失敗した場合は期限切れとみなす
    }
  }

  /**
   * トークンの有効性チェック
   */
  isTokenValid(): boolean {
    const token = this.getAccessToken();
    if (!token) return false;
    return !this.isTokenExpired(token);
  }

  /**
   * アクセストークンの更新
   */
  async refreshAccessToken(): Promise<string | null> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.clearAuthData();
      return null;
    }

    try {
      const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
        refreshToken: refreshToken
      }, {
        headers: { 'Content-Type': 'application/json' }
      });

      if (response.status === 200) {
        const newAccessToken = response.data.accessToken;
        localStorage.setItem('accessToken', newAccessToken);
        return newAccessToken;
      }
    } catch (error) {
      console.error('Token refresh failed:', error);
      this.clearAuthData();
    }

    return null;
  }

  /**
   * 認証状態の復元（ページリロード時）
   */
  restoreAuthState(): AuthState {
    const user = this.getCurrentUser();
    const isTokenValid = this.isTokenValid();
    
    if (user && isTokenValid) {
      return {
        user,
        isAuthenticated: true
      };
    }

    // トークンが無効な場合は認証データをクリア
    if (user && !isTokenValid) {
      this.clearAuthData();
    }

    return {
      user: null,
      isAuthenticated: false
    };
  }

  /**
   * 自動ログアウト処理
   */
  handleAutoLogout(reason: string = 'Session expired'): void {
    console.warn(`Auto logout: ${reason}`);
    this.clearAuthData();
    
    // ログインページにリダイレクト
    if (window.location.pathname !== '/login') {
      window.location.href = '/login?reason=session_expired';
    }
  }
}

export default AuthService.getInstance();