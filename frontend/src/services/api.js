import axios from 'axios';

const API_BASE = 'https://secure-storage-backend-wa3s.onrender.com';

const api = axios.create({
  baseURL: API_BASE,
  // Large videos (up to 100MB) can take a while on slow connections
  timeout: 5 * 60 * 1000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let authFailedFired = false;
api.interceptors.response.use(
  (r) => r,
  (error) => {
    const status = error.response?.status;
    if (status === 401 || status === 403) {
      if (!authFailedFired) {
        authFailedFired = true;
        localStorage.removeItem('token');
        localStorage.removeItem('name');
        window.dispatchEvent(new Event('auth-failed'));
        setTimeout(() => { authFailedFired = false; }, 2000);
      }
    }
    return Promise.reject(error);
  }
);

export const register = (data) => api.post('/register', data);
export const login = (data) => api.post('/login', data);

export const getFiles = (folderId) => {
  if (folderId === undefined) return api.get('/files');
  if (folderId === null) return api.get('/files', { params: { root: true } });
  return api.get('/files', { params: { folderId } });
};
export const uploadFile = (file, folderId) => {
  const formData = new FormData();
  formData.append('file', file);
  const params = folderId != null ? { folderId } : {};
  return api.post('/files/upload', formData, { params });
};
export const deleteFile = (id) => api.delete(`/files/${id}`);
export const moveFile = (id, folderId) => api.post(`/files/${id}/move`, { folderId });
export const getUsage = () => api.get('/files/usage');
export const createFileShare = (fileId) => api.post(`/files/${fileId}/share`, {});

export const listFolders = (parentId) =>
  api.get('/folders', { params: parentId != null ? { parentId } : {} });
export const createFolder = (name, parentId) =>
  api.post('/folders', { name, parentId: parentId ?? null });
export const deleteFolder = (id) => api.delete(`/folders/${id}`);
export const createFolderShare = (folderId) => api.post(`/folders/${folderId}/share`, {});

export const fetchFileBlobUrl = async (url) => {
  const res = await api.get(url, { responseType: 'blob' });
  return URL.createObjectURL(res.data);
};

export default api;
