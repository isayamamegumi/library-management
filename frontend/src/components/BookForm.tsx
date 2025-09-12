import React, { useState, useEffect } from 'react';
import { Book, BookRequest, ReadStatusNames, Genre } from '../types/Book';
import { bookApi, genreApi } from '../services/api';
import './BookForm.css';

interface BookFormProps {
  book?: Book | null;
  onClose: () => void;
}

const BookForm: React.FC<BookFormProps> = ({ book, onClose }) => {
  const [formData, setFormData] = useState<BookRequest>({
    title: '',
    publisher: '',
    publishedDate: '',
    isbn: '',
    readStatus: '',
    genreId: undefined,
    authorNames: ['']
  });
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');
  const [genres, setGenres] = useState<Genre[]>([]);

  useEffect(() => {
    // ジャンル一覧を取得
    const fetchGenres = async () => {
      try {
        const genreData = await genreApi.getAllGenres();
        setGenres(genreData);
      } catch (err) {
        console.error('Failed to fetch genres:', err);
      }
    };
    fetchGenres();

    if (book) {
      setFormData({
        title: book.title,
        publisher: book.publisher || '',
        publishedDate: book.publishedDate || '',
        isbn: book.isbn || '',
        readStatus: book.readStatus?.name || '',
        genreId: book.genre?.id,
        authorNames: (() => {
          const names = book.bookAuthors?.map(ba => ba.author?.name).filter(name => name) || [];
          return names.length > 0 ? names : [''];
        })()
      });
    }
  }, [book]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'genreId' ? (value ? Number(value) : undefined) : value
    }));
  };

  const handleAuthorChange = (index: number, value: string) => {
    const newAuthors = [...formData.authorNames];
    newAuthors[index] = value;
    setFormData(prev => ({
      ...prev,
      authorNames: newAuthors
    }));
  };

  const addAuthor = () => {
    setFormData(prev => ({
      ...prev,
      authorNames: [...prev.authorNames, '']
    }));
  };

  const removeAuthor = (index: number) => {
    if (formData.authorNames.length > 1) {
      const newAuthors = formData.authorNames.filter((_, i) => i !== index);
      setFormData(prev => ({
        ...prev,
        authorNames: newAuthors
      }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const bookData = {
        ...formData,
        authorNames: formData.authorNames.filter(name => name.trim() !== '')
      };

      if (book) {
        await bookApi.updateBook(book.id, bookData);
      } else {
        await bookApi.createBook(bookData);
      }
      
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{book ? '書籍を編集' : '新しい書籍を追加'}</h2>
          <button onClick={onClose} className="close-button">&times;</button>
        </div>

        <form onSubmit={handleSubmit} className="book-form">
          {error && <div className="error-message">{error}</div>}

          <div className="form-group">
            <label htmlFor="title">タイトル *</label>
            <input
              type="text"
              id="title"
              name="title"
              value={formData.title}
              onChange={handleInputChange}
              required
              className="form-input"
            />
          </div>

          <div className="form-group">
            <label>著者</label>
            {formData.authorNames.map((author, index) => (
              <div key={index} className="author-input-group">
                <input
                  type="text"
                  value={author}
                  onChange={(e) => handleAuthorChange(index, e.target.value)}
                  placeholder="著者名"
                  className="form-input"
                />
                {formData.authorNames.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeAuthor(index)}
                    className="remove-author-button"
                  >
                    削除
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={addAuthor} className="add-author-button">
              著者を追加
            </button>
          </div>

          <div className="form-group">
            <label htmlFor="publisher">出版社</label>
            <input
              type="text"
              id="publisher"
              name="publisher"
              value={formData.publisher}
              onChange={handleInputChange}
              className="form-input"
            />
          </div>

          <div className="form-group">
            <label htmlFor="publishedDate">出版日</label>
            <input
              type="date"
              id="publishedDate"
              name="publishedDate"
              value={formData.publishedDate}
              onChange={handleInputChange}
              className="form-input"
            />
          </div>

          <div className="form-group">
            <label htmlFor="isbn">ISBN</label>
            <input
              type="text"
              id="isbn"
              name="isbn"
              value={formData.isbn}
              onChange={handleInputChange}
              className="form-input"
              maxLength={13}
            />
          </div>

          <div className="form-group">
            <label htmlFor="readStatus">読書状況</label>
            <select
              id="readStatus"
              name="readStatus"
              value={formData.readStatus}
              onChange={handleInputChange}
              className="form-select"
            >
              <option value="">選択してください</option>
              {Object.entries(ReadStatusNames).map(([key, value]) => (
                <option key={key} value={value}>{value}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="genreId">ジャンル</label>
            <select
              id="genreId"
              name="genreId"
              value={formData.genreId || ''}
              onChange={handleInputChange}
              className="form-select"
            >
              <option value="">選択してください</option>
              {genres.map((genre) => (
                <option key={genre.id} value={genre.id}>{genre.name}</option>
              ))}
            </select>
          </div>

          <div className="form-actions">
            <button type="button" onClick={onClose} className="cancel-button">
              キャンセル
            </button>
            <button type="submit" disabled={loading} className="submit-button">
              {loading ? '保存中...' : '保存'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default BookForm;