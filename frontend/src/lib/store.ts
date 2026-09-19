import { create } from 'zustand';

interface AppState {
  theme: 'light' | 'dark';
  selectedCity: string;
  setSelectedCity: (city: string) => void;
  toggleTheme: () => void;
}

export const useAppStore = create<AppState>((set) => ({
  theme: 'light',
  selectedCity: 'Dakar',
  setSelectedCity: (city) => set({ selectedCity: city }),
  toggleTheme: () => set((state) => ({ theme: state.theme === 'light' ? 'dark' : 'light' })),
}));
