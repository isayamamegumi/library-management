import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import BookList from './components/BookList';
import Login from './components/Login';
import UserRegistration from './components/UserRegistration';
import UserProfile from './components/UserProfile';
import Navigation from './components/Navigation';
import './App.css';

function App() {
  const isAuthenticated = () => {
    return sessionStorage.getItem('user') !== null;
  };

  const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
    return isAuthenticated() ? <>{children}</> : <Navigate to="/login" />;
  };

  return (
    <Router>
      <div className="App">
        <Navigation />
        <main className="main-content">
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<UserRegistration />} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <BookList />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute>
                  <UserProfile />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/" />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;
