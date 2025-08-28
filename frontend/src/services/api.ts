import axios from 'axios';
import { Book, BookRequest, Author } from '../types/Book';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json; charset=UTF-8',
  },
});

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

export default api;