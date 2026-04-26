export interface MovieSummary {
  id: string;
  title: string;
  releaseYear: number;
  posterUrl: string;
  genres: string[];
  runtimeMinutes: number;
}

export interface MovieDetail {
  id: string;
  tmdbId: number;
  title: string;
  originalTitle: string;
  releaseYear: number;
  synopsis: string;
  posterUrl: string;
  backdropUrl: string;
  runtimeMinutes: number;
  ageRating: string;
  genres: string[];
  castMembers: string[];
  directors: string[];
  folderPath: string;
}

export interface PageResponse<T> {
  content: T[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
}

export interface Profile {
  id: string;
  name: string;
  avatarUrl?: string;
  ageRestriction: number;
  createdAt: string;
}

export interface WatchHistoryEntry {
  movieId: string;
  title: string;
  posterUrl: string;
  positionSeconds: number;
  runtimeMinutes: number;
  lastWatchedAt: string;
  completed: boolean;
}

export interface ResumePosition {
  movieId: string;
  positionSeconds: number | null;
  completed: boolean;
}

export interface SubtitleFile {
  filename: string;
  language: string;
  format: string;
  path: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  role: string;
}
