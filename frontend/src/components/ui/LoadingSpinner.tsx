import React from 'react';
import './LoadingSpinner.css';

interface LoadingSpinnerProps {
  size?: 'small' | 'medium' | 'large';
  color?: 'primary' | 'secondary' | 'white';
  text?: string;
  overlay?: boolean;
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'medium',
  color = 'primary',
  text,
  overlay = false
}) => {
  const spinnerElement = (
    <div className={`loading-spinner-container ${overlay ? 'loading-overlay' : ''}`}>
      <div className={`loading-spinner loading-${size} loading-${color}`}>
        <div className="spinner-circle"></div>
        <div className="spinner-circle"></div>
        <div className="spinner-circle"></div>
        <div className="spinner-circle"></div>
      </div>
      {text && <div className="loading-text">{text}</div>}
    </div>
  );

  return spinnerElement;
};

export default LoadingSpinner;