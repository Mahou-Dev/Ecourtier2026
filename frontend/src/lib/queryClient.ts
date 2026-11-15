import { QueryClient } from '@tanstack/react-query';
import { getErrorStatus } from './errors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error: unknown) => {
        const status = getErrorStatus(error);
        if (status === 401 || status === 403 || status === 404 || status === 410) {
          return false;
        }
        return failureCount < 1;
      },
      staleTime: 1000 * 60 * 5, // 5 minutes
    },
  },
});
