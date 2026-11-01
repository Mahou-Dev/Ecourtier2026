FROM node:20-alpine

WORKDIR /app

# Copy package configuration
COPY package*.json ./

# Install npm dependencies inside container
RUN npm install

# Copy source code
COPY . .

# Fix permissions on /app for non-root node user
RUN chown -R node:node /app

# Run container as non-root node user
USER node

EXPOSE 3000

# Run Vite dev server bound to 0.0.0.0 for container networking
CMD ["npm", "run", "dev"]
