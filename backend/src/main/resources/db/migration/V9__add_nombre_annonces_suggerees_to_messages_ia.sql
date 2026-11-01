-- Migration V9: Add nombre_annonces_suggerees column to messages_ia table
ALTER TABLE messages_ia ADD COLUMN IF NOT EXISTS nombre_annonces_suggerees INTEGER DEFAULT NULL;
