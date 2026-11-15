interface BackendErrorPayload {
  message?: string;
  error?: string;
  detail?: string;
}

interface StructuredErrorLike {
  status?: number;
  message?: string;
  response?: {
    status?: number;
    data?: BackendErrorPayload;
  };
}

function asStructuredError(err: unknown): StructuredErrorLike | null {
  if (typeof err === 'object' && err !== null) {
    return err as StructuredErrorLike;
  }
  return null;
}

export function getErrorStatus(err: unknown): number | undefined {
  const record = asStructuredError(err);
  if (!record) return undefined;
  if (typeof record.response?.status === 'number') {
    return record.response.status;
  }
  if (typeof record.status === 'number') {
    return record.status;
  }
  return undefined;
}

export function getErrorMessage(err: unknown, fallback = 'Une erreur est survenue'): string {
  if (err instanceof Error && err.message.trim().length > 0) {
    return err.message;
  }
  const record = asStructuredError(err);
  if (record) {
    const data = record.response?.data;
    if (data && typeof data === 'object') {
      if (typeof data.message === 'string' && data.message.trim().length > 0) {
        return data.message;
      }
      if (typeof data.detail === 'string' && data.detail.trim().length > 0) {
        return data.detail;
      }
      if (typeof data.error === 'string' && data.error.trim().length > 0) {
        return data.error;
      }
    }
    if (typeof record.message === 'string' && record.message.trim().length > 0) {
      return record.message;
    }
  }
  if (typeof err === 'string' && err.trim().length > 0) {
    return err;
  }
  return fallback;
}
