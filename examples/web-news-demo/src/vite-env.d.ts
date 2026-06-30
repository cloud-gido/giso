/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_TRACK_ENDPOINT?: string;
  readonly VITE_TRACK_DEBUG?: string;
  readonly VITE_APP_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
