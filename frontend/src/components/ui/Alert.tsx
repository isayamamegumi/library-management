import React from 'react';

interface AlertProps {
  children: React.ReactNode;
  className?: string;
  type?: 'success' | 'error' | 'warning' | 'info';
}

interface AlertDescriptionProps {
  children: React.ReactNode;
}

export const Alert: React.FC<AlertProps> = ({ children, className = '', type = 'info' }) => {
  const typeClasses = {
    success: 'border-green-200 bg-green-50 text-green-800',
    error: 'border-red-200 bg-red-50 text-red-800',
    warning: 'border-yellow-200 bg-yellow-50 text-yellow-800',
    info: 'border-blue-200 bg-blue-50 text-blue-800'
  };

  return (
    <div className={`border rounded-lg p-4 ${typeClasses[type]} ${className}`}>
      {children}
    </div>
  );
};

export const AlertDescription: React.FC<AlertDescriptionProps> = ({ children }) => {
  return (
    <div className="text-sm">
      {children}
    </div>
  );
};