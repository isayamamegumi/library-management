import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import AuthService, { User, AuthState } from '../services/auth';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(true);

  // 認証状態の初期化（ページリロード時の復元）
  useEffect(() => {
    const initializeAuth = () => {
      try {
        const authState: AuthState = AuthService.restoreAuthState();
        
        setUser(authState.user);
        setIsAuthenticated(authState.isAuthenticated);
        
        console.log('Auth state initialized:', {
          isAuthenticated: authState.isAuthenticated,
          user: authState.user?.username
        });
      } catch (error) {
        console.error('Failed to initialize auth state:', error);
        setUser(null);
        setIsAuthenticated(false);
      } finally {
        setLoading(false);
      }
    };

    initializeAuth();
  }, []);

  // 定期的なトークン有効性チェック（5分間隔）
  useEffect(() => {
    if (!isAuthenticated) return;

    const checkTokenValidity = () => {
      if (!AuthService.isTokenValid()) {
        console.warn('Token expired, logging out...');
        handleLogout();
      }
    };

    // 初回チェック
    checkTokenValidity();

    // 5分間隔でチェック
    const interval = setInterval(checkTokenValidity, 5 * 60 * 1000);

    return () => clearInterval(interval);
  }, [isAuthenticated]);

  // ページの可視性が変わった際のトークンチェック
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && isAuthenticated) {
        if (!AuthService.isTokenValid()) {
          console.warn('Token expired while tab was hidden, logging out...');
          handleLogout();
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [isAuthenticated]);

  const handleLogin = async (username: string, password: string): Promise<void> => {
    try {
      setLoading(true);
      const response = await AuthService.login({ username, password });
      
      const newUser: User = {
        id: response.id,
        username: response.username,
        email: response.email,
        role: response.role
      };

      setUser(newUser);
      setIsAuthenticated(true);
      
      console.log('Login successful:', newUser.username);
    } catch (error) {
      console.error('Login failed:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async (): Promise<void> => {
    try {
      setLoading(true);
      await AuthService.logout();
      
      setUser(null);
      setIsAuthenticated(false);
      
      console.log('Logout successful');
    } catch (error) {
      console.error('Logout failed:', error);
      // ログアウトが失敗してもクライアント側の状態はクリア
      setUser(null);
      setIsAuthenticated(false);
    } finally {
      setLoading(false);
    }
  };

  const contextValue: AuthContextType = {
    user,
    isAuthenticated,
    login: handleLogin,
    logout: handleLogout,
    loading
  };

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};