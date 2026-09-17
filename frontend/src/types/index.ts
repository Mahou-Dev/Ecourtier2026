export interface HealthCheckResponse {
  status: string;
  service: string;
  version: string;
  timestamp: string;
  environment: string;
}

export interface UserInterestRequest {
  email: string;
  role: 'CHERCHEUR' | 'PROPRIETAIRE' | 'COURTIER';
  phone?: string;
  city?: string;
}
