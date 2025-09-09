import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import BookList from './components/BookList';
import Login from './components/Login';
import UserRegistration from './components/UserRegistration';
import UserProfile from './components/UserProfile';
import Navigation from './components/Navigation';
import ErrorBoundary from './components/ErrorBoundary';
import ErrorPage401 from './components/ErrorPage401';
import ErrorPage403 from './components/ErrorPage403';
import './App.css';

const LoadingSpinner: React.FC = () => (
  <div style={{ 
    display: 'flex', 
    justifyContent: 'center', 
    alignItems: 'center', 
    height: '100vh' 
  }}>
    <div>Loading...</div>
  </div>
);

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated, loading } = useAuth();
  
  if (loading) {
    return <LoadingSpinner />;
  }
  
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
};

const AppContent: React.FC = () => {
  const { loading } = useAuth();

  if (loading) {
    return <LoadingSpinner />;
  }

  return (
    <ErrorBoundary>
      <div className="App">
        <Navigation />
        <main className="main-content">
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<UserRegistration />} />
            <Route path="/error/401" element={<ErrorPage401 />} />
            <Route path="/error/403" element={<ErrorPage403 />} />
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
    </ErrorBoundary>
  );
};

function App() {
  return (
    <Router>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </Router>
  );
}

export default App;
