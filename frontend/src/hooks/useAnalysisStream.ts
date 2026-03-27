import { useState, useCallback, useRef } from 'react';
import type { AnalysisEvent, GraphData, CorpusStats } from '../types';

export interface StreamState {
  running: boolean;
  message: string;
  progress: number;
  graph: GraphData | null;
  stats: CorpusStats | null;
  error: string | null;
}

const INITIAL: StreamState = {
  running: false,
  message: '',
  progress: 0,
  graph: null,
  stats: null,
  error: null,
};

export function useAnalysisStream() {
  const [state, setState] = useState<StreamState>(INITIAL);
  const esRef = useRef<EventSource | null>(null);

  const start = useCallback(() => {
    esRef.current?.close();
    setState({ ...INITIAL, running: true });

    const es = new EventSource('/api/stream/analyze');
    esRef.current = es;

    const handleEvent = (raw: MessageEvent) => {
      const event: AnalysisEvent = JSON.parse(raw.data);
      setState(prev => {
        const next: StreamState = { ...prev, message: event.message, progress: event.progress };
        if (event.stage === 'complete' && event.data) {
          return { ...next, running: false, graph: event.data.graph, stats: event.data.stats };
        }
        if (event.stage === 'error') {
          return { ...next, running: false, error: event.message };
        }
        return next;
      });
      if (event.stage === 'complete' || event.stage === 'error') {
        es.close();
      }
    };

    es.addEventListener('status', handleEvent);
    es.addEventListener('complete', handleEvent);
    es.addEventListener('error', () => {
      // Only treat as error if the stream hasn't already completed
      setState(prev => prev.running ? { ...prev, running: false, error: 'Соединение прервано' } : prev);
      es.close();
    });
  }, []);

  const stop = useCallback(() => {
    esRef.current?.close();
    setState(prev => ({ ...prev, running: false }));
  }, []);

  return { state, start, stop };
}
