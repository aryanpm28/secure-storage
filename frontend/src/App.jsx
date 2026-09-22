import { useState, useEffect } from 'react';
import Login from './pages/Login';
import Register from './pages/Register';
import Storage from './pages/Storage';
import ShareView from './pages/ShareView';
import './App.css';

function App() {
  const [user, setUser] = useState(null);
  const [showRegister, setShowRegister] = useState(false);
  const [ready, setReady] = useState(false);

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('name');
    setUser(null);
  };

  useEffect(() => {
    const token = localStorage.getItem('token');
    const name = localStorage.getItem('name');
    if (token && name) {
      setUser({ token, name });
    }
    setReady(true);
    const onAuthFailed = () => handleLogout();
    window.addEventListener('auth-failed', onAuthFailed);
    return () => window.removeEventListener('auth-failed', onAuthFailed);
  }, []);

  const path = window.location.pathname;
  const shareMatch = path.match(/^\/share\/([^/]+)/);
  if (shareMatch) {
    return <ShareView token={shareMatch[1]} />;
  }

  const handleLogin = (data) => {
    localStorage.setItem('token', data.token);
    localStorage.setItem('name', data.name);
    setUser({ token: data.token, name: data.name });
  };

  if (!ready) {
    return <div className="app"><p className="loading">Loading…</p></div>;
  }

  if (user) {
    return <Storage user={user} onLogout={handleLogout} />;
  }

  return (
    <div className="app">
      <h1 className="logo">SecureStorage</h1>
      {showRegister ? (
        <Register onLogin={handleLogin} onSwitch={() => setShowRegister(false)} />
      ) : (
        <Login onLogin={handleLogin} onSwitch={() => setShowRegister(true)} />
      )}
    </div>
  );
}

export default App;
