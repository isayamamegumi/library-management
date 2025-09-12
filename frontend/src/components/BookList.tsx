import React, { useState, useEffect } from 'react';
import { Book, ReadStatusNames } from '../types/Book';
import { bookApi } from '../services/api';
import BookForm from './BookForm';
import './BookList.css';

const BookList: React.FC = () => {
  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedReadStatus, setSelectedReadStatus] = useState<string>('');
  const [showForm, setShowForm] = useState<boolean>(false);
  const [editingBook, setEditingBook] = useState<Book | null>(null);

  useEffect(() => {
    loadBooks();
  }, [searchQuery, selectedReadStatus]);

  const loadBooks = async () => {
    try {
      setLoading(true);
      console.log('Making request to books API...');
      const data = await bookApi.getAllBooks(searchQuery, selectedReadStatus);
      console.log('Books data received:', data);
      setBooks(data);
      setError('');
    } catch (err) {
      console.error('Error in loadBooks:', err);
      setError('書籍の読み込みに失敗しました');
    } finally {
      setLoading(false);
    }
  };


  const handleSearch = () => {
    setSearchQuery(searchTerm);
  };

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleDelete = async (id: number) => {
    if (window.confirm('この書籍を削除しますか？')) {
      try {
        await bookApi.deleteBook(id);
        loadBooks();
      } catch (err) {
        setError('削除に失敗しました');
      }
    }
  };

  const handleEdit = (book: Book) => {
    setEditingBook(book);
    setShowForm(true);
  };

  const handleCreate = () => {
    setEditingBook(null);
    setShowForm(true);
  };

  const handleFormClose = () => {
    setShowForm(false);
    setEditingBook(null);
    loadBooks();
  };

  const getAuthorsDisplay = (book: Book): string => {
    return book.bookAuthors?.map(ba => ba.author?.name).filter(name => name).join(', ') || '著者未設定';
  };

  const formatDate = (dateString?: string): string => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('ja-JP');
  };

  if (loading) return <div className="loading">読み込み中...</div>;
  if (error) return <div className="error">{error}</div>;

  return (
    <div className="book-list-container">
      <div className="header">
        <h1>蔵書管理システム</h1>
        <button onClick={handleCreate} className="create-button">
          新しい書籍を追加
        </button>
      </div>

      <div className="filters">
        <div className="search-box">
          <input
            type="text"
            placeholder="タイトル、出版社、著者で検索..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyPress={handleKeyPress}
            className="search-input"
          />
          <button
            onClick={handleSearch}
            className="search-button"
          >
            検索
          </button>
        </div>
        
        <div className="status-filter">
          <select
            value={selectedReadStatus}
            onChange={(e) => setSelectedReadStatus(e.target.value)}
            className="status-select"
          >
            <option value="">すべてのステータス</option>
            {Object.entries(ReadStatusNames).map(([key, value]) => (
              <option key={key} value={value}>{value}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="books-grid">
        {books.length === 0 ? (
          <div className="no-books">
            {searchQuery || selectedReadStatus ? '検索条件に一致する書籍が見つかりませんでした' : 'まだ書籍がありません'}
          </div>
        ) : (
          books.map((book) => (
            <div key={book.id} className="book-card">
              <div className="book-header">
                <h3 className="book-title">{book.title}</h3>
                <div className="book-actions">
                  <button
                    onClick={() => handleEdit(book)}
                    className="edit-button"
                  >
                    編集
                  </button>
                  <button
                    onClick={() => handleDelete(book.id)}
                    className="delete-button"
                  >
                    削除
                  </button>
                </div>
              </div>
              
              <div className="book-details">
                <p><strong>著者:</strong> {getAuthorsDisplay(book)}</p>
                {book.publisher && <p><strong>出版社:</strong> {book.publisher}</p>}
                {book.publishedDate && <p><strong>出版日:</strong> {formatDate(book.publishedDate)}</p>}
                {book.isbn && <p><strong>ISBN:</strong> {book.isbn}</p>}
                {book.genre && <p><strong>ジャンル:</strong> {book.genre.name}</p>}
                {book.readStatus && (
                  <p>
                    <strong>読書状況:</strong>
                    <span className={`status status-${book.readStatus.name}`}>
                      {book.readStatus.name}
                    </span>
                  </p>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {showForm && (
        <BookForm
          book={editingBook}
          onClose={handleFormClose}
        />
      )}
    </div>
  );
};

export default BookList;