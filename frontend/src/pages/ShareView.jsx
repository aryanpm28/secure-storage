import { useEffect, useState } from 'react';
import axios from 'axios';

const API_BASE = 'https://secure-storage-backend-wa3s.onrender.com';

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function ShareView({ token }) {
  const [mode, setMode] = useState(null); // 'file' | 'folder'
  const [blobUrl, setBlobUrl] = useState(null);
  const [fileName, setFileName] = useState('');
  const [contentType, setContentType] = useState('');
  const [folder, setFolder] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    let objectUrl = null;

    (async () => {
      try {
        const res = await axios.get(`${API_BASE}/share/${token}`, {
          responseType: 'blob',
          validateStatus: () => true,
        });
        if (cancelled) return;

        if (res.status >= 400) {
          let message = 'This link is invalid or has expired (links last 24 hours)';
          try {
            const text = await res.data.text();
            const json = JSON.parse(text);
            if (json.message) message = json.message;
          } catch { /* keep default */ }
          setError(message);
          return;
        }

        const ct = (res.headers['content-type'] || '').toLowerCase();
        if (ct.includes('application/json')) {
          const text = await res.data.text();
          const json = JSON.parse(text);
          setMode('folder');
          setFolder(json);
        } else {
          const cd = res.headers['content-disposition'] || '';
          const match = cd.match(/filename="(.+)"/);
          objectUrl = URL.createObjectURL(res.data);
          setMode('file');
          setBlobUrl(objectUrl);
          setFileName(match ? match[1] : 'shared-file');
          setContentType(ct || res.data.type || '');
        }
      } catch {
        if (!cancelled) setError('Could not load shared content. The link may have expired (24 hours).');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [token]);

  if (loading) {
    return <div className="app"><p className="loading">Loading shared content…</p></div>;
  }
  if (error) {
    return (
      <div className="app">
        <h1 className="logo">SecureStorage</h1>
        <p className="error">{error}</p>
        <p style={{ color: '#666', fontSize: 14 }}>Share links and QR codes expire after 24 hours.</p>
      </div>
    );
  }

  if (mode === 'folder' && folder) {
    return (
      <div className="app share-page">
        <h1 className="logo">Shared folder</h1>
        <p className="share-name">{folder.folderName}</p>
        <p style={{ fontSize: 12, color: '#888', marginBottom: 16 }}>
          Container · expires {folder.expiresAt ? new Date(folder.expiresAt).toLocaleString() : 'in 24 hours'}
        </p>
        {(!folder.files || folder.files.length === 0) ? (
          <p className="empty">This folder is empty.</p>
        ) : (
          <ul className="share-file-list">
            {folder.files.map((f) => (
              <li key={f.id}>
                <span>{f.fileName}</span>
                <span className="muted">{formatSize(f.fileSize)} · {f.category}</span>
                <a
                  className="share-btn"
                  href={`${API_BASE}${f.downloadUrl}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open / Download
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }

  const isImage = contentType.startsWith('image/');
  const isVideo = contentType.startsWith('video/');
  const isPdf = contentType.includes('pdf');

  return (
    <div className="app share-page">
      <h1 className="logo">Shared file</h1>
      <p className="share-name">{fileName}</p>
      <p style={{ fontSize: 12, color: '#888', marginBottom: 12 }}>This link expires 24 hours after it was created.</p>
      <div className="share-preview">
        {isImage && <img src={blobUrl} alt={fileName} />}
        {isVideo && <video src={blobUrl} controls />}
        {isPdf && <iframe title={fileName} src={blobUrl} className="pdf-frame" />}
        {!isImage && !isVideo && !isPdf && (
          <a className="upload-btn" href={blobUrl} download={fileName}>Download {fileName}</a>
        )}
      </div>
      {(isImage || isVideo || isPdf) && (
        <a className="link-btn" href={blobUrl} download={fileName} style={{ marginTop: 16 }}>Download</a>
      )}
    </div>
  );
}

export default ShareView;
