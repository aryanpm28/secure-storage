import { useState, useEffect, useRef } from 'react';
import {
  getFiles, uploadFile, deleteFile, fetchFileBlobUrl, getUsage,
  createFileShare, listFolders, createFolder, deleteFolder, createFolderShare, moveFile,
} from '../services/api';

const ACCEPTED_TYPES = [
  'image/jpeg', 'image/png', 'image/gif', 'image/webp',
  'video/mp4', 'video/webm', 'video/quicktime',
  'application/pdf', 'text/plain',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
].join(',');

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function extensionOf(fileName) {
  const i = fileName.lastIndexOf('.');
  return i >= 0 ? fileName.slice(i + 1).toUpperCase() : 'FILE';
}

function docGlyph(fileName) {
  const ext = extensionOf(fileName).toLowerCase();
  if (ext === 'pdf') return '📄';
  if (ext === 'doc' || ext === 'docx') return '📝';
  return '📃';
}

function Storage({ user, onLogout }) {
  const [files, setFiles] = useState([]);
  const [blobUrls, setBlobUrls] = useState({}); // fileId -> object URL
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [viewingFile, setViewingFile] = useState(null);
  const [usage, setUsage] = useState(null);
  const [shareInfo, setShareInfo] = useState(null);
  const [folders, setFolders] = useState([]);
  const [currentFolderId, setCurrentFolderId] = useState(null); // null = root
  const [newFolderName, setNewFolderName] = useState('');

  // Track object URLs in a ref too, so the unmount cleanup effect always
  // sees the latest set without needing blobUrls as its own dependency.
  const blobUrlsRef = useRef({});

  const loadBlobFor = async (file) => {
    try {
      const objectUrl = await fetchFileBlobUrl(file.url);
      blobUrlsRef.current[file.id] = objectUrl;
      setBlobUrls((prev) => ({ ...prev, [file.id]: objectUrl }));
    } catch {
      // If one file fails to load (deleted on disk, etc.) don't block the rest
    }
  };

  const loadFiles = async (folderId = currentFolderId) => {
    try {
      setLoading(true);
      const [filesRes, foldersRes] = await Promise.all([
        getFiles(folderId),
        listFolders(folderId),
      ]);
      setFiles(filesRes.data);
      setFolders(foldersRes.data || []);
      await Promise.all(filesRes.data.map(loadBlobFor));
      try {
        const u = await getUsage();
        setUsage(u.data);
      } catch { /* ignore */ }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load files');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFiles();
    // Revoke any object URLs we created when the component unmounts,
    // so we don't leak memory over a long session.
    return () => {
      Object.values(blobUrlsRef.current).forEach((url) => URL.revokeObjectURL(url));
    };
  }, []);

  const handleUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setUploading(true);
    setError('');
    try {
      const res = await uploadFile(file, currentFolderId);
      setFiles((prev) => [res.data, ...prev]);
      await loadBlobFor(res.data);
      try { setUsage((await getUsage()).data); } catch {}
    } catch (err) {
      setError(err.response?.data?.message || 'Upload failed');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this file?')) return;
    try {
      await deleteFile(id);
      setFiles((prev) => prev.filter((f) => f.id !== id));
      if (blobUrlsRef.current[id]) {
        URL.revokeObjectURL(blobUrlsRef.current[id]);
        delete blobUrlsRef.current[id];
        setBlobUrls((prev) => {
          const next = { ...prev };
          delete next[id];
          return next;
        });
      }
      if (viewingFile && viewingFile.id === id) {
        setViewingFile(null);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Delete failed');
    }
  };

  const renderThumb = (file) => {
    const src = blobUrls[file.id];
    if (!src) return <div className="image-loading">Loading…</div>;

    if (file.category === 'IMAGE') {
      return (
        <img
          src={src}
          alt={file.fileName}
          onClick={() => setViewingFile(file)}
          title="Click to view full size"
        />
      );
    }
    if (file.category === 'VIDEO') {
      return (
        <video
          src={src}
          muted
          onClick={() => setViewingFile(file)}
          title="Click to play"
        />
      );
    }
    // DOCUMENT
    return (
      <div className="file-icon" onClick={() => setViewingFile(file)} title="Click to open">
        <span className="icon-glyph">{docGlyph(file.fileName)}</span>
        <span className="icon-ext">{extensionOf(file.fileName)}</span>
      </div>
    );
  };

  const renderModalBody = (file) => {
    const src = blobUrls[file.id];
    if (file.category === 'IMAGE') {
      return <img src={src} alt={file.fileName} />;
    }
    if (file.category === 'VIDEO') {
      return <video src={src} controls autoPlay />;
    }
    // DOCUMENT — browsers can preview PDFs inline via an <iframe>; other
    // document types (doc/docx/txt) don't have reliable in-browser
    // rendering, so those just offer a download link instead.
    const ext = extensionOf(file.fileName).toLowerCase();
    if (ext === 'pdf') {
      return <iframe src={src} title={file.fileName} style={{ width: '80vw', height: '80vh', border: 'none', borderRadius: 8 }} />;
    }
    return (
      <div style={{ textAlign: 'center', color: '#e5e7eb', padding: 40 }}>
        <div style={{ fontSize: 48 }}>{docGlyph(file.fileName)}</div>
        <p style={{ margin: '12px 0' }}>Preview isn't available for this file type.</p>
        <a className="modal-download" href={src} download={file.fileName}>
          Download {file.fileName}
        </a>
      </div>
    );
  };

  const handleShare = async (id) => {
    try {
      const res = await createFileShare(id); // server always sets 24h expiry
      setShareInfo(res.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create share link');
    }
  };

  const goRoot = () => {
    setCurrentFolderId(null);
    loadFiles(null);
  };

  const openFolder = (id) => {
    setCurrentFolderId(id);
    loadFiles(id);
  };

  const handleCreateFolder = async (e) => {
    e.preventDefault();
    const name = newFolderName.trim();
    if (!name) return;
    try {
      await createFolder(name, currentFolderId);
      setNewFolderName('');
      await loadFiles(currentFolderId);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create folder');
    }
  };

  const handleDeleteFolder = async (id) => {
    if (!window.confirm('Delete this folder? Files inside will be moved to root.')) return;
    try {
      await deleteFolder(id);
      if (currentFolderId === id) {
        goRoot();
      } else {
        await loadFiles(currentFolderId);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Could not delete folder');
    }
  };

  const handleShareFolder = async (id) => {
    try {
      const res = await createFolderShare(id);
      setShareInfo(res.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create share link');
    }
  };

  const formatGb = (bytes) => (bytes / (1024 * 1024 * 1024)).toFixed(2);

  return (
    <div className="storage-page">
      <header className="storage-header">
        <h1>SecureStorage</h1>
        <div className="user-info">
          <span>Hello, {user.name}</span>
          <button onClick={onLogout} className="logout-btn">Logout</button>
        </div>
      </header>

      {usage && (
        <div className="usage-bar-wrap">
          <div className="usage-bar-label">
            Storage: {formatGb(usage.usedBytes)} GB / {formatGb(usage.maxBytes)} GB ({usage.usedPercent}%)
          </div>
          <div className="usage-bar">
            <div className="usage-bar-fill" style={{ width: Math.min(usage.usedPercent, 100) + '%' }} />
          </div>
        </div>
      )}

      <div className="folder-bar">
        <button type="button" className={`folder-chip ${currentFolderId == null ? 'active' : ''}`} onClick={goRoot}>
          All / Root
        </button>
        {folders.map((f) => (
          <span key={f.id} style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
            <button type="button" className="folder-chip" onClick={() => openFolder(f.id)}>
              📁 {f.name} ({f.fileCount})
            </button>
            <button type="button" className="share-btn" title="Share folder as container" onClick={() => handleShareFolder(f.id)}>Share</button>
            <button type="button" className="delete-btn" onClick={() => handleDeleteFolder(f.id)}>×</button>
          </span>
        ))}
        <form onSubmit={handleCreateFolder} style={{ display: 'inline-flex', gap: 6 }}>
          <input
            value={newFolderName}
            onChange={(e) => setNewFolderName(e.target.value)}
            placeholder="New folder name"
            style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid #ddd', fontSize: 14 }}
          />
          <button type="submit" className="share-btn">Create folder</button>
        </form>
      </div>

      <div className="upload-section">
        <label className="upload-btn">
          {uploading ? 'Uploading...' : 'Upload File'}
          <input
            type="file"
            accept={ACCEPTED_TYPES}
            onChange={handleUpload}
            disabled={uploading}
            hidden
          />
        </label>
        <p className="upload-hint">
          Images up to 5MB · Documents (PDF/TXT/DOC/DOCX) up to 20MB · Videos (MP4/WebM/MOV) up to 100MB
        </p>
      </div>

      {error && <p className="error">{error}</p>}

      {loading ? (
        <p className="loading">Loading files...</p>
      ) : files.length === 0 ? (
        <p className="empty">No files yet. Upload your first one!</p>
      ) : (
        <div className="image-grid">
          {files.map((file) => (
            <div key={file.id} className="image-card">
              {renderThumb(file)}
              <div className="image-info">
                <div style={{ overflow: 'hidden' }}>
                  <span className="category-badge">{file.category}</span>
                  <div className="filename">
                    {file.fileName}
                    <span className="file-size">{formatSize(file.fileSize)}</span>
                  </div>
                </div>
                <button
                  className="share-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleShare(file.id);
                  }}
                >
                  Share
                </button>
                <button
                  className="delete-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDelete(file.id);
                  }}
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Full-view modal — image, video, or document preview/download */}
      {viewingFile && (
        <div className="modal-overlay" onClick={() => setViewingFile(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <button className="modal-close" onClick={() => setViewingFile(null)}>
              ×
            </button>
            {renderModalBody(viewingFile)}
            <p className="modal-filename">{viewingFile.fileName}</p>
          </div>
        </div>
      )}

      {shareInfo && (
        <div className="modal-overlay" onClick={() => setShareInfo(null)}>
          <div className="share-modal-box" onClick={(e) => e.stopPropagation()}>
            <h3>Share container created (24h)</h3>
            <p style={{ fontSize: 13, color: '#555', margin: '8px 0' }}>
              Anyone with this link can view/download the file. The link and QR expire after 24 hours.
              It is separate from your private storage.
            </p>
            <label style={{ fontSize: 13 }}>Link</label>
            <input readOnly value={shareInfo.shareUrl} onFocus={(e) => e.target.select()} />
            <p style={{ fontSize: 12, color: '#666' }}>
              Expires: {new Date(shareInfo.expiresAt).toLocaleString()}
              {shareInfo.maxDownloads != null ? ` · Max downloads: ${shareInfo.maxDownloads}` : ' · Unlimited downloads'}
            </p>
            <div className="qr-wrap">
              <img
                alt="QR code"
                width={160}
                height={160}
                src={`https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=${encodeURIComponent(shareInfo.shareUrl)}`}
              />
            </div>
            <button className="upload-btn" type="button" onClick={() => {
              navigator.clipboard?.writeText(shareInfo.shareUrl);
            }}>Copy link</button>
            {' '}
            <button className="logout-btn" type="button" onClick={() => setShareInfo(null)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default Storage;
