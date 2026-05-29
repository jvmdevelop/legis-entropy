import axios, { type InternalAxiosRequestConfig } from "axios";

const BASE_URL = "/api";

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 30_000,
});

let memoryToken: string | null = null;

export const tokenStore = {
  get: () => memoryToken,
  set: (t: string | null) => {
    memoryToken = t;
    if (t) {
      document.cookie = `le_access_token=${t}; path=/; SameSite=Lax`;
    }
  },
  clear: () => {
    memoryToken = null;
    document.cookie = "le_access_token=; path=/; max-age=0; SameSite=Lax";
  },
};

const REFRESH_KEY = "le_refresh_token";

export const refreshStore = {
  get: () => localStorage.getItem(REFRESH_KEY),
  set: (t: string) => localStorage.setItem(REFRESH_KEY, t),
  clear: () => localStorage.removeItem(REFRESH_KEY),
};

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let isRefreshing = false;
let queue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

function processQueue(err: unknown, token: string | null) {
  queue.forEach(({ resolve, reject }) => {
    if (err) reject(err);
    else if (token) resolve(token);
  });
  queue = [];
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        queue.push({ resolve, reject });
      }).then((token) => {
        original.headers.Authorization = `Bearer ${token}`;
        return apiClient(original);
      });
    }

    original._retry = true;
    isRefreshing = true;

    try {
      const rt = refreshStore.get();
      if (!rt) throw new Error("No refresh token");

      const { data } = await axios.post<{
        token: string;
        refreshToken: string;
      }>(`${BASE_URL}/auth/refresh`, { refreshToken: rt });

      tokenStore.set(data.token);
      refreshStore.set(data.refreshToken);
      processQueue(null, data.token);

      original.headers.Authorization = `Bearer ${data.token}`;
      return apiClient(original);
    } catch (refreshError) {
      processQueue(refreshError, null);
      tokenStore.clear();
      refreshStore.clear();
      window.dispatchEvent(new CustomEvent("auth:logout"));
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);
