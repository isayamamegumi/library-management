import React from 'react';

interface AlertProps {
  children: React.ReactNode;
  className?: string;
}

interface AlertDescriptionProps {
  children: React.ReactNode;
}

export const Alert: React.FC<AlertProps> = ({ children, className = '' }) => {
  return (
    <div className={`border border-gray-200 rounded-lg p-4 ${className}`}>
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