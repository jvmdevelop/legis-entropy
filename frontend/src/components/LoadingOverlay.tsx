import { Logo } from './Logo';

interface LoadingOverlayProps {
  message?: string;
}

export function LoadingOverlay({ message = "Загрузка графа НПА…" }: LoadingOverlayProps) {
  return (
    <div className="absolute inset-0 flex items-center justify-center z-20 bg-white/80 backdrop-blur-md">
      <div className="text-center space-y-6 p-8 rounded-lg bg-white elevation-3">
        <Logo size={32} className="mx-auto" />
        <div className="space-y-3">
          <div className="w-6 h-6 border-2 border-gray-600 border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-sm text-gray-600 font-medium">{message}</p>
        </div>
      </div>
    </div>
  );
}
