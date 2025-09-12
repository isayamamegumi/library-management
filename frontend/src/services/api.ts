import axios, { AxiosError } from 'axios';
import { Book, BookRequest, Author, ReadStatus, Genre } from '../types/Book';
import AuthService from './auth';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json; charset=UTF-8',
  },
  timeout: 10000, // 10秒でタイムアウト
  withCredentials: true, // CORS with credentials
});

// リクエストインターセプター：JWTトークンを自動で添付
api.interceptors.request.use(
  (config) => {
    const token = AuthService.getAccessToken();
    
    // トークンの有効性をチェック
    if (token) {
      if (AuthService.isTokenValid()) {
        config.headers.Authorization = `Bearer ${token}`;
      } else {
        // トークンが期限切れの場合は削除
        AuthService.clearAuthData();
        console.warn('Token expired, cleared auth data');
      }
    }
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// レスポンスインターセプター：自動トークン更新とエラーハンドリング
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as any;
    
    // 401エラー：認証が必要または無効
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      const currentToken = AuthService.getAccessToken();
      
      // トークンが期限切れかチェック
      if (currentToken && AuthService.isTokenExpired(currentToken)) {
        console.log('Token expired, attempting refresh...');
        
        try {
          const newAccessToken = await AuthService.refreshAccessToken();
          
          if (newAccessToken) {
            // 新しいトークンで元のリクエストを再実行
            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
            return api(originalRequest);
          } else {
            throw new Error('Token refresh failed');
          }
        } catch (refreshError) {
          console.error('Token refresh failed:', refreshError);
          AuthService.handleAutoLogout('Token refresh failed');
          return Promise.reject(new Error('401 Unauthorized - Auto logout'));
        }
      } else {
        // トークンがない、または既に期限切れの場合
        AuthService.handleAutoLogout('No valid token');
        return Promise.reject(new Error('401 Unauthorized - No token'));
      }
    } 
    
    // 403エラー：アクセス権限なし
    else if (error.response?.status === 403) {
      console.warn('Access denied:', error.response.data);
      window.location.href = '/error/403';
      return Promise.reject(new Error('403 Forbidden'));
    }
    
    // 5xx エラー：サーバーエラー
    else if (error.response?.status && error.response.status >= 500) {
      console.error('Server error:', error.response.status, error.response.data);
      // サーバーエラーの場合はリトライしない
    }
    
    // ネットワークエラーまたはタイムアウト
    else if (error.code === 'ECONNABORTED' || !error.response) {
      console.error('Network error or timeout:', error.message);
    }
    
    return Promise.reject(error);
  }
);

export const bookApi = {
  getAllBooks: async (search?: string, readStatus?: string): Promise<Book[]> => {
    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (readStatus) params.append('readStatus', readStatus);
    
    const response = await api.get(`/books?${params.toString()}`);
    return response.data;
  },

  getBookById: async (id: number): Promise<Book> => {
    const response = await api.get(`/books/${id}`);
    return response.data;
  },

  createBook: async (book: BookRequest): Promise<Book> => {
    const response = await api.post('/books', book);
    return response.data;
  },

  updateBook: async (id: number, book: BookRequest): Promise<Book> => {
    const response = await api.put(`/books/${id}`, book);
    return response.data;
  },

  deleteBook: async (id: number): Promise<void> => {
    await api.delete(`/books/${id}`);
  },

  getBookByIsbn: async (isbn: string): Promise<Book> => {
    const response = await api.get(`/books/isbn/${isbn}`);
    return response.data;
  },
};

export const authorApi = {
  getAllAuthors: async (search?: string): Promise<Author[]> => {
    const params = search ? `?search=${search}` : '';
    const response = await api.get(`/authors${params}`);
    return response.data;
  },

  getAuthorById: async (id: number): Promise<Author> => {
    const response = await api.get(`/authors/${id}`);
    return response.data;
  },

  createAuthor: async (author: Omit<Author, 'id' | 'createdAt'>): Promise<Author> => {
    const response = await api.post('/authors', author);
    return response.data;
  },

  updateAuthor: async (id: number, author: Omit<Author, 'id' | 'createdAt'>): Promise<Author> => {
    const response = await api.put(`/authors/${id}`, author);
    return response.data;
  },

  deleteAuthor: async (id: number): Promise<void> => {
    await api.delete(`/authors/${id}`);
  },
};

export const readStatusApi = {
  getAllReadStatuses: async (): Promise<ReadStatus[]> => {
    const response = await api.get('/read-statuses');
    return response.data;
  },

  getReadStatusById: async (id: number): Promise<ReadStatus> => {
    const response = await api.get(`/read-statuses/${id}`);
    return response.data;
  },
};

export const genreApi = {
  getAllGenres: async (): Promise<Genre[]> => {
    const response = await api.get('/genres');
    return response.data;
  },

  getGenreById: async (id: number): Promise<Genre> => {
    const response = await api.get(`/genres/${id}`);
    return response.data;
  },

  createGenre: async (genre: Omit<Genre, 'id'>): Promise<Genre> => {
    const response = await api.post('/genres', genre);
    return response.data;
  },

  updateGenre: async (id: number, genre: Omit<Genre, 'id'>): Promise<Genre> => {
    const response = await api.put(`/genres/${id}`, genre);
    return response.data;
  },

  deleteGenre: async (id: number): Promise<void> => {
    await api.delete(`/genres/${id}`);
  },

  searchGenres: async (search?: string): Promise<Genre[]> => {
    const params = search ? `?search=${search}` : '';
    const response = await api.get(`/genres/search${params}`);
    return response.data;
  },
};

export default api;